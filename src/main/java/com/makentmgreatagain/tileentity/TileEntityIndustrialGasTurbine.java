package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockIndustrialGasTurbine;
import com.makentmgreatagain.init.ModFluids;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyConnectorMK2;
import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 工业燃气轮机核心格（GUI 对应 {@code gui/industial_gas_turbine.png}）。
 *
 * <h3>罐与槽</h3>
 * <ul>
 *   <li>罐0 润滑/甲醇（64000）：只收润滑油或甲醇，识别码槽0限定这两种；</li>
 *   <li>罐1 燃料（64000）：识别码槽1可切任意流体，但只有可燃(FT_Combustible)流体才能启动；
 *       桶入槽2 / 桶出槽3；</li>
 *   <li>槽4 电池（充电）；槽5/6 升级（仅效率/加力燃烧）。</li>
 * </ul>
 *
 * <h3>发电公式（每 tick）</h3>
 * <pre>
 * 消耗 = 节流阀挡位(0~50 mB/t) × 加力倍率
 * 每mB电 = FT_Combustible.getCombustionEnergy()/1000
 * 发电 = 消耗 × 每mB电 × 润滑效率 × 加力倍率 × (1+效率升级)
 * 润滑效率：空=0.85 / 润滑油=1.0 / 甲醇=1.15
 * 加力1~3：消耗与发电同 ×1.33/1.5/1.75
 * 效率1~3：发电 +5%/10%/15%，不增耗
 * </pre>
 */
public class TileEntityIndustrialGasTurbine extends TileEntityMachineBase
		implements IFluidStandardTransceiverMK2, api.hbm.fluidmk2.IFluidConnectorMK2, IEnergyProviderMK2, IEnergyConnectorMK2,
		com.hbm.tileentity.IUpgradeInfoProvider {

	public static final long MAX_POWER = 25_000_000L;
	public static final int CAPACITY = 64_000;

	public static final int SLOT_ID_LUBE = 0;
	public static final int SLOT_ID_FUEL = 1;
	public static final int SLOT_BUCKET_IN = 2;
	public static final int SLOT_BUCKET_OUT = 3;
	public static final int SLOT_BATTERY = 4;
	public static final int SLOT_UPGRADE_1 = 5;
	public static final int SLOT_UPGRADE_2 = 6;
	public static final int SLOT_COUNT = 7;

	public long power;
	public boolean running;
	/** 节流阀挡位 0~10，对应 0~50 mB/t（每挡5） */
	public int throttle;

	public FluidTank[] tanks = new FluidTank[] {
		new FluidTank(Fluids.LUBRICANT, CAPACITY),
		new FluidTank(Fluids.GAS, CAPACITY)
	};

	/** 上 tick 实际消耗/发电，供 GUI 燃料信息悬停 */
	public double lastConsumption;
	public long lastProduction;

	protected PortDef[] cachedPowerPorts;
	private TilePort[] fluidInputPorts;
	private FluidType[] configuredFluidTypes;

	private double fuelAcc;
	/** 润滑消耗累加器：单tick不足1mB的部分攒到下一tick（与fuelAcc同法，不存档，误差<1mB） */
	private double lubeAcc;

	public TileEntityIndustrialGasTurbine() {
		super(SLOT_COUNT);
	}

	@Override
	public String getName() {
		return "container.industrial_gas_turbine";
	}

	/* ========================== 端口（多方块，沿用原布局） ========================== */

	/** 流体输入和电力输出共用全部 12 个接口位置，但流体接收节点仍按接口独立注册。 */
	public PortDef[] getPorts() {
		return getPowerPorts();
	}

	private void updateFluidInputPorts() {
		boolean rebuild = fluidInputPorts == null || configuredFluidTypes == null;
		if(!rebuild) {
			for(int i = 0; i < tanks.length; i++) {
				if(!sameFluid(configuredFluidTypes[i], tanks[i].getTankType())) {
					rebuild = true;
					break;
				}
			}
		}
		if(rebuild) {
			if(fluidInputPorts != null) {
				for(TilePort port : fluidInputPorts) port.disableIfPresent(worldObj);
			}
			PortDef[] ports = getPorts();
			java.util.List<FluidType> types = new java.util.ArrayList<FluidType>();
			for(FluidTank tank : tanks) {
				FluidType type = tank.getTankType();
				if(type == null || sameFluid(type, Fluids.NONE)) continue;
				boolean found = false;
				for(FluidType existing : types) if(sameFluid(existing, type)) found = true;
				if(!found) types.add(type);
			}
			fluidInputPorts = new TilePort[types.size() * ports.length];
			for(int typeIndex = 0; typeIndex < types.size(); typeIndex++) {
				for(int portIndex = 0; portIndex < ports.length; portIndex++) {
					PortDef portDef = ports[portIndex];
					TilePort port = new TilePort().setupOwner(this)
							.setupPositions(portDef.portPositions)
							.setupConnections(portDef.portConnections);
					port.setupType(types.get(typeIndex).getNetworkProvider());
					fluidInputPorts[typeIndex * ports.length + portIndex] = port;
				}
			}
			configuredFluidTypes = new FluidType[tanks.length];
			for(int i = 0; i < tanks.length; i++) configuredFluidTypes[i] = tanks[i].getTankType();
		}

		for(TilePort port : fluidInputPorts) port.update(worldObj);
		for(TilePort port : fluidInputPorts) {
			if(!port.needsRebuild) port.checkSubscribe(worldObj);
		}
	}

	private void disableFluidInputPorts() {
		if(fluidInputPorts != null) {
			for(TilePort port : fluidInputPorts) port.disableIfPresent(worldObj);
			fluidInputPorts = null;
		}
	}

	@Override
	public void onChunkUnload() {
		disableFluidInputPorts();
		super.onChunkUnload();
	}

	@Override
	public void invalidate() {
		disableFluidInputPorts();
		super.invalidate();
	}

	/** 电力输出走全部 12 个接口（8 侧 + 4 前后），任意接口接电缆都能输电 */
	public PortDef[] getPowerPorts() {
		if(cachedPowerPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			BlockIndustrialGasTurbine block = (BlockIndustrialGasTurbine) this.getBlockType();
			int[][] side = block.getPortsAtCore(dir);
			int[][] extra = block.getPowerPortsAtCore(dir);
			cachedPowerPorts = new PortDef[side.length + extra.length];
			int n = 0;
			for(int[] p : side)
				cachedPowerPorts[n++] = PortDef.make(xCoord + p[0], yCoord + p[1], zCoord + p[2], ForgeDirection.getOrientation(p[3]));
			for(int[] p : extra)
				cachedPowerPorts[n++] = PortDef.make(xCoord + p[0], yCoord + p[1], zCoord + p[2], ForgeDirection.getOrientation(p[3]));
		}
		return cachedPowerPorts;
	}

	/* ========================== 升级 / 润滑 ========================== */

	private int upgradeTier(ItemMachineUpgrade.UpgradeType type) {
		int tier = 0;
		for(int s : new int[] {SLOT_UPGRADE_1, SLOT_UPGRADE_2}) {
			ItemStack st = slots[s];
			if(st != null && st.getItem() instanceof ItemMachineUpgrade) {
				ItemMachineUpgrade up = (ItemMachineUpgrade) st.getItem();
				if(up.type == type) tier = Math.max(tier, up.tier);
			}
		}
		return tier;
	}

	public double afterburnMult() {
		switch(upgradeTier(ItemMachineUpgrade.UpgradeType.AFTERBURN)) {
		case 1: return 1.33D;
		case 2: return 1.5D;
		case 3: return 1.75D;
		default: return 1D;
		}
	}

	public double efficiencyBonus() {
		switch(upgradeTier(ItemMachineUpgrade.UpgradeType.EFFECT)) {
		case 1: return 0.05D;
		case 2: return 0.10D;
		case 3: return 0.15D;
		default: return 0D;
		}
	}

	public double lubeEfficiency() {
		if(tanks[0].getFill() <= 0) return 0.85D;
		if(sameFluid(tanks[0].getTankType(), ModFluids.METHANOL.type)) return 1.15D;
		if(sameFluid(tanks[0].getTankType(), Fluids.LUBRICANT)) return 1.0D;
		return 0.85D;
	}

	public boolean isFuelFlammable() {
		return tanks[1].getTankType() != null && tanks[1].getTankType().hasTrait(FT_Combustible.class);
	}

	public long energyPerMb() {
		FluidType t = tanks[1].getTankType();
		if(t != null && t.hasTrait(FT_Combustible.class))
			return t.getTrait(FT_Combustible.class).getCombustionEnergy() / 1000L;
		return 0;
	}

	/** 按 HBM 燃料等级(FT_Combustible.FuelGrade)的发电效率：低55/中85/高100/航空90/气体85 */
	public static double gradeEfficiency(FT_Combustible.FuelGrade grade) {
		switch(grade) {
		case LOW: return 0.55D;
		case MEDIUM: return 0.85D;
		case HIGH: return 1.0D;
		case AERO: return 0.90D;
		case GAS: return 0.85D;
		default: return 1.0D;
		}
	}

	public double fuelGradeEff() {
		FluidType t = tanks[1].getTankType();
		if(t != null && t.hasTrait(FT_Combustible.class))
			return gradeEfficiency(t.getTrait(FT_Combustible.class).getGrade());
		return 1.0D;
	}

	public void trySetRunning(boolean on) {
		this.running = on;
		markDirty();
	}

	public void setThrottle(int t) {
		this.throttle = Math.max(0, Math.min(10, t));
		markDirty();
	}

	/* ========================== 升级信息（仅本机器 GUI 内悬停升级时显示） ==========================
	 * 原版 ItemMachineUpgrade.addInformation 会检查当前打开容器的槽0库存是否
	 * IUpgradeInfoProvider，是才调 provideInfo——所以这段说明只在本机器 GUI 里出现，
	 * 背包/NEI 的普通物品提示不带它。 */

	@Override
	public boolean canProvideInfo(ItemMachineUpgrade.UpgradeType type, int level, boolean extendedInfo) {
		return type == ItemMachineUpgrade.UpgradeType.EFFECT || type == ItemMachineUpgrade.UpgradeType.AFTERBURN;
	}

	@Override
	public void provideInfo(ItemMachineUpgrade.UpgradeType type, int level, java.util.List<String> info, boolean extendedInfo) {
		info.add(com.hbm.tileentity.IUpgradeInfoProvider.getStandardLabel(com.makentmgreatagain.init.ModBlocks.industrial_gas_turbine));

		int tier = Math.max(1, Math.min(3, level));
		int powerPct;
		if(type == ItemMachineUpgrade.UpgradeType.AFTERBURN) {
			powerPct = tier == 1 ? 33 : tier == 2 ? 50 : 75;
		} else {
			powerPct = tier == 1 ? 5 : tier == 2 ? 10 : 15;
		}
		info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey("mnga.turbine.upgrade.power", "+" + powerPct + "%"));
		// 效率升级不增耗，不显示消耗行；加力才显示燃料消耗增幅
		if(type == ItemMachineUpgrade.UpgradeType.AFTERBURN) {
			info.add(net.minecraft.util.EnumChatFormatting.RED + com.hbm.util.i18n.I18nUtil.resolveKey("mnga.turbine.upgrade.cons", "+" + powerPct + "%"));
		}
	}

	@Override
	public java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer> getValidUpgrades() {
		java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer> map = new java.util.HashMap<ItemMachineUpgrade.UpgradeType, Integer>();
		map.put(ItemMachineUpgrade.UpgradeType.EFFECT, 3);
		map.put(ItemMachineUpgrade.UpgradeType.AFTERBURN, 3);
		return map;
	}

	/* ========================== 主循环 ========================== */

	/** 工作音效（客户端循环，参照工业汽轮机；音量随流量增大，上限为其 150%） */
	private com.hbm.sound.AudioWrapper audio;

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) {
			updateAudio();
			return;
		}

		this.setupPowerPorts(this.getPowerPorts());
		for(TilePort port : this.powerPorts) port.update(worldObj);
		this.providePower();

		// 识别码：罐0 只认润滑油/甲醇；罐1 任意。
		// 不用 FluidTank.setType：它用引用比较判断「是否换了流体」且换型就清空罐子，
		// 宿主双实例下同一个甲醇每 tick 都会被判成「换了」而反复清空，永远灌不满。
		if(slots[SLOT_ID_LUBE] != null && slots[SLOT_ID_LUBE].getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) {
			FluidType f = ((com.hbm.items.machine.IItemFluidIdentifier) slots[SLOT_ID_LUBE].getItem())
					.getType(worldObj, xCoord, yCoord, zCoord, slots[SLOT_ID_LUBE]);
			if(isLubeFluid(f)) ensureTankType(tanks[0], f);
		}
		if(slots[SLOT_ID_FUEL] != null && slots[SLOT_ID_FUEL].getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) {
			FluidType f = ((com.hbm.items.machine.IItemFluidIdentifier) slots[SLOT_ID_FUEL].getItem())
					.getType(worldObj, xCoord, yCoord, zCoord, slots[SLOT_ID_FUEL]);
			ensureTankType(tanks[1], f);
		}

		this.updateFluidInputPorts();

		// 燃料桶入 / 满桶出
		tanks[1].loadTank(SLOT_BUCKET_IN, SLOT_BUCKET_OUT, slots);
		if(slots[SLOT_BUCKET_IN] == null || slots[SLOT_BUCKET_IN].getItem() != com.hbm.items.ModItems.fluid_barrel_infinite) {
			tanks[1].unloadTank(SLOT_BUCKET_IN, SLOT_BUCKET_OUT, slots);
		}

		lastConsumption = 0;
		lastProduction = 0;

		// 满电自动停机：开关保持开着，电量被输出/充电消耗后自动恢复发电
		if(running && isFuelFlammable() && throttle > 0 && tanks[1].getFill() > 0 && power < MAX_POWER) {

			double ab = afterburnMult();
			double target = throttle * 5D * ab;
			double consume = Math.min(target, tanks[1].getFill());

			fuelAcc += consume;
			int whole = (int) Math.floor(fuelAcc);
			fuelAcc -= whole;
			tanks[1].setFill(tanks[1].getFill() - whole);

			// 润滑/甲醇按燃料消耗比例微量消耗（每100mB燃料耗1mB）。
			// 单tick消耗最大87.5mB恒<100，直接floor会永远是0——必须像燃料一样累加取整
			if(tanks[0].getFill() > 0) {
				lubeAcc += consume / 100D;
				int lube = (int) Math.floor(lubeAcc);
				if(lube > 0) {
					lubeAcc -= lube;
					tanks[0].setFill(Math.max(0, tanks[0].getFill() - lube));
				}
			}

			// 加力倍率已含在 consume 里（target=挡位×ab），这里不能再乘 ab，
			// 否则发电按倍率平方增长（3级会+206%而非+75%）。发电与消耗同比例。
			long prod = (long) (consume * energyPerMb() * fuelGradeEff() * lubeEfficiency() * (1D + efficiencyBonus()));
			power = Math.min(MAX_POWER, power + prod);

			lastConsumption = consume;
			lastProduction = prod;
		}

		// 电池充电
		power = Library.chargeItemsFromTE(slots, SLOT_BATTERY, power, MAX_POWER);

		// 周期同步到客户端（识别码/罐型/电量/开关/挡位），markDirty 只存档不发包
		// 范围 64：机器长 13 格，端口离核心最远 6 格，50 会让远端玩家收不到同步
		this.networkPackNT(64);
		if(worldObj.getTotalWorldTime() % 20 == 0) markDirty();
	}

	/** 当前音效音量（客户端），停机时逐 tick 缓降淡出 */
	private float audioVol;

	/**
	 * 客户端工作音效：运行且可燃且有流量时循环播放汽轮机运行声。
	 * 目标音量 = 0.4 + 流量占比 × 1.1，封顶 1.5（不超过原版汽轮机的 150%）。
	 * 停机不立即切断：每 tick 降 0.04（约 1.5 秒从满音量淡出），启动 likewise 平滑淡入。
	 */
	private void updateAudio() {

		boolean active = running && isFuelFlammable() && throttle > 0 && tanks[1].getFill() > 0 && power < MAX_POWER;
		float target = active ? Math.min(1.5F, 0.4F + (throttle / 10F) * 1.1F) : 0F;

		if(target > audioVol) audioVol = Math.min(target, audioVol + 0.04F);
		else audioVol = Math.max(target, audioVol - 0.04F);

		if(audioVol > 0.01F) {
			if(audio == null) {
				audio = com.hbm.main.MainRegistry.proxy.getLoopedSound(
						"hbm:block.turbinegasRunning", xCoord, yCoord, zCoord, audioVol, 20F, 1.0F, 20);
				audio.startSound();
			}
			audio.updateVolume(getVolume(audioVol));
			audio.keepAlive();
		} else if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	/* ========================== 客户端同步 ========================== */

	@Override
	public void serialize(io.netty.buffer.ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(power);
		buf.writeBoolean(running);
		buf.writeInt(throttle);
		buf.writeDouble(lastConsumption);
		buf.writeLong(lastProduction);
		for(FluidTank t : tanks) t.serialize(buf);
	}

	@Override
	public void deserialize(io.netty.buffer.ByteBuf buf) {
		super.deserialize(buf);
		power = buf.readLong();
		running = buf.readBoolean();
		throttle = buf.readInt();
		lastConsumption = buf.readDouble();
		lastProduction = buf.readLong();
		for(FluidTank t : tanks) t.deserialize(buf);
	}

	/* ========================== 电力 / 流体接口 ========================== */

	@Override
	public long getPower() { return power; }

	@Override
	public void setPower(long p) { this.power = Math.min(p, MAX_POWER); }

	@Override
	public long getMaxPower() { return MAX_POWER; }

	@Override
	public boolean canConnect(ForgeDirection dir) { return true; }

	/** Only join a fluid network while a matching tank has room for this fluid. */
	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return getDemand(type, tanks[0].getPressure()) > 0 || getDemand(type, tanks[1].getPressure()) > 0
				|| (isClaiming(type) && tanks[1].getFill() < tanks[1].getMaxFill());
	}

	@Override
	public FluidTank[] getAllTanks() { return tanks; }

	@Override
	public FluidTank[] getReceivingTanks() { return tanks; }

	@Override
	public FluidTank[] getSendingTanks() { return new FluidTank[0]; }

	/* 收液按识别符设置的罐型进行：
	 * 两罐显式指定为同一种流体时，会将需求和流量分配到两个罐；若只匹配一个罐，
	 * 只写入该罐，罐满后不改投另一个不同流体的罐。空燃料罐仍可认领未配置的普通燃料。
	 * 甲醇因此可同时进入润滑罐和燃料罐，并分别保留润滑效率加成与燃烧用途。
	 * 所有类型比较都用 sameFluid()，兼容宿主中同种流体存在多个 FluidType 实例。 */

	/**
	 * 同一种流体：优先引用相等；引用不同时按本地化名兜底。
	 * 只用于本机内部比较罐型与来液类型，不改变 FluidType 本身的注册关系。
	 */
	private static boolean sameFluid(FluidType a, FluidType b) {

		if(a == b) return true;
		if(a == null || b == null) return false;

		String ua = a.getUnlocalizedName();
		return ua != null && ua.equals(b.getUnlocalizedName());
	}

	private boolean isLubeFluid(FluidType type) {
		if(type == null) return false;
		return sameFluid(type, Fluids.LUBRICANT) || sameFluid(type, ModFluids.METHANOL.type);
	}

	/**
	 * 只在「不是同一种流体」时改罐型。
	 *
	 * <p>{@link FluidTank#setTankType} 用引用比较判断是否换了流体，换了就 {@code setFill(0)}。
	 * 宿主双实例下同一个甲醇每 tick 传进来都是「新实例」，直接调 setTankType 会把罐子反复清空，
	 * 表现为管道一直在送、罐子永远是 0。</p>
	 */
	private static void ensureTankType(FluidTank tank, FluidType type) {

		if(type == null || sameFluid(type, Fluids.NONE)) return;
		if(!sameFluid(tank.getTankType(), type)) tank.setTankType(type);
	}

	/** Empty fuel tanks may claim ordinary fuels; fluids with an explicit tank type keep their own routes. */
	private boolean isClaiming(FluidType type) {
		return type != null && !sameFluid(type, Fluids.NONE) && !isLubeFluid(type)
				&& !sameFluid(tanks[0].getTankType(), type) && !sameFluid(tanks[1].getTankType(), type)
				&& tanks[1].getFill() == 0 && tanks[1].getPressure() == 0;
	}

	@Override
	public long getDemand(FluidType type, int pressure) {

		if(type == null || sameFluid(type, Fluids.NONE)) return 0;
		long demand = 0;
		for(FluidTank tank : tanks) {
			if(sameFluid(tank.getTankType(), type) && tank.getPressure() == pressure) {
				demand += Math.max(0, tank.getMaxFill() - tank.getFill());
			}
		}
		if(demand > 0) return demand;
		if(isClaiming(type) && pressure >= 0 && pressure <= HIGHEST_VALID_PRESSURE) {
			return Math.max(0, tanks[1].getMaxFill() - tanks[1].getFill());
		}
		return 0;
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long amount) {

		if(amount <= 0 || type == null || sameFluid(type, Fluids.NONE)) return amount;
		if(isClaiming(type) && pressure >= 0 && pressure <= HIGHEST_VALID_PRESSURE) {
			ensureTankType(tanks[1], type);
			tanks[1].withPressure(pressure);
		}

		for(FluidTank tank : tanks) {
			if(amount <= 0) break;
			if(!sameFluid(tank.getTankType(), type) || tank.getPressure() != pressure) continue;
			int add = (int) Math.min(amount, Math.max(0, tank.getMaxFill() - tank.getFill()));
			if(add > 0) {
				tank.setFill(tank.getFill() + add);
				amount -= add;
			}
		}
		return amount;
	}

	@Override
	public int[] getReceivingPressureRange(FluidType type) {

		if(type == null || sameFluid(type, Fluids.NONE)) return new int[] {0, 0};
		int lowest = HIGHEST_VALID_PRESSURE;
		int highest = 0;
		boolean found = false;
		for(FluidTank tank : tanks) {
			if(sameFluid(tank.getTankType(), type) && tank.getFill() < tank.getMaxFill()) {
				lowest = Math.min(lowest, tank.getPressure());
				highest = Math.max(highest, tank.getPressure());
				found = true;
			}
		}
		if(found) return new int[] {lowest, highest};
		if(isClaiming(type)) return new int[] {0, HIGHEST_VALID_PRESSURE};
		return new int[] {0, 0};
	}

	/* ========================== 物品 / 自动化 ========================== */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] {SLOT_BUCKET_IN, SLOT_BUCKET_OUT, SLOT_BATTERY};
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(stack == null) return false;
		switch(slot) {
		case SLOT_ID_LUBE:
			return stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier;
		case SLOT_ID_FUEL:
			return stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier;
		case SLOT_BUCKET_IN:
			// 收「满」容器往里倒：getEmptyContainer(满)!=null 才是满容器；
			// 早先误用 getFullContainer(空→满) 导致任何流体桶都放不进
			return stack.getItem() == com.hbm.items.ModItems.fluid_barrel_infinite
				|| stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
				|| com.hbm.inventory.FluidContainerRegistry.getEmptyContainer(stack) != null;
		case SLOT_BUCKET_OUT:
			return false;
		case SLOT_BATTERY:
			return stack.getItem() instanceof IBatteryItem || stack.getItem() == com.hbm.items.ModItems.battery_creative;
		case SLOT_UPGRADE_1:
		case SLOT_UPGRADE_2:
			if(stack.getItem() instanceof ItemMachineUpgrade) {
				ItemMachineUpgrade.UpgradeType t = ((ItemMachineUpgrade) stack.getItem()).type;
				return t == ItemMachineUpgrade.UpgradeType.EFFECT || t == ItemMachineUpgrade.UpgradeType.AFTERBURN;
			}
			return false;
		default: return false;
		}
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) { return true; }

	/* ========================== 存档 / 同步 ========================== */

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setBoolean("running", running);
		nbt.setInteger("throttle", throttle);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "tank" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("power");
		running = nbt.getBoolean("running");
		throttle = nbt.getInteger("throttle");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "tank" + i);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		this.writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		this.readFromNBT(packet.func_148857_g());
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 7, yCoord - 1, zCoord - 7, xCoord + 8, yCoord + 5, zCoord + 8);
	}
}

package com.makentmgreatagain.tileentity;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockElectricBlastFurnace;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电力高炉核心。
 *
 * <p>接口方块（{@link TileEntityElectricBlastFurnacePort}）把库存 / 电力 / 流体
 * 全部转发到这里，所以物品物流、电线、管道只要接上任意一个接口就能用。</p>
 *
 * <p>槽位：0~5 为输入，6~11 为输出；物流只能往输入槽塞、只能从输出槽抽。
 * 储罐：2 个输入 + 2 个输出，类型留空（通用），等配方阶段再定死。</p>
 *
 * <p>本阶段没有界面，也没有处理逻辑：电收得进来、物品进得去出得来、
 * 流体会在端口上订阅和供应。</p>
 */
public class TileEntityElectricBlastFurnace extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2, com.hbm.interfaces.IControlReceiver, com.hbm.tileentity.IUpgradeInfoProvider {

	/** 输入：5 种原料 × 每种 4 格 */
	public static final int INPUT_TYPES = 5;
	public static final int INPUT_PER_TYPE = 4;
	public static final int INPUT_SLOTS = INPUT_TYPES * INPUT_PER_TYPE;
	public static final int SLOT_INPUT = 0;
	/** 输出 8 格：内置连续铸造机直接往里铸 */
	public static final int OUTPUT_SLOTS = 8;
	public static final int SLOT_OUTPUT = INPUT_SLOTS;
	public static final int SLOT_BATTERY = SLOT_OUTPUT + OUTPUT_SLOTS;
	public static final int SLOT_TEMPLATE = SLOT_BATTERY + 1;
	public static final int SLOT_UPGRADE_1 = SLOT_TEMPLATE + 1;
	public static final int SLOT_UPGRADE_2 = SLOT_UPGRADE_1 + 1;
	public static final int SLOT_COUNT = SLOT_UPGRADE_2 + 1;

	public static final int TANK_CAPACITY = 64_000;
	public static final long MAX_POWER = 10_000_000L;
	/** 热量上限（TU）：达到它才开始熔炼 */
	public static final int MAX_HEAT = 600_000;
	/** 升温耗电（HE/t），红升级按倍率放大 */
	public static final long HEAT_POWER_COST = 10_000L;
	/** 保温耗电（HE/t），红升级按倍率放大 */
	public static final long HOLD_POWER_COST = 5_000L;
	/** 每 tick 基础升温（TU），红升级按倍率加快 */
	public static final int HEAT_PER_TICK = 2_000;
	/** 每类型熔炼间隔（tick）：标准速度单个物品/10tick */
	public static final int SMELT_INTERVAL = 10;
	/** 合金处理间隔（tick） */
	public static final int ALLOY_INTERVAL = 10;
	/** 熔融缓冲上限（quanta，全部金属合计） */
	public static final int MOLTEN_CAP = 256_000;
	/** 熔炼上限：合金+未组合熔融物合计不超过容量的 75%，留 25% 余量 */
	public static final int SMELT_CAP = MOLTEN_CAP * 3 / 4;
	/** 铸造批量间隔（tick），与连续铸造机的 10 秒冲刷同节奏 */
	public static final int CAST_INTERVAL = 200;

	public long power;
	/** 当前炉温（TU） */
	public float heat;
	/** 是否正在加热（界面熔炼指示用） */
	public boolean heatingUp;
	/** 各输入类型的熔炼计时（并行） */
	public final int[] smeltTimers = new int[INPUT_TYPES];
	/** 合金处理计时 */
	public int alloyTimer;
	/** 熔融金属缓冲：多金属共存，合金用 */
	public final List<com.hbm.inventory.material.Mats.MaterialStack> molten = new java.util.ArrayList<com.hbm.inventory.material.Mats.MaterialStack>();
	/** 上次铸造时间 */
	private long lastCastTick;
	/** 选中的坩埚配方名（"null" = 未选） */
	public String recipe = "null";
	/** 炉渣缓冲（quanta）：不管有没有模具都默认铸成炉渣锭 */
	public int slagAmount;
	/** 熔融物展示罐：界面右侧的熔融物条（合计存量） */
	public final FluidTank moltenGauge = new FluidTank(Fluids.NONE, MOLTEN_CAP);
	public final FluidTank[] inputTanks = new FluidTank[2];
	public final FluidTank[] outputTanks = new FluidTank[2];

	protected PortDef[] cachedPorts;

	private static final int[] ACCESSIBLE = new int[SLOT_COUNT];

	static {
		for(int i = 0; i < SLOT_COUNT; i++) ACCESSIBLE[i] = i;
	}

	public TileEntityElectricBlastFurnace() {
		super(SLOT_COUNT);
		for(int i = 0; i < this.inputTanks.length; i++) this.inputTanks[i] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
		for(int i = 0; i < this.outputTanks.length; i++) this.outputTanks[i] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.electric_blast_furnace";
	}

	/** 接口坐标直接取自方块类，成型、渲染预览与这里共用同一张表 */
	public PortDef[] getPorts() {

		if(this.cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			List<int[]> offsets = BlockElectricBlastFurnace.getPortOffsets(dir);
			PortDef[] defs = new PortDef[offsets.size()];

			for(int i = 0; i < defs.length; i++) {
				int[] p = offsets.get(i);
				defs[i] = PortDef.make(xCoord + p[0], yCoord + p[1], zCoord + p[2],
						ForgeDirection.getOrientation(p[3]));
			}

			this.cachedPorts = defs;
		}

		return this.cachedPorts;
	}

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) return;

		PortDef[] ports = this.getPorts();
		this.setupAllPorts(ports);
		this.updateAllPorts();

		this.receivePower();
		this.drainBattery();
		this.receiveFluid(this.inputTanks);
		this.provideFluid(this.outputTanks);

		this.tryHeat();
		this.trySmelt();
		this.tryAlloy();
		this.tryCast();
		this.tryCastSlag();
		this.syncMoltenGauge();

		this.networkPackNT(50);
	}

	// ================= 运行逻辑（参照坩埚熔炼 + 连续铸造机） =================

	/** 升温 10k HE/t、满温保温 5k HE/t；红升级倍率放大耗电并加快升温；断电自然降温 */
	private void tryHeat() {

		long mult = this.powerMultiplier();
		long heatMult = this.heatMultiplier();

		if(this.heat < MAX_HEAT) {
			long cost = HEAT_POWER_COST * mult;
			if(this.power >= cost) {
				this.power -= cost;
				this.heat = Math.min(MAX_HEAT, this.heat + HEAT_PER_TICK * heatMult);
				this.heatingUp = true;
			} else {
				this.heatingUp = false;
				this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
			}
			return;
		}

		// 满温保温
		long hold = HOLD_POWER_COST * mult;
		if(this.power >= hold) {
			this.power -= hold;
			this.heatingUp = true;
		} else {
			this.heatingUp = false;
			this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
		}
	}

	/** 达到 600k 才熔炼：每种同时熔，标准速度单个物品/10tick。
	 *  合金+未组合熔融物合计到 75% 上限即暂停；合金只熔比例落后的金属，防止单一金属一直熔。 */
	private void trySmelt() {

		if(this.heat < MAX_HEAT) return;

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.getLoadedRecipe();

		// 75% 上限：合金+未组合熔融物合计不超过 SMELT_CAP，留 25% 余量
		if(this.totalMolten() >= SMELT_CAP) return;

		for(int type = 0; type < INPUT_TYPES; type++) {

			int slot = this.getFirstSmeltableSlotOfType(type);
			if(slot == -1) {
				this.smeltTimers[type] = 0;
				continue;
			}

			// 合金：按配方比例给每种金属分配预留上限（单种不超 75%），达到各自上限即停。
			// 门控含 type < rec.input.length：配方金属数少于 5 时，超出范围的槽位
			// 不能进 withinProportionalCap（rec.input[type] 会数组越界崩溃）
			if(rec != null && MoltenCapSpec.alloySlotInRecipe(rec.input.length, type, INPUT_TYPES)
					&& !withinProportionalCap(rec, type)) {
				this.smeltTimers[type] = 0;
				continue;
			}

			if(++this.smeltTimers[type] < this.smeltInterval()) continue;
			this.smeltTimers[type] = 0;

			List<com.hbm.inventory.material.Mats.MaterialStack> materials =
					com.hbm.inventory.material.Mats.getSmeltingMaterialsFromItem(this.slots[slot]);

			for(com.hbm.inventory.material.Mats.MaterialStack mat : materials) {
				com.hbm.inventory.material.NTMMaterial into = mat.material.smeltsInto;

				boolean recipeMetal = false;
				if(rec != null) {
					for(com.hbm.inventory.material.Mats.MaterialStack in : rec.input) if(in.material == into) recipeMetal = true;
					for(com.hbm.inventory.material.Mats.MaterialStack out : rec.output) if(out.material == into) recipeMetal = true;
				}

				if(recipeMetal || rec == null) {
					this.addToMolten(into, mat.amount);
				} else {
					// 炉渣：与坩埚的 waste 同路，这里默认铸成炉渣锭
					this.slagAmount = Math.min(this.slagAmount + mat.amount, MOLTEN_CAP);
				}
			}

			this.decrStackSize(slot, 1);
		}
	}

	/** 多金属合金：把 75% 总预留额度按配方输入比例拆分给每种金属，作为各自熔炼上限。
	 *  该金属当前熔融量低于其比例额度才允许继续熔；单种额度不超过 SMELT_CAP（75%）。
	 *  各额度之和=75%，故缓冲按比例填充、不会单一金属灌满，也不会开局死锁。 */
	private boolean withinProportionalCap(com.hbm.inventory.recipes.CrucibleRecipe rec, int type) {

		int[] req = new int[rec.input.length];
		for(int i = 0; i < rec.input.length; i++) req[i] = rec.input[i].amount;

		long cap = MoltenCapSpec.proportionalCap(SMELT_CAP, req, type);
		return getMolten(rec.input[type].material) < cap;
	}

	private int getFirstSmeltableSlotOfType(int type) {
		int from = SLOT_INPUT + type * INPUT_PER_TYPE;
		for(int i = from; i < from + INPUT_PER_TYPE; i++) {
			ItemStack stack = this.slots[i];
			if(stack != null && !com.hbm.inventory.material.Mats.getSmeltingMaterialsFromItem(stack).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** 往多金属缓冲里加料，合计不超上限 */
	private void addToMolten(com.hbm.inventory.material.NTMMaterial mat, int amount) {
		// 所有熔融物（合金+未组合）总和硬顶 75%，超出部分不加入
		amount = MoltenCapSpec.allowedAddition(SMELT_CAP, this.totalMolten(), amount);
		if(amount <= 0) return;
		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			if(stack.material == mat) {
				stack.amount += amount;
				return;
			}
		}
		this.molten.add(new com.hbm.inventory.material.Mats.MaterialStack(mat, amount));
	}

	public int totalMolten() {
		int sum = 0;
		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) sum += stack.amount;
		return sum;
	}

	private int getMolten(com.hbm.inventory.material.NTMMaterial mat) {
		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			if(stack.material == mat) return stack.amount;
		}
		return 0;
	}

	private void takeMolten(com.hbm.inventory.material.NTMMaterial mat, int amount) {
		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			if(stack.material == mat) {
				stack.amount -= amount;
				return;
			}
		}
	}

	/**
	 * 合金合成：每 10tick 一次，按内部熔融物比例，
	 * 能凑一块（9锭）就按块批，否则按 1 锭批，再否则按 1 粒批；一次只处理一批。
	 */
	private void tryAlloy() {

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.getLoadedRecipe();
		if(rec == null || rec.input == null || rec.output == null) return;

		if(++this.alloyTimer < this.alloyInterval()) return;
		this.alloyTimer = 0;

		int[] units = {
				com.hbm.inventory.material.MaterialShapes.BLOCK.q(1),
				com.hbm.inventory.material.MaterialShapes.INGOT.q(1),
				com.hbm.inventory.material.MaterialShapes.NUGGET.q(1) };

		for(int unit : units) {

			boolean can = true;
			for(com.hbm.inventory.material.Mats.MaterialStack in : rec.input) {
				if(this.getMolten(in.material) < in.amount * unit) { can = false; break; }
			}
			if(!can) continue;

			for(com.hbm.inventory.material.Mats.MaterialStack in : rec.input) this.takeMolten(in.material, in.amount * unit);
			for(com.hbm.inventory.material.Mats.MaterialStack out : rec.output) this.addToMolten(out.material, out.amount * unit);

			this.molten.removeIf(s -> s.amount <= 0);
			this.markChanged();
			return;
		}
	}

	/** 内置连续铸造机：模板 + 熔融金属 → 直接铸进输出槽。
	 *  选了合金配方时只铸配方输出金属，原料金属留在缓冲里等组合，不会被单独铸锭。
	 *  炉渣例外：不管有没有模具，熔融物里的炉渣攒满一锭直接浇成炉渣锭。 */
	private void tryCast() {

		// 炉渣直铸（无模具也铸）：先把缓冲里的炉渣清成锭，再走模具铸造
		castSlagFromMolten();

		com.hbm.items.machine.ItemMold.Mold mold = this.getInstalledMold();
		if(mold == null || this.molten.isEmpty()) return;

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.getLoadedRecipe();
		boolean did = false;

		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			if(stack.amount <= 0) continue;
			if(stack.material == com.hbm.inventory.material.Mats.MAT_SLAG) continue;

			boolean isOut = false, isIn = false;
			if(rec != null) {
				if(rec.output != null) for(com.hbm.inventory.material.Mats.MaterialStack o : rec.output) if(o.material == stack.material) isOut = true;
				if(rec.input != null) for(com.hbm.inventory.material.Mats.MaterialStack in : rec.input) if(in.material == stack.material) isIn = true;
			}
			if(!CastEligibilitySpec.canCast(rec != null, isOut, isIn)) continue;

			ItemStack out = mold.getOutput(stack.material);
			if(out == null) continue;

			// 与连续铸造机同节奏：攒满 9 模或 10 秒冲刷一次
			int molds = stack.amount / mold.getCost();
			if(molds <= 0) continue;
			if(molds < 9 && this.worldObj.getTotalWorldTime() < this.lastCastTick + CAST_INTERVAL) continue;

			int free = this.freeOutputSpace(out);
			molds = Math.min(molds, free / out.stackSize);
			if(molds <= 0) continue;

			stack.amount -= molds * mold.getCost();

			int remaining = out.stackSize * molds;
			for(int i = SLOT_OUTPUT; i < SLOT_OUTPUT + OUTPUT_SLOTS && remaining > 0; i++) {
				ItemStack in = this.slots[i];
				if(in == null) {
					int put = Math.min(remaining, out.getMaxStackSize());
					ItemStack add = out.copy();
					add.stackSize = put;
					this.slots[i] = add;
					remaining -= put;
				} else if(in.isItemEqual(out) && ItemStack.areItemStackTagsEqual(in, out)) {
					int put = Math.min(remaining, in.getMaxStackSize() - in.stackSize);
					in.stackSize += put;
					remaining -= put;
				}
			}

			did = true;
		}

		if(did) {
			this.molten.removeIf(s -> s.amount <= 0);
			this.lastCastTick = this.worldObj.getTotalWorldTime();
			this.markChanged();
		}
	}

	/** 熔融缓冲里的炉渣直铸：不管有没有模具，攒满一锭（INGOT=72单位）立即浇成炉渣锭。
	 *  与 tryCastSlag（熔炼副产炉渣计数）同产物同节奏，输出满时保留等下一轮。 */
	private void castSlagFromMolten() {

		int cost = com.hbm.inventory.material.MaterialShapes.INGOT.q(1);
		ItemStack out = com.hbm.inventory.material.Mats.MAT_SLAG.make(com.hbm.items.ModItems.ingot_raw);
		boolean did = false;

		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			if(stack.material != com.hbm.inventory.material.Mats.MAT_SLAG || stack.amount <= 0) continue;

			int free = this.freeOutputSpace(out);
			int ingots = CastEligibilitySpec.autoIngotCount(stack.amount, cost, free / out.stackSize);
			if(ingots <= 0) continue;

			stack.amount -= ingots * cost;

			int remaining = ingots * out.stackSize;
			for(int i = SLOT_OUTPUT; i < SLOT_OUTPUT + OUTPUT_SLOTS && remaining > 0; i++) {
				ItemStack in = this.slots[i];
				if(in == null) {
					int put = Math.min(remaining, out.getMaxStackSize());
					ItemStack add = out.copy();
					add.stackSize = put;
					this.slots[i] = add;
					remaining -= put;
				} else if(in.isItemEqual(out) && ItemStack.areItemStackTagsEqual(in, out)) {
					int put = Math.min(remaining, in.getMaxStackSize() - in.stackSize);
					in.stackSize += put;
					remaining -= put;
				}
			}

			did = true;
		}

		if(did) {
			this.molten.removeIf(s -> s.amount <= 0);
			this.markChanged();
		}
	}

	/** 炉渣默认铸造：不依赖模具，攒够一锭就铸进输出槽 */
	private void tryCastSlag() {

		if(this.slagAmount <= 0) return;

		int cost = com.hbm.inventory.material.MaterialShapes.INGOT.q(1);
		ItemStack out = com.hbm.inventory.material.Mats.MAT_SLAG.make(com.hbm.items.ModItems.ingot_raw);

		int ingots = this.slagAmount / cost;
		if(ingots <= 0) return;

		int free = this.freeOutputSpace(out);
		ingots = Math.min(ingots, free / out.stackSize);
		if(ingots <= 0) return;

		this.slagAmount -= ingots * cost;

		int remaining = ingots * out.stackSize;
		for(int i = SLOT_OUTPUT; i < SLOT_OUTPUT + OUTPUT_SLOTS && remaining > 0; i++) {
			ItemStack in = this.slots[i];
			if(in == null) {
				int put = Math.min(remaining, out.getMaxStackSize());
				ItemStack add = out.copy();
				add.stackSize = put;
				this.slots[i] = add;
				remaining -= put;
			} else if(in.isItemEqual(out) && ItemStack.areItemStackTagsEqual(in, out)) {
				int put = Math.min(remaining, in.getMaxStackSize() - in.stackSize);
				in.stackSize += put;
				remaining -= put;
			}
		}

		this.markChanged();
	}

	private int freeOutputSpace(ItemStack out) {
		int free = 0;
		for(int i = SLOT_OUTPUT; i < SLOT_OUTPUT + OUTPUT_SLOTS; i++) {
			ItemStack in = this.slots[i];
			if(in == null) free += out.getMaxStackSize();
			else if(in.isItemEqual(out) && ItemStack.areItemStackTagsEqual(in, out)) free += in.getMaxStackSize() - in.stackSize;
		}
		return free;
	}

	/** 模板槽里装的是不是连续铸造机同款模板 */
	public com.hbm.items.machine.ItemMold.Mold getInstalledMold() {
		ItemStack stack = this.slots[SLOT_TEMPLATE];
		if(stack != null && stack.getItem() == com.hbm.items.ModItems.mold) {
			return ((com.hbm.items.machine.ItemMold) stack.getItem()).getMold(stack);
		}
		return null;
	}

	/** 熔融缓冲合计同步到展示罐 */
	private void syncMoltenGauge() {
		int total = this.totalMolten();
		if(total <= 0) {
			this.moltenGauge.setTankType(Fluids.NONE);
			this.moltenGauge.setFill(0);
			return;
		}
		this.moltenGauge.setTankType(Fluids.NONE);
		this.moltenGauge.setFill(Math.min(total, this.moltenGauge.getMaxFill()));
	}

	/** 电池槽放电补电：按电池自身放电速率与机器缺额取小 */
	private void drainBattery() {
		ItemStack stack = this.slots[SLOT_BATTERY];
		if(stack == null || !(stack.getItem() instanceof api.hbm.energymk2.IBatteryItem)) return;

		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) stack.getItem();
		long need = this.getMaxPower() - this.power;
		if(need <= 0) return;

		long give = Math.min(need, Math.min(battery.getCharge(stack), battery.getDischargeRate(stack)));
		if(give <= 0) return;

		battery.dischargeBattery(stack, give);
		this.power += give;
	}

	// ================= 客户端同步 =================

	@Override
	public void serialize(io.netty.buffer.ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		buf.writeFloat(this.heat);
		buf.writeBoolean(this.heatingUp);
		buf.writeInt(this.slagAmount);
		cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, this.recipe);
		buf.writeShort(this.molten.size());
		for(com.hbm.inventory.material.Mats.MaterialStack stack : this.molten) {
			buf.writeInt(stack.material.id);
			buf.writeInt(stack.amount);
		}
		this.moltenGauge.serialize(buf);
		for(FluidTank tank : this.inputTanks) tank.serialize(buf);
		for(FluidTank tank : this.outputTanks) tank.serialize(buf);
	}

	@Override
	public void deserialize(io.netty.buffer.ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.heat = buf.readFloat();
		this.heatingUp = buf.readBoolean();
		this.slagAmount = buf.readInt();
		this.recipe = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
		this.molten.clear();
		int n = buf.readShort();
		for(int i = 0; i < n; i++) {
			int id = buf.readInt();
			int amount = buf.readInt();
			com.hbm.inventory.material.NTMMaterial mat = com.hbm.inventory.material.Mats.matById.get(id);
			if(mat != null) this.molten.add(new com.hbm.inventory.material.Mats.MaterialStack(mat, amount));
		}
		this.moltenGauge.deserialize(buf);
		for(FluidTank tank : this.inputTanks) tank.deserialize(buf);
		for(FluidTank tank : this.outputTanks) tank.deserialize(buf);
	}

	// ================= 库存 =================

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return ACCESSIBLE;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(stack == null) return false;
		if(slot == SLOT_BATTERY) return stack.getItem() instanceof api.hbm.energymk2.IBatteryItem;
		if(slot == SLOT_TEMPLATE) return stack.getItem() == com.hbm.items.ModItems.mold;
		if(slot == SLOT_UPGRADE_1 || slot == SLOT_UPGRADE_2) return isFurnaceUpgrade(stack);
		if(slot >= SLOT_INPUT && slot < SLOT_INPUT + INPUT_SLOTS) return isSmeltableForSlot(slot, stack);
		return false;
	}

	/** 只收红（速度）/绿（效率）两系 1~3 级升级 */
	private boolean isFurnaceUpgrade(ItemStack stack) {
		if(!(stack.getItem() instanceof com.hbm.items.machine.ItemMachineUpgrade)) return false;
		com.hbm.items.machine.ItemMachineUpgrade up = (com.hbm.items.machine.ItemMachineUpgrade) stack.getItem();
		return (up.type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED
				|| up.type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT)
				&& up.tier >= 1 && up.tier <= 3;
	}

	/** 两槽里红/绿升级的最高等级 */
	public int upgradeTier(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType type) {
		int best = 0;
		for(int slot : new int[] {SLOT_UPGRADE_1, SLOT_UPGRADE_2}) {
			ItemStack stack = this.slots[slot];
			if(stack != null && stack.getItem() instanceof com.hbm.items.machine.ItemMachineUpgrade) {
				com.hbm.items.machine.ItemMachineUpgrade up = (com.hbm.items.machine.ItemMachineUpgrade) stack.getItem();
				if(up.type == type) best = Math.max(best, up.tier);
			}
		}
		return best;
	}

	/** 红（速度）升级：熔炼间隔 10/8/5/2 tick 每件 */
	public int smeltInterval() {
		switch(this.upgradeTier(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED)) {
			case 1: return 8;
			case 2: return 5;
			case 3: return 2;
			default: return SMELT_INTERVAL;
		}
	}

	/** 绿（效率）升级：合金间隔 10/8/5/2 tick 每批，无负面 */
	public int alloyInterval() {
		switch(this.upgradeTier(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT)) {
			case 1: return 8;
			case 2: return 5;
			case 3: return 2;
			default: return ALLOY_INTERVAL;
		}
	}

	/** 红（速度）升级的耗电倍率：1/2/8/32 */
	public long powerMultiplier() {
		switch(this.upgradeTier(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED)) {
			case 1: return 2;
			case 2: return 8;
			case 3: return 32;
			default: return 1;
		}
	}

	/** 红（速度）升级的升温倍率：1/2/4/8 */
	public long heatMultiplier() {
		switch(this.upgradeTier(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED)) {
			case 1: return 2;
			case 2: return 4;
			case 3: return 8;
			default: return 1;
		}
	}

	// ================= 升级描述（HBM 标准） =================

	@Override
	public boolean canProvideInfo(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType type, int level, boolean extendedInfo) {
		return type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED
				|| type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT;
	}

	@Override
	public void provideInfo(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType type, int level, List<String> info, boolean extendedInfo) {

		info.add(com.hbm.tileentity.IUpgradeInfoProvider.getStandardLabel(com.makentmgreatagain.init.ModBlocks.machine_electric_blast_furnace));

		if(type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED) {
			// 红：熔炼与升温提速，耗电按倍率大幅增加
			info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey(com.hbm.tileentity.IUpgradeInfoProvider.KEY_SPEED, "熔炼 " + smeltIntervalFor(level) + "t/件 · 升温 ×" + heatMultFor(level)));
			info.add(net.minecraft.util.EnumChatFormatting.RED + com.hbm.util.i18n.I18nUtil.resolveKey(com.hbm.tileentity.IUpgradeInfoProvider.KEY_CONSUMPTION, "×" + multFor(level)));
		}

		if(type == com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT) {
			// 绿：只提速合金，无负面
			info.add(net.minecraft.util.EnumChatFormatting.GREEN + com.hbm.util.i18n.I18nUtil.resolveKey(com.hbm.tileentity.IUpgradeInfoProvider.KEY_SPEED, "合金 " + alloyIntervalFor(level) + "t/批"));
		}
	}

	@Override
	public java.util.HashMap<com.hbm.items.machine.ItemMachineUpgrade.UpgradeType, Integer> getValidUpgrades() {
		java.util.HashMap<com.hbm.items.machine.ItemMachineUpgrade.UpgradeType, Integer> map = new java.util.HashMap<com.hbm.items.machine.ItemMachineUpgrade.UpgradeType, Integer>();
		map.put(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED, 3);
		map.put(com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT, 3);
		return map;
	}

	private static int smeltIntervalFor(int level) {
		switch(level) { case 1: return 8; case 2: return 5; case 3: return 2; default: return SMELT_INTERVAL; }
	}

	private static int alloyIntervalFor(int level) {
		switch(level) { case 1: return 8; case 2: return 5; case 3: return 2; default: return ALLOY_INTERVAL; }
	}

	private static long multFor(int level) {
		switch(level) { case 1: return 2; case 2: return 8; case 3: return 32; default: return 1; }
	}

	private static long heatMultFor(int level) {
		switch(level) { case 1: return 2; case 2: return 4; case 3: return 8; default: return 1; }
	}

	/** 第 i 行只收能熔成第 i 种配方金属的物品；未选配方时只要可熔就收，且缓冲有金属后只收同种 */
	private boolean isSmeltableForSlot(int slot, ItemStack stack) {

		List<com.hbm.inventory.material.Mats.MaterialStack> materials =
				com.hbm.inventory.material.Mats.getSmeltingMaterialsFromItem(stack);
		if(materials.isEmpty()) return false;

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.getLoadedRecipe();
		int type = (slot - SLOT_INPUT) / INPUT_PER_TYPE;

		for(com.hbm.inventory.material.Mats.MaterialStack mat : materials) {
			com.hbm.inventory.material.NTMMaterial into = mat.material.smeltsInto;

			if(rec == null) {
				// 没选配方：直接熔成该物品的熔融物；缓冲已有种类时只收同种
				if(this.molten.isEmpty()) return true;
				for(com.hbm.inventory.material.Mats.MaterialStack have : this.molten) {
					if(have.material == into && have.amount > 0) return true;
				}
				continue;
			}

			// 严格：该行只认它对应的配方金属，其他配方的物品放不进
			if(type < rec.input.length && rec.input[type].material == into) return true;
		}
		return false;
	}

	public com.hbm.inventory.recipes.CrucibleRecipe getLoadedRecipe() {
		return com.hbm.inventory.recipes.CrucibleRecipes.INSTANCE.recipeNameMap.get(this.recipe);
	}

	@Override
	public boolean hasPermission(net.minecraft.entity.player.EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(net.minecraft.entity.player.EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("index") && data.hasKey("selection")) {
			this.recipe = data.getString("selection");
			this.markChanged();
		}
		if(data.hasKey("scrap")) {
			this.convertMoltenToScrap();
		}
	}

	/** 把内部熔融金属直接转成对应铸造废料（带 amount NBT）放进输出槽。
	 *  只有成功放进槽的才从缓冲移除；输出满时保留熔融物等下一轮，绝不凭空丢失。 */
	private void convertMoltenToScrap() {

		java.util.Iterator<com.hbm.inventory.material.Mats.MaterialStack> it = this.molten.iterator();
		while(it.hasNext()) {
			com.hbm.inventory.material.Mats.MaterialStack stack = it.next();
			if(stack.amount <= 0 || stack.material == null) {
				it.remove();
				continue;
			}

			ItemStack scrap = com.hbm.items.machine.ItemScraps.create(stack);

			for(int i = SLOT_OUTPUT; i < SLOT_OUTPUT + OUTPUT_SLOTS; i++) {
				if(this.slots[i] == null) {
					this.slots[i] = scrap;
					it.remove();
					break;
				}
			}
		}

		this.markChanged();
	}

	/** 幽灵列表缓存：配方名 → 每种输入金属的可熔物品循环列表 */
	private final java.util.Map<String, List<List<ItemStack>>> ghostCache = new java.util.HashMap<String, List<List<ItemStack>>>();

	/** 配方输入里第 index 种金属能熔自哪些物品（幽灵循环显示用），优先锭/壳/板/铸造板 */
	public List<ItemStack> getGhostItems(int index) {

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.getLoadedRecipe();
		if(rec == null || index < 0 || index >= rec.input.length) return new java.util.ArrayList<ItemStack>();

		List<List<ItemStack>> all = this.ghostCache.get(this.recipe);
		if(all == null) {
			all = new java.util.ArrayList<List<ItemStack>>();
			for(int i = 0; i < rec.input.length; i++) all.add(buildGhostList(rec.input[i].material));
			this.ghostCache.put(this.recipe, all);
		}
		return index < all.size() ? all.get(index) : new java.util.ArrayList<ItemStack>();
	}

	private List<ItemStack> buildGhostList(com.hbm.inventory.material.NTMMaterial target) {

		List<ItemStack> priority = new java.util.ArrayList<ItemStack>();
		List<ItemStack> rest = new java.util.ArrayList<ItemStack>();
		java.util.Set<String> seen = new java.util.HashSet<String>();

		// 优先形态：锭、壳、板、铸造板、焊接板、块，其次粒、粉
		com.hbm.inventory.material.MaterialShapes[] shapes = {
			com.hbm.inventory.material.MaterialShapes.INGOT,
			com.hbm.inventory.material.MaterialShapes.SHELL,
			com.hbm.inventory.material.MaterialShapes.PLATE,
			com.hbm.inventory.material.MaterialShapes.CASTPLATE,
			com.hbm.inventory.material.MaterialShapes.WELDEDPLATE,
			com.hbm.inventory.material.MaterialShapes.BLOCK,
			com.hbm.inventory.material.MaterialShapes.NUGGET,
			com.hbm.inventory.material.MaterialShapes.DUST };

		for(com.hbm.inventory.material.MaterialShapes shape : shapes) {
			for(String name : target.names) {
				for(ItemStack ore : net.minecraftforge.oredict.OreDictionary.getOres(shape.name() + name)) {
					addGhost(priority, seen, ore);
				}
			}
		}

		// 其余矿辞反向表
		for(java.util.Map.Entry<String, List<com.hbm.inventory.material.Mats.MaterialStack>> entry
				: com.hbm.inventory.material.Mats.materialOreEntries.entrySet()) {
			if(!smeltsTo(entry.getValue(), target)) continue;
			for(ItemStack ore : net.minecraftforge.oredict.OreDictionary.getOres(entry.getKey())) {
				addGhost(rest, seen, ore);
			}
		}

		// 物品反向表
		for(java.util.Map.Entry<com.hbm.inventory.RecipesCommon.ComparableStack, List<com.hbm.inventory.material.Mats.MaterialStack>> entry
				: com.hbm.inventory.material.Mats.materialEntries.entrySet()) {
			if(!smeltsTo(entry.getValue(), target)) continue;
			addGhost(rest, seen, entry.getKey().toStack());
		}

		priority.addAll(rest);
		return priority;
	}

	private boolean smeltsTo(List<com.hbm.inventory.material.Mats.MaterialStack> mats, com.hbm.inventory.material.NTMMaterial target) {
		for(com.hbm.inventory.material.Mats.MaterialStack mat : mats) {
			if(mat.material.smeltsInto == target) return true;
		}
		return false;
	}

	private void addGhost(List<ItemStack> list, java.util.Set<String> seen, ItemStack stack) {
		if(stack == null || stack.getItem() == null) return;
		if(isRail(stack)) return; // 铁轨不该出现在原料循环里
		String key = net.minecraft.item.Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage();
		if(seen.add(key)) {
			ItemStack copy = stack.copy();
			copy.stackSize = 1;
			list.add(copy);
		}
	}

	/** 原版铁轨 / HBM 各类轨道（含登记了铜/紫铜的动力轨）一律排除 */
	private static boolean isRail(ItemStack stack) {
		net.minecraft.item.Item item = stack.getItem();
		if(item instanceof net.minecraft.item.ItemBlock) {
			net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) item).field_150939_a;
			if(block instanceof net.minecraft.block.BlockRailBase) return true;
		}
		String name = item.getUnlocalizedName();
		return name != null && name.toLowerCase().contains("rail");
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return this.isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot >= SLOT_OUTPUT && slot < SLOT_OUTPUT + OUTPUT_SLOTS;
	}

	// ================= 电力 =================

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getPower() {
		return this.power;
	}

	@Override
	public long getMaxPower() {
		return MAX_POWER;
	}

	// ================= 流体 =================

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {this.inputTanks[0], this.inputTanks[1], this.outputTanks[0], this.outputTanks[1], this.moltenGauge};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {this.inputTanks[0], this.inputTanks[1], this.moltenGauge};
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.outputTanks;
	}

	// ================= 渲染 =================

	/**
	 * 默认包围盒只有核心那一格，摄像机一转开核心所在的方块，
	 * 整台 5×5×7 的机器就会被视锥剔除掉（表现为「特定视角模型消失」）。
	 * 这里把包围盒撑到整机，让 TESR 只在整机真的离屏时才不画。
	 */
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 7, zCoord + 3);
	}

	// ================= 存档 =================

	/**
	 * NBT 键名。<b>熔融物清单与展示罐必须用不同的键</b>：
	 * {@code FluidTank.writeToNBT(nbt, key)} 会把 {@code key} 本身写成一个 int，
	 * 若与 {@code setIntArray} 同名（历史上两者都叫 "molten"），罐子会把数组覆盖成整数；
	 * 读档时 {@code NBTTagCompound.getIntArray} 拿到 TAG_Int 会抛 ClassCastException，
	 * 而 Forge 的 {@code TileEntity.createAndLoadEntity} 捕获异常后直接把整个 TE 置 null
	 * （日志只有一行 "has thrown an exception during loading, its state cannot be restored"），
	 * 表现为「配方、熔融物、升级插件、库存全部无声清空且不崩溃」。
	 */
	private static final String TAG_MOLTEN_MATS = "moltenMats";
	private static final String TAG_MOLTEN_GAUGE = "moltenGauge";

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.heat = nbt.getFloat("heat");
		this.slagAmount = nbt.getInteger("slagAmount");
		this.recipe = nbt.getString("recipe");
		if(this.recipe == null || this.recipe.isEmpty()) this.recipe = "null";
		this.molten.clear();
		// 先验类型再取值：类型不符一律按空处理，绝不让 getIntArray 抛异常把整个 TE 带走
		// （旧存档的键名是 "molten"，里面若碰巧还是 int 数组就沿用，是 int 则直接忽略）
		String moltenKey = nbt.getTag(TAG_MOLTEN_MATS) instanceof net.minecraft.nbt.NBTTagIntArray ? TAG_MOLTEN_MATS
				: (nbt.getTag("molten") instanceof net.minecraft.nbt.NBTTagIntArray ? "molten" : null);
		if(moltenKey != null) {
			int[] rec = nbt.getIntArray(moltenKey);
			for(int i = 0; i + 1 < rec.length; i += 2) {
				com.hbm.inventory.material.NTMMaterial mat = com.hbm.inventory.material.Mats.matById.get(rec[i]);
				if(mat != null) this.molten.add(new com.hbm.inventory.material.Mats.MaterialStack(mat, rec[i + 1]));
			}
		}
		this.moltenGauge.readFromNBT(nbt, TAG_MOLTEN_GAUGE);

		for(int i = 0; i < this.inputTanks.length; i++) this.inputTanks[i].readFromNBT(nbt, "in" + i);
		for(int i = 0; i < this.outputTanks.length; i++) this.outputTanks[i].readFromNBT(nbt, "out" + i);

		this.cachedPorts = null;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setFloat("heat", this.heat);
		nbt.setInteger("slagAmount", this.slagAmount);
		nbt.setString("recipe", this.recipe == null ? "null" : this.recipe);
		int[] rec = new int[this.molten.size() * 2];
		for(int i = 0; i < this.molten.size(); i++) {
			rec[i * 2] = this.molten.get(i).material.id;
			rec[i * 2 + 1] = this.molten.get(i).amount;
		}
		nbt.setIntArray(TAG_MOLTEN_MATS, rec);
		this.moltenGauge.writeToNBT(nbt, TAG_MOLTEN_GAUGE);

		for(int i = 0; i < this.inputTanks.length; i++) this.inputTanks[i].writeToNBT(nbt, "in" + i);
		for(int i = 0; i < this.outputTanks.length; i++) this.outputTanks[i].writeToNBT(nbt, "out" + i);
	}
}

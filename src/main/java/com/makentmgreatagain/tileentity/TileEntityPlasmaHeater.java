package com.makentmgreatagain.tileentity;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.FusionRecipe;
import com.makentmgreatagain.recipes.StellaratorRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.blocks.BlockPlasmaHeater;
import com.makentmgreatagain.compat.HBMVersionCompat;
import com.makentmgreatagain.recipes.HydrogenFusion;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 等离子加热器核心格。
 *
 * <h3>定位</h3>
 * 仿星器的等离子体注入口：把两侧输入罐的液氢加热成氢等离子体
 * （{@code PLASMA_H}），缓冲后注入<b>桶管口伸进去的那一台</b>仿星器
 * （{@link TileEntityStellarator#receivePlasma}），并把配方名一起带过去——
 * 仿星器在等离子罐为空时接受该配方并按它运行。
 *
 * <p><b>对接判定</b>：桶管口在核心格前方 {@link BlockPlasmaHeater#MUZZLE_DISTANCE} 格，
 * 口子那一格落在某台仿星器的整机范围内（{@link TileEntityStellarator#containsPoint}，
 * 含无碰撞的环内空腔）才允许注入，见 {@link #updateDock()}。</p>
 *
 * <h3>界面布局</h3>
 * 与底图 {@code gui/plasma_heater.png} 一一对齐：
 * <ul>
 *   <li>电力条 (10,17) 16×58、电池槽 (10,77)；</li>
 *   <li>左输入罐 (57,17) 16×52，桶入 (39,17)、桶出 (39,53)；</li>
 *   <li>等离子缓冲 (88,17) 26 宽；</li>
 *   <li>右输入罐 (129,17) 16×52，桶入 (147,17)、桶出 (147,53)；</li>
 *   <li>配方选择点击区 (42,76)、蓝图槽 (62,77)、升级插件槽 (84,77)、玩家背包从 (8,126)。</li>
 * </ul>
 *
 * <p><b>运行</b>：按所选配方的进料表（{@link HydrogenFusion#heaterFeed(String)}）扣原料：
 * 氢-氢是左右罐各 50 mB 液氢 → 10 mB 氢等离子体；氢-硼是左罐 100 mB 氢 + 右罐 100 mB
 * 液态硼 → 20 mB 氢-硼等离子体。耗电取配方自身的 {@code power}（100k / 300k HE/t）。
 * 升级插件槽只认速度（每级 +50% 转化量）与节能（每级 -15% 耗电）。</p>
 *
 * <p><b>配方</b>：整张聚变表由 {@link HydrogenFusion} 提供（氢-氦3、氢-氦4、氢-硼），
 * 但机器<b>不会自动认配方</b>——玩家在界面里点选才会生效，也可以点“清除”取消
 * （配方选择器发 {@code null}，本机改成 {@link #NULL_RECIPE}）。旧存档里失效的配方名会退回未选状态。</p>
 */
public class TileEntityPlasmaHeater extends TileEntityMachineBase
        implements IFluidStandardTransceiverMK2, IEnergyReceiverMK2, IControlReceiver, IUpgradeInfoProvider {

	/* ========================== 常量 ========================== */

	/** 内部储电上限，与仿星器同量级 */
	public static final long MAX_POWER = 100_000_000L;

	/** 三个罐统一容量：左右输入罐 + 等离子缓冲罐 */
	public static final int TANK_CAPACITY = 128_000;

	/** 未选配方 */
	public static final String NULL_RECIPE = "null";

	public static final int TANK_LEFT = 0;
	public static final int TANK_RIGHT = 1;
	public static final int TANK_PLASMA = 2;

	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_LEFT_IN = 1;
	public static final int SLOT_LEFT_OUT = 2;
	public static final int SLOT_RIGHT_IN = 3;
	public static final int SLOT_RIGHT_OUT = 4;
	public static final int SLOT_BLUEPRINT = 5;
	public static final int SLOT_UPGRADE = 6;
	public static final int SLOT_COUNT = 7;

	/* ========================== 状态 ========================== */

	public long power;

	/** 当前选中的聚变配方名，配方选择器切换 */
	public String recipe = NULL_RECIPE;

	public FluidTank[] tanks = new FluidTank[] {
		new FluidTank(Fluids.NONE, TANK_CAPACITY),
		new FluidTank(Fluids.NONE, TANK_CAPACITY),
		new FluidTank(Fluids.NONE, TANK_CAPACITY),
	};

	/** 等离子缓冲罐的短名字 */
	public FluidTank plasma = this.tanks[TANK_PLASMA];

	/** 已对接的仿星器核心坐标（每 20 tick 重判一次），null = 桶管口不在任何仿星器内部 */
	private int[] dockedStellarator;

	/** 升级插件（槽 6）：红色速度升级 1~3 级 */
	public final UpgradeManagerNT upgrades = new UpgradeManagerNT(this);

	/** 速度倍率小数累积器，保证 +15% / +25% / +45% 产率无取整误差 */
	private double opAccumulator = 0.0D;

	protected PortDef[] cachedPorts;

	/** 端口格是否已经补过 extra（旧存档第一次 tick 补一次） */
	private boolean portsChecked;

	/** 旧版 22×9 结构是否已经清过（第一次 tick 清一次） */
	private boolean structureTrimmed;

	public TileEntityPlasmaHeater() {
		super(SLOT_COUNT);
	}

	@Override
	public String getName() {
		return "container.plasmaHeater";
	}

	/* ========================== 端口 ========================== */

	public PortDef[] getPorts() {
		if(this.cachedPorts == null) {
			if(!(this.getBlockType() instanceof BlockPlasmaHeater)) return new PortDef[0];
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable_offset());
			BlockPlasmaHeater block = (BlockPlasmaHeater) this.getBlockType();
			int[][] ports = block.getPortsAtCore(dir);
			this.cachedPorts = new PortDef[ports.length];
			for(int i = 0; i < ports.length; i++) {
				this.cachedPorts[i] = PortDef.make(xCoord + ports[i][0], yCoord + ports[i][1], zCoord + ports[i][2],
						ForgeDirection.getOrientation(ports[i][3]));
			}
		}
		return this.cachedPorts;
	}

	/** 元数据里核心朝向的基址（BlockDummyable.offset） */
	private static int BlockDummyable_offset() {
		return com.hbm.blocks.BlockDummyable.offset;
	}

	/**
	 * 端口格补 extra：旧的 {@code makeExtra} 写错判据，端口格一直停在普通 dummy
	 * （元数据 0~5、没有代理 TE），管道/线缆上门就找不到 TE。这里在第一次 tick 补一次，
	 * 已经放下的机器不用拆了重放。
	 */
	private void ensurePortExtras() {

		if(this.portsChecked) return;
		this.portsChecked = true;

		if(!(this.getBlockType() instanceof BlockPlasmaHeater)) return;

		BlockPlasmaHeater block = (BlockPlasmaHeater) this.getBlockType();

		for(PortDef port : this.getPorts()) {

			if(port.portPositions == null || port.portPositions.length == 0) continue;

			int px = port.portPositions[0].getX();
			int py = port.portPositions[0].getY();
			int pz = port.portPositions[0].getZ();

			if(this.worldObj.getBlock(px, py, pz) != block) continue;
			if(this.worldObj.getBlockMetadata(px, py, pz) > 5) continue;	// 已经是 extra

			block.makeExtra(this.worldObj, px, py, pz);
		}
	}

	/**
	 * 旧结构是 22×9、新结构是 16×7×7：把新结构范围之外、属于本机器的 dummy 格清成空气，
	 * 这样桶管伸出去的那几格和两侧多出来的一圈都能直接放方块，旧存档不用拆了重放。
	 */
	private void trimLegacyStructure() {

		if(this.structureTrimmed) return;
		this.structureTrimmed = true;

		if(!(this.getBlockType() instanceof BlockPlasmaHeater)) return;

		BlockPlasmaHeater block = (BlockPlasmaHeater) this.getBlockType();
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable_offset());

		int[] oldRot = com.hbm.handler.MultiblockHandlerXR.rotate(new int[] {6, 0, 11, 10, 4, 4}, dir);
		int[] newRot = com.hbm.handler.MultiblockHandlerXR.rotate(new int[] {6, 0, 5, 10, 3, 3}, dir);

		boolean changed = false;
		com.hbm.blocks.BlockDummyable.safeRem = true;

		for(int a = this.xCoord - oldRot[4]; a <= this.xCoord + oldRot[5]; a++) {
			for(int b = this.yCoord - oldRot[1]; b <= this.yCoord + oldRot[0]; b++) {
				for(int c = this.zCoord - oldRot[2]; c <= this.zCoord + oldRot[3]; c++) {

					boolean inside = a >= this.xCoord - newRot[4] && a <= this.xCoord + newRot[5]
							&& b >= this.yCoord - newRot[1] && b <= this.yCoord + newRot[0]
							&& c >= this.zCoord - newRot[2] && c <= this.zCoord + newRot[3];

					if(inside) continue;
					if(this.worldObj.getBlock(a, b, c) != block) continue;

					this.worldObj.setBlockToAir(a, b, c);
					changed = true;
				}
			}
		}

		com.hbm.blocks.BlockDummyable.safeRem = false;

		if(changed) {
			this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
		}
	}

	/* ========================== 主循环 ========================== */

	@Override
	public void updateEntity() {

		// 客户端也要有本模组的配方对象：GUI 的配方图标、仿星器的储电上限 / 指示灯都靠它。
		// 宿主把聚变表写成 json 再读回来时，配方会退化成普通 FusionRecipe，这里连类型一起查
		if(!HydrogenFusion.isRegistered()) HydrogenFusion.addRecipe();

		if(this.worldObj.isRemote) return;

		this.ensurePortExtras();
		this.trimLegacyStructure();
		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		// 配方表被宿主重建时（/ntmreload、客户端配方同步）自愈：缺了就重新注册一次，
		// 否则 getRecipe() 永远是 null，界面/运行都“找不到配方”
		if(!HydrogenFusion.isRegistered()) {
			HydrogenFusion.addRecipe();
		}

		this.upgrades.checkSlots(this.slots, SLOT_UPGRADE, SLOT_UPGRADE);

		this.power = Library.chargeTEFromItems(this.slots, SLOT_BATTERY, this.power, MAX_POWER);

		// 桶装流体：同一对槽兼作灌桶与抽桶（满桶进 → 空桶出；空桶进 → 满桶出）
		this.tanks[TANK_LEFT].loadTank(SLOT_LEFT_IN, SLOT_LEFT_OUT, this.slots);
		this.tanks[TANK_LEFT].unloadTank(SLOT_LEFT_IN, SLOT_LEFT_OUT, this.slots);
		this.tanks[TANK_RIGHT].loadTank(SLOT_RIGHT_IN, SLOT_RIGHT_OUT, this.slots);
		this.tanks[TANK_RIGHT].unloadTank(SLOT_RIGHT_IN, SLOT_RIGHT_OUT, this.slots);

		this.operate();
		this.pushPlasma();

		// 每 tick 同步一次，液柱才会逐 tick 变化，不然界面上是一段一段跳的
		this.networkPackNT(150);
	}

	/* ========================== 运行 ========================== */

	/**
	 * 一 tick 的加热：按配方的进料表对齐罐型、扣原料、产等离子。
	 *
	 * <p>进料表来自 {@link HydrogenFusion#heaterFeed(String)}：
	 * 氢-氢是左右罐各 50 mB 液氢 → 10 mB 氢等离子体；
	 * 氢-硼是左罐 100 mB 氢 + 右罐 100 mB 液态硼 → 20 mB 氢-硼等离子体。
	 * 耗电取配方自身的 {@code power}（氢-氢 100k、氢-硼 300k HE/t），
	 * 只有真的开了一炉才扣电。</p>
	 */
	private void operate() {

		FusionRecipe r = this.getRecipe();

		// 旧存档里可能存着已被删除的配方名（fus.dt 之类）：退回“未选”，让玩家自己重新选
		if(r == null) {
			this.opAccumulator = 0.0D;
			if(!NULL_RECIPE.equals(this.recipe)) {
				this.recipe = NULL_RECIPE;
				this.markDirty();
			}
			return;
		}

		HydrogenFusion.HeaterFeed feed = HydrogenFusion.heaterFeed(r.getInternalName());
		if(feed == null) return;	// 不是本模组的配方，本机不处理

		double speed = this.speedMult();
		long powerCost = Math.max(1L, Math.round(r.power * this.powerMult()));

		// 罐型对齐：左右输入罐按配方进料，缓冲罐按配方输出的等离子体
		this.tanks[TANK_LEFT].conform(new FluidStack(feed.tankFluid[0], 0));
		this.tanks[TANK_RIGHT].conform(new FluidStack(feed.tankFluid[1], 0));
		this.plasma.conform(new FluidStack(feed.outFluid, 0));

		if(this.power < powerCost) return;

		double nextProgress = this.opAccumulator + speed;
		int desiredOps = (int) Math.floor(nextProgress);
		if(desiredOps <= 0) {
			this.opAccumulator = nextProgress;
			return;
		}

		int needLeft = feed.tankAmount[0];
		int needRight = feed.tankAmount[1];
		int outPerOp = feed.outAmount;

		int haveLeft = this.takeOf(this.tanks[TANK_LEFT], feed.tankFluid[0], needLeft * desiredOps);
		int haveRight = this.takeOf(this.tanks[TANK_RIGHT], feed.tankFluid[1], needRight * desiredOps);

		// 两种原料都够才开炉；缓冲罐余量再压一次上限
		int maxOps = Math.min(haveLeft / needLeft, haveRight / needRight);
		int space = this.plasma.getMaxFill() - this.plasma.getFill();
		maxOps = Math.min(maxOps, space / outPerOp);
		int ops = Math.min(desiredOps, maxOps);
		if(ops <= 0) return;

		this.opAccumulator = Math.min(nextProgress - ops, 0.9999D);

		this.tanks[TANK_LEFT].setFill(this.tanks[TANK_LEFT].getFill() - needLeft * ops);
		this.tanks[TANK_RIGHT].setFill(this.tanks[TANK_RIGHT].getFill() - needRight * ops);
		this.plasma.setFill(this.plasma.getFill() + outPerOp * ops);

		this.power -= powerCost;
		this.markDirty();
	}

	/** 红色速度升级（1~3 级）：速度分别增加 15%、25%、45% */
	public double speedMult() {
		int level = HBMVersionCompat.upgradeLevel(this.upgrades, UpgradeType.SPEED);
		switch(level) {
		case 1: return 1.15D;
		case 2: return 1.25D;
		case 3: return 1.45D;
		default: return 1.0D;
		}
	}

	/** 红色速度升级（1~3 级）：耗电分别增加 100%、200%、300% */
	public double powerMult() {
		int level = HBMVersionCompat.upgradeLevel(this.upgrades, UpgradeType.SPEED);
		switch(level) {
		case 1: return 2.0D;
		case 2: return 3.0D;
		case 3: return 4.0D;
		default: return 1.0D;
		}
	}

	/** 单侧可扣量：罐型与配方进料一致才有效，上限 {@code convert} */
	private int takeOf(FluidTank tank, FluidType want, int convert) {
		if(tank.getTankType() != want) return 0;
		return Math.min(convert, tank.getFill());
	}

	/**
	 * 配方输入流体 → 等离子类型。
	 *
	 * <p>聚变表现在只剩氢-氢一条，等离子体固定是 {@link HydrogenFusion#plasmaType()}（PLASMA_H）。
	 * 保留这个方法是为了给 GUI / 调试一个稳定入口。</p>
	 */
	public FluidType mapPlasmaType(FusionRecipe r) {
		return r == null ? HydrogenFusion.plasmaType() : HydrogenFusion.plasmaType(r.getInternalName());
	}

	/* ========================== 直写仿星器 ========================== */

	/** 桶管朝向（放机器时朝远处的那一头）：本方块朝向的反方向 */
	private ForgeDirection getBarrelDir() {
		return ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable_offset()).getOpposite();
	}

	/** 桶管口那一格：核心格沿桶管方向 {@link BlockPlasmaHeater#MUZZLE_DISTANCE} 格 */
	private int[] getMuzzle() {

		ForgeDirection barrel = this.getBarrelDir();

		return new int[] {
				this.xCoord + barrel.offsetX * BlockPlasmaHeater.MUZZLE_DISTANCE,
				this.yCoord + barrel.offsetY * BlockPlasmaHeater.MUZZLE_DISTANCE,
				this.zCoord + barrel.offsetZ * BlockPlasmaHeater.MUZZLE_DISTANCE };
	}

	/**
	 * 找桶管正对的仿星器。
	 *
	 * <p><b>严格判定</b>：桶管口那一格必须真的落在仿星器的整机范围内（核心格水平半径 15、
	 * 高度 0~8，含环内空腔），也就是模型上的桶管已经插进仿星器内部。不做距离放宽、
	 * 不做反向兜底、不按“最近的一台”硬配——口子在谁的机身里，就只和谁配对；
	 * 同时插进多台时取口子离核心最近的那台。</p>
	 *
	 * <p>仿星器的占格/碰撞只在圆环上，桶管正是从无碰撞的空腔穿进去的，所以这里不能拿
	 * “该格有没有方块”来判——只能问仿星器自己（{@link TileEntityStellarator#containsPoint}）。</p>
	 */
	private void updateDock() {

		if(this.worldObj.getTotalWorldTime() % 20 != 0) return;

		this.dockedStellarator = null;

		int[] muzzle = this.getMuzzle();

		TileEntityStellarator best = null;
		double bestDist = Double.MAX_VALUE;

		for(Object obj : this.worldObj.loadedTileEntityList) {

			if(!(obj instanceof TileEntityStellarator)) continue;

			TileEntityStellarator te = (TileEntityStellarator) obj;

			// 严格：桶管口必须在这台仿星器的整机范围内
			if(!te.acceptsNozzle(muzzle[0], muzzle[1], muzzle[2])) continue;

			double dx = te.xCoord - muzzle[0];
			double dz = te.zCoord - muzzle[2];
			double dist = dx * dx + dz * dz;
			if(dist >= bestDist) continue;

			bestDist = dist;
			best = te;
		}

		if(best != null) {
			this.dockedStellarator = new int[] {best.xCoord, best.yCoord, best.zCoord};
		}
	}

	/**
	 * 把缓冲罐里的等离子推给<b>桶管口正对的那一台</b>仿星器。
	 *
	 * <p>只有口子伸进仿星器内部才会输出（{@link #updateDock()}）；配方名随等离子一起带过去，
	 * 仿星器在等离子罐空时采纳。</p>
	 */
	private void pushPlasma() {

		this.updateDock();
		if(this.dockedStellarator == null) return;

		TileEntity te = this.worldObj.getTileEntity(
				this.dockedStellarator[0], this.dockedStellarator[1], this.dockedStellarator[2]);

		if(!(te instanceof TileEntityStellarator)) {
			this.dockedStellarator = null;
			return;
		}

		TileEntityStellarator stellarator = (TileEntityStellarator) te;

		// 配方随对接一起交过去：缓冲罐还没产出时也能让仿星器先认出配方；
		// 本机没选配方（玩家取消）时不递，仿星器保持原样
		if(this.getRecipe() != null) stellarator.offerRecipe(this.recipe);

		if(this.plasma.getFill() <= 0) return;

		int fill = this.plasma.getFill();
		int got = stellarator.receivePlasma(this.plasma.getTankType(), fill, this.recipe);

		if(got > 0) {
			this.plasma.setFill(fill - got);
			this.markDirty();
		}
	}

	/* ========================== 配方 ========================== */

	public String getRecipeName() {
		return this.recipe;
	}

	public FusionRecipe getRecipe() {

		if(this.recipe == null || NULL_RECIPE.equals(this.recipe)) return null;

		GenericRecipe r = (GenericRecipe) StellaratorRecipes.INSTANCE.recipeNameMap.get(this.recipe);
		return r instanceof FusionRecipe ? (FusionRecipe) r : null;
	}

	/** 配方选择器回调（GUIScreenRecipeSelector → IControlReceiver），与聚变堆一致：无条件切换 */
	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {

		if(data.hasKey("index") && data.hasKey("selection")) {
			if(data.getInteger("index") == 0) {
				// 选择器点“清除”时发 null / 空串，这里必须接受，否则配方取消不掉
				String selection = data.getString("selection");
				String next = (selection == null || selection.isEmpty()) ? NULL_RECIPE : selection;
				if(!next.equals(this.recipe)) {
					this.recipe = next;
					this.markDirty();
				}
			}
		}
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	/* ========================== 交互距离 ========================== */

	/**
	 * 本机 21 格长、核心在正中，站在端头离核心 10 格以上；
	 * {@code TileEntityMachineBase} 默认 12 格会把界面顶掉，按仿星器的口径给 32。
	 */
	@Override
	public double getUseRange() {
		return 32D;
	}

	/* ========================== 电力 ========================== */

	@Override public long getPower() { return this.power; }
	@Override public void setPower(long power) { this.power = Math.max(0L, Math.min(MAX_POWER, power)); }
	@Override public long getMaxPower() { return MAX_POWER; }

	/** GUI 电力条：0~58 像素 */
	public int getPowerScaled(int scale) {
		return (int) (this.power * scale / MAX_POWER);
	}

	/* ========================== 流体接口 ========================== */

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { this.tanks[TANK_LEFT], this.tanks[TANK_RIGHT] };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		return this.tanks;
	}

	/**
	 * 两个输入罐是「匿名罐」：类型由灌进来的流体决定（桶装或管道）。
	 * 默认的 {@code transferFluid} 只认同类型罐，空 NONE 罐永远收不到管道流体，
	 * 所以这里重写需求/接收，让空罐认领来料类型（大型火力发电站同款做法）。
	 */
	@Override
	public long getDemand(FluidType type, int pressure) {

		long amount = 0;
		for(int i = 0; i < 2; i++) {
			FluidTank tank = this.tanks[i];
			if(tank.getPressure() != pressure) continue;
			if(tank.getTankType() == type) amount += tank.getMaxFill() - tank.getFill();
			else if(tank.getTankType() == Fluids.NONE && tank.getFill() == 0) amount += tank.getMaxFill();
		}
		return amount;
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long amount) {

		for(int i = 0; i < 2 && amount > 0; i++) {
			FluidTank tank = this.tanks[i];
			if(tank.getPressure() != pressure) continue;
			if(tank.getTankType() != type && !(tank.getTankType() == Fluids.NONE && tank.getFill() == 0)) continue;
			if(tank.getTankType() != type) tank.setTankType(type);	// 空罐认领来料类型
			int toAdd = (int) Math.min(amount, tank.getMaxFill() - tank.getFill());
			tank.setFill(tank.getFill() + toAdd);
			amount -= toAdd;
		}

		return amount;
	}

	/* ========================== 物品接口 ========================== */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { SLOT_BATTERY, SLOT_LEFT_IN, SLOT_LEFT_OUT, SLOT_RIGHT_IN, SLOT_RIGHT_OUT, SLOT_BLUEPRINT, SLOT_UPGRADE };
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		if(slot == SLOT_BATTERY)
			return stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative;

		if(slot == SLOT_LEFT_IN || slot == SLOT_RIGHT_IN) {
			if(stack.getItem() instanceof ItemInfiniteFluid) return false;
			// 满桶（任意流体）或空桶都收，具体灌哪罐由 loadTank/unloadTank 按桶内容决定
			return FluidContainerRegistry.getFluidType(stack) != Fluids.NONE
					|| FluidContainerRegistry.getEmptyContainer(stack) != null;
		}

		if(slot == SLOT_BLUEPRINT)
			return stack.getItem() == ModItems.blueprints;

		if(slot == SLOT_UPGRADE) {
			if(!(stack.getItem() instanceof ItemMachineUpgrade)) return false;
			return this.getValidUpgrades().containsKey(((ItemMachineUpgrade) stack.getItem()).type);
		}

		return false;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return this.isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot == SLOT_BATTERY || slot == SLOT_LEFT_OUT || slot == SLOT_RIGHT_OUT;
	}

	/* ========================== 升级插件 ========================== */

	/** 只认红色速度升级（1~3 级） */
	@Override
	public HashMap<UpgradeType, Integer> getValidUpgrades() {
		HashMap<UpgradeType, Integer> map = new HashMap<UpgradeType, Integer>();
		map.put(UpgradeType.SPEED, 3);
		return map;
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {

		info.add(IUpgradeInfoProvider.getStandardLabel(this.getBlockType()));

		int tier = Math.max(1, Math.min(3, level));

		if(type == UpgradeType.SPEED) {
			int speedPct = (tier == 1 ? 15 : (tier == 2 ? 25 : 45));
			int powerPct = tier * 100;
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("upgrade.speed", "+" + speedPct + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey("upgrade.consumption", "+" + powerPct + "%"));
		}
	}

	/* ========================== 同步与存档 ========================== */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		for(FluidTank tank : this.tanks) tank.serialize(buf);
		ByteBufUtils.writeUTF8String(buf, this.recipe == null ? NULL_RECIPE : this.recipe);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		for(FluidTank tank : this.tanks) tank.deserialize(buf);
		String name = ByteBufUtils.readUTF8String(buf);
		this.recipe = (name == null || name.isEmpty()) ? NULL_RECIPE : name;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		String name = nbt.getString("recipe");
		this.recipe = (name == null || name.isEmpty()) ? NULL_RECIPE : name;
		this.tanks[TANK_LEFT].readFromNBT(nbt, "left");
		this.tanks[TANK_RIGHT].readFromNBT(nbt, "right");
		this.tanks[TANK_PLASMA].readFromNBT(nbt, "plasma");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setString("recipe", this.recipe == null ? NULL_RECIPE : this.recipe);
		this.tanks[TANK_LEFT].writeToNBT(nbt, "left");
		this.tanks[TANK_RIGHT].writeToNBT(nbt, "right");
		this.tanks[TANK_PLASMA].writeToNBT(nbt, "plasma");
	}

	/* ========================== 渲染 ========================== */

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 11, yCoord, zCoord - 11, xCoord + 12, yCoord + 7, zCoord + 12);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}

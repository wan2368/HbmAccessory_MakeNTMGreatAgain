package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.lib.Library;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.blocks.BlockRiserRig;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import java.util.HashMap;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 海面立管钻机核心。
 *
 * <p>模型 + 碰撞 + 接口 + GUI + 向下采掘管道 + 结核开采 + 原地粉碎。</p>
 *
 * <p>电力：只进不出（{@link IEnergyReceiverMK2}），电池槽的电池会持续给内部缓冲充电。
 * 6 个接口全部是电力 + 流体 + 物品物流通用口，位置与模型上的 6 个黄色标记格一致。</p>
 *
 * <p>流体：右侧 1 个输入罐（类型由识别码槽决定，采掘时必须装高性能溶剂）
 * + 左侧 3 个输出罐（固定产出：海水 / 尾砂浆 / 重砂浆），每个 64000 mB。</p>
 *
 * <p>拉杆：左＝采掘。开启门槛：输入罐里至少 8000 mB 高性能溶剂，且机器正下方
 * 有 5×5×5 的完全实心方块地基（中心 1×1 钻杆通道除外）；常开时每 tick 耗电 50k HE
 * （随消耗倍率变化），基础每 5 秒向下落一格管道并消耗 1000 mB 高性能溶剂；
 * 遇到深层岩石/基岩停止，遇到深海结核混合物则停下开采：基础每 20 秒采一次
 * （随工作速度倍率缩短/延长），每采一次消耗 16000 mB 高性能溶剂，一个结核 30 次，
 * 产出海水/尾砂浆/重砂浆和三种精矿。
 * 右＝粉碎：开启后精矿不再原样输出，而是先过一遍粉碎机配方（精矿 → 混粉）再进输出格；
 * 没有粉碎配方的物品原样输出。粉碎不额外耗电，代价是混粉少了「先攒着」的灵活度。</p>
 *
 * <p>升级：两个升级槽接受速度（SPEED）、效率（EFFECT）、节能（POWER）三类
 * {@link ItemMachineUpgrade}，同类等级合计上限 3，数值按 HBM 升级模板：
 * 速度 1~3 级工作速度 +25/+33/+50%、消耗 +100/+200/+350%；
 * 效率 1~3 级单次资源产出 +10/+20/+30%；
 * 节能 1~3 级工作速度 -25/-33/-50%、消耗 -33/-50/-75%。
 * （本机的「消耗」指每下落一格管道消耗的高性能溶剂。）</p>
 */
public class TileEntityRiserRig extends TileEntityLoadedBase
		implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2, IInventory, ISidedInventory, IControlReceiver, IUpgradeInfoProvider {

	/** 单个流体缓冲罐容量 */
	public static final int TANK_CAPACITY = 64_000;
	/** 内部电力缓冲上限（HE） */
	public static final long MAX_POWER = 50_000_000L;
	/** 采掘基础间隔：100 tick（5 秒）一格，实际间隔按工作速度倍率缩短/延长 */
	public static final int PIPE_INTERVAL = 100;
	/** 开始采掘至少需要的高性能溶剂（mB） */
	public static final int MINING_START_FILL = 8_000;
	/** 每向下落一格管道的基础高性能溶剂消耗（mB），实际按消耗倍率变化 */
	public static final int PIPE_SOLVENT_COST = 1_000;
	/** 采掘常开时的基础耗电（HE/tick），实际按消耗倍率变化 */
	public static final long POWER_CONSUMPTION = 50_000L;
	/** 底部实心地基：以核心为中心 5×5 横向、向下 5 格 */
	public static final int BASE_RADIUS = 2;
	public static final int BASE_DEPTH = 5;

	/** 钻到结核矿后，基础每 20 秒（400 tick）开采一次；实际间隔见 {@link #getOreMiningInterval()} */
	public static final int ORE_MINING_INTERVAL = 400;
	/**
	 * 每成功开采一颗结核消耗的高性能溶剂（mB）。
	 *
	 * <p><b>故意不吃消耗倍率</b>：速度升级 III 的消耗倍率是 4.5，16000×4.5 = 72000 mB
	 * 已经超过输入罐容量 64000 mB，那样一插速度芯片就永远采不出东西来。这里固定 16000，
	 * 一罐正好 4 次。</p>
	 */
	public static final int ORE_SOLVENT_COST = 16_000;
	/** 每次开采的流体产量（对应左→右三个输出罐：海水 / 尾砂浆 / 重砂浆） */
	public static final int[] ORE_FLUID_YIELD = {8_000, 3_000, 1_000};
	/** 每次开采三种精矿的产出概率 */
	public static final float ORE_ITEM_CHANCE_COBALT = 0.30F;
	public static final float ORE_ITEM_CHANCE_HEAVY_SAND = 0.40F;
	public static final float ORE_ITEM_CHANCE_RARE_EARTH = 0.65F;

	/* ---- 升级数值表（下标＝等级 0~3） ---- */
	/** 速度升级：工作速度倍率 */
	private static final double[] SPEED_WORK_FACTOR = {1.00, 1.25, 1.33, 1.50};
	/** 速度升级：消耗倍率 */
	private static final double[] SPEED_COST_FACTOR = {1.00, 2.00, 3.00, 4.50};
	/** 节能升级：工作速度倍率 */
	private static final double[] POWER_WORK_FACTOR = {1.00, 0.75, 0.67, 0.50};
	/** 节能升级：消耗倍率 */
	private static final double[] POWER_COST_FACTOR = {1.00, 0.67, 0.50, 0.25};
	/** 效率升级：单次资源产出倍率 */
	private static final double[] EFFECT_OUTPUT_FACTOR = {1.00, 1.10, 1.20, 1.30};
	/** 效率升级：横向开采范围额外增加（切比雪夫半径），基础为 2（5×5） */
	private static final int[] EFFECT_RANGE_BONUS = {0, 3, 5, 7};
	/** 范围开采时纵向容许的海床高低差（海床很少完全平，差 1 格也要能采到） */
	private static final int ORE_RANGE_VERTICAL = 1;

	/* ---- 槽位编号（与 GUI 贴图布局一一对应） ---- */
	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_FLUID_ID = 1;
	public static final int SLOT_CAN_START = 2;      // 2..4：三个输出罐底部的流体罐槽
	public static final int SLOT_OUTPUT_START = 5;   // 5..13：3×3 物品输出槽
	public static final int SLOT_UPGRADE_START = 14; // 14..15：两个升级插槽
	public static final int SLOT_COUNT = 16;

	public FluidTank[] inputTanks = new FluidTank[] { new FluidTank(Fluids.NONE, TANK_CAPACITY) };
	public FluidTank[] outputTanks = new FluidTank[] {
			new FluidTank(Fluids.NONE, TANK_CAPACITY),
			new FluidTank(Fluids.NONE, TANK_CAPACITY),
			new FluidTank(Fluids.NONE, TANK_CAPACITY)
	};

	public long power;

	public boolean enableMining = false;
	public boolean enableCrusher = false;
	/** 产物无处可放（物品输出格满或任意一个输出罐满），每 tick 由服务端算好同步给 GUI */
	public boolean outputFull = false;

	/** 管道已经向下延伸的格数 */
	public int pipeLength = 0;
	protected int pipeTimer = 0;

	/** 结核矿开采计时（每 20 秒一次）；同步到客户端用于 GUI 显示开采进度 */
	public int oreTimer = 0;
	/**
	 * 当前开采目标：钻头正下方的结核，或空结核附近范围内的一个结核。
	 *
	 * <p><b>不能拿坐标是否 ≥ 0 当“有没有目标”的判据</b>：海里机器的 x/z 经常是负数，
	 * 一旦目标在负坐标区就会被误判成“没有目标”，计时器每 tick 清零，机器停在结核上方
	 * 却永远不产出。所以单独用 {@link #hasOreTarget} 记状态，坐标只当数据。</p>
	 */
	protected int oreTargetX = -1;
	protected int oreTargetY = -1;
	protected int oreTargetZ = -1;
	/** 是否已经锁定了一个开采目标（与坐标正负无关）；同步到客户端供 GUI 显示 */
	public boolean hasOreTarget = false;
	/** 空结核周围没有目标时的重扫间隔（每 20 tick 扫一次范围） */
	protected int oreTargetRefresh = 0;

	/** 升级等级（0~3），同类升级的 tier 合计后封顶 3 */
	public int speedLevel = 0;
	public int efficiencyLevel = 0;
	public int powerLevel = 0;

	/** 当前工作速度倍率（速度升级 × 节能升级） */
	public double workSpeedMultiplier = 1.0;
	/** 当前溶剂消耗倍率（速度升级 × 节能升级） */
	public double consumptionMultiplier = 1.0;
	/** 当前耗电倍率（只吃节能升级，速度升级不影响耗电） */
	public double powerConsumptionMultiplier = 1.0;
	/** 单次资源产出倍率（效率升级），留给后续开采产出逻辑 */
	public double outputMultiplier = 1.0;

	protected PortDef[] cachedPorts;

	/** 6 个通用接口，位置为方块旋转后的世界坐标 */
	public PortDef[] getPorts() {

		if(cachedPorts == null) {

			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			int[][] ports = ((BlockRiserRig) this.getBlockType()).getPortsAtCore(dir);

			cachedPorts = new PortDef[ports.length];
			for(int i = 0; i < ports.length; i++) {
				cachedPorts[i] = PortDef.make(
						xCoord + ports[i][0],
						yCoord + ports[i][1],
						zCoord + ports[i][2],
						ForgeDirection.getOrientation(ports[i][3]));
			}
		}

		return cachedPorts;
	}

	@Override
	public void updateEntity() {

		// 客户端也要算升级倍率：GUI 里的消耗提示直接读 consumptionMultiplier
		this.updateUpgrades();
		this.initOutputTankTypes();

		if(worldObj.isRemote) return;

		// 电池槽放电给内部缓冲（只进不出，不对外输电）
		this.power = Library.chargeTEFromItems(slots, SLOT_BATTERY, this.power, MAX_POWER);

		// 识别码只决定输入罐的流体类型；左侧三个输出罐是固定产物
		this.inputTanks[0].setType(SLOT_FLUID_ID, slots);

		this.setupAllPorts(this.getPorts());
		this.updatePortPIFIFO();
		this.updateCanFilling();

		// 同步给 GUI：产物是否已经没地方放
		this.outputFull = !this.hasOutputSpace();

		// 采掘常开：每 tick 固定耗电，电不够就停摆（管道与溶剂都不再推进）
		if(this.enableMining) {
			long need = this.getPowerConsumption();
			if(this.power >= need) {
				this.power -= need;
				// 产物无处可放（物品输出格满或任意一个输出罐满）时暂停采掘
				if(this.hasOutputSpace()) {
					this.updateMiningPipe();
					this.updateOreMining();
				}
			}
		}

		// 40 tick 同步一次：快到能看清开采进度（每 400 tick 采一次），又不至于每 tick 灌包
		this.networkPackNT(40);
	}

	/** 三个输出罐固定产出：左→右 海水 / 尾砂浆 / 重砂浆 */
	protected void initOutputTankTypes() {
		if(this.outputTanks.length < 3) return;

		FluidType[] types = new FluidType[] {
				ModFluids.SEA_WATER.type,
				ModFluids.TAILINGS_SLURRY.type,
				ModFluids.HEAVY_SAND_SLURRY.type
		};
		for(int i = 0; i < 3; i++) {
			if(types[i] != null) this.outputTanks[i].setTankType(types[i]);
		}
	}

	/**
	 * 输出罐底部的流体罐槽：放一个单格空罐，就把对应输出罐里的流体灌成
	 * canister_full（1000 mB）留在原槽位。
	 *
	 * <p>HBM 的 {@code FluidTank.unloadTank} 需要「进/出两个槽」，这里用一个
	 * 临时两格数组模拟：进槽是玩家放进来的空罐，出槽为 null。</p>
	 */
	protected void updateCanFilling() {

		for(int i = 0; i < 3 && i < this.outputTanks.length; i++) {

			ItemStack can = slots[SLOT_CAN_START + i];
			if(can == null || can.stackSize != 1) continue;

			FluidTank tank = this.outputTanks[i];
			if(tank.getTankType() == Fluids.NONE || tank.getFill() <= 0) continue;

			ItemStack[] temp = new ItemStack[] { can, null };
			if(tank.unloadTank(0, 1, temp) && temp[1] != null) {
				slots[SLOT_CAN_START + i] = temp[1];
				this.markDirty();
			}
		}
	}

	/* ---- 采掘：耗电 + 消耗高性能溶剂，在世界里逐格落下输送管道 ---- */

	/** 当前常开耗电（HE/t）：只受节能升级影响，速度升级不增加耗电 */
	public long getPowerConsumption() {
		return Math.max(1L, Math.round(POWER_CONSUMPTION * this.powerConsumptionMultiplier));
	}

	/** 输入罐里是否有足够开启一次采掘的高性能溶剂 */
	public boolean hasEnoughSolvent() {
		return this.inputTanks[0].getTankType() == Fluids.RADIOSOLVENT
				&& this.inputTanks[0].getFill() >= MINING_START_FILL;
	}

	/** 输入罐里的高性能溶剂够不够完整采下一颗结核（{@link #ORE_SOLVENT_COST} mB） */
	public boolean hasOreSolvent() {
		return this.inputTanks[0].getTankType() == Fluids.RADIOSOLVENT
				&& this.inputTanks[0].getFill() >= ORE_SOLVENT_COST;
	}

	/**
	 * 扣掉一颗结核的溶剂。
	 *
	 * <p>固定 {@link #ORE_SOLVENT_COST}，<b>不乘</b> {@code consumptionMultiplier}：
	 * 速度升级 III 的 4.5 倍会要 72000 mB，比输入罐容量 64000 mB 还大，那样一插速度芯片就再也采不出东西。</p>
	 */
	protected void drainOreSolvent() {
		FluidTank solvent = this.inputTanks[0];
		solvent.setFill(Math.max(0, solvent.getFill() - ORE_SOLVENT_COST));
	}

	/**
	 * 是否满足开始采掘的门槛：输入罐里必须是高性能溶剂且至少
	 * {@link #MINING_START_FILL} mB，底部有 5×5×5 的实心地基，
	 * 并且 3×3 物品输出格与三个输出流体罐都还有空位。
	 * GUI 与拉杆控制都走这一处判断。
	 */
	public boolean canStartMining() {
		return this.hasEnoughSolvent() && this.hasSolidBase() && this.hasOutputSpace();
	}

	/** 任意一个输出流体罐满了 */
	public boolean isFluidOutputFull() {
		for(int i = 0; i < 3 && i < this.outputTanks.length; i++) {
			if(this.outputTanks[i].getFill() >= this.outputTanks[i].getMaxFill()) return true;
			// 剩余容量装不下本轮产量时也算满：不足的差额会被 Math.min 静默丢掉
			int round = (int) Math.round(ORE_FLUID_YIELD[i] * this.outputMultiplier);
			if(this.outputTanks[i].getFill() + round > this.outputTanks[i].getMaxFill()) return true;
		}
		return false;
	}

	/**
	 * 产物是否有地方放：3×3 物品输出格至少有一个空位/可堆叠位置，
	 * 并且三个输出流体罐一个都没满（任意一个满了就停）。
	 */
	public boolean hasOutputSpace() {
		return this.hasItemOutputRoom() && !this.isFluidOutputFull();
	}

	/**
	 * 底部地基检测：以核心格为中心，横向 5×5、向下 5 格必须全部是
	 * 完全实心的完整方块（不透光、任意面都能贴靠，不包含液体/空气/半砖等）。
	 *
	 * <p>正中间 1×1 的钻杆通道除外——管道要从这里向下替换方块，
	 * 否则钻第一格就会把自己的地基判没了。</p>
	 */
	public boolean hasSolidBase() {

		for(int dx = -BASE_RADIUS; dx <= BASE_RADIUS; dx++) {
			for(int dz = -BASE_RADIUS; dz <= BASE_RADIUS; dz++) {

				if(dx == 0 && dz == 0) continue;   // 中心钻杆通道

				for(int dy = 1; dy <= BASE_DEPTH; dy++) {

					int x = xCoord + dx;
					int y = yCoord - dy;
					int z = zCoord + dz;

					if(y <= 0 || !worldObj.blockExists(x, y, z)) return false;

					Block block = worldObj.getBlock(x, y, z);
					if(!block.isOpaqueCube() || block.getMaterial().isLiquid()
							|| !block.isSideSolid(worldObj, x, y, z, ForgeDirection.UP)) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/** 实际采掘间隔：基础 100 tick 按工作速度倍率缩放（速度 +25% → 80 tick） */
	public int getMiningInterval() {
		return Math.max(1, (int) Math.round(PIPE_INTERVAL / this.workSpeedMultiplier));
	}

	/**
	 * 实际开采间隔：基础 400 tick 同样按工作速度倍率缩放。
	 *
	 * <p>工作速度倍率 = 速度升级 × 节能升级（{@link #updateUpgrades()}），所以红色速度芯片
	 * 会让一轮变短（1.5 倍 → 267 tick ≈ 13.3 秒），蓝色节能芯片会让一轮变长（0.5 倍 → 800 tick = 40 秒）。
	 * GUI 的「开采中 xx%」用的就是这个分母——两者必须共用同一个方法，否则进度条会和实际产出脱节。</p>
	 */
	public int getOreMiningInterval() {
		return Math.max(1, (int) Math.round(ORE_MINING_INTERVAL / this.workSpeedMultiplier));
	}

	/** 每格管道实际消耗的高性能溶剂 */
	public int getMiningCost() {
		return Math.max(1, (int) Math.round(PIPE_SOLVENT_COST * this.consumptionMultiplier));
	}

	protected void updateMiningPipe() {

		if(!this.enableMining) {
			this.pipeTimer = 0;
			return;
		}

		if(++this.pipeTimer < this.getMiningInterval()) return;
		this.pipeTimer = 0;

		this.recountPipe();
		if(this.extendPipe()) this.markDirty();
	}

	/**
	 * 每 5 秒重新数一遍机器正下方的连续管道格数。
	 *
	 * <p>玩家可以手动拆掉管道，按 NBT 里的旧长度继续落会留下空档，
	 * 所以每次延伸前都以世界里的实际方块为准。</p>
	 */
	protected void recountPipe() {

		int n = 0;

		while(n < 255) {
			int y = yCoord - n - 1;
			if(y <= 0) break;
			if(worldObj.getBlock(xCoord, y, zCoord) != ModBlocks.riser_pipe) break;
			n++;
		}

		this.pipeLength = n;
	}

	/**
	 * 向下落一格管道：先扣高性能溶剂，再看目标位置的方块。
	 *
	 * <p>输入罐里不是高性能溶剂、或余量不足 {@link #PIPE_SOLVENT_COST} mB 时
	 * 不再下探（拉杆保持开启，补液后自动继续）。</p>
	 *
	 * <p>HBM 的深层岩石层（{@code BlockDepth} 及其深部矿石）和基岩都是
	 * 硬度小于 0 的不可破坏方块，这里一律不替换、直接停下。</p>
	 */
	protected boolean extendPipe() {

		// 底部地基被破坏时也不能继续工作
		if(!this.hasSolidBase()) return false;

		FluidTank solvent = this.inputTanks[0];
		int cost = this.getMiningCost();

		if(solvent.getTankType() != Fluids.RADIOSOLVENT) return false;
		if(solvent.getFill() < cost) return false;

		int nextY = yCoord - this.pipeLength - 1;
		if(nextY <= 0) return false;

		Block block = worldObj.getBlock(xCoord, nextY, zCoord);

		// 基岩、深层岩石这种不可破坏方块：不能替换
		if(block == Blocks.bedrock || block.getBlockHardness(worldObj, xCoord, nextY, zCoord) < 0) return false;

		// 深海结核：管道停在它上方，交给开采逻辑
		if(this.isPipeTarget(block)) return false;

		// 钻头停在空结核、且周围范围内还有结核：先采旁边的，不继续下钻
		if(block == ModBlocks.empty_deep_sea_nodule && this.hasNoduleInRange(nextY)) return false;

		if(worldObj.setBlock(xCoord, nextY, zCoord, ModBlocks.riser_pipe, 0, 3)) {
			solvent.setFill(solvent.getFill() - cost);
			this.pipeLength++;
			return true;
		}

		return false;
	}

	/**
	 * 特定矿物：管道遇到深海结核混合物就停下，改为开采它。
	 */
	protected boolean isPipeTarget(Block block) {
		return block == ModBlocks.deep_sea_nodule;
	}

	/* ---- 结核矿开采：基础每 20 秒一次、每次 16000mB 溶剂、一个结核 30 次 ---- */

	/** 横向开采半径：基础 2（以空结核为中心的 5×5），效率升级 1/2/3 级 +3/+5/+7 */
	public int getMiningRadius() {
		return 2 + EFFECT_RANGE_BONUS[this.efficiencyLevel];
	}

	/**
	 * 钻头正下方是结核就采它；是“空结核”则在范围内找最近的结核继续采；
	 * 周围都采空了才允许管道继续向下。
	 */
	protected void updateOreMining() {

		int tipY = yCoord - this.pipeLength - 1;
		if(tipY <= 0) {
			this.clearOreTarget();
			this.oreTimer = 0;
			return;
		}

		Block tip = worldObj.getBlock(xCoord, tipY, zCoord);

		if(tip == ModBlocks.deep_sea_nodule) {
			this.oreTargetX = xCoord;
			this.oreTargetY = tipY;
			this.oreTargetZ = zCoord;
			this.hasOreTarget = true;
		} else if(tip == ModBlocks.empty_deep_sea_nodule) {
			if(this.isOreTargetValid()) {
				this.oreTargetRefresh = 0;
			} else if(++this.oreTargetRefresh >= 20) {
				// 没目标时每秒扫一次范围就够了，不必每 tick 扫 5×5~19×19
				this.oreTargetRefresh = 0;
				this.refreshOreTarget(tipY);
			}
		} else {
			this.clearOreTarget();
			this.oreTimer = 0;
			return;
		}

		if(!this.isOreTargetValid()) {
			this.oreTimer = 0;
			return;
		}

		// 间隔随速度升级 / 节能升级变化，进度条与实际产出必须用同一个分母
		int interval = this.getOreMiningInterval();
		// 封顶而不是一直加：条件卡住时计时器停在 100%，一恢复就立刻开采
		if(this.oreTimer < interval) this.oreTimer++;
		if(this.oreTimer < interval) return;

		// 输出满 / 溶剂不够采一颗结核时，计时器停在满进度等条件恢复，
		// 不把进度清零重来（否则补上溶剂还要再等一整轮）
		if(!this.hasOutputSpace()) return;
		if(!this.hasOreSolvent()) return;

		this.oreTimer = 0;

		// 真的采到了才扣 16000mB；刚好撞上已采空的结核（mineNodule 返回 false）不白扣溶剂
		if(this.mineNodule(this.oreTargetX, this.oreTargetY, this.oreTargetZ)) {
			this.drainOreSolvent();
			// 「海底捞」：钻机从海底结核里掏出东西的那一下，站在机器旁的玩家记一笔
			com.makentmgreatagain.init.ModAchievements.unlockNearby(worldObj, xCoord, yCoord, zCoord, 24D,
					com.makentmgreatagain.init.ModAchievements.sea_dredge);
		}
		this.clearOreTarget();
	}

	protected void clearOreTarget() {
		this.hasOreTarget = false;
		this.oreTargetX = -1;
		this.oreTargetY = -1;
		this.oreTargetZ = -1;
	}

	/**
	 * 目标是否仍然有效：必须真的锁定过目标，且那一格还是含矿结核。
	 *
	 * <p>判定「有没有锁定目标」用 {@link #hasOreTarget}，绝不能用 {@code oreTargetX >= 0}——
	 * 海里坐标本来就可以是负数。</p>
	 */
	protected boolean isOreTargetValid() {

		if(!this.hasOreTarget) return false;
		if(!worldObj.blockExists(this.oreTargetX, this.oreTargetY, this.oreTargetZ)) return false;

		return worldObj.getBlock(this.oreTargetX, this.oreTargetY, this.oreTargetZ) == ModBlocks.deep_sea_nodule;
	}

	/**
	 * 在以空结核为中心的横向范围内找最近的结核。
	 *
	 * <p>纵向容许 ±{@link #ORE_RANGE_VERTICAL} 格：海床很少完全平，相邻列的结核
	 * 常常高出一两格，只扫同一层会让机器误判成“周围采空”。</p>
	 */
	protected void refreshOreTarget(int tipY) {

		this.clearOreTarget();

		int r = this.getMiningRadius();
		int best = Integer.MAX_VALUE;

		for(int dy = -ORE_RANGE_VERTICAL; dy <= ORE_RANGE_VERTICAL; dy++) {
			for(int dx = -r; dx <= r; dx++) {
				for(int dz = -r; dz <= r; dz++) {
					if(dx == 0 && dz == 0 && dy == 0) continue;
					int d = dx * dx + dz * dz + dy * dy;
					if(d >= best) continue;

					int x = xCoord + dx;
					int y = tipY + dy;
					int z = zCoord + dz;
					if(worldObj.getBlock(x, y, z) == ModBlocks.deep_sea_nodule) {
						best = d;
						this.oreTargetX = x;
						this.oreTargetY = y;
						this.oreTargetZ = z;
						this.hasOreTarget = true;
					}
				}
			}
		}
	}

	/** 范围内是否还有结核（用于阻止管道在还有矿时继续下钻） */
	protected boolean hasNoduleInRange(int tipY) {
		int r = this.getMiningRadius();
		for(int dy = -ORE_RANGE_VERTICAL; dy <= ORE_RANGE_VERTICAL; dy++) {
			for(int dx = -r; dx <= r; dx++) {
				for(int dz = -r; dz <= r; dz++) {
					if(dx == 0 && dz == 0 && dy == 0) continue;
					if(worldObj.getBlock(xCoord + dx, tipY + dy, zCoord + dz) == ModBlocks.deep_sea_nodule) return true;
				}
			}
		}
		return false;
	}

	/** 采一次结核矿：扣一次剩余次数，产出流体并按概率产出精矿（粉碎开关打开时直接出混粉）。返回是否真的采到 */
	protected boolean mineNodule(int tx, int ty, int tz) {

		if(worldObj.getBlock(tx, ty, tz) != ModBlocks.deep_sea_nodule) return false;

		TileEntityDeepSeaNodule nodule = this.getOrCreateNodule(tx, ty, tz);
		if(nodule == null || nodule.isDepleted()) {
			this.depleteNodule(tx, ty, tz);
			return false;
		}

		nodule.uses--;
		nodule.markDirty();

		// 流体：左→右 海水 8000 / 尾砂浆 3000 / 重砂浆 1000，效率升级放大产出量
		for(int i = 0; i < 3 && i < this.outputTanks.length; i++) {
			int amount = (int) Math.round(ORE_FLUID_YIELD[i] * this.outputMultiplier);
			FluidTank tank = this.outputTanks[i];
			tank.setFill(Math.min(tank.getMaxFill(), tank.getFill() + amount));
		}

		// 物品：各自独立掷概率，命中就往 3×3 输出格里放（走 addOreOutput，粉碎开关开着就直接输出混粉）
		if(worldObj.rand.nextFloat() < ORE_ITEM_CHANCE_COBALT) {
			this.addOreOutput(com.makentmgreatagain.init.ModItems.cobalt_polymetallic_concentrate);
		}
		if(worldObj.rand.nextFloat() < ORE_ITEM_CHANCE_HEAVY_SAND) {
			this.addOreOutput(com.makentmgreatagain.init.ModItems.heavy_sand_concentrate);
		}
		if(worldObj.rand.nextFloat() < ORE_ITEM_CHANCE_RARE_EARTH) {
			this.addOreOutput(com.makentmgreatagain.init.ModItems.rare_earth_concentrate);
		}

		// 采空：变成空结核方块，钻机在范围内继续找下一块
		if(nodule.isDepleted()) this.depleteNodule(tx, ty, tz);

		this.markDirty();
		return true;
	}

	/**
	 * 取结核方块实体。
	 *
	 * <p>旧存档里可能是在本版本之前生成、没有方块实体的结核，这里补一个默认的
	 * （剩余 30 次），保证老存档也能正常开采。</p>
	 */
	protected TileEntityDeepSeaNodule getOrCreateNodule(int tx, int ty, int tz) {

		TileEntity te = worldObj.getTileEntity(tx, ty, tz);
		if(te instanceof TileEntityDeepSeaNodule) return (TileEntityDeepSeaNodule) te;

		TileEntityDeepSeaNodule nodule = new TileEntityDeepSeaNodule();
		worldObj.setTileEntity(tx, ty, tz, nodule);
		return nodule;
	}

	/** 采空：替换成“空深海结核混合物”方块 */
	protected void depleteNodule(int tx, int ty, int tz) {
		worldObj.setBlock(tx, ty, tz, ModBlocks.empty_deep_sea_nodule, 0, 3);
	}

	/** 3×3 输出格里至少有一个空位或可堆叠的位置 */
	protected boolean hasItemOutputRoom() {
		for(int i = SLOT_OUTPUT_START; i < SLOT_UPGRADE_START; i++) {
			if(slots[i] == null) return true;
			if(slots[i].stackSize < Math.min(slots[i].getMaxStackSize(), this.getInventoryStackLimit())) return true;
		}
		return false;
	}

	/** 往 3×3 输出格里塞一个物品：先找同类可堆叠的，再找空位 */
	protected boolean addItemOutput(ItemStack stack) {

		for(int i = SLOT_OUTPUT_START; i < SLOT_UPGRADE_START; i++) {
			if(slots[i] == null) {
				slots[i] = stack;
				return true;
			}
			if(slots[i].getItem() == stack.getItem()
					&& slots[i].getItemDamage() == stack.getItemDamage()
					&& slots[i].stackSize < slots[i].getMaxStackSize()) {
				slots[i].stackSize++;
				return true;
			}
		}

		return false;
	}

	/** 按 stackSize 逐个塞（{@link #addItemOutput} 一次只处理 1 个） */
	protected void addItemOutputs(ItemStack stack) {

		int count = Math.max(1, stack.stackSize);

		for(int n = 0; n < count; n++) {
			if(!this.addItemOutput(new ItemStack(stack.getItem(), 1, stack.getItemDamage()))) return;
		}
	}

	/**
	 * 采矿产物的出口：粉碎开关打开且该物品有粉碎配方时，直接输出粉碎产物。
	 *
	 * <p>「部分物品可以直接粉碎」——三种精矿在 {@code ModRecipes.addDeepSeaShredderRecipes()}
	 * 里都有粉碎机配方（精矿 → 混粉），所以开关一开就在机器里粉碎完，省得再拉去粉碎机。
	 * 没有配方的物品原样输出；粉碎机配方用的是本体 {@code ShredderRecipes} 的同一张表，
	 * 配方改了这里自动跟随，不需要另建一份映射。</p>
	 */
	protected void addOreOutput(Item item) {

		ItemStack raw = new ItemStack(item);

		if(this.enableCrusher) {
			ItemStack crushed = getCrushedResult(raw);
			if(crushed != null) {
				this.addItemOutputs(crushed);
				return;
			}
		}

		this.addItemOutputs(raw);
	}

	/**
	 * 查本体的粉碎机配方表；查不到返回 null。
	 *
	 * <p>不能直接用 {@code ShredderRecipes.getShredderResult()}：它查不到时返回 scrap，
	 * 会把「没有配方」伪装成「粉碎成废料」。这里直接查表，查不到就是查不到。
	 * 元数据通配（{@code WILDCARD_VALUE}）那一遍与本体实现保持一致。</p>
	 */
	protected static ItemStack getCrushedResult(ItemStack stack) {

		if(stack == null || stack.getItem() == null) return null;

		ComparableStack key = new ComparableStack(stack).makeSingular();
		ItemStack out = ShredderRecipes.shredderRecipes.get(key);

		if(out == null) {
			key.meta = OreDictionary.WILDCARD_VALUE;
			out = ShredderRecipes.shredderRecipes.get(key);
		}

		// 配方表里的 ItemStack 是共享实例，必须拷贝，否则改 stackSize 会污染配方
		return out == null ? null : out.copy();
	}

	/* ---- 升级 ---- */

	protected void updateUpgrades() {

		int speed = 0, effect = 0, powerSaving = 0;

		for(int i = SLOT_UPGRADE_START; i < SLOT_COUNT; i++) {

			if(slots[i] == null || !(slots[i].getItem() instanceof ItemMachineUpgrade)) continue;

			ItemMachineUpgrade upgrade = (ItemMachineUpgrade) slots[i].getItem();
			int tier = Math.min(upgrade.tier, 3);

			if(upgrade.type == UpgradeType.SPEED) speed += tier;
			else if(upgrade.type == UpgradeType.EFFECT) effect += tier;
			else if(upgrade.type == UpgradeType.POWER) powerSaving += tier;
		}

		this.speedLevel = Math.min(speed, 3);
		this.efficiencyLevel = Math.min(effect, 3);
		this.powerLevel = Math.min(powerSaving, 3);

		// 速度升级与节能升级同时作用在工作速度和溶剂消耗上（乘算）
		this.workSpeedMultiplier = SPEED_WORK_FACTOR[this.speedLevel] * POWER_WORK_FACTOR[this.powerLevel];
		this.consumptionMultiplier = SPEED_COST_FACTOR[this.speedLevel] * POWER_COST_FACTOR[this.powerLevel];
		// 耗电只吃节能升级；速度升级不再增加耗电
		this.powerConsumptionMultiplier = POWER_COST_FACTOR[this.powerLevel];
		// 效率升级只影响单次资源产出量（每轮开采的流体数量）
		this.outputMultiplier = EFFECT_OUTPUT_FACTOR[this.efficiencyLevel];
	}

	/* ---- 升级提示（照 HBM 升级模板：工作速度/消耗/单次资源产出量） ---- */

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.EFFECT || type == UpgradeType.POWER;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {

		// 作用提示只在客户端渲染物品提示时用；level 来自升级物品的 tier
		int tier = Math.max(0, Math.min(level, 3));
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.riser_rig));

		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("upgrade.riser_rig.speed", formatPercent(SPEED_WORK_FACTOR[tier] - 1.0)));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey("upgrade.riser_rig.consumption", formatPercent(SPEED_COST_FACTOR[tier] - 1.0)));
		}
		if(type == UpgradeType.EFFECT) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("upgrade.riser_rig.output", formatPercent(EFFECT_OUTPUT_FACTOR[tier] - 1.0)));
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("upgrade.riser_rig.range", "+" + EFFECT_RANGE_BONUS[tier]));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey("upgrade.riser_rig.speed", formatPercent(POWER_WORK_FACTOR[tier] - 1.0)));
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("upgrade.riser_rig.consumption", formatPercent(POWER_COST_FACTOR[tier] - 1.0)));
		}
	}

	@Override
	public HashMap<UpgradeType, Integer> getValidUpgrades() {
		HashMap<UpgradeType, Integer> upgrades = new HashMap<>();
		upgrades.put(UpgradeType.SPEED, 3);
		upgrades.put(UpgradeType.EFFECT, 3);
		upgrades.put(UpgradeType.POWER, 3);
		return upgrades;
	}

	/** 把倍率差转成带符号的百分比文本（-0.33 → "-33%"，0.25 → "+25%"） */
	private static String formatPercent(double delta) {
		long pct = Math.round(Math.abs(delta) * 100);
		return (delta >= 0 ? "+" : "-") + pct + "%";
	}

	/* ---- 电力 ---- */

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return MAX_POWER; }

	/* ---- 物品（供物流管道识别 + GUI） ---- */

	public ItemStack[] slots = new ItemStack[SLOT_COUNT];

	@Override public int getSizeInventory() { return slots.length; }
	@Override public ItemStack getStackInSlot(int i) { return slots[i]; }
	@Override public ItemStack getStackInSlotOnClosing(int i) {
		// 与基类/IInventory 约定一致：取走的同时清槽，否则个别关闭路径可能复制物品
		ItemStack stack = slots[i];
		slots[i] = null;
		return stack;
	}

	@Override
	public ItemStack decrStackSize(int i, int n) {
		ItemStack stack = slots[i];
		if(stack == null) return null;
		if(stack.stackSize <= n) {
			slots[i] = null;
			return stack;
		}
		ItemStack out = stack.splitStack(n);
		if(stack.stackSize <= 0) slots[i] = null;
		return out;
	}

	@Override public void setInventorySlotContents(int i, ItemStack stack) { slots[i] = stack; }
	@Override public String getInventoryName() { return "container.riser_rig"; }
	@Override public boolean hasCustomInventoryName() { return false; }
	@Override public int getInventoryStackLimit() { return 64; }
	@Override public void openInventory() {}
	@Override public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {

		if(stack == null) return false;

		// 3×3 是输出槽，物流管道也不能往里塞
		if(i >= SLOT_OUTPUT_START && i < SLOT_UPGRADE_START) return false;

		if(i == SLOT_BATTERY) {
			return stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative;
		}
		if(i == SLOT_FLUID_ID) {
			return stack.getItem() instanceof IItemFluidIdentifier;
		}
		if(i >= SLOT_UPGRADE_START) {
			if(!(stack.getItem() instanceof ItemMachineUpgrade)) return false;
			UpgradeType type = ((ItemMachineUpgrade) stack.getItem()).type;
			return type == UpgradeType.SPEED || type == UpgradeType.EFFECT || type == UpgradeType.POWER;
		}
		if(i >= SLOT_CAN_START && i < SLOT_OUTPUT_START) {
			return stack.getItem() == ModItems.canister_empty || stack.getItem() == ModItems.canister_full;
		}

		return true;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 256;
	}

	/* ---- 物流/管道访问（代理方块要求核心实现 ISidedInventory 才会转发） ---- */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		int[] all = new int[SLOT_COUNT];
		for(int i = 0; i < SLOT_COUNT; i++) all[i] = i;
		return all;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return this.isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		// 只允许抽走 3×3 输出槽里的产物；电池/识别码/流体罐/升级不让管道顺走
		return slot >= SLOT_OUTPUT_START && slot < SLOT_UPGRADE_START;
	}

	/* ---- 控制（GUI 拉杆） ---- */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {

		if(data.hasKey("mining")) {
			// 开启前必须满足高性能溶剂的门槛；关闭随时可以
			if(this.enableMining) this.enableMining = false;
			else if(this.canStartMining()) this.enableMining = true;
		}
		if(data.hasKey("crusher")) this.enableCrusher = !this.enableCrusher;

		this.markDirty();
	}

	/* ---- 流体 ---- */

	@Override public FluidTank[] getReceivingTanks() { return inputTanks; }
	@Override public FluidTank[] getSendingTanks() { return outputTanks; }

	@Override
	public FluidTank[] getAllTanks() {
		FluidTank[] all = new FluidTank[inputTanks.length + outputTanks.length];
		System.arraycopy(inputTanks, 0, all, 0, inputTanks.length);
		System.arraycopy(outputTanks, 0, all, inputTanks.length, outputTanks.length);
		return all;
	}

	/* ---- 同步（GUI 需要看到电量、罐内流体和拉杆状态） ---- */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(power);
		buf.writeBoolean(enableMining);
		buf.writeBoolean(enableCrusher);
		buf.writeBoolean(outputFull);
		// 开采进度（GUI 里显示“开采中 xx%”），诊断“停在矿上不产出”时一眼能看出计时器有没有在走
		buf.writeInt(oreTimer);
		buf.writeBoolean(hasOreTarget);
		// 工作速度倍率：GUI 要用它算开采进度的分母（各等级的服务端/客户端一致，但别指望客户端一定能从槽位重算出来）
		buf.writeDouble(workSpeedMultiplier);
		for(FluidTank tank : inputTanks) tank.serialize(buf);
		for(FluidTank tank : outputTanks) tank.serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.enableMining = buf.readBoolean();
		this.enableCrusher = buf.readBoolean();
		this.outputFull = buf.readBoolean();
		this.oreTimer = buf.readInt();
		this.hasOreTarget = buf.readBoolean();
		this.workSpeedMultiplier = buf.readDouble();
		for(FluidTank tank : inputTanks) tank.deserialize(buf);
		for(FluidTank tank : outputTanks) tank.deserialize(buf);
	}

	/* ---- NBT ---- */

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.enableMining = nbt.getBoolean("enableMining");
		this.enableCrusher = nbt.getBoolean("enableCrusher");
		this.pipeLength = nbt.getInteger("pipeLength");
		this.pipeTimer = nbt.getInteger("pipeTimer");
		this.oreTimer = nbt.getInteger("oreTimer");
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].readFromNBT(nbt, "in" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].readFromNBT(nbt, "out" + i);

		NBTTagList list = nbt.getTagList("items", 10);
		for(int i = 0; i < slots.length; i++) {
			if(i < list.tagCount()) slots[i] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
			else slots[i] = null;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setBoolean("enableMining", enableMining);
		nbt.setBoolean("enableCrusher", enableCrusher);
		nbt.setInteger("pipeLength", pipeLength);
		nbt.setInteger("pipeTimer", pipeTimer);
		nbt.setInteger("oreTimer", oreTimer);
		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].writeToNBT(nbt, "in" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].writeToNBT(nbt, "out" + i);

		NBTTagList list = new NBTTagList();
		for(ItemStack stack : slots) {
			NBTTagCompound tag = new NBTTagCompound();
			if(stack != null) stack.writeToNBT(tag);
			list.appendTag(tag);
		}
		nbt.setTag("items", list);
	}

	/* ---- 渲染 ---- */

	AxisAlignedBB bb = null;

	/**
	 * 模型 7×7×16，核心格只是足迹中心的一格；默认单格包围盒会让高处的井架在
	 * 特定视角突然消失，这里覆盖整机并留出余量。
	 *
	 * <p>泵送管道是世界里真实存在的方块，各自有 TESR，不需要在这里扩展包围盒。</p>
	 */
	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 5,
					yCoord,
					zCoord - 5,
					xCoord + 5,
					yCoord + 18,
					zCoord + 5);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}

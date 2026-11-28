package com.makentmgreatagain.tileentity;

import com.hbm.blocks.BlockDummyable;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.FusionRecipe;
import com.makentmgreatagain.recipes.StellaratorRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IConditionalInvAccess;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TileEntityProxyBase;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockStellarator;
import com.makentmgreatagain.recipes.HydrogenFusion;
import com.makentmgreatagain.recipes.HydrogenFusionRecipe;
import com.makentmgreatagain.recipes.StellaratorBreeding;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 仿星器核心格。
 *
 * <h3>界面布局</h3>
 * 六个流体罐与五个物品槽，与底图 {@code gui/gui_stellarator.png} 一一对齐：
 * <ul>
 *   <li>罐 0 等离子体（44,18）：等离子加热器注入口，也是点火后的燃料；</li>
 *   <li>罐 1 增值流体（72,18）：被中子增殖的原料，槽 4 的流体识别码切换种类；</li>
 *   <li>罐 2 超浓密蒸汽（206,18）：冷却水被加热后的产出，位于水罐右侧；</li>
 *   <li>罐 3 输出流体（134,18）：配方流体产物；</li>
 *   <li>罐 4 增值后输出（152,18）：增殖产物；</li>
 *   <li>罐 5 水（188,18）：冷却液入口；</li>
 *   <li>槽 0 电池（8,72）、槽 1 等离子配方物品产物（116,36）、
 *       槽 2 增值物品（54,72）、槽 3 增值物品产物（116,54）、槽 4 流体识别码（72,72）。</li>
 * </ul>
 *
 * <p><b>运行</b>：默认既没有等离子体也没有配方，全部由等离子加热器灌进来——加热器每 tick
 * 尽量把等离子直写进来，并把<b>当前选中的聚变配方名</b>一起带过来；仿星器在等离子罐为空时
 * 才接受新配方，之后完全按该配方运行（用原版 {@code FusionRecipe} 的数据）：</p>
 * <ul>
 *   <li>水 / 超浓密蒸汽罐的上限随配方自动设置：默认 1,280,000 mB，每级 +1,280,000，
 *       直到单 tick 水耗也有至少 50% 的冗余；</li>
 *   <li>开工门槛：等离子罐里必须<b>攒够 64,000 mB</b> 才会点火开工，掉到门槛以下就停机等料
 *       （判定只发生在每轮开始前，已起跳的一轮会跑完，不会半途丢掉预扣的燃料）；</li>
 *   <li>点火：不用速调管，有电就能起跳；之后每 tick 扣配方的 {@code power}（= 加热器造这份等离子体的电），断电即熄火；</li>
 *   <li>燃料：默认一个循环（{@code duration} tick）预扣 {@code 2 × 配方输入流体总量} mB 等离子；
 *       配方若在 {@link HydrogenFusion} 里登记了每 tick 消耗量（氢-氢 = 1000 mB/t），就改成逐 tick 扣，
 *       与等离子加热器的产出对齐；</li>
 *   <li>产出：循环结束时 {@code outputFluid} → 罐 3、{@code outputItem} → 槽 1；</li>
 *   <li>增殖：中子通量 {@code neutronFlux} 累积，按 {@link StellaratorBreeding}（<b>聚变专用表</b>，
 *       不是原版炼油 / 照射室那两张通用表）完成嬗变——槽 2 有可增殖物品就照物品
 *       （固体产物进槽 3、流体产物进罐 4），否则照罐 1 的流体（产物进罐 4）；
 *       罐 4 的类型跟着当前路线走，换识别码后罐子上立刻是新产物，不用等第一炉；</li>
 *   <li>冷却：配方的产热 {@code outputTemp} 按「每 1,000 TU 把 50,000 mB 水变成 5,000 mB 超浓密蒸汽」换蒸汽；
 *       水不够一轮或蒸汽罐装不下就<b>停机</b>，供水不足时按到手的量减产。</li>
 * </ul>
 */
public class TileEntityStellarator extends TileEntityMachineBase
		implements IFluidStandardTransceiverMK2, IEnergyReceiverMK2, IConditionalInvAccess, IControlReceiver {

	/* ========================== 常量 ========================== */

	/**
	 * 没有配方时的兜底储电上限。有配方时按 {@link #getMaxPower()} 走：
	 * 配方“约束力量”× 100（氢配方 10M/t → 1G）。
	 */
	public static final long MAX_POWER = 100_000_000L;

	/**
	 * 等离子 / 增殖 / 产物四个罐的容量 128,000 mB。底图上六个液柱同高，容量按规格给；
	 * 等离子罐再大也只是缓冲，真正的门槛是下面的 {@link #MIN_PLASMA}。
	 */
	public static final int TANK_CAPACITY = 128_000;

	/**
	 * 水 / 超临界蒸汽罐的默认基础容量（64,000 mB），以及按配方自动扩容的步长（32,000 mB/级）。
	 * 实际容量由 {@link #applyCoolantCapacity(FusionRecipe)} 按配方的单 tick 最大耗水量
	 * 往上递增，直到单 tick 水耗也有 ≥ 50% 的冗余。
	 */
	public static final int COOLANT_CAPACITY = 64_000;
	public static final int COOLANT_STEP = 32_000;

	/** 开工门槛：等离子罐里至少要有 64,000 mB（一半）才读条，掉到门槛以下就停机等料 */
	public static final int MIN_PLASMA = 64_000;

	/** 等离子体输入上限（mB/tick）：加热器每 tick 最多推进来这么多 */
	public static final int PLASMA_INTAKE_PER_TICK = 100;

	public static final int PLASMA_CAPACITY = TANK_CAPACITY;
	public static final int BREED_CAPACITY = TANK_CAPACITY;
	public static final int FLUID_OUT_CAPACITY = TANK_CAPACITY;
	public static final int BREED_OUT_CAPACITY = TANK_CAPACITY;

	public static final int TANK_PLASMA = 0;
	public static final int TANK_BREED_IN = 1;
	public static final int TANK_STEAM = 2;
	public static final int TANK_FLUID_OUT = 3;
	public static final int TANK_BREED_OUT = 4;
	public static final int TANK_WATER = 5;

	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_PLASMA_OUT = 1;
	public static final int SLOT_BREED_IN = 2;
	public static final int SLOT_BREED_OUT = 3;
	public static final int SLOT_BREED_ID = 4;
	public static final int SLOT_COUNT = 5;

	/** 防止多方块拆机时重复掉落仿星器物品（每台机器只允许掉落一次） */
	public transient boolean droppedByHarvest = false;

	/** 每 1000 TU 完成一轮「水 → 超临界蒸汽」 */
	public static final int TU_PER_BOIL = 1000;
	/** 每轮消耗 100 mB 水 */
	public static final int WATER_PER_BOIL = 100;
	/** 每轮产出 10 mB 超临界蒸汽 */
	public static final int STEAM_PER_BOIL = 10;
	/**
	 * 增殖通量基准，与 {@link StellaratorBreeding#BASE_FLUX}、原版
	 * {@code TileEntityFusionBreeder.capacity} 同值；具体每条配方要多少由表里各自给。
	 */
	public static final double BREED_UNITS = StellaratorBreeding.BASE_FLUX;

	/** 未选配方，与加热器的 {@code NULL_RECIPE} 同值 */
	public static final String NULL_RECIPE = "null";

	/* ========================== 状态 ========================== */

	public long power;

	/** 当前运行中的聚变配方名，由等离子加热器注入时带过来 */
	public String recipe = NULL_RECIPE;

	/** 仿星器开关：需三个指示灯全亮才可开启，开启后允许准备启动聚变 */
	public boolean isOn;

	/** 是否已点火：开启且有等离子、有电、有水并读条满后点着 */
	public boolean ignited;

	/** 启动读条进度（0~当前配方读条长度，见 {@link #getStartupTicks()}），GUI 的进度条直接读它 */
	public int ignition;

	/** 客户端 3D 循环音效控制器（仅客户端有效） */
	private AudioWrapper audioLoop;
	private boolean wasIgnited = false;
	private int spinDownTicks = 0;

	/** 这一轮接手到的供料配方名（每 tick 由加热器调用 receivePlasma 时登记） */
	private final java.util.Set<String> incomingRecipes = new java.util.HashSet<String>();
	/** 所有供料者都换成的候选配方，以及它连续出现的 tick 数 */
	private String pendingRecipe;
	private int pendingTicks;

	/** 增殖进度（中子通量累积） */
	private double breedProgress;

	/** 物品产物计数：配方的物品产物并非每 tick 都给，攒够间隔才出一次（氢-硼 60 tick） */
	private int itemProgress;

	public FluidTank[] tanks = new FluidTank[] {
		new FluidTank(Fluids.NONE, PLASMA_CAPACITY),
		new FluidTank(Fluids.NONE, BREED_CAPACITY),
		new FluidTank(supercriticalSteamType(), COOLANT_CAPACITY),
		new FluidTank(Fluids.NONE, FLUID_OUT_CAPACITY),
		new FluidTank(Fluids.NONE, BREED_OUT_CAPACITY),
		new FluidTank(Fluids.WATER, COOLANT_CAPACITY),
	};

	public static FluidType supercriticalSteamType() {
		FluidType type = com.makentmgreatagain.init.ModFluids.SUPERCRITICAL_STEAM.type;
		return type == null ? Fluids.NONE : type;
	}

	/** 等离子罐的短名字（等离子加热器按字段注入，保持既有调用点不变） */
	public FluidTank plasma = this.tanks[TANK_PLASMA];

	protected PortDef[] cachedPorts;

	/** 端口格是否已经补过 extra（旧存档第一次 tick 补一次） */
	private boolean portsChecked;

	/** 旧版整片 31×31×9 占格是否已经清过（第一次 tick 清一次） */
	private boolean structureTrimmed;

	/** 端口代理是否已经绑定过核心（第一次 tick 立即绑，之后每 20 tick 复查） */
	private boolean portsBound;

	public TileEntityStellarator() {
		super(SLOT_COUNT);
	}

	@Override
	public String getName() {
		return "container.stellarator";
	}

	/* ========================== 端口 ========================== */

	public PortDef[] getPorts() {
		if(this.cachedPorts == null) {
			if(!(this.getBlockType() instanceof BlockStellarator)) return new PortDef[0];
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			BlockStellarator block = (BlockStellarator) this.getBlockType();
			int[][] ports = block.getPortsAtCore(dir);
			this.cachedPorts = new PortDef[ports.length];
			for(int i = 0; i < ports.length; i++) {
				this.cachedPorts[i] = PortDef.make(xCoord + ports[i][0], yCoord + ports[i][1], zCoord + ports[i][2],
						ForgeDirection.getOrientation(ports[i][3]));
			}
		}
		return this.cachedPorts;
	}

	/**
	 * 端口格补 extra。
	 *
	 * <p>{@code BlockStellarator.fillSpace} 现在会在放机器时把端口格升成 extra（元数据 +6），
	 * 代理 TE 才会被创建、管道才连得上。但早先版本放的机器端口格还是普通 dummy
	 * （元数据 0~5、没有 TE），这里在第一次 tick 补一次，省得把整台机器拆了重放。</p>
	 */
	private void ensurePortExtras() {

		if(this.portsChecked) return;
		this.portsChecked = true;

		if(!(this.getBlockType() instanceof BlockStellarator)) return;

		BlockStellarator block = (BlockStellarator) this.getBlockType();

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
	 * 旧版结构是一个整片 31×31×9 的方块盒子，四角和圆环内部空腔虽然没碰撞也占着格。
	 * 现在占格只保留碰撞圆环（{@link BlockStellarator#isRingCell}）；第一次服务端 tick
	 * 把范围内的其余自家 dummy 清成空气，旧机器不用拆了重放，清出来的位置可以直接放方块、
	 * 也让等离子加热器的无碰撞桶管伸进来。
	 */
	private void trimLegacyStructure() {

		if(this.structureTrimmed) return;
		this.structureTrimmed = true;

		if(!(this.getBlockType() instanceof BlockStellarator)) return;

		BlockStellarator block = (BlockStellarator) this.getBlockType();

		boolean changed = false;
		BlockDummyable.safeRem = true;

		for(int a = this.xCoord - 15; a <= this.xCoord + 15; a++) {
			for(int b = this.yCoord; b <= this.yCoord + 8; b++) {
				for(int c = this.zCoord - 15; c <= this.zCoord + 15; c++) {

					if(block.isRingCell(b - this.yCoord, a - this.xCoord, c - this.zCoord)) continue;
					if(this.worldObj.getBlock(a, b, c) != block) continue;

					this.worldObj.setBlockToAir(a, b, c);
					changed = true;
				}
			}
		}

		BlockDummyable.safeRem = false;

		if(changed) {
			this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
		}
	}

	/**
	 * 把核心坐标写进每个端口代理 TE 的 {@code cachedPosition}。
	 *
	 * <p>{@code TileEntityProxyBase.getTE()} 会优先用这个字段直接取核心 TE，绕开元数据链。
	 * 仿星器的顶板只剩中心 r≤3，那一层的元数据是 UP，往下一格就是空腔，
	 * {@code findCore} 在空腔处断链；不绑定的话顶部 5 个接口的代理格全部拿不到核心，
	 * 库存、电力、流体都透传不过去。</p>
	 */
	private void bindPortTiles() {

		if(!(this.getBlockType() instanceof BlockStellarator)) return;
		if(this.getPorts().length == 0) return;

		for(PortDef port : this.getPorts()) {

			if(port.portPositions == null) continue;

			for(BlockPos pos : port.portPositions) {

				int px = pos.getX(), py = pos.getY(), pz = pos.getZ();

				if(px == this.xCoord && py == this.yCoord && pz == this.zCoord) continue;

				TileEntity te = this.worldObj.getTileEntity(px, py, pz);
				if(!(te instanceof TileEntityProxyBase)) continue;

				TileEntityProxyBase proxy = (TileEntityProxyBase) te;

				// 旧存档里可能留着早先创建的代理（当时还没开库存/电/流体透传），
				// 这里顺手把三项标志补上，省得玩家把整台机器拆了重放。
				if(proxy instanceof TileEntityProxyCombo) {
					((TileEntityProxyCombo) proxy).inventory().power().fluid();
				}

				BlockPos old = proxy.cachedPosition;

				if(old == null || old.getX() != this.xCoord || old.getY() != this.yCoord || old.getZ() != this.zCoord) {
					proxy.cachedPosition = new BlockPos(this.xCoord, this.yCoord, this.zCoord);
					proxy.markDirty();
				}
			}
		}
	}

	/* ========================== 主循环 ========================== */

	@Override
	public void updateEntity() {

		// 客户端也要有本模组的配方对象：GUI 的储电上限、配方图标、三格指示灯都靠它。
		// 宿主把聚变表写成 json 再读回来时，配方会退化成普通 FusionRecipe，这里连类型一起查
		if(!HydrogenFusion.isRegistered()) HydrogenFusion.addRecipe();

		// 端口代理格直接绑定核心坐标：顶板的元数据链会穿过圆环空腔，基类默认的
		// findCore 在那一侧找不到核心，代理格就会变成没有库存/电/流体的空壳。
		if(this.worldObj.getTotalWorldTime() % 20 == 0) this.bindPortTiles();

		if(this.worldObj.isRemote) {
			this.updateAudioClient();
			return;
		}

		this.ensurePortExtras();
		this.trimLegacyStructure();
		this.sanitizeRecipe();

		// 第一次 tick 立刻绑一次，别让玩家刚接上管道还要等 1 秒才有响应
		if(!this.portsBound) { this.bindPortTiles(); this.portsBound = true; }

		// 配方表被宿主重建时（/ntmreload、客户端配方同步）自愈：缺了就重新注册一次
		if(!HydrogenFusion.isRegistered()) {
			HydrogenFusion.addRecipe();
		}
		FluidType steamType = supercriticalSteamType();
		if(steamType != Fluids.NONE && this.tanks[TANK_STEAM].getTankType() != steamType) {
			this.tanks[TANK_STEAM].setTankType(steamType);
			this.markDirty();
		}
		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		this.power = Library.chargeTEFromItems(this.slots, SLOT_BATTERY, this.power, this.getMaxPower());
		this.power = Math.min(this.power, this.getMaxPower());

		// 流体识别码槽：换型时罐内原有的流体清零（原版识别符的通用行为）
		this.tanks[TANK_BREED_IN].setType(SLOT_BREED_ID, this.slots);
		// 产物罐跟着识别码一起换型：换完配方界面上立刻是新的产物流体，不用等第一炉
		this.syncBreedTankType();

		this.operate();
		this.trackIncomingRecipes();

		// 每 tick 同步一次：罐里每 tick 都在变（10 mB 级），隔几 tick 才发一次界面上就会“一段一段”
		this.networkPackNT(200);
	}

	/* ========================== 运行 ========================== */

	/**
	 * 本配方每 tick 的水耗（mB/t）：产热每 {@link #TU_PER_BOIL} TU 算一轮，
	 * 每轮消耗 {@link #WATER_PER_BOIL} mB 水（与 {@link #boil(FusionRecipe)} 的口径一致）。
	 */
	private static long waterPerTick(FusionRecipe r) {
		if(r == null || r.outputTemp <= 0) return 0L;
		return (long) r.outputTemp / TU_PER_BOIL * WATER_PER_BOIL;
	}

	/**
	 * 冷却罐（水 / 超临界蒸汽）的容量：从 {@link #COOLANT_CAPACITY}（64,000 mB）起，
	 * 每次再加一个 {@link #COOLANT_STEP}（32,000 mB），直到单 tick 水耗也有 ≥ 50% 的冗余。
	 */
	private static int coolantCapacity(FusionRecipe r) {
		long needed = waterPerTick(r) * 3L / 2L;
		int cap = COOLANT_CAPACITY;
		while(cap < needed && cap <= Integer.MAX_VALUE - COOLANT_STEP) cap += COOLANT_STEP;
		return cap;
	}

	/** 把水 / 超浓密蒸汽罐的上限调到当前配方需要的容量（超出部分由 changeTankSize 截掉） */
	private void applyCoolantCapacity(FusionRecipe r) {
		int cap = coolantCapacity(r);
		if(this.tanks[TANK_WATER].getMaxFill() == cap && this.tanks[TANK_STEAM].getMaxFill() == cap) return;
		this.tanks[TANK_WATER].changeTankSize(cap);
		this.tanks[TANK_STEAM].changeTankSize(cap);
	}

	/**
	 * 启动 / 运行：
	 * <ol>
	 *   <li>配方、燃料（超过一半）、水、电、产物空间任一不满足 → 熄火，读条按 5 s 收回，不瞬间清零；</li>
	 *   <li>条件满足但读条未满 → 每 tick +1（读条长度按配方：氢-氢 200 tick = 10 s，氢-硼 600 tick = 30 s）；</li>
	 *   <li>读条满了才开始运行：每 tick 扣 20 mB 等离子 + 电，产出 15 mB 氦，增殖 / 冷却照旧；</li>
	 *   <li>运行期间条件一直满足，读条就停在满格，不会自己回落。</li>
	 * </ol>
	 */
	private void operate() {

		FusionRecipe r = this.getRecipe();

		// 冷却罐容量跟着配方走：换配方后上限立刻变，超出的存量会被截掉
		this.applyCoolantCapacity(r);

		if(r == null) {
			this.isOn = false;
			this.stop();
			return;
		}

		int perTick = HydrogenFusion.plasmaPerTick(r);
		long cost = Math.max(1L, r.power);

		boolean ready = this.plasma.getFill() > MIN_PLASMA
				&& this.canCool(r)
				&& this.power >= cost
				&& this.canFitOutputs(r);

		if(!ready) {
			this.isOn = false;
			this.stop();
			return;
		}

		if(!this.isOn) {
			this.stop();
			return;
		}

		int start = this.getStartupTicks();

		if(this.ignition > start) this.ignition = start;

		if(this.ignition < start) {
			this.ignition++;
			this.ignited = false;
			return;
		}

		this.ignition = start;
		this.ignited = true;

		this.plasma.setFill(this.plasma.getFill() - perTick);
		this.power -= cost;
		this.produce(r);
		this.breed(r);
		this.boil(r);
		this.markDirty();
	}

	/** 熄火，并让启动读条从右往左收回（{@link HydrogenFusion#SHUTDOWN_TICKS} = 100 tick = 5 s 收完） */
	private void stop() {

		this.ignited = false;

		if(this.ignition > 0) {
			int step = Math.max(1, this.getStartupTicks() / HydrogenFusion.SHUTDOWN_TICKS);
			this.ignition = Math.max(0, this.ignition - step);
		}
	}

	/** 当前配方的启动读条长度（氢-氢 200 tick，氢-硼 600 tick） */
	public int getStartupTicks() {
		return HydrogenFusion.startupTicks(this.recipe);
	}

	/** 产出罐能不能装下这一 tick 的产物 */
	private boolean canFitOutputs(FusionRecipe r) {

		if(r.outputItem != null && r.outputItem.length > 0) {
			// 物品产物按间隔给（氢-硼 60 tick 一个），只有这一 tick 该出时才检查槽位
			int interval = HydrogenFusion.itemInterval(r.getInternalName());
			if(interval <= 1 || this.itemProgress + 1 >= interval) {
				// 聚变配方都是单产出，getSingle() 拿不到（多产出池）时跳过这项检查
				ItemStack out = r.outputItem[0].getSingle();
				if(out != null && !this.canFitStack(SLOT_PLASMA_OUT, out)) return false;
			}
		}

		if(r.outputFluid != null) {
			for(FluidStack stack : r.outputFluid) {
				FluidTank tank = this.tanks[TANK_FLUID_OUT];
				if(tank.getTankType() != stack.type && tank.getFill() > 0) return false;
				if(tank.getMaxFill() - tank.getFill() < stack.fill) return false;
			}
		}

		return true;
	}

	private boolean canFitStack(int slot, ItemStack stack) {

		ItemStack current = this.slots[slot];
		if(current == null) return true;
		if(current.getItem() != stack.getItem() || current.getItemDamage() != stack.getItemDamage()) return false;

		return current.stackSize + stack.stackSize <= current.getMaxStackSize();
	}

	/** 循环完成：出物品与流体 */
	private void produce(FusionRecipe r) {

		if(r.outputItem != null && r.outputItem.length > 0) {
			// 物品产物按间隔给：每跑满 interval tick 出一次（氢-硼 60 tick 一个电离粒子）
			int interval = HydrogenFusion.itemInterval(r.getInternalName());
			if(interval <= 1 || ++this.itemProgress >= interval) {
				this.itemProgress = 0;
				ItemStack out = r.outputItem[0].collapse();
				if(out != null) {
					ItemStack slot = this.slots[SLOT_PLASMA_OUT];
					if(slot == null) this.slots[SLOT_PLASMA_OUT] = out.copy();
					else slot.stackSize += out.stackSize;
				}
			}
		}

		if(r.outputFluid != null) {
			for(FluidStack stack : r.outputFluid) {
				FluidTank tank = this.tanks[TANK_FLUID_OUT];
				tank.setTankType(stack.type);
				tank.setFill(Math.min(tank.getMaxFill(), tank.getFill() + stack.fill));
			}
		}
	}

	/**
	 * 增殖：中子通量累积，按 {@link StellaratorBreeding} 的聚变专用表完成嬗变。
	 *
	 * <p><b>固相优先</b>：槽 2 里放着表认得的物品就照物品，固体产物进槽 3、流体产物进罐 4；
	 * 槽 2 空着才走液相，把罐 1 的原料转成产物进罐 4。两条路共用一份 {@code breedProgress}，
	 * 完成一次需要的通量由配方自己的 {@code flux} 给出。</p>
	 *
	 * <p>每一 tick 运行都会按聚变配方给出中子通量，但只有槽 2 有可增殖物品、或罐 1 有可增殖
	 * 流体并且够一次转化时，内部通量才继续积攒；没有可增殖的料就只是空转，不再累积。
	 * 产物装不下时同样只是<b>停止累积</b>，不清零——接上管道 / 清空产物后从原进度继续攒。</p>
	 */
	private void breed(FusionRecipe r) {

		if(r.neutronFlux <= 0) return;

		StellaratorBreeding.ItemRecipe solid = StellaratorBreeding.getItemRecipe(this.slots[SLOT_BREED_IN]);

		// 固相优先：槽 2 有可增殖物品就只走固相，不再回落到液相（回落到液相会让
		// 通量一直原地不动，看着就是“有配方但不增殖”）。产物暂时装不下时把通量
		// 顶在门槛上等待，不清零，接上管道 / 清空产物后立刻完成这一次转化。
		if(solid != null) {
			this.breedProgress = Math.min(solid.flux, this.breedProgress + r.neutronFlux);
			if(this.breedProgress >= solid.flux && this.canFitBreedItem(solid)) {
				this.breedProgress -= solid.flux;
				this.runBreedItem(solid);
			}
			return;
		}

		FluidTank in = this.tanks[TANK_BREED_IN];
		StellaratorBreeding.FluidRecipe liquid = StellaratorBreeding.getFluidRecipe(in.getTankType());

		// 没有可增殖的流体 / 物品就只运行、不积攒内部通量
		if(liquid == null || in.getFill() < liquid.inputAmount) return;
		if(!this.canFitBreedFluid(liquid)) return;

		// 内部通量攒够配方要求的那一 tick，按配方完成一次转化
		this.breedProgress = Math.min(liquid.flux, this.breedProgress + r.neutronFlux);
		if(this.breedProgress >= liquid.flux) {
			this.breedProgress -= liquid.flux;
			in.setFill(in.getFill() - liquid.inputAmount);
			this.addBreedFluid(liquid.output, liquid.outputAmount);
			this.markDirty();
		}
	}

	/**
	 * 中子进度条：槽 2 里的物品在当前聚变通量下，已攒的通量占该物品门槛的比例。
	 * 没有物品（或物品不在增殖表里）时返回 0，GUI 就不显示这条。
	 */
	public int getNeutronScaled(int pixels) {

		StellaratorBreeding.ItemRecipe solid = StellaratorBreeding.getItemRecipe(this.slots[SLOT_BREED_IN]);
		if(solid == null || solid.flux <= 0D) return 0;

		double frac = this.breedProgress / solid.flux;
		if(frac <= 0D) return 0;
		if(frac > 1D) frac = 1D;
		return (int) Math.round(frac * pixels);
	}

	/** 固相配方的产物位置是否装得下（物品槽 + 流体罐两项都要过） */
	private boolean canFitBreedItem(StellaratorBreeding.ItemRecipe recipe) {

		if(this.slots[SLOT_BREED_IN] == null) return false;

		if(recipe.output != null && !this.canFitStack(SLOT_BREED_OUT, recipe.output)) return false;

		if(recipe.fluidOutput != null) {
			FluidTank out = this.tanks[TANK_BREED_OUT];
			if(out.getTankType() != recipe.fluidOutput.type && out.getFill() > 0) return false;
			if(out.getMaxFill() - out.getFill() < recipe.fluidOutput.fill) return false;
		}

		return true;
	}

	private boolean canFitBreedFluid(StellaratorBreeding.FluidRecipe recipe) {

		FluidTank out = this.tanks[TANK_BREED_OUT];
		if(out.getTankType() != recipe.output && out.getFill() > 0) return false;

		return out.getMaxFill() - out.getFill() >= recipe.outputAmount;
	}

	private void runBreedItem(StellaratorBreeding.ItemRecipe recipe) {

		this.decrStackSize(SLOT_BREED_IN, 1);

		if(recipe.output != null) {
			ItemStack slot = this.slots[SLOT_BREED_OUT];
			if(slot == null) this.slots[SLOT_BREED_OUT] = recipe.output.copy();
			else slot.stackSize += recipe.output.stackSize;
		}

		if(recipe.fluidOutput != null) this.addBreedFluid(recipe.fluidOutput.type, recipe.fluidOutput.fill);

		this.markDirty();
	}

	/** 往「增值后输出」罐里加流体（同型才加，换型交给 {@link #syncBreedTankType()}） */
	private void addBreedFluid(FluidType type, int amount) {

		FluidTank out = this.tanks[TANK_BREED_OUT];
		out.setTankType(type);
		out.setFill(Math.min(out.getMaxFill(), out.getFill() + amount));
		this.markDirty();
	}

	/**
	 * 冷却：把配方的产热 {@code outputTemp}（TU/t）按「每 {@link #TU_PER_BOIL} TU
	 * 把 {@link #WATER_PER_BOIL} mB 水变成 {@link #STEAM_PER_BOIL} mB 超临界蒸汽」换成超临界蒸汽。
	 *
	 * <p>氢-氢满功率是 100,000 TU/t → 每 tick 100 轮（水 10,000 mB → 超临界蒸汽 1,000 mB）。
	 * 罐容由 {@link #coolantCapacity(FusionRecipe)} 从 64,000 mB 起按每级 +32,000 mB 自动扩容，
	 * 为单 tick 最大水耗留出 ≥ 50% 冗余；供水跟不上时按到手的量减产，一滴水都没有就由 {@link #canCool} 直接停机。</p>
	 */
	private void boil(FusionRecipe r) {

		if(r.outputTemp <= 0) return;

		FluidTank water = this.tanks[TANK_WATER];
		FluidTank steam = this.tanks[TANK_STEAM];

		long cycles = r.outputTemp / TU_PER_BOIL;
		cycles = Math.min(cycles, water.getFill() / WATER_PER_BOIL);
		cycles = Math.min(cycles, (steam.getMaxFill() - steam.getFill()) / STEAM_PER_BOIL);
		if(cycles <= 0) return;

		water.setFill(water.getFill() - (int) (cycles * WATER_PER_BOIL));
		steam.setFill(steam.getFill() + (int) (cycles * STEAM_PER_BOIL));
	}

	/**
	 * 这一 tick 能不能冷却：水罐至少够一轮（{@link #WATER_PER_BOIL} mB 水），
	 * 蒸汽罐至少能装一轮（{@link #STEAM_PER_BOIL} mB）。
	 * 两个条件都不满足就整机停机——<b>没有水就是真的停</b>，不会烧着等离子空转。
	 */
	private boolean canCool(FusionRecipe r) {

		if(r.outputTemp <= 0) return true;

		FluidTank water = this.tanks[TANK_WATER];
		FluidTank steam = this.tanks[TANK_STEAM];

		if(water.getFill() < WATER_PER_BOIL) return false;
		return steam.getMaxFill() - steam.getFill() >= STEAM_PER_BOIL;
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

	/**
	 * 旧存档里的配方名可能已经不在表里（现在整张聚变表只剩氢-氢）：
	 * 未点火时退回“未选配方”，清掉罐里的旧等离子，等加热器重新灌入。
	 */
	private void sanitizeRecipe() {

		if(this.recipe == null || NULL_RECIPE.equals(this.recipe)) return;
		if(this.getRecipe() != null) return;
		if(this.ignited) return;

		this.recipe = NULL_RECIPE;
		this.ignition = 0;
		if(this.plasma.getFill() > 0) this.plasma.setFill(0);
		this.markDirty();
	}

	/**
	 * 指定格是否落在本机整机范围内（水平 ±15 格、高度 0~8，含环内空腔）。
	 *
	 * <p>等离子加热器用它判定“桶管口是否伸进仿星器内部”：桶管是从无碰撞的空腔穿进来的，
	 * 所以判定不能看那一格有没有方块，只能看它是否在这台机器的包围范围里。</p>
	 */
	public boolean containsPoint(int x, int y, int z) {
		return this.containsPoint(x, y, z, 0);
	}

	/**
	 * 带容差版本：{@code margin} 格以内也算“贴着”。加热器用 1 格容差——
	 * 桶管口正好在紧邻的一格、或两台机器地面相差 1 格时也能判定为对接，
	 * 免得玩家必须把 31 格宽的机器卡得严丝合缝。
	 */
	public boolean containsPoint(int x, int y, int z, int margin) {

		int dx = x - this.xCoord;
		int dy = y - this.yCoord;
		int dz = z - this.zCoord;

		if(dy < -margin || dy > 8 + margin) return false;

		int r = 15 + margin;
		if(Math.abs(dx) > r || Math.abs(dz) > r) return false;

		return dx * dx + dz * dz <= r * r;
	}

	/**
	 * 桶管口能不能算“插进来了”：水平半径必须比整机包围圈 15 再往里 2 格（≤13）。
	 *
	 * <p>加热器机体前沿在本机核心格前方 5 格、桶管口在 10 格。若口子离环面还差 g 格，
	 * 则口子到核心的距离是 10+g；要求 g ≤ 3 就是半径 ≤ 13。这样“离环面 4 格还能收到等离子”
	 * 的情况会被挡掉。</p>
	 */
	public boolean acceptsNozzle(int x, int y, int z) {

		int dx = x - this.xCoord;
		int dy = y - this.yCoord;
		int dz = z - this.zCoord;

		if(dy < 0 || dy > 8) return false;

		return dx * dx + dz * dz <= 13 * 13;
	}

	/** 罐空着、或还没有配方时接受加热器带来的配方；已经有配方且罐里有料时才拒绝，避免半途换配方 */
	public void adoptRecipe(String name) {
		if(name == null || name.isEmpty() || NULL_RECIPE.equals(name)) return;
		if(this.plasma.getFill() > 0 && !NULL_RECIPE.equals(this.recipe)) return;
		if(name.equals(this.recipe)) return;

		this.applyRecipe(name);
	}

	/**
	 * 换配方：旧的聚变产物直接清空，产出罐换成新配方的产物类型。
	 *
	 * <p>两种情况会走到这里：罐被烧空后换料（{@link #adoptRecipe}），或者所有供料加热器
	 * 都把配方改了（{@link #trackIncomingRecipes} 的强制切换）。</p>
	 */
	private void applyRecipe(String name) {

		this.recipe = name;
		this.ignited = false;
		this.ignition = 0;
		this.itemProgress = 0;

		FusionRecipe r = this.lookupRecipe(name);
		FluidTank out = this.tanks[TANK_FLUID_OUT];
		out.setFill(0);
		this.slots[SLOT_PLASMA_OUT] = null;
		if(r != null && r.outputFluid != null && r.outputFluid.length > 0) {
			out.setTankType(r.outputFluid[0].type);
		}

		this.markDirty();
	}

	private FusionRecipe lookupRecipe(String name) {
		GenericRecipe recipe = StellaratorRecipes.INSTANCE.recipeNameMap.get(name);
		return recipe instanceof FusionRecipe ? (FusionRecipe) recipe : null;
	}

	/**
	 * 供料配方跟踪：所有接上的加热器这一轮都在递同一个新配方、且都已经不是当前配方时，
	 * 连续 10 tick 后强制切换（产物按新配方清空换型）。只要还有一台加热器在递当前配方，
	 * 就保持“先到先得”。
	 */
	private void trackIncomingRecipes() {

		if(this.incomingRecipes.isEmpty() || this.incomingRecipes.contains(this.recipe)
				|| this.incomingRecipes.size() != 1) {
			this.pendingRecipe = null;
			this.pendingTicks = 0;
			this.incomingRecipes.clear();
			return;
		}

		String next = this.incomingRecipes.iterator().next();

		if(next.equals(this.pendingRecipe)) {
			this.pendingTicks++;
		} else {
			this.pendingRecipe = next;
			this.pendingTicks = 1;
		}

		if(this.pendingTicks >= 10) {
			this.applyRecipe(next);
			this.pendingRecipe = null;
			this.pendingTicks = 0;
		}

		this.incomingRecipes.clear();
	}

	/** 已点火且正在运行（供渲染 / 调试用） */
	public boolean isRunning() {
		return this.ignited;
	}

	/** 启动读条按比例换算成像素（GUI 从左往右填充） */
	public int getIgnitionScaled(int pixels) {
		int start = this.getStartupTicks();
		if(this.ignition <= 0 || start <= 0) return 0;
		return (int) Math.round((double) Math.min(this.ignition, start) / start * pixels);
	}

	/* ========================== 指示灯 ========================== */

	/** 指示灯 1：有配方、内部电力够本 tick 运行 */
	public boolean hasPowerForRun() {

		FusionRecipe r = this.getRecipe();
		if(r == null) return false;

		return this.power >= Math.max(1L, r.power);
	}

	/** 指示灯 2：水超过当前容量的一半 */
	public boolean hasEnoughWater() {
		FluidTank water = this.tanks[TANK_WATER];
		return water.getFill() * 2 > water.getMaxFill();
	}

	/** 指示灯 3：等离子体超过一半 */
	public boolean hasEnoughPlasma() {
		return this.plasma.getFill() * 2 > PLASMA_CAPACITY;
	}

	/** 三个指示灯全部亮起时才允许操作仿星器开关按钮 */
	public boolean canTurnOn() {
		return this.hasPowerForRun() && this.hasEnoughWater() && this.hasEnoughPlasma();
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {
		if(data.hasKey("toggle")) {
			if(this.isOn) {
				this.isOn = false;
				this.markDirty();
			} else if(this.canTurnOn()) {
				this.isOn = true;
				this.markDirty();
			}
		}
	}

	/* ========================== 注入接口 ========================== */

	/**
	 * 同步来料类型。
	 *
	 * <p>同种流体不动；异种流体只在<b>没点火</b>时才允许换型。换型会清空存量
	 * （{@link FluidTank#setTankType} 的行为），但这是换配方后唯一能把旧等离子换掉的路：
	 * 等离子罐既不接管道、也没有可以手动取出的槽位，不给这条路换完配方就死锁在
	 * 「旧类型的料不到 64,000 点不着火、新的又进不来」。</p>
	 */
	public void adoptPlasmaType(FluidType type) {

		if(type == null || type == Fluids.NONE) return;
		if(this.plasma.getTankType() == type) return;
		if(this.ignited) return;	// 正在烧就不打断，等停机后自然换型

		this.plasma.setTankType(type);
		this.markDirty();
	}

	/** 类型不符、罐满都不收；有料就自动运行，不需要手动开关 */
	public boolean acceptsPlasma(FluidType type) {

		if(type == null || type == Fluids.NONE) return false;

		if(this.plasma.getFill() <= 0) return true;

		return this.plasma.getTankType() == type;
	}

	/**
	 * 接收等离子，返回实际收下的量。
	 * 由等离子加热器每 tick 调用，单次量由调用方限制（旧版为 40 mB/tick）。
	 */
	public int receivePlasma(FluidType type, int amount) {
		return this.receivePlasma(type, amount, null);
	}

	/**
	 * 带配方名的注入：罐空着时顺便把加热器选中的聚变配方接过来，
	 * 之后仿星器就按这个配方运行（判定与产物都用原版 {@link FusionRecipe} 的数据）。
	 */
	public int receivePlasma(FluidType type, int amount, String recipeName) {

		if(amount <= 0 || !this.acceptsPlasma(type)) return 0;

		// 登记这一 tick 的供料配方：所有供料者都换成同一个新配方时，trackIncomingRecipes 会强制切换
		if(recipeName != null && !NULL_RECIPE.equals(recipeName)) this.incomingRecipes.add(recipeName);

		if(this.plasma.getFill() <= 0) {
			this.plasma.setTankType(type);
			this.adoptRecipe(recipeName);
		}

		// 等离子体输入上限：每 tick 最多收 100 mB（加热器那边也是同样口径）
		int space = this.plasma.getMaxFill() - this.plasma.getFill();
		int accepted = Math.min(space, Math.min(amount, PLASMA_INTAKE_PER_TICK));
		if(accepted <= 0) return 0;

		this.plasma.setFill(this.plasma.getFill() + accepted);
		this.markDirty();

		return accepted;
	}

	/**
	 * 只交配方不交料：加热器对接上就把配方名递过来，等离子罐还空着时直接采纳，
	 * 界面上立刻能看到当前配方（真正开工仍要等等离子攒够）。
	 */
	public void offerRecipe(String name) {
		if(this.plasma.getFill() <= 0) this.adoptRecipe(name);
	}

	/* ========================== 开关 ========================== */

	/**
	 * GUI 的交互距离。{@code TileEntityMachineBase} 默认 12 格，是给一格大的机器用的；
	 * 本机 31×31、核心格在正中，站在外圈点开的格子离核心有 20 格以上，服务端每 tick 的
	 * {@code ForgeHooks.canInteractWith} 会立刻把界面关掉——表现就是"很多格子右键没界面"。
	 * 按「最远一格到核心 ≈ 21.9 格 + 手上 4.5 格操作距离」给 32。
	 */
	@Override
	public double getUseRange() {
		return 32D;
	}

	/* ========================== 电力 ========================== */

	@Override public long getPower() { return this.power; }
	@Override public void setPower(long power) { this.power = Math.max(0L, Math.min(this.getMaxPower(), power)); }

	/** 储电上限：配方“约束力量”× 100（氢配方 10M/t → 1G）；没有配方时用兜底值 */
	@Override public long getMaxPower() {

		long confinement = 0L;

		FusionRecipe r = this.getRecipe();
		if(r instanceof HydrogenFusionRecipe) confinement = ((HydrogenFusionRecipe) r).getConfinementPower();

		// 客户端表里可能只剩普通 FusionRecipe（json 读回 / 同步），按名字兜底
		if(confinement <= 0 && HydrogenFusion.isHydrogen(this.recipe)) confinement = HydrogenFusion.CONFINEMENT;

		return confinement > 0 ? confinement * 100L : MAX_POWER;
	}

	/** GUI 电力条：0~52 像素 */
	public int getPowerScaled(int scale) {
		return (int) (this.power * scale / Math.max(1L, this.getMaxPower()));
	}

	/* ========================== 流体接口 ========================== */

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { this.tanks[TANK_WATER], this.tanks[TANK_BREED_IN] };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { this.tanks[TANK_STEAM], this.tanks[TANK_FLUID_OUT], this.tanks[TANK_BREED_OUT] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return this.tanks;
	}

	/* ========================== 增殖配对 ========================== */

	/**
	 * 让「增值后输出」罐始终摆着当前增殖路线的产物类型。
	 *
	 * <p>原版 {@code TileEntityFusionBreeder.canProcessLiquid} 每个 tick 都会把产物罐
	 * {@code setTankType} 成配方产物，所以识别码一换、罐子上立刻能看到产物，不必等中子通量
	 * 攒满。本机之前只在真正产出时改类型，换完配方界面上还是旧流体，
	 * 而且旧流体不排空新配方就永远走不动——两头堵。</p>
	 *
	 * <p>两条路线的处理不一样：<b>液相</b>走识别码，是玩家显式切换的，换完直接换型
	 * （{@code setTankType} 会清掉旧产物，否则旧料会把新配方堵死）；
	 * <b>固相</b>换型只在罐子空着时发生，免得玩家往槽里放一块锂就把攒了半天的氚清空。</p>
	 */
	private void syncBreedTankType() {

		FluidTank out = this.tanks[TANK_BREED_OUT];

		StellaratorBreeding.ItemRecipe solid = StellaratorBreeding.getItemRecipe(this.slots[SLOT_BREED_IN]);

		if(solid != null) {
			if(solid.fluidOutput != null && out.getFill() <= 0 && out.getTankType() != solid.fluidOutput.type) {
				out.setTankType(solid.fluidOutput.type);
				this.breedProgress = 0;
				this.markDirty();
			}
			return;
		}

		FluidType product = StellaratorBreeding.getFluidProduct(this.tanks[TANK_BREED_IN].getTankType());
		if(product == null || out.getTankType() == product) return;

		out.setTankType(product);
		// 换型时已攒的通量作废，否则换完配方第一炉会「立刻」出产物，看着像配方错位
		this.breedProgress = 0;
		this.markDirty();
	}

	/* ========================== 物品接口 ========================== */

	/**
	 * 端口代理会把这些槽位转给管道/漏斗：电池进出、等离子产物取出、增殖进料 / 产物、识别码塞入。
	 *
	 * <p>增殖的物品路线现在有了自己的表（{@link StellaratorBreeding}），所以槽 2/3 也一起开放：
	 * 管道可以往槽 2 塞锂粉、金锭之类的原料，槽 3 的产物也能被抽走，整条链不再需要玩家手动掏。</p>
	 */
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { SLOT_BATTERY, SLOT_PLASMA_OUT, SLOT_BREED_IN, SLOT_BREED_OUT, SLOT_BREED_ID };
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		if(slot == SLOT_BATTERY)
			return stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative;

		if(slot == SLOT_BREED_ID)
			return stack.getItem() instanceof IItemFluidIdentifier;

		if(slot == SLOT_BREED_IN)
			return StellaratorBreeding.isBreedable(stack);

		return false;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return this.isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot == SLOT_PLASMA_OUT || slot == SLOT_BREED_OUT;
	}

	/* ========================== 同步与存档 ========================== */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		buf.writeBoolean(this.isOn);
		buf.writeBoolean(this.ignited);
		buf.writeInt(this.ignition);
		buf.writeDouble(this.breedProgress);
		for(FluidTank tank : this.tanks) tank.serialize(buf);
		ByteBufUtils.writeUTF8String(buf, this.recipe == null ? NULL_RECIPE : this.recipe);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.isOn = buf.readBoolean();
		this.ignited = buf.readBoolean();
		this.ignition = buf.readInt();
		this.breedProgress = buf.readDouble();
		for(FluidTank tank : this.tanks) tank.deserialize(buf);
		String name = ByteBufUtils.readUTF8String(buf);
		this.recipe = (name == null || name.isEmpty()) ? NULL_RECIPE : name;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.isOn = nbt.getBoolean("is_on");
		this.ignited = nbt.getBoolean("ignited");
		this.ignition = nbt.getInteger("ignition");
		this.breedProgress = nbt.getDouble("breed_progress");
		this.itemProgress = nbt.getInteger("item_progress");
		String name = nbt.getString("recipe");
		this.recipe = (name == null || name.isEmpty()) ? NULL_RECIPE : name;
		this.tanks[TANK_PLASMA].readFromNBT(nbt, "plasma");
		this.tanks[TANK_BREED_IN].readFromNBT(nbt, "breed_in");
		this.tanks[TANK_STEAM].readFromNBT(nbt, "steam");
		FluidType steamType = supercriticalSteamType();
		if(steamType != Fluids.NONE && this.tanks[TANK_STEAM].getTankType() != steamType) {
			this.tanks[TANK_STEAM].setTankType(steamType);
		}
		this.tanks[TANK_FLUID_OUT].readFromNBT(nbt, "fluid_out");
		this.tanks[TANK_BREED_OUT].readFromNBT(nbt, "breed_out");
		this.tanks[TANK_WATER].readFromNBT(nbt, "water");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setBoolean("is_on", this.isOn);
		nbt.setBoolean("ignited", this.ignited);
		nbt.setInteger("ignition", this.ignition);
		nbt.setDouble("breed_progress", this.breedProgress);
		nbt.setInteger("item_progress", this.itemProgress);
		nbt.setString("recipe", this.recipe == null ? NULL_RECIPE : this.recipe);
		this.tanks[TANK_PLASMA].writeToNBT(nbt, "plasma");
		this.tanks[TANK_BREED_IN].writeToNBT(nbt, "breed_in");
		this.tanks[TANK_STEAM].writeToNBT(nbt, "steam");
		this.tanks[TANK_FLUID_OUT].writeToNBT(nbt, "fluid_out");
		this.tanks[TANK_BREED_OUT].writeToNBT(nbt, "breed_out");
		this.tanks[TANK_WATER].writeToNBT(nbt, "water");
	}

	/**
	 * 物品自动化：接口格上的 {@code TileEntityProxyCombo} 库存代理会把位置相关的询问转给这里，
	 * 所以物流管道读到的就是本机的五个槽。
	 *
	 * <p>槽位规则：电池可进不可出（避免无线电池被物流抽走）；流体识别码可进不可出；
	 * 增殖原料槽只进；两个产物槽（聚变物品产物、增殖产物）只出。所有接口格一视同仁，
	 * 因为上下两组端口都只是把东西透传到核心。</p>
	 */
	@Override
	public int[] getAccessibleSlotsFromSide(int x, int y, int z, int side) {
		return new int[] { SLOT_BATTERY, SLOT_PLASMA_OUT, SLOT_BREED_IN, SLOT_BREED_OUT, SLOT_BREED_ID };
	}

	@Override
	public boolean isItemValidForSlot(int x, int y, int z, int slot, ItemStack stack) {
		if(stack == null) return false;
		switch(slot) {
		case SLOT_BATTERY: return stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative;
		case SLOT_BREED_ID: return stack.getItem() instanceof IItemFluidIdentifier;
		case SLOT_BREED_IN: return StellaratorBreeding.getItemRecipe(stack) != null;
		default: return false;
		}
	}

	@Override
	public boolean canExtractItem(int x, int y, int z, int slot, ItemStack stack, int side) {
		return slot == SLOT_PLASMA_OUT || slot == SLOT_BREED_OUT;
	}

	/**
	 * 核心格离玩家放置的那一格有 15 格远，客户端必须收到描述包才会创建核心 TE，
	 * 否则 TESR 永远不会被调用（世界里没有模型）。
	 */
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

	/* ========================== 渲染 ========================== */

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 15, yCoord, zCoord - 15, xCoord + 16, yCoord + 9, zCoord + 16);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	/* ========================== 音效与生命周期 ========================== */

	@SideOnly(Side.CLIENT)
	private void updateAudioClient() {
		float centerX = this.xCoord + 0.5F;
		float centerY = this.yCoord + 4.5F;
		float centerZ = this.zCoord + 0.5F;
		float range = 40.0F;

		if(MainRegistry.proxy.me() == null) return;
		double distSq = MainRegistry.proxy.me().getDistanceSq(centerX, centerY, centerZ);

		// 超出 50 格直接停掉声音，节省客户端开销
		if(distSq > 50 * 50) {
			this.stopAudio();
			this.spinDownTicks = 0;
			this.wasIgnited = this.ignited;
			return;
		}

		// 运行音量提高 50%（1.0F -> 1.5F），基频设为 0.80F 深沉超导嗡鸣
		final float RUN_VOLUME = 1.50F;
		final float RUN_PITCH = 0.80F;

		if(this.ignited) {
			// 状态 1：稳态点火运行中
			this.spinDownTicks = 0;

			if(this.audioLoop == null) {
				this.audioLoop = MainRegistry.proxy.getLoopedSound(
						"makentmgreatagain:block.stellaratorLoop",
						centerX, centerY, centerZ,
						RUN_VOLUME, range, RUN_PITCH, 20);
				this.audioLoop.startSound();
			} else {
				this.audioLoop.updateVolume(RUN_VOLUME);
				this.audioLoop.updatePitch(RUN_PITCH);
				this.audioLoop.keepAlive();
			}

		} else if(this.ignition > 0) {
			// 状态 2：充磁启动读条中（同源平滑升调与平滑淡入，完全不刺耳）
			this.spinDownTicks = 0;

			float totalTicks = Math.max(1, this.getStartupTicks());
			float progress = Math.min(1.0F, (float) this.ignition / totalTicks);

			// 音调从 0.35 逐渐平滑爬升至 0.80（点火瞬间与稳态 100% 自然吻合）
			float pitch = 0.35F + progress * (RUN_PITCH - 0.35F);
			// 音量从 0.20 平滑放大至 1.50
			float volume = 0.20F + progress * (RUN_VOLUME - 0.20F);

			if(this.audioLoop == null) {
				this.audioLoop = MainRegistry.proxy.getLoopedSound(
						"makentmgreatagain:block.stellaratorLoop",
						centerX, centerY, centerZ,
						volume, range, pitch, 20);
				this.audioLoop.startSound();
			} else {
				this.audioLoop.updateVolume(volume);
				this.audioLoop.updatePitch(pitch);
				this.audioLoop.keepAlive();
			}

		} else {
			// 状态 3：非点火且非读条中
			// 若上一时刻正在运行，触发 40 tick (2 秒) 的平滑下潜淡出，避免突兀截断
			if(this.wasIgnited && this.audioLoop != null) {
				this.spinDownTicks = 40;
			}

			if(this.spinDownTicks > 0 && this.audioLoop != null) {
				float decay = (float) this.spinDownTicks / 40.0F;
				float pitch = 0.25F + decay * (RUN_PITCH - 0.25F);
				float volume = decay * RUN_VOLUME;

				this.audioLoop.updateVolume(volume);
				this.audioLoop.updatePitch(pitch);
				this.audioLoop.keepAlive();
				this.spinDownTicks--;

				if(this.spinDownTicks <= 0) {
					this.stopAudio();
				}
			} else {
				this.stopAudio();
			}
		}

		this.wasIgnited = this.ignited;
	}

	@SideOnly(Side.CLIENT)
	private void stopAudio() {
		if(this.audioLoop != null) {
			this.audioLoop.stopSound();
			this.audioLoop = null;
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(this.worldObj.isRemote) {
			this.stopAudio();
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(this.worldObj.isRemote) {
			this.stopAudio();
		}
	}
}

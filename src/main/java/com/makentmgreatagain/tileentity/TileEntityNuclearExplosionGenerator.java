package com.makentmgreatagain.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.makentmgreatagain.blocks.BlockNuclearExplosionGenerator;
import com.makentmgreatagain.init.ModCoatings;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.init.ModNukeCores;
import com.makentmgreatagain.items.ItemCoating;
import com.makentmgreatagain.packet.NukeShakePacket;
import com.makentmgreatagain.recipes.NukeGeneratorData;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.common.network.NetworkRegistry;
import io.netty.buffer.ByteBuf;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * 可控核爆炸发生器核心。
 *
 * <p>运行一次 = 20 秒充能（光效从内向外展开）→ 核爆结算 → 20 秒消散。核爆那一 tick 里
 * 扣电力、把液态钠换成等量高温液态钠、把中子通量灌进增殖进度（见 {@link #onCycleFinished()}）。
 * 电力、液态钠不够或高温钠罐装不下则整次跳过。</p>
 *
 * <h3>接口</h3>
 * <p>能力全部通过 20 个 {@link TileEntityNuclearExplosionGeneratorPort} 代理方块对外暴露：
 * 电力可进可出、流体罐 0 收 / 罐 1 送、库存五格。代理方块只是"外形"，
 * 真正的管网连接靠 {@link #getPorts()} 生成的 UNINOS 节点（见 {@code updateEntity}）。</p>
 *
 * <h3>槽位</h3>
 * <pre>
 *  0  电池（只收 IBatteryItem）
 *  1  覆盖层（只收本模组的 ItemCoating）
 *  2  被增殖物品
 *  3  增殖产物（只出不进）
 *  4  核心（只收本模组的核装药核心）
 * </pre>
 *
 * <h3>流体</h3>
 * <p>罐 0 液态钠（输入），罐 1 高温液态钠（输出），类型固定，不需要识别码。</p>
 */
public class TileEntityNuclearExplosionGenerator extends TileEntityMachineBase
		implements IEnergyReceiverMK2, IEnergyProviderMK2, IFluidStandardTransceiverMK2 {

	public static final long MAX_POWER = 100_000_000L;
	/** 没有覆层时的默认罐容量；插上覆层后按覆层的容量走 */
	public static final int TANK_CAPACITY = 64_000;

	/** 一个周期 20 秒 = 400 tick：前半充能，爆轰后同样的 20 秒是光效消散 */
	public static final int CYCLE = 400;
	/** 自动模式在冷却结束（或刚开启）后再等 10 秒 = 200 tick 才启动下一轮 */
	public static final int AUTO_DELAY = 200;

	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_COATING = 1;
	public static final int SLOT_INPUT = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SLOT_CORE = 4;

	public long power;
	/** 0..CYCLE */
	public int progress;
	public boolean running;
	/** 爆轰结束后的光效消散阶段，同样 20 秒，期间运行键不能点 */
	public boolean cooling;
	/** 0..CYCLE */
	public int cool;

	/** 自动模式：Shift + 运行键开关，条件合格就按「冷却 20s + 等待 10s」循环引爆 */
	public boolean auto;
	/** 自动模式距离启动还剩多少 tick */
	public int autoDelay;

	/** 0 液态钠 / 1 高温液态钠 */
	public final FluidTank[] tanks = new FluidTank[2];

	/** 当前被增殖物品已积累的中子通量（设计文档里的 F1） */
	public int breedFlux;
	/** 这份进度属于哪个物品（注册名）；换物品时清空进度 */
	public String breedItem;

	/** 20 个接口的节点定义，坐标依赖核心位置，所以缓存一份 */
	protected PortDef[] cachedPorts;

	/**
	 * 正在充能的发生器（只在服务端登记，弱引用避免区块卸载后泄漏）。
	 * 拆方块时只查这一张小表，不再扫全世界找核心，拆机才不会卡。
	 */
	private static final Set<TileEntityNuclearExplosionGenerator> RUNNING =
			Collections.newSetFromMap(new WeakHashMap<TileEntityNuclearExplosionGenerator, Boolean>());

	public TileEntityNuclearExplosionGenerator() {
		super(5);
		this.tanks[0] = new FluidTank(Fluids.SODIUM, TANK_CAPACITY);
		this.tanks[1] = new FluidTank(Fluids.SODIUM_HOT, TANK_CAPACITY);
	}

	@Override
	public String getName() {
		return "container.nuclear_explosion_generator";
	}

	/** 模型比一个方块大得多，渲染包围盒要覆盖整台机器 */
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		// 结构 13×13×17，但模型机臂外伸到 x ±12，包围盒要覆盖到 ±12
		return AxisAlignedBB.getBoundingBox(xCoord - 12, yCoord, zCoord - 12, xCoord + 13, yCoord + 18, zCoord + 13);
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		switch(slot) {
		case SLOT_BATTERY: return stack.getItem() instanceof IBatteryItem;
		case SLOT_COATING: return ModCoatings.isCoating(stack);
		case SLOT_CORE: return ModNukeCores.isCore(stack);
		case SLOT_OUTPUT: return false;
		// 被辐照/增殖输入槽只收可增殖物品：早先 default 收任何物品，
		// 物流管道会把燃料核心塞进这里。核心只能进中心核心槽，便于自动化各归各位。
		case SLOT_INPUT: return NukeGeneratorData.findRecipe(stack) != null;
		default: return false;
		}
	}

	/** 管道 / 漏斗能看到的槽位；进什么由 {@link #isItemValidForSlot} 逐格判定 */
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] {SLOT_BATTERY, SLOT_COATING, SLOT_INPUT, SLOT_OUTPUT, SLOT_CORE};
	}

	/** 只有增殖产物格允许被抽走 */
	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot == SLOT_OUTPUT;
	}

	/**
	 * 20 个接口的 UNINOS 节点：位置就是接口代理方块本身，向外一格是连接点。
	 * 结构四面各 5 个口、y+1 层，和模型上的贴片一一对应。
	 */
	public PortDef[] getPorts() {

		if(this.cachedPorts == null) {

			List<PortDef> ports = new ArrayList<PortDef>();
			int y = yCoord + BlockNuclearExplosionGenerator.PORT_Y;
			int r = BlockNuclearExplosionGenerator.RADIUS;

			for(int t : BlockNuclearExplosionGenerator.PORT_T) {
				ports.add(PortDef.make(xCoord + t, y, zCoord - r, Library.NEG_Z));
				ports.add(PortDef.make(xCoord + t, y, zCoord + r, Library.POS_Z));
				ports.add(PortDef.make(xCoord + r, y, zCoord + t, Library.POS_X));
				ports.add(PortDef.make(xCoord - r, y, zCoord + t, Library.NEG_X));
			}

			this.cachedPorts = ports.toArray(new PortDef[ports.size()]);
		}

		return this.cachedPorts;
	}

	@Override
	public void updateEntity() {

		// 电池放电只在服务端结算：客户端也跑会改电池NBT，与服务端权威值打架
		if(!worldObj.isRemote) {
			this.power = Library.chargeTEFromItems(slots, SLOT_BATTERY, this.power, MAX_POWER);
		}

		// 两边都推进进度：服务端是权威值，客户端靠同步包纠正，动画才不会每 20 tick 跳一下
		if(this.running) {
			this.progress++;

			if(this.progress >= CYCLE) {
				this.progress = 0;
				this.running = false;
				// 光效从内到外消散，再过 20 秒才完全结束
				this.cooling = true;
				this.cool = 0;
				if(!worldObj.isRemote) this.onCycleFinished();
				if(!worldObj.isRemote) this.networkPackNT(64);
			}
		} else if(this.cooling) {
			this.cool++;

			if(this.cool >= CYCLE) {
				this.cool = 0;
				this.cooling = false;
				// 冷却结束再等 10 秒，自动模式才会开下一轮
				this.autoDelay = AUTO_DELAY;
				if(!worldObj.isRemote) this.networkPackNT(64);
			}
		}

		if(worldObj.isRemote) {
			// 充能中每半秒叠一声激光，音量 / 音调随进度缓慢抬升；只在客户端放，避免服务端广播
			if(this.running && this.progress % 10 == 0) {
				float t = this.progress / (float) CYCLE;
				this.worldObj.playSound(
						this.xCoord + 0.5D, this.yCoord + 1.5D, this.zCoord + 0.5D,
						"hbm:weapon.tauChargeLoop", 0.35F + 1.65F * t, 0.7F + 0.3F * t, false);
			}
			return;
		}

		// 拆方块时靠这张表找正在充能的机器
		if(this.running) RUNNING.add(this);
		else RUNNING.remove(this);

		// 充能期间覆层被取走：和拆机器一样直接按装药核爆
		if(this.running && this.slots[SLOT_COATING] == null) {
			this.onMachineBroken();
			return;
		}

		// 覆层决定罐容量，必须赶在收流体之前处理
		this.updateTankCapacity();

		// 自动模式：冷却结束（或刚开启）等 10 秒后自己开下一轮；条件不满足就直接关掉自动
		if(this.auto && !this.running && !this.cooling) {
			if(this.autoDelay > 0) {
				this.autoDelay--;
			} else if(!this.tryStart()) {
				this.auto = false;
				this.markDirty();
				this.networkPackNT(64);
			}
		}

		// 接口：电力收、流体收 / 送。代理方块只负责外形，管网认的是这里建的节点
		this.setupAllPorts(this.getPorts());
		this.updateAllPorts();
		this.receivePower();
		this.receiveFluid(new FluidTank[] {this.tanks[0]});
		this.provideFluid(new FluidTank[] {this.tanks[1]});

		// 参数是同步半径（格），不是间隔
		this.networkPackNT(64);
	}

	/**
	 * 一个 20 秒周期跑完的时刻，也就是核爆的那一 tick：
	 * 把液态钠换成等量高温液态钠、把中子通量灌进增殖进度、烧掉核心。
	 * 电在按下按钮时就扣过了；液态钠不够或高温钠罐装不下就整次跳过。
	 */
	protected void onCycleFinished() {

		ItemStack core = this.slots[SLOT_CORE];
		ItemStack coating = this.slots[SLOT_COATING];

		if(core == null || coating == null) return;

		NukeGeneratorData.CoreStat stat = NukeGeneratorData.getCore(core);
		if(stat == null) return;

		// 禁爆核心不爆轰（正常流程按钮已被拦，这里兜底自动模式等旁路）
		if(NukeGeneratorData.isNonExplosive(core)) return;

		int sodium = stat.sodium;

		if(sodium > 0) {
			if(this.tanks[0].getFill() < sodium) return;
			if(this.tanks[1].getMaxFill() - this.tanks[1].getFill() < sodium) return;
		}

		if(sodium > 0) {
			this.tanks[0].setFill(this.tanks[0].getFill() - sodium);
			this.tanks[1].setFill(this.tanks[1].getFill() + sodium);
		}

		if(stat.flux > 0) this.breed(stat.flux);

		// 爆炸这一 tick 把核心烧掉；按钮按下后到这一 tick 之前核心槽是锁住的
		this.slots[SLOT_CORE] = null;

		this.playExplosionFeedback(stat);

		this.markDirty();
	}

	/** 核爆视听反馈：两层爆响 + 附近玩家的屏幕抖动 */
	private void playExplosionFeedback(NukeGeneratorData.CoreStat stat) {

		double x = this.xCoord + 0.5D;
		double y = this.yCoord + 1.5D;
		double z = this.zCoord + 0.5D;

		// 主体用 HBM 核爆音，再叠一层近场爆响；音调 0.5 = 播放速度减半，时长翻倍且更低沉
		this.worldObj.playSoundEffect(x, y, z, "hbm:weapon.nuclearExplosion", 10F, 0.6F);
		this.worldObj.playSoundEffect(x, y, z, "hbm:weapon.explosionLargeNear", 8F, 0.5F);

		// 当量越大，震得越久：2s 起步，最多 5s
		int duration = 2000 + Math.min(3000, Math.max(0, stat.power) * 40);
		float intensity = 0.7F + Math.min(1F, Math.max(0, stat.power) / 100F);

		ModNetwork.NETWORK.sendToAllAround(
				new NukeShakePacket.Message(duration, intensity),
				new NetworkRegistry.TargetPoint(
						this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 512));
	}

	/**
	 * 任意一块被拆时由方块的 {@code breakBlock} 调用：只要拆的是某台正在充能的机器的方块，就按装药引爆。
	 *
	 * <p>只遍历 {@link #RUNNING}（通常 0~1 台），不扫方块也不扫 TileEntity，
	 * 所以拆空闲的机器时只有一次空表判断，不会卡。</p>
	 */
	public static void onAnyBlockBroken(World world, int x, int y, int z) {

		if(world == null || world.isRemote || RUNNING.isEmpty()) return;

		TileEntityNuclearExplosionGenerator hit = null;
		int best = Integer.MAX_VALUE;

		for(TileEntityNuclearExplosionGenerator te : RUNNING) {

			if(te.worldObj != world) continue;

			// 结构 13×13×17，顶部三层外扩到 ±8，再留一格余量
			int dx = te.xCoord - x;
			int dy = y - te.yCoord;
			int dz = te.zCoord - z;

			if(Math.abs(dx) > 9 || Math.abs(dz) > 9 || dy < -1 || dy > 18) continue;

			int dist = dx * dx + dy * dy + dz * dz;
			if(dist < best) {
				best = dist;
				hit = te;
			}
		}

		if(hit != null) hit.onMachineBroken();
	}

	/**
	 * 充能过程中机器被拆掉：按核心的反应系数当量在机器位置来一发真正的核爆。
	 *
	 * <p>走 HBM 自己的核弹实体，地形破坏、辐射、蘑菇云都由它负责，
	 * 当量（半径）随核心的电力倍率放大；因为是意外引爆，威力和辐射在正常当量上再翻一倍。</p>
	 */
	public void onMachineBroken() {

		if(this.worldObj == null || this.worldObj.isRemote) return;

		RUNNING.remove(this);

		// 引爆前先把 running 清掉：爆炸拆结构时 breakBlock 会再进来，看到 running=false 就直接返回，
		// 不需要一个会永久卡住的 detonated 标记（以前用标记，只要拆机时机器是空闲的，这个 TE 就永远炸不了了）
		if(!this.running) return;
		this.running = false;

		ItemStack core = this.slots[SLOT_CORE];
		NukeGeneratorData.CoreStat stat = core == null ? null : NukeGeneratorData.getCore(core);

		// 禁爆核心：拆机也不引爆，核心原样留在槽里
		if(core != null && NukeGeneratorData.isNonExplosive(core)) {
			this.progress = 0;
			this.cooling = false;
			this.cool = 0;
			this.markDirty();
			return;
		}

		// 电力倍率 1..80 → 正常当量 25..175；意外爆炸（拆机 / 取走覆层）再翻一倍，
		// 爆炸半径、辐射范围、蘑菇云和落尘都按 strength 放大（statFac 内部还会再 ×2）
		int strength = (stat == null ? 25 : Math.max(25, Math.min(175, 15 + stat.power * 2))) * 2;

		this.slots[SLOT_CORE] = null;
		this.progress = 0;
		this.cooling = false;
		this.cool = 0;

		// statFac 只负责造实体和算参数，返回的实体必须自己 spawn 进世界
		EntityNukeExplosionMK5 nuke = EntityNukeExplosionMK5.statFac(
				this.worldObj, strength,
				this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D);
		if(nuke != null) this.worldObj.spawnEntityInWorld(nuke);

		EntityNukeTorex.statFacStandard(
				this.worldObj,
				this.xCoord + 0.5D, this.yCoord + 1D, this.zCoord + 0.5D, strength);

		// 和正常核爆同一套抖动，按最大当量给满 5 秒
		ModNetwork.NETWORK.sendToAllAround(
				new NukeShakePacket.Message(5000, 1.7F),
				new NetworkRegistry.TargetPoint(
						this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 512));

		this.markDirty();
	}

	/**
	 * 一次爆轰的中子通量灌进当前物品的进度。
	 *
	 * <p>设计文档：F2 = F1 + (F - Fm)，F2 达到 Fl 就消耗一个原料产出一个产物、清空进度，
	 * 剩下的通量 F = F2 - Fl 在同一 tick 继续结算（于是下一轮还会再扣一次 Fm）。</p>
	 */
	/** 当前物品注册名是不是这份中子通量进度的归属物品。 */
	public boolean isBreedingItem(NukeGeneratorData.Breeding recipe) {
		if(recipe == null || this.breedItem == null) return false;
		String name = Item.itemRegistry.getNameForObject(recipe.input.getItem());
		return name != null && name.equals(this.breedItem);
	}

	/** 当前输入物品对应配方的累计通量；换输入物品后显示为零。 */
	/** 服务端当前累计通量所对应物品的注册名。 */
	public String getBreedItem() {
		return this.breedItem;
	}

	public int getBreedingProgress(NukeGeneratorData.Breeding recipe) {
		if(!this.isBreedingItem(recipe)) return 0;
		ItemStack input = this.slots[SLOT_INPUT];
		if(input != null && input.getItem() != recipe.input.getItem()) return 0;
		return Math.max(0, this.breedFlux);
	}

	/** 通量已达目标但输出槽无法接收产物时，机器会保留通量并等待腾出空间。 */
	public boolean isBreedingOutputBlocked(NukeGeneratorData.Breeding recipe) {
		if(recipe == null || this.getBreedingProgress(recipe) < recipe.reqFlux) return false;
		ItemStack output = this.slots[SLOT_OUTPUT];
		if(output == null) return false;
		return output.getItem() != recipe.output.getItem()
				|| output.getItemDamage() != recipe.output.getItemDamage()
				|| output.stackSize >= output.getMaxStackSize();
	}

	private void breed(int flux) {

		ItemStack input = this.slots[SLOT_INPUT];
		if(input == null) return;

		NukeGeneratorData.Breeding recipe = NukeGeneratorData.findRecipe(input);
		if(recipe == null || recipe.reqFlux <= 0) return;

		int previousFlux = this.breedFlux;
		String previousItem = this.breedItem;
		String name = Item.itemRegistry.getNameForObject(recipe.input.getItem());
		if(this.breedItem == null || name == null || !this.breedItem.equals(name)) {
			this.breedItem = name;
			this.breedFlux = 0;
			this.networkPackNT(64);
		}

		int f = flux;

		for(int guard = 0; guard < 64 && f >= recipe.minFlux; guard++) {

			int f2 = this.breedFlux + (f - recipe.minFlux);

			if(f2 < recipe.reqFlux) {
				this.breedFlux = f2;
				break;
			}

			if(!this.breedOne(recipe)) {
				this.breedFlux = f2;
				break;
			}

			f = f2 - recipe.reqFlux;
			this.breedFlux = 0;
		}

		if(this.breedFlux != previousFlux || !java.util.Objects.equals(this.breedItem, previousItem)) {
			this.markDirty();
			this.networkPackNT(64);
		}
	}

	/** 消耗一个原料、产出一个产物；产物格塞不下就返回 false，什么都不做 */
	private boolean breedOne(NukeGeneratorData.Breeding recipe) {

		ItemStack input = this.slots[SLOT_INPUT];
		if(input == null || input.getItem() != recipe.input.getItem()) return false;

		ItemStack output = this.slots[SLOT_OUTPUT];

		if(output != null) {
			if(output.getItem() != recipe.output.getItem()
					|| output.getItemDamage() != recipe.output.getItemDamage()) return false;
			if(output.stackSize >= output.getMaxStackSize()) return false;
		}

		input.stackSize--;
		if(input.stackSize <= 0) this.slots[SLOT_INPUT] = null;

		if(output == null) this.slots[SLOT_OUTPUT] = recipe.output.copy();
		else output.stackSize++;

		return true;
	}

	/** 罐容量跟着覆层走：前五级 512k ~ 8.192M，曼巴 24 */
	private void updateTankCapacity() {

		int capacity = TANK_CAPACITY;

		ItemStack coating = this.slots[SLOT_COATING];
		if(coating != null && coating.getItem() instanceof ItemCoating) {
			long value = ((ItemCoating) coating.getItem()).getCapacity();
			capacity = (int) Math.min(Integer.MAX_VALUE, value);
		}

		if(this.tanks[0].getMaxFill() != capacity) this.tanks[0].changeTankSize(capacity);
		if(this.tanks[1].getMaxFill() != capacity) this.tanks[1].changeTankSize(capacity);
	}

	/** 按下运行键：核心 + 覆层都在、不在运行 / 消散中，而且电和钠都够 */
	public boolean tryStart() {

		if(!this.canStart()) return false;

		NukeGeneratorData.CoreStat stat = NukeGeneratorData.getCore(this.slots[SLOT_CORE]);
		if(stat == null) return false;

		// 启动当场就把这一发的电扣掉，之后 20 秒里断电也不影响已经点着的反应
		this.power -= NukeGeneratorData.powerCost(stat);

		this.running = true;
		this.progress = 0;
		RUNNING.add(this);
		this.markDirty();
		this.networkPackNT(64);

		return true;
	}

	/** 运行键：自动模式下点一下立刻退出自动（运行中也一并取消），否则启动 / 取消本轮 */
	public void onRunKey() {

		if(this.auto) {
			this.auto = false;
			if(this.running) {
				this.cancelRun();
			} else {
				this.autoDelay = AUTO_DELAY;
				this.markDirty();
				this.networkPackNT(64);
			}
			return;
		}

		if(this.running) this.cancelRun();
		else this.tryStart();
	}

	/** 运行中再按一次：立刻取消本轮充能，不引爆（启动时扣的电不退） */
	public void cancelRun() {

		if(this.worldObj == null || this.worldObj.isRemote || !this.running) return;

		this.running = false;
		this.progress = 0;
		RUNNING.remove(this);
		// 自动模式的话，取消之后同样隔 10 秒再开下一轮
		this.autoDelay = AUTO_DELAY;
		this.markDirty();
		this.networkPackNT(64);
	}

	/** Shift + 运行键：开关自动模式，刚打开时也先等 10 秒再启动 */
	public void toggleAuto() {

		if(this.worldObj == null || this.worldObj.isRemote) return;

		this.auto = !this.auto;
		this.autoDelay = AUTO_DELAY;
		this.markDirty();
		this.networkPackNT(64);
	}

	/** 运行键不能点的原因，按优先级返回 */
	public static final int STATUS_READY = 0;
	public static final int STATUS_NO_CORE = 1;
	public static final int STATUS_NO_COATING = 2;
	public static final int STATUS_NO_POWER = 3;
	/** 液态钠罐装不下这次反应要的钠，或者罐里没存够 */
	public static final int STATUS_NO_COOLANT = 4;
	/** 高温钠罐剩余空间装不下这次要产出的高温钠 */
	public static final int STATUS_NO_OUTPUT = 5;
	/** 禁爆核心（铯137/碘131/铀燃料/钍燃料）：能进槽但不能启动爆轰 */
	public static final int STATUS_NON_EXPLOSIVE = 6;

	public int getStartStatus() {

		ItemStack core = this.slots[SLOT_CORE];
		if(core == null) return STATUS_NO_CORE;

		if(this.slots[SLOT_COATING] == null) return STATUS_NO_COATING;

		NukeGeneratorData.CoreStat stat = NukeGeneratorData.getCore(core);
		if(stat == null) return STATUS_NO_CORE;

		if(NukeGeneratorData.isNonExplosive(core)) return STATUS_NON_EXPLOSIVE;

		// 电是按下按钮当场扣的，扣不起就不许按
		if(this.power < NukeGeneratorData.powerCost(stat)) return STATUS_NO_POWER;

		if(stat.sodium > 0) {
			// 罐容量本身就不够，钠永远备不满
			if(this.tanks[0].getMaxFill() < stat.sodium) return STATUS_NO_COOLANT;
			if(this.tanks[0].getFill() < stat.sodium) return STATUS_NO_COOLANT;

			// 高温钠罐剩余空间不够
			if(this.tanks[1].getMaxFill() - this.tanks[1].getFill() < stat.sodium) return STATUS_NO_OUTPUT;
		}

		return STATUS_READY;
	}

	/** 运行键是否可点：不在运行 / 消散中，且各项条件都满足 */
	public boolean canStart() {
		return !this.running && !this.cooling && this.getStartStatus() == STATUS_READY;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeBoolean(this.running);
		buf.writeInt(this.progress);
		buf.writeBoolean(this.cooling);
		buf.writeInt(this.cool);
		buf.writeLong(this.power);
		buf.writeBoolean(this.auto);
		buf.writeInt(this.breedFlux);
		cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, this.breedItem == null ? "" : this.breedItem);
		this.tanks[0].serialize(buf);
		this.tanks[1].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		this.running = buf.readBoolean();
		this.progress = buf.readInt();
		this.cooling = buf.readBoolean();
		this.cool = buf.readInt();
		this.power = buf.readLong();
		this.auto = buf.readBoolean();
		this.breedFlux = buf.readInt();
		String syncedBreedItem = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
		this.breedItem = syncedBreedItem.isEmpty() ? null : syncedBreedItem;
		this.tanks[0].deserialize(buf);
		this.tanks[1].deserialize(buf);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.running = nbt.getBoolean("running");
		this.progress = nbt.getInteger("progress");
		this.cooling = nbt.getBoolean("cooling");
		this.cool = nbt.getInteger("cool");
		this.auto = nbt.getBoolean("auto");
		this.autoDelay = AUTO_DELAY;
		this.breedFlux = nbt.getInteger("breedFlux");
		if(nbt.hasKey("breedItem")) this.breedItem = nbt.getString("breedItem");
		this.tanks[0].readFromNBT(nbt, "t0");
		this.tanks[1].readFromNBT(nbt, "t1");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setBoolean("running", this.running);
		nbt.setInteger("progress", this.progress);
		nbt.setBoolean("cooling", this.cooling);
		nbt.setInteger("cool", this.cool);
		nbt.setBoolean("auto", this.auto);
		nbt.setInteger("breedFlux", this.breedFlux);
		if(this.breedItem != null) nbt.setString("breedItem", this.breedItem);
		this.tanks[0].writeToNBT(nbt, "t0");
		this.tanks[1].writeToNBT(nbt, "t1");
	}

	@Override
	public void invalidate() {
		super.invalidate();
		RUNNING.remove(this);
	}

	/* ---- 电力：既能收也能送 ---- */

	@Override
	public long getPower() {
		return this.power;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return MAX_POWER;
	}

	/* ---- 流体：罐 0 收、罐 1 送 ---- */

	@Override
	public FluidTank[] getAllTanks() {
		return this.tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {this.tanks[0]};
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {this.tanks[1]};
	}
}

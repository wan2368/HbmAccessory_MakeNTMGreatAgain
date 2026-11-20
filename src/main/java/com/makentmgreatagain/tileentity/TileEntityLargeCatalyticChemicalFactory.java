package com.makentmgreatagain.tileentity;

import java.util.HashMap;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemArcElectrode;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.main.NTMSounds;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.tileentity.TilePort;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.blocks.BlockLargeCatalyticChemicalFactory;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.HeatChemicalRecipe;
import com.makentmgreatagain.recipes.HybridFractionRecipes;
import com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes;
import com.makentmgreatagain.recipes.SolidFuelRecipe;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型催化化工厂核心。
 *
 * <p>加工逻辑与配方表完全沿用本体化工厂（{@link com.hbm.module.machine.ModuleMachineChemplant}
 * + {@link com.hbm.inventory.recipes.ChemicalPlantRecipes}），一台机器同时只跑一个配方。</p>
 *
 * <h3>槽位</h3>
 * <pre>
 *  0            电池
 *  1 ~ 3        速度 / 电力 / 超频升级
 *  4 ~ 19       物品输入：4 个类型 × 每个类型 4 个槽
 *                   类型 t 的槽：4 + t*4 + 0..3
 * 20 ~ 27       物品输出：4 个类型 × 每个类型 2 个槽
 *                   类型 t 的槽：20 + t*2 + 0..1
 * 28           催化剂
 * 29           蓝图（限制可选配方池）
 * </pre>
 *
 * <h3>流体</h3>
 * <p>4 个输入罐、4 个输出罐；冷却剂为冷四氟甲烷，运行中每 tick 消耗 10 mB
 * （随速度 / 超频升级上升、随节能升级下降）并转化为四氟甲烷排出。</p>
 */
public class TileEntityLargeCatalyticChemicalFactory extends TileEntityMachineBase
		implements IEnergyReceiverMK2, IFluidStandardTransceiverMK2, IControlReceiver, IUpgradeInfoProvider {

	public static final int SLOT_BATTERY = 0;
	public static final int SLOT_UPGRADE = 1;
	public static final int SLOT_INPUT = 4;
	public static final int SLOT_OUTPUT = 20;
	public static final int SLOT_CATALYST = 28;
	public static final int SLOT_BLUEPRINT = 29;
	public static final int SLOT_ELECTRODE = 30;

	public static final int INPUT_TYPES = 4;
	public static final int INPUT_SLOTS = 4;
	public static final int OUTPUT_TYPES = 4;
	public static final int OUTPUT_SLOTS = 2;

	/**
	 * 流体路数：FF（高并行）4 路、MF（多配方）8 路。
	 *
	 * <p>与 {@link #INPUT_TYPES} 分开：那个同时决定<b>物品槽分组</b>（4 组 × 4 槽），
	 * 不能跟着流体一起变，否则槽位会错位。</p>
	 */
	public static final int TANK_TYPES_FF = 4;
	public static final int TANK_TYPES_MF = 8;

	public static final int TANK_CAPACITY = 128_000;
	/** MF（多配方）模式的单罐容量：8 路合计与 FF 的 4 路持平，避免总量超标 */
	public static final int TANK_CAPACITY_MF = 64_000;
	public static final int COOLANT_CAPACITY = 64_000;
	/** 基准冷却剂消耗（mB/tick） */
	public static final int COOLANT_BASE = 10;

	/** 加热功率：每 tick 消耗 5000 EU 换 1000 Tu（10 万 Tu 约 5 秒） */
	public static final int HEAT_POWER_PER_TICK = 5_000;
	public static final int HEAT_TU_PER_TICK = 1_000;
	/** 保温：每 5 Tu 每 tick 额外消耗 1 EU（与 5000EU→1000Tu 的加热比例一致） */
	public static final int HEAT_TU_PER_EU = 5;
	/** 机器内部热量上限（Tu） */
	public static final int MAX_HEAT = 1_000_000;
	/** 供不上热时每 tick 散失的热量（Tu） */
	public static final int HEAT_DECAY = 500;

	/**
	 * 流体罐：数组建满 8 路，实际启用几路由模式决定（{@link #activeTanks()}）。
	 *
	 * <p>固定 8 个而不是切模式时重建数组：模块构造时就持有了这个引用，
	 * 重建会让模块指向旧数组；而且网络同步两端的罐数必须一致。</p>
	 */
	public final FluidTank[] inputTanks = new FluidTank[TANK_TYPES_MF];
	public final FluidTank[] outputTanks = new FluidTank[TANK_TYPES_MF];
	public final FluidTank coolant;
	public final FluidTank coolantOut;

	public long power;
	public long maxPower = 20_000_000;

	public boolean didProcess;
	/** 有配方、但任意一个输出槽/罐已经满了 */
	public boolean outputFull;

	/** 催化剂等级：0 无 / 1 原版催化重整 / 2 铂 / 3 CMB，同步到客户端切换界面贴图 */
	public int catalystType;

	/** 机器内部热量（Tu） */
	public float heat;
	/** 当前配方需要的热量，0 表示不需要加热 */
	public int targetHeat;
	/** 本 tick 是否正在产生热量（界面画火焰） */
	public boolean heatingUp;

	/** 风扇角度：服务端恒为 0，只在客户端推进 */
	public float rot;
	public float prevRot;
	protected float rotSpeed;

	public final ModuleLargeChemicalFactory module;

	/**
	 * 运行模式：false = 高并行模式（默认，1 个配方最高 64x），
	 * true = 多配方模式（最多 5 个配方，每个最高 16x）。
	 *
	 * <p>由界面右下角的模式旋钮切换，切换时会清空全部输入/输出流体
	 * （两套模式的流体口分配不同，残留会串味）。</p>
	 */
	public boolean multiRecipeMode;

	/** 高并行模式的并行上限 */
	public static final int MAX_BATCH_SINGLE = 64;
	/** 多配方模式的并行上限 */
	public static final int MAX_BATCH_MULTI = 16;
	/** 多配方模式最多同时处理的配方数 */
	public static final int MULTI_RECIPE_COUNT = 5;

	/** 当前模式允许的并行上限 */
	public int maxBatches() {
		return this.multiRecipeMode ? MAX_BATCH_MULTI : MAX_BATCH_SINGLE;
	}

	/** 当前模式启用的流体路数：FF 4 路 / MF 8 路 */
	public int activeTanks() {
		return this.multiRecipeMode ? TANK_TYPES_MF : TANK_TYPES_FF;
	}

	/**
	 * 按当前模式设置全部流体罐容量：FF 128000 / MF 64000。
	 *
	 * <p>MF 有 8 路，若仍用 128000 总量会翻倍超标，所以单罐减半，
	 * 8×64000 与 FF 的 4×128000 持平。{@code changeTankSize} 会自动截断超量存量。</p>
	 */
	public void applyTankCapacity() {
		int cap = this.multiRecipeMode ? TANK_CAPACITY_MF : TANK_CAPACITY;
		for(FluidTank tank : inputTanks) if(tank != null) tank.changeTankSize(cap);
		for(FluidTank tank : outputTanks) if(tank != null) tank.changeTankSize(cap);
	}

	/**
	 * MF 模式下已选的配方名，按格位顺序存放，最多 {@link #MULTI_RECIPE_COUNT} 个。
	 *
	 * <p>空串表示该格位空着。删除某格后，后面的会依次前移补位。</p>
	 */
	public final String[] multiSelections = new String[MULTI_RECIPE_COUNT];

	/** 已选配方数量 */
	public int multiCount() {
		int n = 0;
		for(String s : multiSelections) if(s != null && !s.isEmpty()) n++;
		return n;
	}

	/** 删除第 index 个已选配方，后面的依次前移补位 */
	public void removeMultiSelection(int index) {
		if(index < 0 || index >= MULTI_RECIPE_COUNT) return;
		for(int i = index; i < MULTI_RECIPE_COUNT - 1; i++) {
			multiSelections[i] = multiSelections[i + 1];
		}
		multiSelections[MULTI_RECIPE_COUNT - 1] = "";
	}

	/** 追加一个配方到第一个空格位；已满返回 false */
	public boolean addMultiSelection(String name) {
		for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
			if(multiSelections[i] == null || multiSelections[i].isEmpty()) {
				multiSelections[i] = name;
				return true;
			}
		}
		return false;
	}

	public final UpgradeManagerNT upgradeManager = new UpgradeManagerNT(this);

	protected PortDef[] cachedPorts;
	private AudioWrapper audio;

	public TileEntityLargeCatalyticChemicalFactory() {
		super(SLOT_ELECTRODE + 2);

		for(int i = 0; i < TANK_TYPES_MF; i++) {
			inputTanks[i] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
			outputTanks[i] = new FluidTank(Fluids.NONE, TANK_CAPACITY);
		}

		this.coolant = new FluidTank(Fluids.PERFLUOROMETHYL_COLD, COOLANT_CAPACITY);
		this.coolantOut = new FluidTank(Fluids.PERFLUOROMETHYL, COOLANT_CAPACITY);

		this.module = new ModuleLargeChemicalFactory(0, this, slots, inputTanks, outputTanks, SLOT_INPUT, SLOT_OUTPUT);
	}

	@Override
	public String getName() {
		return "container.large_catalytic_chemical_factory";
	}

	/* ============================== 槽位 ============================== */

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		if(slot == SLOT_BATTERY) return stack.getItem() instanceof api.hbm.energymk2.IBatteryItem
				|| stack.getItem() == com.hbm.items.ModItems.battery_creative;
		if(slot >= SLOT_UPGRADE && slot < SLOT_INPUT) return stack.getItem() instanceof ItemMachineUpgrade;
		if(slot == SLOT_CATALYST) return HybridFractionRecipes.catalystTier(stack) > 0;
		if(slot == SLOT_BLUEPRINT) return stack.getItem() instanceof ItemBlueprints;
		if(slot >= SLOT_ELECTRODE && slot < SLOT_ELECTRODE + 2) return isElectrodeItem(stack);

		int type = inputTypeOf(slot);
		if(type >= 0) {
			// MF：每格只收它那格显示的那一种原料
			if(this.multiRecipeMode) return matchesInputMF(slot, stack);
			return matchesInputGroup(type, stack);
		}

		return false;
	}

	/**
	 * MF 收料判定：<b>每格只收它那格幽灵图标对应的那一种原料</b>。
	 *
	 * <p>16 格按种类均分成连续片区（{@link #mfKindOfSlot}），格里显示什么就只能放什么。
	 * 不能放开成「任意格收任意原料」—— 那样接物流会把一种物品灌满全部 16 格。</p>
	 */
	protected boolean matchesInputMF(int slot, ItemStack stack) {

		GenericRecipe recipe = module.getRecipe();
		if(recipe == null || recipe.inputItem == null || recipe.inputItem.length == 0) return false;

		int kind = mfKindOfSlot(slot - SLOT_INPUT, recipe.inputItem.length);
		if(kind < 0 || kind >= recipe.inputItem.length) return false;

		AStack required = recipe.inputItem[kind];
		return required != null && required.matchesRecipe(stack, true);
	}

	/**
	 * MF：第 {@code index} 个输入格（0..15）归属第几种原料。
	 *
	 * <p>按种类把 16 格均分成连续片区，收料校验与幽灵显示<b>共用这一个公式</b>，
	 * 保证「显示的」和「能放的」永远一致。种类数为 0 时返回 -1。</p>
	 */
	public static int mfKindOfSlot(int index, int kinds) {
		if(kinds <= 0) return -1;
		int inCount = INPUT_TYPES * INPUT_SLOTS;
		if(index < 0 || index >= inCount) return -1;
		return Math.min(kinds - 1, index * kinds / inCount);
	}

	/** 电极槽只收本体电弧炉电极和附属的 CMB 电极 */
	public static boolean isElectrodeItem(ItemStack stack) {
		if(stack == null) return false;
		if(stack.getItem() instanceof ItemArcElectrode) return true;
		return stack.getItem() == com.makentmgreatagain.init.ModItems.cmb_electrode;
	}

	/** 这支电极还能用：CMB 电极永不磨损，普通电极看剩余耐久 */
	protected boolean isUsableElectrode(int slot) {
		ItemStack stack = slots[slot];
		if(!isElectrodeItem(stack)) return false;
		if(stack.getItem() == com.makentmgreatagain.init.ModItems.cmb_electrode) return true;
		return ItemArcElectrode.getDurability(stack) < ItemArcElectrode.getMaxDurability(stack);
	}

	/** 加热要求两个电极槽都插着可用的电极 */
	protected boolean hasBothElectrodes() {
		return isUsableElectrode(SLOT_ELECTRODE) && isUsableElectrode(SLOT_ELECTRODE + 1);
	}

	/** 槽位属于哪个输入类型，不属于返回 -1 */
	public static int inputTypeOf(int slot) {
		if(slot < SLOT_INPUT || slot >= SLOT_INPUT + INPUT_TYPES * INPUT_SLOTS) return -1;
		return (slot - SLOT_INPUT) / INPUT_SLOTS;
	}

	/** 槽位属于哪个输出类型，不属于返回 -1 */
	public static int outputTypeOf(int slot) {
		if(slot < SLOT_OUTPUT || slot >= SLOT_OUTPUT + OUTPUT_TYPES * OUTPUT_SLOTS) return -1;
		return (slot - SLOT_OUTPUT) / OUTPUT_SLOTS;
	}

	/**
	 * 选定配方后，同一类型的 4 个槽只收这一种原料：
	 * 既要是配方要求的物品，也要和该类型里已有的物品完全一致。
	 */
	protected boolean matchesInputGroup(int type, ItemStack stack) {

		GenericRecipe recipe = module.getRecipe();
		if(recipe == null || recipe.inputItem == null || recipe.inputItem.length == 0) return false;

		int kinds = recipe.inputItem.length;
		int span = ModuleLargeChemicalFactory.spanFor(kinds);
		int kind = type / span;
		if(kind >= kinds) return false;

		AStack required = recipe.inputItem[kind];
		if(required == null || !required.matchesRecipe(stack, true)) return false;

		int start = SLOT_INPUT + kind * span * INPUT_SLOTS;
		int end = start + span * INPUT_SLOTS;

		for(int slot = start; slot < end; slot++) {
			ItemStack existing = slots[slot];
			if(existing == null) continue;
			if(existing.getItem() != stack.getItem()) return false;
			if(existing.getItemDamage() != stack.getItemDamage()) return false;
			if(!ItemStack.areItemStackTagsEqual(existing, stack)) return false;
		}

		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {

		int[] all = new int[INPUT_TYPES * INPUT_SLOTS + OUTPUT_TYPES * OUTPUT_SLOTS];
		for(int i = 0; i < all.length; i++) all[i] = SLOT_INPUT + i;
		return all;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return inputTypeOf(slot) >= 0 && isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return outputTypeOf(slot) >= 0;
	}

	/* ============================== 每 tick ============================== */

	@Override
	public void updateEntity() {

		if(worldObj.isRemote) {
			updateClient();
			return;
		}

		this.setupAllPorts(getPorts());
		this.updatePortsAdaptive();

		this.maxPower = 20_000_000;
		this.power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);
		this.upgradeManager.checkSlots(slots, 1, 3);
		this.catalystType = HybridFractionRecipes.catalystTier(slots[SLOT_CATALYST]);

		double speed = 1D;
		double pow = 1D;

		speed += Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(upgradeManager, UpgradeType.SPEED), 3) / 3D;
		speed += Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(upgradeManager, UpgradeType.OVERDRIVE), 3);

		pow -= Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(upgradeManager, UpgradeType.POWER), 3) * 0.25D;
		pow += Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(upgradeManager, UpgradeType.SPEED), 3);
		pow += Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(upgradeManager, UpgradeType.OVERDRIVE), 3) * 10D / 3D;

		boolean canCool = canCool(pow);

		GenericRecipe recipe = module.getRecipe();
		this.targetHeat = recipe instanceof HeatChemicalRecipe ? Math.min(MAX_HEAT, ((HeatChemicalRecipe) recipe).requiredHeat) : 0;

		// 配方要求的催化剂等级（0 = 不限制），催化剂槽里放的东西等级不够就不开工
		int requiredCatalyst = recipe instanceof HeatChemicalRecipe ? ((HeatChemicalRecipe) recipe).requiredCatalyst : 0;
		boolean catalystReady = HybridFractionRecipes.catalystTier(slots[SLOT_CATALYST]) >= requiredCatalyst;

		boolean heatReady = catalystReady;
		this.heatingUp = false;

		if(!catalystReady) {
			// 催化剂不满足：不加热、不耗电，下面统一清进度
		} else if(this.targetHeat > 0) {
			// 配方只需要这么多热量，多出来的部分直接钳掉
			if(this.heat > this.targetHeat) this.heat = this.targetHeat;

			if(this.heat < this.targetHeat) {
				// 还没到配方温度：先加热，不推进配方
				heatReady = false;
				this.heatingUp = this.heatUp(this.targetHeat);
				// 电力不够（或没有可用电极）时，热量按固定速率散失
				if(!this.heatingUp) this.heat = Math.max(0F, this.heat - HEAT_DECAY);
			} else {
				// 到达温度后保温：额外耗电与当前热量成正比
				int upkeep = Math.max(1, (int) (this.heat / HEAT_TU_PER_EU));
				if(this.power >= upkeep) {
					this.power -= upkeep;
				} else {
					// 电力不足以维持热量，同样按固定速率散失
					heatReady = false;
					this.heat = Math.max(0F, this.heat - HEAT_DECAY);
				}
			}
		} else {
			// 配方不需要热量，余热慢慢散掉
			this.heat = Math.max(0F, this.heat - HEAT_DECAY);
		}

		if(heatReady) {
			// 不乘大型机那条 2 倍率：本体的 ChemicalFactory / AssemblyFactory 是 ×2 速 ×2 功耗，
			// 这里按配方字面值跑，recipe.duration 与 recipe.power 写多少就是多少。
			// （ModuleMachineBase#process 里是 progress += speed / duration、battery -= power * recipe.power）
			this.module.update(speed, pow, canCool, slots[SLOT_BLUEPRINT]);
		} else {
			// 暂停加工：保持罐型、清空进度，避免残留的运行状态
			module.setupTanks(recipe);
			module.progress = 0;
			module.didProcess = false;
		}
		this.didProcess = this.module.didProcess;
		this.outputFull = recipe != null && !canFitOutputNow(recipe);

		// 冷却：每 tick 把冷四氟甲烷变成四氟甲烷，消耗量随并行份数翻倍
		if(this.didProcess) {
			int use = Math.max(1, (int) Math.round(COOLANT_BASE * pow)) * Math.max(1, module.batches);
			int actual = Math.min(use, coolant.getFill());
			int room = coolantOut.getMaxFill() - coolantOut.getFill();
			actual = Math.min(actual, room);

			this.coolant.setFill(this.coolant.getFill() - actual);
			this.coolantOut.setFill(this.coolantOut.getFill() + actual);
			this.markChanged();
		}

		if(this.module.markDirty) this.markDirty();

		this.networkPackNT(50);
	}

	/** 输出槽/罐是否还装得下（给黄灯用） */
	public boolean canFitOutputNow(GenericRecipe recipe) {
		return module.canFitOutputPublic(recipe);
	}

	/** 冷却剂够不够本 tick 用，且热冷却液罐还有空间 */
	public boolean canCool(double pow) {
		int use = Math.max(1, (int) Math.round(COOLANT_BASE * pow)) * Math.max(1, module.batches);
		return coolant.getFill() >= use && coolantOut.getFill() + use <= coolantOut.getMaxFill();
	}

	/** 消耗电力升温，返回本 tick 有没有产热；红色速度升级会让升温更快，但电力消耗成倍增加 */
	protected boolean heatUp(int target) {

		int speedLv = Math.min(com.makentmgreatagain.compat.HBMVersionCompat.upgradeLevel(this.upgradeManager, UpgradeType.SPEED), 3);
		int rate = HEAT_TU_PER_TICK * (1 + speedLv);
		long cost = (long) HEAT_POWER_PER_TICK * (1 + speedLv) * (1 + speedLv);

		if(this.power < cost) return false;

		// 必须两个电极槽都有可用电极才能产热
		if(!this.hasBothElectrodes()) return false;

		this.power -= cost;
		this.heat = Math.min(Math.min(target, MAX_HEAT), this.heat + rate);

		// 运行时每 80 tick 两支电极一起磨损
		if(this.worldObj.getTotalWorldTime() % 80 == 0) {
			this.damageElectrodes();
		}

		this.markChanged();
		return true;
	}

	/** 两支电极一起磨损，烧穿后变焦电极；CMB 电极不在这个分支里，永不磨损 */
	protected void damageElectrodes() {
		for(int i = SLOT_ELECTRODE; i < SLOT_ELECTRODE + 2; i++) {
			ItemStack electrode = slots[i];
			if(electrode == null || !(electrode.getItem() instanceof ItemArcElectrode)) continue;
			if(ItemArcElectrode.getDurability(electrode) >= ItemArcElectrode.getMaxDurability(electrode)) continue;

			if(ItemArcElectrode.damage(electrode)) {
				this.slots[i] = new ItemStack(com.hbm.items.ModItems.arc_electrode_burnt, 1, electrode.getItemDamage());
			}
		}
	}

	protected void updateClient() {

		boolean running = this.didProcess;

		if(running) {
			this.rotSpeed = Math.min(30F, this.rotSpeed + 0.1F);
		} else {
			this.rotSpeed = Math.max(0F, this.rotSpeed - 0.1F);
		}

		this.prevRot = this.rot;
		this.rot += this.rotSpeed;

		if(this.rot >= 360F) {
			this.rot -= 360F;
			this.prevRot -= 360F;
		}

		// 与本体化工厂相同的运行音效
		if(running && MainRegistry.proxy.me().getDistance(xCoord, yCoord, zCoord) < 50) {
			if(audio == null) {
				audio = createAudioLoop();
				audio.startSound();
			} else if(!audio.isPlaying()) {
				audio = rebootAudio(audio);
			}
			audio.keepAlive();
			audio.updateVolume(getVolume(1F));
		} else {
			if(audio != null) {
				audio.stopSound();
				audio = null;
			}
		}
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound(NTMSounds.CHEMPLANT_LOOP, xCoord, yCoord, zCoord, 1F, 15F, 1.0F, 20);
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		destroySolidFuelInPorts();
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		destroySolidFuelInPorts();
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	/* ============================== 配方控制 ============================== */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {

		// 模式旋钮：切换高并行 / 多配方，并清空全部输入输出流体
		if(data.hasKey("mode")) {
			this.multiRecipeMode = !this.multiRecipeMode;

			// 两套模式的流体口分配不同，残留流体会串味，直接清空
			for(FluidTank tank : inputTanks) tank.resetTank();
			for(FluidTank tank : outputTanks) tank.resetTank();

			// 容量随模式切换：MF 单罐 64000，FF 128000
			applyTankCapacity();

			// 进度归零，避免用旧模式的份数继续跑
			this.module.progress = 0;
			this.didProcess = false;
			this.outputFull = false;

			this.markChanged();
			return;
		}

		// MF：整份已选列表（选择器关屏时一次性同步，含删除后的补位结果）
		if(data.hasKey("mfSet")) {
			for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
				String name = data.getString("mf" + i);
				this.multiSelections[i] = name == null ? "" : name;
			}
			// 立刻按新配方重设罐型：否则上一轮用过的罐会留着旧类型，
			// 存量虽空但界面仍显示旧流体（要等热量达标才会被 update 纠正）
			this.module.setupTanks(this.module.getRecipe());
			this.module.progress = 0;
			this.markChanged();
			return;
		}

		// MF：追加一个已选配方（满 5 个则忽略）
		if(data.hasKey("mfAdd")) {
			String name = LargeChemicalFactoryRecipes.get().resolveSelection(data.getString("mfAdd"));
			if(name != null && !name.isEmpty() && !"null".equals(name)) {
				this.addMultiSelection(name);
				this.markChanged();
			}
			return;
		}

		// MF：删除某个格位的配方，后面的前移补位
		if(data.hasKey("mfRemove")) {
			this.removeMultiSelection(data.getInteger("mfRemove"));
			this.markChanged();
			return;
		}

		if(data.hasKey("index") && data.hasKey("selection")) {
			String selection = LargeChemicalFactoryRecipes.get().resolveSelection(data.getString("selection"));
			boolean wasSolidFuel = this.module.getRecipe() instanceof SolidFuelRecipe;
			this.module.setRecipe(selection, false);

			// 从合并配方切走时，把各罐残留的原料流体清掉，
			// 避免下一 tick setupTanks 换罐型时把它们静默清空
			if(wasSolidFuel && !(this.module.getRecipe() instanceof SolidFuelRecipe)) {
				for(FluidTank tank : inputTanks) tank.resetTank();
				destroySolidFuelInPorts();
			}
			this.markChanged();
		}
	}

	/* ============================== 接口 ============================== */

	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			List<int[]> offsets = BlockLargeCatalyticChemicalFactory.getPortOffsets(dir);

			cachedPorts = new PortDef[offsets.size()];
			for(int i = 0; i < offsets.size(); i++) {
				int[] o = offsets.get(i);
				cachedPorts[i] = PortDef.make(
						xCoord + o[0], yCoord + o[1], zCoord + o[2],
						ForgeDirection.getOrientation(o[3]));
			}
		}

		return cachedPorts;
	}

	@Override
	public FluidTank[] getAllTanks() {
		FluidTank[] all = new FluidTank[inputTanks.length + outputTanks.length + 2];
		int i = 0;
		for(FluidTank tank : inputTanks) all[i++] = tank;
		for(FluidTank tank : outputTanks) all[i++] = tank;
		all[i++] = coolant;
		all[i] = coolantOut;
		return all;
	}

	private FluidTank[] receivingTanks;
	private FluidTank[] sendingTanks;

	@Override
	public FluidTank[] getReceivingTanks() {
		if(receivingTanks == null) {
			receivingTanks = new FluidTank[inputTanks.length + 1];
			System.arraycopy(inputTanks, 0, receivingTanks, 0, inputTanks.length);
			receivingTanks[receivingTanks.length - 1] = coolant;
		}
		return receivingTanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		if(sendingTanks == null) {
			sendingTanks = new FluidTank[outputTanks.length + 1];
			System.arraycopy(outputTanks, 0, sendingTanks, 0, outputTanks.length);
			sendingTanks[sendingTanks.length - 1] = coolantOut;
		}
		return sendingTanks;
	}

	/* ============================== 动态流体输入（合并固体燃料配方） ============================== */

	/** 合并配方接管输入端口时，为每种白名单流体各建一个端口 */
	private TilePort[] solidFuelInPorts;
	/** 是否正在接管输入流体端口（合并配方选中期间） */
	private boolean solidFuelPortsActive = false;

	/**
	 * 端口更新入口：合并配方选中时接管输入流体端口，其余时候走原版逻辑。
	 *
	 * <p>HBM 的流体管网按流体种类分网，标准输入端口按罐内流体类型订阅网络。
	 * 合并配方下空罐是 NONE 类型、标准端口被禁用，燃油网络发现不了机器。
	 * 之前的做法是在标准端口旁边另建一组节点，但第一个流体认领后标准端口会按新罐型
	 * 重建节点，和旁路节点在同一位置抢同一个网络，第二个流体的订阅就丢了。</p>
	 *
	 * <p>现在改为：选中合并配方期间<b>完全接管</b>输入流体端口——标准输入端口停用，
	 * 由本类按白名单为每种流体各建一个端口并每 tick 维护订阅。所有白名单流体始终
	 * 可达，空罐认领、同种补灌都由 {@link #getDemand}/{@link #transferFluid} 处理，
	 * 不再和标准端口抢位置。切走配方时销毁接管端口、交回标准机制。</p>
	 */
	private void updatePortsAdaptive() {

		boolean takeover = this.module.getRecipe() instanceof SolidFuelRecipe;

		if(takeover) {
			// 电力端口照常
			this.setupPowerPorts(getPorts());
			this.receivePower();

			// 标准输入端口停用但保留对象：切走配方时标准机制按罐型平滑接管，
			// 不会因 fluidInPorts 被置 null 而整组重建（重建会销毁所在网络）
			if(this.fluidInPorts != null) {
				for(TilePort port : this.fluidInPorts) {
					if(port != null) port.disable();
				}
			}

			this.setupSolidFuelInPorts();
			for(TilePort port : this.solidFuelInPorts) {
				port.update(worldObj);
				port.checkSubscribe(worldObj);
			}

			// 输出端口照常（按输出罐型）
			this.setupFluidOutPorts(getSendingTanks(), PortDef.combine(getPorts()));
			this.provideFluid(getSendingTanks());
		} else {
			if(this.solidFuelPortsActive) {
				this.destroySolidFuelInPorts();
			}
			this.updatePortPIFIFO();
		}
	}

	/** 为每种白名单流体各建一个输入端口（只在首次接管时建） */
	private void setupSolidFuelInPorts() {
		if(this.solidFuelPortsActive) return;

		SolidFuelRecipe recipe = (SolidFuelRecipe) this.module.getRecipe();
		PortDef[] ports = getPorts();
		if(ports == null || ports.length == 0) return;
		PortDef unified = PortDef.combine(ports);

		List<SolidFuelRecipe.Entry> entries = recipe.getEntries();
		this.solidFuelInPorts = new TilePort[entries.size()];
		for(int i = 0; i < entries.size(); i++) {
			this.solidFuelInPorts[i] = new TilePort()
					.setupOwner(this)
					.setupType(entries.get(i).type.getNetworkProvider())
					.setupPositions(unified.portPositions)
					.setupConnections(unified.portConnections);
		}
		this.solidFuelPortsActive = true;
	}

	/**
	 * 销毁接管的输入端口，交回标准机制。
	 * 不能直接 disableIfPresent——它底层 popNode 会销毁节点所在的整个网络，
	 * 端口若已并入玩家的燃油管网就会把整条管网炸掉。
	 * 这里先让节点安全退出网络（共享网络只摘链接，独占网络才销毁），再移除节点。
	 */
	private void destroySolidFuelInPorts() {
		if(this.solidFuelInPorts != null) {
			for(TilePort port : this.solidFuelInPorts) {
				if(port == null) continue;
				GenNode node = port.getNode();
				if(node != null) {
					NodeNet net = node.net;
					if(net != null) {
						net.removeReceiver(this);
						if(net.links.size() > 1) {
							net.leaveLink(node); // 共享网络：只摘掉本节点，其余管道不受影响
						} else {
							net.destroy(); // 只有本节点的空网络，直接销毁
						}
					}
					UniNodespace.destroyNode(worldObj, node);
				}
			}
			this.solidFuelInPorts = null;
		}
		this.solidFuelPortsActive = false;
	}

	/**
	 * 合并配方下，4 个输入罐不再按罐型精确匹配：
	 * 白名单内的流体可以进同种罐，也可以认领空罐（最多 4 种）。
	 * 冷却剂罐始终走原来的精确匹配。
	 */
	@Override
	public long getDemand(FluidType type, int pressure) {

		SolidFuelRecipe recipe = this.module.getRecipe() instanceof SolidFuelRecipe
				? (SolidFuelRecipe) this.module.getRecipe() : null;

		if(recipe == null || !recipe.accepts(type)) {
			// 标准精确匹配（与接口默认实现一致）
			long amount = 0;
			for(FluidTank tank : getReceivingTanks()) {
				if(tank.getTankType() == type && tank.getPressure() == pressure) amount += tank.getMaxFill() - tank.getFill();
			}
			return amount;
		}

		long amount = 0;
		for(FluidTank tank : inputTanks) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				amount += tank.getMaxFill() - tank.getFill();
			}
		}

		// 还没有自己的罐时，认领第一个空罐（一种流体只占一个罐，最多 4 种）
		int slot = claimableEmptyTank(type, pressure);
		if(slot >= 0) amount += inputTanks[slot].getMaxFill();

		// 冷却剂罐照旧精确匹配
		if(coolant.getTankType() == type && coolant.getPressure() == pressure) {
			amount += coolant.getMaxFill() - coolant.getFill();
		}
		return amount;
	}

	/**
	 * 这种流体现在能不能认领一个空罐，能就返回第一个空罐的下标，否则 -1。
	 *
	 * <p>规则：这种流体<b>还没有自己的罐</b>时才认领，且只认领第一个空罐。
	 * 一种流体只占一个罐，所以 4 个罐最多装 4 种不同的流体；已经有罐的流体
	 * 只往自己那个罐里补灌，不会把空罐全吃掉、把后来的流体挤在外面。</p>
	 *
	 * <p>注意<b>不能按白名单序号分配罐子</b>：白名单有 26 种流体、罐子只有 4 个，
	 * 序号 ≥ 空罐数的流体永远认领不到（排在前面的燃油能进、靠后的煤油进不去）。
	 * 这里改成「谁来了就给第一个空罐」，与流体在白名单里的位置无关。</p>
	 *
	 * <p>空罐只接常压（pressure 0）：加压流体只补灌同种罐，避免同一份空位在
	 * 6 个压力档上被重复申报。</p>
	 */
	private int claimableEmptyTank(FluidType type, int pressure) {

		if(pressure != 0) return -1;

		for(FluidTank tank : inputTanks) {
			if(tank.getTankType() == type) return -1; // 已经有自己的罐，只补灌不认领
		}
		for(int i = 0; i < inputTanks.length; i++) {
			FluidTank tank = inputTanks[i];
			if(tank.getTankType() == Fluids.NONE && tank.getFill() <= 0) return i;
		}
		return -1;
	}

	/**
	 * 合并配方下白名单流体的压力范围：已有同种罐就按那些罐的压力，
	 * 另外只要还有空罐就把常压档（0）包进来，让新流体进得来。
	 */
	@Override
	public int[] getReceivingPressureRange(FluidType type) {

		SolidFuelRecipe recipe = this.module.getRecipe() instanceof SolidFuelRecipe
				? (SolidFuelRecipe) this.module.getRecipe() : null;

		int lowest = HIGHEST_VALID_PRESSURE;
		int highest = 0;
		for(FluidTank tank : getReceivingTanks()) {
			if(tank.getTankType() == type) {
				if(tank.getPressure() < lowest) lowest = tank.getPressure();
				if(tank.getPressure() > highest) highest = tank.getPressure();
			}
		}

		if(recipe != null && recipe.accepts(type) && claimableEmptyTank(type, 0) >= 0) {
			// 还能认领空罐：把常压档纳入范围
			if(lowest > highest) return new int[] {0, 0};
			return new int[] {Math.min(lowest, 0), Math.max(highest, 0)};
		}

		// 没有这种流体的罐、也没空罐可认领：返回空区间，不参与分配
		if(lowest > highest) return new int[] {0, -1};
		return new int[] {lowest, highest};
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long amount) {

		SolidFuelRecipe recipe = this.module.getRecipe() instanceof SolidFuelRecipe
				? (SolidFuelRecipe) this.module.getRecipe() : null;

		if(recipe == null || !recipe.accepts(type)) {
			// 标准精确匹配（与接口默认实现一致）：同种多罐先均分再溢流
			int tanks = 0;
			for(FluidTank tank : getReceivingTanks()) {
				if(tank.getTankType() == type && tank.getPressure() == pressure) tanks++;
			}
			if(tanks > 1) {
				int firstRound = (int) Math.floor((double) amount / (double) tanks);
				for(FluidTank tank : getReceivingTanks()) {
					if(tank.getTankType() == type && tank.getPressure() == pressure) {
						int toAdd = Math.min(firstRound, tank.getMaxFill() - tank.getFill());
						tank.setFill(tank.getFill() + toAdd);
						amount -= toAdd;
					}
				}
			}
			if(amount > 0) for(FluidTank tank : getReceivingTanks()) {
				if(tank.getTankType() == type && tank.getPressure() == pressure) {
					int toAdd = (int) Math.min(amount, tank.getMaxFill() - tank.getFill());
					tank.setFill(tank.getFill() + toAdd);
					amount -= toAdd;
				}
			}
			return amount;
		}

		// 先灌同种罐
		for(FluidTank tank : inputTanks) {
			if(amount <= 0) break;
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				int add = (int) Math.min(amount, tank.getMaxFill() - tank.getFill());
				tank.setFill(tank.getFill() + add);
				amount -= add;
			}
		}

		// 还有剩余就认领需求计算时看到的同一个空罐（同一条规则，两边必然一致）
		if(amount > 0) {
			int slot = claimableEmptyTank(type, pressure);
			if(slot >= 0) {
				FluidTank tank = inputTanks[slot];
				tank.setTankType(type);
				tank.withPressure(pressure);
				int add = (int) Math.min(amount, tank.getMaxFill());
				tank.setFill(add);
				amount -= add;
			}
		}

		// 冷却剂罐照旧精确匹配
		if(amount > 0 && coolant.getTankType() == type && coolant.getPressure() == pressure) {
			int add = (int) Math.min(amount, coolant.getMaxFill() - coolant.getFill());
			coolant.setFill(coolant.getFill() + add);
			amount -= add;
		}

		if(amount <= 0) this.markChanged();
		return amount;
	}

	/* ============================== 电力 ============================== */

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return maxPower; }

	/* ============================== 存档 / 同步 ============================== */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		for(FluidTank tank : inputTanks) tank.serialize(buf);
		for(FluidTank tank : outputTanks) tank.serialize(buf);
		coolant.serialize(buf);
		coolantOut.serialize(buf);

		buf.writeLong(power);
		buf.writeLong(maxPower);
		buf.writeBoolean(didProcess);
		buf.writeBoolean(outputFull);
		buf.writeByte(catalystType);
		buf.writeFloat(heat);
		buf.writeInt(targetHeat);
		buf.writeBoolean(heatingUp);
		buf.writeBoolean(multiRecipeMode);
		for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
			String s = multiSelections[i] == null ? "" : multiSelections[i];
			buf.writeInt(s.length());
			for(int c = 0; c < s.length(); c++) buf.writeChar(s.charAt(c));
		}
		module.serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		for(FluidTank tank : inputTanks) tank.deserialize(buf);
		for(FluidTank tank : outputTanks) tank.deserialize(buf);
		coolant.deserialize(buf);
		coolantOut.deserialize(buf);

		this.power = buf.readLong();
		this.maxPower = buf.readLong();
		this.didProcess = buf.readBoolean();
		this.outputFull = buf.readBoolean();
		this.catalystType = buf.readByte();
		this.heat = buf.readFloat();
		this.targetHeat = buf.readInt();
		this.heatingUp = buf.readBoolean();
		this.multiRecipeMode = buf.readBoolean();
		for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
			int len = buf.readInt();
			StringBuilder sb = new StringBuilder(len);
			for(int c = 0; c < len; c++) sb.append(buf.readChar());
			this.multiSelections[i] = sb.toString();
		}
		// 客户端也要按同步过来的模式设容量，否则罐条上限显示不一致
		applyTankCapacity();
		module.deserialize(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].writeToNBT(nbt, "i" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].writeToNBT(nbt, "o" + i);
		coolant.writeToNBT(nbt, "c");
		coolantOut.writeToNBT(nbt, "co");

		nbt.setLong("power", power);
		nbt.setLong("maxPower", maxPower);
		nbt.setFloat("heat", heat);
		nbt.setBoolean("multiRecipeMode", multiRecipeMode);
		for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
			nbt.setString("mf" + i, multiSelections[i] == null ? "" : multiSelections[i]);
		}
		module.writeToNBT(nbt);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		for(int i = 0; i < inputTanks.length; i++) inputTanks[i].readFromNBT(nbt, "i" + i);
		for(int i = 0; i < outputTanks.length; i++) outputTanks[i].readFromNBT(nbt, "o" + i);
		coolant.readFromNBT(nbt, "c");
		coolantOut.readFromNBT(nbt, "co");

		this.power = nbt.getLong("power");
		this.maxPower = nbt.getLong("maxPower");
		this.heat = nbt.getFloat("heat");
		// 旧存档没有这个键，getBoolean 返回 false，正好落回默认的高并行模式
		this.multiRecipeMode = nbt.getBoolean("multiRecipeMode");
		for(int i = 0; i < MULTI_RECIPE_COUNT; i++) {
			this.multiSelections[i] = nbt.getString("mf" + i);
		}
		// 存档里的模式决定罐容量，读回来后重新应用一次
		applyTankCapacity();
		module.readFromNBT(nbt);
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 5, yCoord, zCoord - 5,
					xCoord + 5, yCoord + 4, zCoord + 5);
		}
		return bb;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	/* ============================== 升级简介 ============================== */

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_large_catalytic_chemical_factory));

		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_SPEED, "+" + (level * 100 / 3) + "%"));
			// 与 :405 的 pow += min(SPEED 等级, 3) 对齐：每级 +100% 功耗。
			// 这里原先写 level * 50，与实际倍率差一倍，会误导玩家估电。
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_CONSUMPTION, "+" + (level * 100) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_COOLANT_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_CONSUMPTION, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_COOLANT_CONSUMPTION, "-" + (level * 25) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public HashMap<UpgradeType, Integer> getValidUpgrades() {
		HashMap<UpgradeType, Integer> upgrades = new HashMap<UpgradeType, Integer>();
		upgrades.put(UpgradeType.SPEED, 3);
		upgrades.put(UpgradeType.POWER, 3);
		upgrades.put(UpgradeType.OVERDRIVE, 3);
		return upgrades;
	}
}

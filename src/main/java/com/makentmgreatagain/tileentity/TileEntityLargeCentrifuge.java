package com.makentmgreatagain.tileentity;

import java.util.HashMap;
import java.util.List;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.main.NTMSounds;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.TilePort;
import com.hbm.tileentity.TilePort.PortDef;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.CentrifugeRecipe;
import com.makentmgreatagain.recipes.LargeCentrifugeRecipes;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型离心机核心。
 *
 * <p>两条物品进料线互相独立：A、B 两个输入槽各自匹配配方并计时，可以同时处理。
 * 输入罐里的流体也会单独匹配流体配方并行处理。产物先进 8 个物品输出槽和 4 个输出罐，
 * 再由右侧的容器槽把输出罐灌进空罐。</p>
 *
 * <p>冷却剂：冷四氟甲烷进入机器后变成四氟甲烷，运行时按升级后的速率消耗。</p>
 */
public class TileEntityLargeCentrifuge extends TileEntityLoadedBase implements IEnergyReceiverMK2, IBufPacketReceiver, IFluidStandardTransceiverMK2, ISidedInventory, IUpgradeInfoProvider, IControlReceiver {

	public static final int SLOT_CAN_IN = 0;
	public static final int SLOT_CAN_OUT = 1;
	public static final int SLOT_IN_A = 2;
	public static final int SLOT_IN_B = 3;
	public static final int SLOT_FLUID_ID = 4;
	public static final int SLOT_OUT = 5;        // 5 ~ 12
	public static final int SLOT_FLUID_CAN = 13; // 13 ~ 16
	public static final int SLOT_FILLED = 17;    // 17 ~ 20
	public static final int SLOT_BATTERY = 21;
	public static final int SLOT_UPGRADE = 22;   // 22 ~ 23
	public static final int INV_SIZE = 24;

	/** 本体离心机 1,000,000 的 25 倍：要撑得住「速度 3 + 超频 3 + 8 倍并行」的单线满耗 */
	public static final long MAX_POWER = 25_000_000L;
	/** 输入/输出流体罐容量；冷却剂单独用 COOLANT_CAP，不跟着改 */
	public static final int TANK_CAP = 64_000;
	public static final int COOLANT_CAP = 32_000;
	public static final int CAN_AMOUNT = 1_000;
	/** 基础冷却剂消耗：3 mB/t，随升级提高 */
	public static final int BASE_COOLANT = 3;
	public static final int BASE_DURATION = 180;

	public ItemStack[] slots = new ItemStack[INV_SIZE];
	public FluidTank tankIn = new FluidTank(Fluids.NONE, TANK_CAP);
	public FluidTank[] tanksOut = new FluidTank[] {
		new FluidTank(Fluids.NONE, TANK_CAP),
		new FluidTank(Fluids.NONE, TANK_CAP),
		new FluidTank(Fluids.NONE, TANK_CAP),
		new FluidTank(Fluids.NONE, TANK_CAP),
	};
	public FluidTank coolantCold = new FluidTank(Fluids.PERFLUOROMETHYL_COLD, COOLANT_CAP);
	public FluidTank coolantHot = new FluidTank(Fluids.PERFLUOROMETHYL, COOLANT_CAP);

	public long power;
	/** 物品阶段进度：A、B 两个槽共用这一条 */
	public int progressA;
	public int progressF;
	/** 客户端动画进度：产物里有物品 / 有流体的线各取最大进度 */
	public float animItems;
	public float animFluids;
	public boolean activeA;
	public boolean activeB;
	public boolean activeF;
	public boolean isOn;

	/** 当前正在处理的配方，用于输入换了配方时强制从头重启（只服务端用） */
	private CentrifugeRecipe runA, runB, runF;

	/** 原版离心机的运转循环音效（客户端） */
	private AudioWrapper audio;
	private int audioDuration;

	/** 并行倍率档位：0=1×、1=2×、2=4×、3=8×（GUI 滑块的 4 个档，0 在底部） */
	public int parallelMode;

	/* ---------------- 基础 ---------------- */

	@Override
	public void updateEntity() {

		if(worldObj == null) return;

		if(worldObj.isRemote) {
			clientTick();
			return;
		}

		chargeBattery();
		handleFluidIdentifier();
		handleCanisterInput();

		// 必须在建端口之前：把进料罐声明成配方要求的 PU，否则带压流体永远进不来
		presetInputPressure();

		// 与本体重型机器一致：给 12 个接口建 UNINOS 节点，电力/流体管网才能真正接上
		this.setupAllPorts(getPorts());
		this.updatePortPIFIFO();

		// 冷却剂必须在场；运行途中耗尽就直接取消当前这批处理。
		// 消耗固定按滑块档位算且为原来的 2 倍：只要开着这个档位，就一直按该档位扣，与原料够不够无关
		int coolantBase = getCoolantUse();
		int parallel = getParallel();
		boolean hasCoolant = this.coolantCold.getFill() >= coolantBase * parallel * 2;

		if(!hasCoolant) {
			cancelProcessing();
		}

		// 每条线只有在电力够的时候才会推进，完成后按实际运行的线数扣电/冷却剂
		// 物品阶段：A、B 两个槽共用一条进度，一次离心同时处理两边的配方
		boolean ranItems = hasCoolant && tickItemStage();
		boolean ranF = hasCoolant && tickFluidLine();

		long cost = 0L;
		int coolant = 0;
		// 并行不再增加耗电（只按单线功率结算）；冷却剂仍随并行倍率增加，且整体为原来的 2 倍
		if(ranItems) { cost += getPowerPerTick(); coolant += coolantBase * parallel * 2; }
		if(ranF) { cost += getPowerPerTick(); coolant += coolantBase * parallel * 2; }

		if(cost > 0L) {
			this.power = Math.max(0L, this.power - cost);
			// 冷四氟甲烷消耗，同时产出四氟甲烷
			this.coolantCold.setFill(Math.max(0, this.coolantCold.getFill() - coolant));
			this.coolantHot.setFill(Math.min(COOLANT_CAP, this.coolantHot.getFill() + coolant));
			this.isOn = true;
		} else {
			this.isOn = false;
		}

		fillOutputCanisters();
		updateAnimations();

		// GUI 的电力条、进度动画和罐量全靠这条包同步
		this.networkPackNT(50);
	}

	@SideOnly(Side.CLIENT)
	private void clientTick() {

		// 和本体离心机一致：运转时音量/音调逐渐拉起，停机后慢慢淡出
		this.audioDuration += this.isOn ? 2 : -3;
		this.audioDuration = MathHelper.clamp_int(this.audioDuration, 0, 60);

		boolean audible = this.audioDuration > 10
				&& MainRegistry.proxy.me().getDistance(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) < 25D;

		if(audible) {
			if(this.audio == null) {
				this.audio = createAudioLoop();
				this.audio.startSound();
			} else if(!this.audio.isPlaying()) {
				this.audio = rebootAudio(this.audio);
			}

			this.audio.updateVolume(getVolume(1F));
			this.audio.updatePitch((this.audioDuration - 10) / 100F + 0.5F);
			this.audio.keepAlive();

		} else if(this.audio != null) {
			this.audio.stopSound();
			this.audio = null;
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound(NTMSounds.CENTRIFUGE_LOOP, xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F, 20);
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		stopAudioLoop();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		stopAudioLoop();
	}

	private void stopAudioLoop() {

		if(this.worldObj != null && this.worldObj.isRemote && this.audio != null) {
			this.audio.stopSound();
			this.audio = null;
		}
	}

	/* ---------------- 处理 ---------------- */

	/**
	 * 物品阶段：A、B 两个槽共用一条进度，一次离心同时处理两边不同的配方，
	 * 完成时各扣各的料、各出各的产物。
	 */
	private boolean tickItemStage() {

		CentrifugeRecipe ra = LargeCentrifugeRecipes.forItem(this.slots[SLOT_IN_A]);
		CentrifugeRecipe rb = LargeCentrifugeRecipes.forItem(this.slots[SLOT_IN_B]);

		// 多物品配方要靠两个槽凑料，只由 A 代表；B 再单独跑一份只会重复消耗
		if(ra != null && ra.inputItems.size() > 1) rb = null;
		if(rb != null && rb.inputItems.size() > 1) rb = null;
		// 两槽是同一个配方时只跑一次
		if(ra != null && rb == ra) rb = null;

		// 料不够的槽不参与
		if(ra != null && !hasItemCount(this.slots[SLOT_IN_A], ra)) ra = null;
		if(rb != null && !hasItemCount(this.slots[SLOT_IN_B], rb)) rb = null;

		if(ra == null && rb == null) {
			stopItemStage();
			return false;
		}

		// 槽里被换成了别的配方：丢弃进度，两边一起从头开始
		if((ra != null && this.runA != null && this.runA != ra)
				|| (rb != null && this.runB != null && this.runB != rb)) {
			this.progressA = 0;
			this.activeA = false;
			this.activeB = false;
		}

		int batchA = 0;
		int batchB = 0;

		if(ra != null && rb != null) {
			// 两个不同配方也吃并行倍率：按滑块档位逐级回落，找两边能一起跑的份数。
			// 输入流体必须校验合计：各自canStart只查单份，两线同吃一罐会把存量扣成负数
			for(int batch = getParallel(); batch >= 1; batch >>= 1) {
				if(canStart(ra, true, batch) && canStart(rb, true, batch)
						&& hasCombinedFluidInput(ra, batch, rb, batch)
						&& hasCombinedSpace(ra, batch, rb, batch)) {
					batchA = batch;
					batchB = batch;
					break;
				}
			}

			if(batchA <= 0) {
				// 一起装不下：这轮先跑单独装得下的那一边，另一边留到下一轮
				batchA = resolveBatch(ra, true);
				if(batchA <= 0) {
					ra = null;
					batchB = resolveBatch(rb, true);
					if(batchB <= 0) rb = null;
				} else {
					rb = null;
				}
			}
		} else if(ra != null) {
			batchA = resolveBatch(ra, true);
		} else if(rb != null) {
			batchB = resolveBatch(rb, true);
		}

		if(ra != null && batchA <= 0) ra = null;
		if(rb != null && batchB <= 0) rb = null;

		if(ra == null && rb == null) {
			stopItemStage();
			return false;
		}

		this.progressA++;
		this.activeA = true;
		this.activeB = rb != null;
		this.runA = ra;
		this.runB = rb;

		if(this.progressA >= getDuration()) {
			if(ra != null) finish(ra, SLOT_IN_A, batchA);
			if(rb != null) finish(rb, SLOT_IN_B, batchB);
			this.progressA = 0;
			this.activeA = false;
			this.activeB = false;
		}

		return true;
	}

	private boolean tickFluidLine() {

		// 物品线正在处理时不启动流体线：一条离心液体、一条离心物品会让两套动画互相串位，
		// 新进入的物品视作新任务，直接顶掉流体线的进度
		if(this.activeA || this.activeB) {
			stopFluidLine();
			return false;
		}

		// 输入罐正被物品线的配方占用时，流体线不抢这批流体，
		// 否则水会先被纯水配方抽干，钨酸那条永远启动不了
		if(isTankReservedForItemLines()) {
			stopFluidLine();
			return false;
		}

		CentrifugeRecipe recipe = LargeCentrifugeRecipes.forFluid(
				this.tankIn.getTankType(), this.tankIn.getFill(), this.tankIn.getPressure());

		if(recipe == null) {
			stopFluidLine();
			return false;
		}

		// 罐里换成了别的流体：丢弃已完成的进度，强制从头开始
		if(this.runF != null && this.runF != recipe) {
			this.progressF = 0;
			this.activeF = false;
		}

		// 实际并行份数：设定倍率不够时逐级回落 8→4→2→1
		int batch = resolveBatch(recipe, false);

		if(batch <= 0) {
			stopFluidLine();
			return false;
		}

		this.progressF++;
		this.activeF = true;
		this.runF = recipe;

		if(this.progressF >= getDuration()) {
			finish(recipe, -1, batch);
			this.progressF = 0;
			this.activeF = false;
		}

		return true;
	}

	private void stopItemStage() {
		this.activeA = false;
		this.activeB = false;
		this.progressA = 0;
		this.runA = null;
		this.runB = null;
	}

	private void stopFluidLine() {
		this.activeF = false;
		this.progressF = 0;
		this.runF = null;
	}

	/** 取消当前所有处理（冷却剂耗尽时调用，进度归零且不产出） */
	private void cancelProcessing() {

		stopItemStage();
		stopFluidLine();
	}

	private boolean hasItemCount(ItemStack stack, CentrifugeRecipe recipe) {

		if(stack == null) return false;

		for(AStack input : recipe.inputItems) {
			if(input.matchesRecipe(stack, true)) {
				return stack.stackSize >= Math.max(1, input.stacksize);
			}
		}

		return false;
	}

	/** 配方要求的物品是否都能在两个输入槽里凑齐（多种物品必须分属不同槽） */
	private boolean hasAllInputs(CentrifugeRecipe recipe) {

		if(recipe.inputItems.isEmpty()) return true;

		if(recipe.inputItems.size() == 1) {
			AStack required = recipe.inputItems.get(0);
			return matchesInput(required, this.slots[SLOT_IN_A]) || matchesInput(required, this.slots[SLOT_IN_B]);
		}

		AStack first = recipe.inputItems.get(0);
		AStack second = recipe.inputItems.get(1);

		return (matchesInput(first, this.slots[SLOT_IN_A]) && matchesInput(second, this.slots[SLOT_IN_B]))
			|| (matchesInput(second, this.slots[SLOT_IN_A]) && matchesInput(first, this.slots[SLOT_IN_B]));
	}

	private static boolean matchesInput(AStack required, ItemStack stack) {
		return stack != null && required.matchesRecipe(stack, true) && stack.stackSize >= Math.max(1, required.stacksize);
	}

	/** 两个输入槽里的匹配物品总数是否够 {@code batch} 份配方（多物品配方按种类分别统计） */
	private boolean hasInputsFor(CentrifugeRecipe recipe, int batch) {

		for(AStack required : recipe.inputItems) {
			int need = Math.max(1, required.stacksize) * batch;
			int have = 0;

			for(int i = SLOT_IN_A; i <= SLOT_IN_B; i++) {
				ItemStack stack = this.slots[i];
				if(stack != null && required.matchesRecipe(stack, true)) have += stack.stackSize;
			}

			if(have < need) return false;
		}

		return true;
	}

	/** 两个物品槽里是否有正在等当前罐流体的配方（这时候流体线要让路） */
	private boolean isTankReservedForItemLines() {

		for(int slot = SLOT_IN_A; slot <= SLOT_IN_B; slot++) {
			CentrifugeRecipe recipe = LargeCentrifugeRecipes.forItem(this.slots[slot]);
			if(recipe == null || recipe.inputFluid == null) continue;
			if(recipe.inputFluid.type != this.tankIn.getTankType()) continue;
			if(hasAllInputs(recipe)) return true;
		}

		return false;
	}

	/**
	 * 实际并行份数：从设定倍率开始按 8→4→2→1 逐级回落，
	 * 取第一个输入、输出空间、电力、冷却剂都够的份数；一个都不够返回 0。
	 */
	private int resolveBatch(CentrifugeRecipe recipe, boolean itemLine) {

		for(int batch = getParallel(); batch >= 1; batch >>= 1) {
			if(canStart(recipe, itemLine, batch)) return batch;
		}

		return 0;
	}

	/** 启动条件：输入物品、输入流体、输出空间、电力都不缺（按并行份数校验） */
	private boolean canStart(CentrifugeRecipe recipe, boolean itemLine, int batch) {

		// 物品线要凑齐配方要求的全部物品；流体线只跑纯流体配方，不走这里
		if(itemLine && !hasInputsFor(recipe, batch)) return false;

		if(recipe.inputFluid != null) {
			if(this.tankIn.getTankType() != recipe.inputFluid.type) return false;
			if(this.tankIn.getFill() < recipe.inputFluid.fill * batch) return false;
			// 带压配方：进料罐压力必须精确匹配（0 = 不限压，老配方不受影响）
			if(recipe.inputFluid.pressure != 0
					&& this.tankIn.getPressure() != recipe.inputFluid.pressure) return false;
		}

		// 耗电固定按单线功率结算（并行不增加耗电）；冷却剂按滑块档位结算且为原来的 2 倍，
		// 原料不足而回落份数时也不会少扣
		if(this.power < getPowerPerTick()) return false;

		// 没有冷四氟甲烷就不允许开始
		if(this.coolantCold.getFill() < getCoolantUse() * getParallel() * 2) return false;

		if(!hasItemSpace(recipe, batch)) return false;
		if(!hasFluidSpace(recipe, batch)) return false;

		return true;
	}

	private void finish(CentrifugeRecipe recipe, int slot, int batch) {

		if(slot >= 0) {
			consumeInputs(recipe, slot, batch);
		}

		// 物品线和流体线都可能带流体输入，完成时统一扣（钳到0兜底，绝不允许负存量）
		if(recipe.inputFluid != null) {
			this.tankIn.setFill(Math.max(0, this.tankIn.getFill() - recipe.inputFluid.fill * batch));
		}

		for(int i = 0; i < batch; i++) {
			for(ItemStack out : recipe.outputItems) insertItem(out.copy());
			for(int f = 0; f < recipe.outputFluids.size(); f++) insertFluid(recipe.outputFluids.get(f), f);
		}

		this.markDirty();
	}

	/** 扣掉配方要求的全部物品（按并行份数）：优先从本条线自己的槽扣，不够再看另一个输入槽 */
	private void consumeInputs(CentrifugeRecipe recipe, int preferredSlot, int batch) {

		for(AStack required : recipe.inputItems) {
			int remaining = Math.max(1, required.stacksize) * batch;

			remaining = consumeFromSlot(required, preferredSlot, remaining);

			for(int i = SLOT_IN_A; i <= SLOT_IN_B && remaining > 0; i++) {
				if(i == preferredSlot) continue;
				remaining = consumeFromSlot(required, i, remaining);
			}
		}
	}

	/** 从指定槽最多扣掉 {@code amount} 个匹配物品，返回还差多少没扣到 */
	private int consumeFromSlot(AStack required, int slot, int amount) {

		ItemStack stack = this.slots[slot];
		if(stack == null || amount <= 0 || !required.matchesRecipe(stack, true)) return amount;

		int take = Math.min(amount, stack.stackSize);
		stack.stackSize -= take;
		if(stack.stackSize <= 0) this.slots[slot] = null;
		return amount - take;
	}

	/* ---------------- 输出空间 ---------------- */

	private boolean hasItemSpace(CentrifugeRecipe recipe, int batch) {

		for(ItemStack out : recipe.outputItems) {
			if(getItemSpace(out) < out.stackSize * batch) return false;
		}

		return true;
	}

	private int getItemSpace(ItemStack stack) {

		int space = 0;

		for(int i = SLOT_OUT; i < SLOT_OUT + 8; i++) {
			ItemStack slot = this.slots[i];
			if(slot == null) {
				space += stack.getMaxStackSize();
			} else if(slot.getItem() == stack.getItem() && slot.getItemDamage() == stack.getItemDamage()
					&& ItemStack.areItemStackTagsEqual(slot, stack)) {
				space += Math.max(0, Math.min(slot.getMaxStackSize(), 64) - slot.stackSize);
			}
		}

		return space;
	}

	private void insertItem(ItemStack stack) {

		int max = Math.min(stack.getMaxStackSize(), 64);

		for(int i = SLOT_OUT; i < SLOT_OUT + 8 && stack.stackSize > 0; i++) {
			ItemStack slot = this.slots[i];
			if(slot != null && slot.getItem() == stack.getItem() && slot.getItemDamage() == stack.getItemDamage()
					&& ItemStack.areItemStackTagsEqual(slot, stack) && slot.stackSize < max) {
				int add = Math.min(max - slot.stackSize, stack.stackSize);
				slot.stackSize += add;
				stack.stackSize -= add;
			}
		}

		for(int i = SLOT_OUT; i < SLOT_OUT + 8 && stack.stackSize > 0; i++) {
			if(this.slots[i] == null) {
				int add = Math.min(max, stack.stackSize);
				ItemStack copy = stack.copy();
				copy.stackSize = add;
				this.slots[i] = copy;
				stack.stackSize -= add;
			}
		}
	}

	private boolean hasFluidSpace(CentrifugeRecipe recipe, int batch) {

		// 一一对应：第 i 种流体只进第 i 个罐，对应罐装不下就停机，不串罐
		for(int i = 0; i < recipe.outputFluids.size(); i++) {
			FluidStack out = recipe.outputFluids.get(i);
			if(out == null || i >= this.tanksOut.length) return false;

			FluidTank tank = this.tanksOut[i];
			boolean compatible = tank.getTankType() == out.type
					|| tank.getTankType() == Fluids.NONE || tank.getFill() == 0;
			if(!compatible) return false;

			if(TANK_CAP - tank.getFill() < out.fill * batch) return false;
		}

		return true;
	}

	private void insertFluid(FluidStack stack, int index) {

		// 一一对应：只写第 index 个罐，满了也不串到别的罐
		if(index < 0 || index >= this.tanksOut.length) return;
		FluidTank tank = this.tanksOut[index];

		if(tank.getTankType() != stack.type) {
			// 空罐（或无类型）才允许定型；已有别的流体就不写，避免混罐
			if(tank.getTankType() != Fluids.NONE && tank.getFill() > 0) return;
			tank.setTankType(stack.type);
		}

		int add = Math.min(TANK_CAP - tank.getFill(), stack.fill);
		tank.setFill(tank.getFill() + add);
	}

	/**
	 * A、B 两个配方同一轮出料时，8 个物品输出槽和 4 个流体输出罐要能一起装下。
	 * 按现有内容模拟插入一遍，避免两边单独都装得下、合起来却溢出。
	 */
	/** 两条线同时跑时输入罐存量必须够两边合计消耗（同罐同型；类型不同则canStart已各自拦下） */
	private boolean hasCombinedFluidInput(CentrifugeRecipe ra, int batchA, CentrifugeRecipe rb, int batchB) {
		long need = 0;
		if(ra.inputFluid != null) need += (long) ra.inputFluid.fill * batchA;
		if(rb.inputFluid != null) need += (long) rb.inputFluid.fill * batchB;
		return this.tankIn.getFill() >= need;
	}

	private boolean hasCombinedSpace(CentrifugeRecipe ra, int batchA, CentrifugeRecipe rb, int batchB) {

		ItemStack[] itemSlots = new ItemStack[8];
		for(int i = 0; i < 8; i++) {
			ItemStack stack = this.slots[SLOT_OUT + i];
			itemSlots[i] = stack == null ? null : stack.copy();
		}

		if(!simulateItems(itemSlots, ra, batchA) || !simulateItems(itemSlots, rb, batchB)) return false;

		FluidType[] types = new FluidType[4];
		int[] fills = new int[4];
		for(int i = 0; i < 4; i++) {
			types[i] = this.tanksOut[i].getTankType();
			fills[i] = this.tanksOut[i].getFill();
		}

		return simulateFluids(types, fills, ra, batchA) && simulateFluids(types, fills, rb, batchB);
	}

	private static boolean simulateItems(ItemStack[] slots, CentrifugeRecipe recipe, int batch) {

		for(int i = 0; i < batch; i++) {
			for(ItemStack out : recipe.outputItems) {
				if(!simulateInsertItem(slots, out.copy())) return false;
			}
		}

		return true;
	}

	private static boolean simulateInsertItem(ItemStack[] slots, ItemStack stack) {

		int max = Math.min(stack.getMaxStackSize(), 64);

		for(int i = 0; i < slots.length && stack.stackSize > 0; i++) {
			ItemStack slot = slots[i];
			if(slot != null && slot.getItem() == stack.getItem() && slot.getItemDamage() == stack.getItemDamage()
					&& ItemStack.areItemStackTagsEqual(slot, stack) && slot.stackSize < max) {
				int add = Math.min(max - slot.stackSize, stack.stackSize);
				slot.stackSize += add;
				stack.stackSize -= add;
			}
		}

		for(int i = 0; i < slots.length && stack.stackSize > 0; i++) {
			if(slots[i] == null) {
				int add = Math.min(max, stack.stackSize);
				ItemStack copy = stack.copy();
				copy.stackSize = add;
				slots[i] = copy;
				stack.stackSize -= add;
			}
		}

		return stack.stackSize <= 0;
	}

	private static boolean simulateFluids(FluidType[] types, int[] fills, CentrifugeRecipe recipe, int batch) {

		// 与 insertFluid 一致：第 f 种流体只进第 f 个罐
		for(int i = 0; i < batch; i++) {
			for(int f = 0; f < recipe.outputFluids.size(); f++) {
				FluidStack out = recipe.outputFluids.get(f);
				if(out == null || f >= types.length) return false;

				boolean compatible = types[f] == out.type || types[f] == Fluids.NONE || fills[f] == 0;
				if(!compatible) return false;

				if(TANK_CAP - fills[f] < out.fill) return false;

				if(fills[f] == 0) types[f] = out.type;
				fills[f] += out.fill;
			}
		}

		return true;
	}

	/* ---------------- 罐 / 冷却剂 / 电量 ---------------- */

	private void handleFluidIdentifier() {

		// 和本体机器一致：识别码槽里放了物品就直接改输入罐类型（换类型会清空），
		// 不再限制“罐必须为空”，否则识别码看着像没反应
		this.tankIn.setType(SLOT_FLUID_ID, this.slots);
	}

	private void handleCanisterInput() {

		ItemStack in = this.slots[SLOT_CAN_IN];
		if(in == null) return;

		// 无限流体容器（创造桶 / 无限水）：不消耗物品，直接把目标罐灌满
		if(in.getItem() instanceof ItemInfiniteFluid) {
			handleInfiniteFluid((ItemInfiniteFluid) in.getItem());
			return;
		}

		boolean lead = in.getItem() == ModItems.fluid_tank_lead_full;
		boolean normal = in.getItem() == ModItems.fluid_tank_full;
		boolean barrel = in.getItem() == ModItems.fluid_barrel_full;
		if(!lead && !normal && !barrel) return;

		Item emptyType = lead ? ModItems.fluid_tank_lead_empty
				: normal ? ModItems.fluid_tank_empty : ModItems.fluid_barrel_empty;
		if(this.slots[SLOT_CAN_OUT] != null && this.slots[SLOT_CAN_OUT].getItem() != emptyType) return;

		FluidType type = Fluids.fromID(in.getItemDamage());
		if(type == null || type == Fluids.NONE) return;

		// 冷四氟甲烷是冷却剂，灌进冷却剂罐；其它流体进加工用的输入罐
		boolean isCoolant = type == Fluids.PERFLUOROMETHYL_COLD;
		FluidTank target = isCoolant ? this.coolantCold : this.tankIn;
		int capacity = isCoolant ? COOLANT_CAP : TANK_CAP;

		// 目标罐为空时直接用桶里的流体定型；已有流体时必须同种
		if(target.getFill() > 0 && target.getTankType() != type) return;
		if(capacity - target.getFill() < CAN_AMOUNT) return;

		target.setTankType(type);
		target.setFill(target.getFill() + CAN_AMOUNT);

		if(in.stackSize > 1) {
			in.stackSize--;
			if(this.slots[SLOT_CAN_OUT] == null) {
				this.slots[SLOT_CAN_OUT] = new ItemStack(emptyType, 1);
			} else {
				this.slots[SLOT_CAN_OUT].stackSize++;
			}
		} else {
			this.slots[SLOT_CAN_IN] = null;
			this.slots[SLOT_CAN_OUT] = new ItemStack(emptyType, 1);
		}

		this.markDirty();
	}

	/** 无限流体容器：按容器类型选罐并灌满，物品不消耗 */
	private void handleInfiniteFluid(ItemInfiniteFluid item) {

		FluidType type = item.getType();
		FluidTank target;

		if(type == Fluids.PERFLUOROMETHYL_COLD) {
			// 冷四氟甲烷专门喂冷却剂罐
			target = this.coolantCold;
		} else if(type != null || this.tankIn.getTankType() != Fluids.NONE) {
			target = this.tankIn;
		} else {
			// 创造模式的无限桶类型为 null（任意流体），输入罐还没定型时先喂冷却剂罐
			target = this.coolantCold;
		}

		if(type != null) target.setTankType(type);
		target.setFill(target.getMaxFill());
		this.markDirty();
	}

	private void fillOutputCanisters() {

		for(int i = 0; i < 4; i++) {
			ItemStack empty = this.slots[SLOT_FLUID_CAN + i];
			ItemStack out = this.slots[SLOT_FILLED + i];
			if(empty == null) continue;

			boolean lead = empty.getItem() == ModItems.fluid_tank_lead_empty;
			boolean normal = empty.getItem() == ModItems.fluid_tank_empty;
			boolean barrel = empty.getItem() == ModItems.fluid_barrel_empty;
			if(!lead && !normal && !barrel) continue;

			Item fullType = lead ? ModItems.fluid_tank_lead_full
					: normal ? ModItems.fluid_tank_full : ModItems.fluid_barrel_full;

			// 槽位 i 严格对应输出罐 i（与四个输出面的方向绑定一致）：
			// 原先是「从任意够量的罐里取第一个」，导致四个槽都只灌到罐0的流体。
			FluidTank source = this.tanksOut[i];
			if(source.getTankType() == Fluids.NONE || source.getFill() < CAN_AMOUNT) continue;

			if(out != null && (out.getItem() != fullType || out.getItemDamage() != source.getTankType().getID())) continue;
			if(out != null && out.stackSize >= out.getMaxStackSize()) continue;

			source.setFill(source.getFill() - CAN_AMOUNT);
			empty.stackSize--;
			if(empty.stackSize <= 0) this.slots[SLOT_FLUID_CAN + i] = null;

			if(out == null) {
				this.slots[SLOT_FILLED + i] = new ItemStack(fullType, 1, source.getTankType().getID());
			} else {
				out.stackSize++;
			}

			this.markDirty();
			// 不再 return：四个槽各对应各自的罐，应当同刻并行灌装
		}
	}

	private void handleCoolant() {

		// 冷四氟甲烷不足时尝试从输入罐/容器补充不现实，这里只做冷->热转换
		if(this.coolantCold.getFill() <= 0 && this.coolantHot.getFill() > 0) {
			// 没有冷却剂就停机
			this.activeA = false;
			this.activeB = false;
			this.activeF = false;
		}
	}

	private void chargeBattery() {

		// 统一走本体的电池放电逻辑，ItemBattery / 自充电池 / 电池包都能用
		this.power = Library.chargeTEFromItems(this.slots, SLOT_BATTERY, this.power, MAX_POWER);
	}

	public int getSpeedLevel() {

		return getUpgradeLevel(ModItems.upgrade_speed_1, ModItems.upgrade_speed_2, ModItems.upgrade_speed_3);
	}

	public int getOverdriveLevel() {

		return getUpgradeLevel(ModItems.upgrade_overdrive_1, ModItems.upgrade_overdrive_2, ModItems.upgrade_overdrive_3);
	}

	/** 和原版一样按等级累加（1 级 + 2 级 = 3 级），同类型封顶 3 级 */
	private int getUpgradeLevel(Item tier1, Item tier2, Item tier3) {

		int level = 0;

		for(int i = SLOT_UPGRADE; i < SLOT_UPGRADE + 2; i++) {
			ItemStack stack = this.slots[i];
			if(stack == null) continue;
			if(stack.getItem() == tier1) level += 1;
			else if(stack.getItem() == tier2) level += 2;
			else if(stack.getItem() == tier3) level += 3;
		}

		return Math.min(3, level);
	}

	public int getDuration() {
		double speed = (1D + 0.25D * getSpeedLevel()) * Math.pow(2D, getOverdriveLevel());
		return Math.max(20, (int) Math.ceil(BASE_DURATION / speed));
	}

	public long getPowerPerTick() {

		int speed = getSpeedLevel();
		int over = getOverdriveLevel();
		double mul = (1D + 0.5D * speed) * Math.pow(4D, over);
		// 所有配方统一 10,000 HE/t，物品线与流体线同价
		return (long) Math.max(1, 10_000D * mul);
	}

	/** 设定的并行份数：1 / 2 / 4 / 8 */
	public int getParallel() {
		return 1 << Math.max(0, Math.min(3, this.parallelMode));
	}

	/* ---------------- GUI 控制（并行倍率滑块） ---------------- */

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {

		if(!data.hasKey("parallelMode")) return;

		int mode = data.getByte("parallelMode");
		this.parallelMode = mode < 0 ? 0 : (mode > 3 ? 3 : mode);
		this.markDirty();
	}

	/** 冷却剂消耗：基础 3 mB/t，速度升级 +50%/级，超频每级再翻倍 */
	public int getCoolantUse() {

		double mul = (1D + 0.5D * getSpeedLevel()) * Math.pow(2D, getOverdriveLevel());
		return (int) Math.max(1, Math.round(BASE_COOLANT * mul));
	}

	/* ---------------- 升级悬浮说明 ---------------- */

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {

		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.large_centrifuge));

		if(type == UpgradeType.SPEED) {
			// 处理时间 ×1/(1+0.25L)，折算成速度 +25%/级；耗电与冷却剂各 +50%/级
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_SPEED, "+" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_CONSUMPTION, "+" + (level * 50) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_COOLANT_CONSUMPTION, "+" + (level * 50) + "%"));
		}

		if(type == UpgradeType.OVERDRIVE) {
			// 和本体一致：超速只显示闪烁的 YES
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public HashMap<UpgradeType, Integer> getValidUpgrades() {

		HashMap<UpgradeType, Integer> upgrades = new HashMap<UpgradeType, Integer>();
		upgrades.put(UpgradeType.SPEED, 3);
		upgrades.put(UpgradeType.OVERDRIVE, 3);
		return upgrades;
	}

	/** 当前进度（0~1），给 GUI 动画用：line 2 是流体线，其余都取物品阶段 */
	public float getProgress(int line) {

		int progress = line == 2 ? this.progressF : this.progressA;
		return Math.min(1F, progress / (float) getDuration());
	}

	/** 刷新动画进度：只有产物里真的含物品/流体的配方才点亮对应动画 */
	private void updateAnimations() {

		CentrifugeRecipe a = this.activeA ? LargeCentrifugeRecipes.forItem(this.slots[SLOT_IN_A]) : null;
		CentrifugeRecipe b = this.activeB ? LargeCentrifugeRecipes.forItem(this.slots[SLOT_IN_B]) : null;
		CentrifugeRecipe f = this.activeF ? LargeCentrifugeRecipes.forFluid(
				this.tankIn.getTankType(), this.tankIn.getFill(), this.tankIn.getPressure()) : null;

		// 物品阶段两个配方共用一条进度，同进同出，动画不会再错位
		float stageItems = Math.max(animProgress(a, this.progressA, true), animProgress(b, this.progressA, true));
		float stageFluids = Math.max(animProgress(a, this.progressA, false), animProgress(b, this.progressA, false));

		this.animItems = Math.max(stageItems, animProgress(f, this.progressF, true));
		this.animFluids = Math.max(stageFluids, animProgress(f, this.progressF, false));
	}

	private float animProgress(CentrifugeRecipe recipe, int progress, boolean wantItems) {

		if(recipe == null) return 0F;
		if(wantItems ? recipe.outputItems.isEmpty() : recipe.outputFluids.isEmpty()) return 0F;

		int duration = getDuration();
		if(duration <= 0) return 0F;

		return Math.min(1F, progress / (float) duration);
	}

	/* ---------------- 渲染 ---------------- */

	/** 模型比单个方块大，必须扩大渲染包围盒，否则核心格一出视锥模型就整个消失 */
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 4, zCoord + 3);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	/* ---------------- 接口 ---------------- */

	/** 12 个接口方块的世界坐标，连接方向朝机器外 */
	protected PortDef[] cachedPorts;

	public PortDef[] getPorts() {

		if(cachedPorts == null) {
			cachedPorts = new PortDef[] {
				// +X 面
				PortDef.make(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				PortDef.make(xCoord + 2, yCoord, zCoord, Library.POS_X),
				PortDef.make(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				// -X 面
				PortDef.make(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				PortDef.make(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				PortDef.make(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
				// +Z 面
				PortDef.make(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				PortDef.make(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				PortDef.make(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				// -Z 面
				PortDef.make(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z),
				PortDef.make(xCoord, yCoord, zCoord - 2, Library.NEG_Z),
				PortDef.make(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
			};
		}

		return cachedPorts;
	}

	@Override
	public void setPower(long power) { this.power = power; }

	@Override
	public long getPower() { return this.power; }

	@Override
	public long getMaxPower() { return MAX_POWER; }

	/**
	 * 预设进料罐压力（必须在 {@code setupAllPorts} 之前调用）。
	 *
	 * <p><b>为什么不能等匹配到配方再设压</b>：HBM 收料时
	 * {@code IFluidStandardReceiverMK2.getDemand} 要求罐子的<b>类型和压力都匹配</b>。
	 * 若罐子还是 0PU，2PU 的流体根本进不来 → 罐子永远空、类型永远 NONE →
	 * {@code forFluid} 永远匹配不到配方 → 也就永远没机会设压。这是个死锁，
	 * 表现为「熔融地核泵进离心机却显示没有配方」。</p>
	 *
	 * <p>所以改成预先声明：罐里已有流体就按该流体的配方定压；罐子空着时，
	 * 若全表只有一种流体要求带压（当前就是地核熔体 2PU），直接预定成那个 PU，
	 * 让第一种带压流体能顺利进来。</p>
	 */
	private void presetInputPressure() {

		FluidType type = this.tankIn.getTankType();

		// 罐里已有流体：按吃这种流体的配方定压。
		// withPressure 换压会清空存量（宿主语义），罐里有货时绝不能动——
		// 带压流体进罐已由 transferFluid 按来料定压，这里只处理空罐
		if(type != null && type != Fluids.NONE) {
			if(this.tankIn.getFill() > 0) return;
			for(CentrifugeRecipe rec : LargeCentrifugeRecipes.RECIPES) {
				if(rec.inputFluid == null || rec.inputFluid.type != type) continue;
				if(this.tankIn.getPressure() != rec.inputFluid.pressure) {
					this.tankIn.withPressure(rec.inputFluid.pressure);
				}
				return;
			}
			return;
		}

		// 空罐保持 0 PU：早先这里把空罐预定成全表唯一的带压值（2 PU），
		// 导致没有流体配方时罐提示也恒显「Pressure: 2 PU / Pressurized」。
		// 带压流体的进入改由 getDemand/transferFluid 对空罐放宽压力匹配、
		// 进液前按来料定压解决（见下方覆写），不再靠空罐预设定压。
		if(this.tankIn.getPressure() != 0) {
			this.tankIn.withPressure(0);
		}
	}

	/** 空罐是否可放宽压力匹配接收该流体（类型一致、罐空、0 PU）。 */
	private boolean emptyAccepts(FluidType type) {
		return this.tankIn.getFill() == 0
				&& this.tankIn.getPressure() == 0
				&& this.tankIn.getTankType() == type;
	}

	@Override
	public long getDemand(FluidType type, int pressure) {
		long amount = IFluidStandardTransceiverMK2.super.getDemand(type, pressure);
		// 空罐0压且来料也是0压时，super已按精确匹配计过一次需求，不能再叠加（否则翻倍）
		if(emptyAccepts(type) && pressure != 0) amount += this.tankIn.getMaxFill() - this.tankIn.getFill();
		return amount;
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long amount) {
		// 空罐放宽匹配：先按来料压力定压（空罐 withPressure 不会清掉已有流体），
		// 再走标准分配，避免「0 PU 空罐拒收带压流体」的死锁。
		if(amount > 0 && emptyAccepts(type)) {
			this.tankIn.withPressure(pressure);
		}
		return IFluidStandardTransceiverMK2.super.transferFluid(type, pressure, amount);
	}

	@Override
	public int[] getReceivingPressureRange(FluidType type) {
		if(emptyAccepts(type)) return new int[] { 0, HIGHEST_VALID_PRESSURE };
		return IFluidStandardTransceiverMK2.super.getReceivingPressureRange(type);
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { this.tankIn, this.coolantCold };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { this.tanksOut[0], this.tanksOut[1], this.tanksOut[2], this.tanksOut[3], this.coolantHot };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { this.tankIn, this.tanksOut[0], this.tanksOut[1], this.tanksOut[2], this.tanksOut[3], this.coolantCold, this.coolantHot };
	}

	/**
	 * 外部接口一律通用：12 个端口任意一个都能进料、出料、走冷却剂。
	 *
	 * <p>这里<b>故意不重载</b> {@code canConnect}，直接用 {@code IFluidConnectorMK2}
	 * 的默认实现（只排除 UNKNOWN）。HBM 的流体网络本身按流体类型寻址，
	 * {@code getFluidAvailable} 会遍历 getSendingTanks() 把同类型罐的量加起来，
	 * 所以管道要哪种流体就能从任意口取到哪种，不需要再按面区分。</p>
	 *
	 * <p>历史版本曾把四个输出罐绑到北/东/南/西四面（仿原版真空蒸馏塔），
	 * 玩家不看源码接不对管道，已按要求改为全接口通用。<b>不要再加回方向绑定。</b>
	 * GUI 里 13~16 号空罐槽与 tanksOut[0..3] 的对应关系是内部显示，与此无关。</p>
	 */

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
		buf.writeInt(this.progressA);
		buf.writeInt(this.progressF);
		buf.writeBoolean(this.isOn);
		buf.writeInt(this.tankIn.getFill());
		buf.writeInt(this.tankIn.getTankType().getID());
		// 压力必须同步，否则客户端恒为 0，罐提示里不会出现「Pressure: n PU」
		buf.writeInt(this.tankIn.getPressure());
		for(FluidTank tank : this.tanksOut) {
			buf.writeInt(tank.getFill());
			buf.writeInt(tank.getTankType().getID());
		}
		buf.writeInt(this.coolantCold.getFill());
		buf.writeInt(this.coolantHot.getFill());
		buf.writeFloat(this.animItems);
		buf.writeFloat(this.animFluids);
		buf.writeByte(this.parallelMode);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
		this.progressA = buf.readInt();
		this.progressF = buf.readInt();
		this.isOn = buf.readBoolean();
		this.tankIn.setFill(buf.readInt());
		this.tankIn.setTankType(Fluids.fromID(buf.readInt()));
		this.tankIn.withPressure(buf.readInt());
		for(FluidTank tank : this.tanksOut) {
			tank.setFill(buf.readInt());
			tank.setTankType(Fluids.fromID(buf.readInt()));
		}
		this.coolantCold.setFill(buf.readInt());
		this.coolantHot.setFill(buf.readInt());
		this.animItems = buf.readFloat();
		this.animFluids = buf.readFloat();
		this.parallelMode = buf.readByte();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", this.power);
		nbt.setInteger("progressA", this.progressA);
		nbt.setInteger("progressF", this.progressF);
		nbt.setByte("parallelMode", (byte) this.parallelMode);
		this.tankIn.writeToNBT(nbt, "tankIn");
		for(int i = 0; i < 4; i++) this.tanksOut[i].writeToNBT(nbt, "tankOut" + i);
		this.coolantCold.writeToNBT(nbt, "coolCold");
		this.coolantHot.writeToNBT(nbt, "coolHot");

		NBTTagList list = new NBTTagList();
		for(int i = 0; i < INV_SIZE; i++) {
			if(this.slots[i] == null) continue;
			NBTTagCompound tag = new NBTTagCompound();
			tag.setByte("slot", (byte) i);
			this.slots[i].writeToNBT(tag);
			list.appendTag(tag);
		}
		nbt.setTag("items", list);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.power = nbt.getLong("power");
		this.progressA = nbt.getInteger("progressA");
		this.progressF = nbt.getInteger("progressF");
		this.parallelMode = nbt.getByte("parallelMode");
		this.tankIn.readFromNBT(nbt, "tankIn");
		for(int i = 0; i < 4; i++) this.tanksOut[i].readFromNBT(nbt, "tankOut" + i);
		this.coolantCold.readFromNBT(nbt, "coolCold");
		this.coolantHot.readFromNBT(nbt, "coolHot");

		NBTTagList list = nbt.getTagList("items", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			int slot = tag.getByte("slot") & 255;
			if(slot < INV_SIZE) this.slots[slot] = ItemStack.loadItemStackFromNBT(tag);
		}
	}

	/* ---------------- 基础容器方法 ---------------- */

	public int getSizeInventory() { return INV_SIZE; }
	public ItemStack getStackInSlot(int slot) { return this.slots[slot]; }

	public ItemStack decrStackSize(int slot, int amount) {

		if(this.slots[slot] == null) return null;

		if(this.slots[slot].stackSize <= amount) {
			ItemStack stack = this.slots[slot];
			this.slots[slot] = null;
			return stack;
		}

		ItemStack stack = this.slots[slot].splitStack(amount);
		if(this.slots[slot].stackSize <= 0) this.slots[slot] = null;
		return stack;
	}

	public ItemStack getStackInSlotOnClosing(int slot) {

		if(this.slots[slot] == null) return null;

		ItemStack stack = this.slots[slot];
		this.slots[slot] = null;
		return stack;
	}

	public void setInventorySlotContents(int slot, ItemStack stack) {
		this.slots[slot] = stack;
		if(stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
	}

	public String getInventoryName() { return "container.large_centrifuge"; }
	public boolean hasCustomInventoryName() { return false; }
	public int getInventoryStackLimit() { return 64; }
	public boolean isUseableByPlayer(EntityPlayer player) { return true; }
	public void openInventory() { }
	public void closeInventory() { }

	public boolean isItemValidForSlot(int slot, ItemStack stack) {

		if(stack == null) return false;

		if(slot == SLOT_CAN_IN) return stack.getItem() == ModItems.fluid_tank_lead_full || stack.getItem() == ModItems.fluid_tank_full
				|| stack.getItem() == ModItems.fluid_barrel_full || stack.getItem() instanceof ItemInfiniteFluid;
		if(slot == SLOT_CAN_OUT || (slot >= SLOT_FILLED && slot < SLOT_FILLED + 4)) return false;
		if(slot >= SLOT_FLUID_CAN && slot < SLOT_FLUID_CAN + 4) {
			return stack.getItem() == ModItems.fluid_tank_lead_empty || stack.getItem() == ModItems.fluid_tank_empty
					|| stack.getItem() == ModItems.fluid_barrel_empty;
		}
		if(slot >= SLOT_OUT && slot < SLOT_OUT + 8) return false;
		if(slot == SLOT_FLUID_ID) return stack.getItem() instanceof IItemFluidIdentifier;
		if(slot == SLOT_BATTERY) return stack.getItem() instanceof IBatteryItem;
		if(slot >= SLOT_UPGRADE && slot < SLOT_UPGRADE + 2) {
			return stack.getItem() == ModItems.upgrade_speed_1 || stack.getItem() == ModItems.upgrade_speed_2
				|| stack.getItem() == ModItems.upgrade_speed_3 || stack.getItem() == ModItems.upgrade_overdrive_1
				|| stack.getItem() == ModItems.upgrade_overdrive_2 || stack.getItem() == ModItems.upgrade_overdrive_3;
		}
		return stack != null && (slot == SLOT_IN_A || slot == SLOT_IN_B) && LargeCentrifugeRecipes.forItem(stack) != null;
	}

	/* ---------------- 物流（ISidedInventory） ---------------- */

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		int[] slots = new int[INV_SIZE];
		for(int i = 0; i < INV_SIZE; i++) slots[i] = i;
		return slots;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return isItemValidForSlot(slot, stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		// 只允许物流从产出侧抽走成品、空罐和灌满的罐
		if(slot == SLOT_CAN_OUT) return true;
		if(slot >= SLOT_OUT && slot < SLOT_OUT + 8) return true;
		if(slot >= SLOT_FILLED && slot < SLOT_FILLED + 4) return true;
		return false;
	}
}


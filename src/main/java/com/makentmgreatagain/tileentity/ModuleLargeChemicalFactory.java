package com.makentmgreatagain.tileentity;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.module.machine.ModuleMachineBase;
import com.hbm.util.InventoryUtil;
import com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes;
import com.makentmgreatagain.recipes.MergedRecipe;
import com.makentmgreatagain.recipes.SolidFuelRecipe;

import api.hbm.energymk2.IEnergyHandlerMK2;
import net.minecraft.item.ItemStack;

/**
 * 大型催化化工厂的配方模块。
 *
 * <p>加工流程（供电判定、进度推进、扣料、产出）完全沿用本体
 * {@link ModuleMachineBase} 与 {@link com.hbm.module.machine.ModuleMachineChemplant}，
 * 配方表也是本体那份 {@link ChemicalPlantRecipes}。</p>
 *
 * <h3>自动分组</h3>
 * <p>机器固定有 4 组输入 / 4 组输出 / 4 个输入罐 / 4 个输出罐。实际占用多少组，
 * 由配方里该类别有几种原料决定（{@link #spanFor(int)}）：</p>
 * <ul>
 *   <li>4 种 → 每种 1 组；</li>
 *   <li>2 种 → 每种 2 组，一种满了自动溢到同种的另一组；</li>
 *   <li>1 种 → 4 组全部归它；</li>
 *   <li>3 种（4 不能整除）→ 保持每种 1 组，第 4 组空置。</li>
 * </ul>
 * <p>流体同理，例如单流体配方会把 4 个罐全部算作同一种流体。</p>
 */
public class ModuleLargeChemicalFactory extends ModuleMachineBase {

	/** 输入/输出的“类型”数量 */
	public static final int GROUPS = 4;
	/** 每个输入类型的槽位数 */
	public static final int INPUT_SLOTS = 4;
	/** 每个输出类型的槽位数 */
	public static final int OUTPUT_SLOTS = 2;

	/** 当前这一轮一次性处理几份配方：1 / 2 / 4 / 8 */
	public int batches = 1;

	private final int baseInput;
	private final int baseOutput;

	public ModuleLargeChemicalFactory(int index, IEnergyHandlerMK2 battery, ItemStack[] slots,
			FluidTank[] inputTanks, FluidTank[] outputTanks, int inputBase, int outputBase) {

		super(index, battery, slots);

		this.baseInput = inputBase;
		this.baseOutput = outputBase;

		this.inputSlots = new int[GROUPS];
		this.outputSlots = new int[GROUPS];

		for(int i = 0; i < GROUPS; i++) {
			this.inputSlots[i] = inputBase + i * INPUT_SLOTS;
			this.outputSlots[i] = outputBase + i * OUTPUT_SLOTS;
		}

		this.inputTanks = inputTanks;
		this.outputTanks = outputTanks;

		this.sfFuel = new int[inputTanks.length];
		this.sfAsh = new int[inputTanks.length];
	}

	/**
	 * 4 组能被 {@code kindCount} 整除时，每种原料占 {@code 4 / kindCount} 组；
	 * 否则退回每种 1 组。
	 */
	public static int spanFor(int kindCount) {
		if(kindCount <= 0 || kindCount > GROUPS) return 1;
		if(GROUPS % kindCount != 0) return 1;
		return GROUPS / kindCount;
	}

	/**
	 * 流体专用分组：按<b>实际罐数</b>分，而不是物品的 {@link #GROUPS}。
	 *
	 * <p>FF 模式 4 个罐、MF 模式 8 个罐，物品槽始终只有 4 组，两者必须解耦，
	 * 否则 MF 下多出来的 4 个罐永远分不到流体。</p>
	 */
	private int fluidSpanFor(int kindCount, int tankCount) {
		int active = activeTankCount(tankCount);
		if(kindCount <= 0 || kindCount > active) return 1;
		if(active % kindCount != 0) return 1;
		return active / kindCount;
	}

	/** 当前模式实际启用的罐数：FF 只用前 4 个，MF 用满 8 个 */
	private int activeTankCount(int arrayLength) {
		if(this.battery instanceof TileEntityLargeCatalyticChemicalFactory) {
			return Math.min(arrayLength, ((TileEntityLargeCatalyticChemicalFactory) this.battery).activeTanks());
		}
		return arrayLength;
	}

	/** 宿主 TE（非本机器时返回 null） */
	private TileEntityLargeCatalyticChemicalFactory host() {
		return this.battery instanceof TileEntityLargeCatalyticChemicalFactory
				? (TileEntityLargeCatalyticChemicalFactory) this.battery : null;
	}

	/** 是否处于 MF（多配方）模式 */
	private boolean isMF() {
		TileEntityLargeCatalyticChemicalFactory host = host();
		return host != null && host.multiRecipeMode;
	}

	/** MF 下输入物品可用的槽位总数：16 格全部独立，不再分 4 组 */
	private int mfInputSlotCount() {
		return TileEntityLargeCatalyticChemicalFactory.INPUT_TYPES
				* TileEntityLargeCatalyticChemicalFactory.INPUT_SLOTS;
	}

	/** MF 下输出物品可用的槽位总数：8 格全部独立 */
	private int mfOutputSlotCount() {
		return TileEntityLargeCatalyticChemicalFactory.OUTPUT_TYPES
				* TileEntityLargeCatalyticChemicalFactory.OUTPUT_SLOTS;
	}

	@Override
	public GenericRecipes getRecipeSet() {
		return LargeChemicalFactoryRecipes.get();
	}

	/** 老存档里选中的逐条固体燃料配方名，自动映射到合并配方 */
	@Override
	public GenericRecipe getRecipe() {

		// MF：把已选的多个配方合成一个虚拟配方，沿用本模块全部处理逻辑
		TileEntityLargeCatalyticChemicalFactory host = host();
		if(host != null && host.multiRecipeMode) {
			return MergedRecipe.merge(getRecipeSet(), host.multiSelections);
		}

		GenericRecipe recipe = super.getRecipe();
		if(recipe == null) {
			String mapped = LargeChemicalFactoryRecipes.get().resolveSelection(this.recipe);
			if(!mapped.equals(this.recipe)) {
				this.recipe = mapped;
				recipe = super.getRecipe();
			}
		}
		return recipe;
	}

	/** 每次跑之前按当前配方的种类数刷新“每种占几组”，GUI 的幽灵物品也靠这个定位 */
	@Override
	public void update(double speed, double power, boolean extraCondition, ItemStack blueprint) {

		GenericRecipe recipe = getRecipe();

		if(isMF()) {
			updateMFSlots(recipe);
			super.update(speed, power, extraCondition, blueprint);
			return;
		}

		int kinds = recipe != null && recipe.inputItem != null ? recipe.inputItem.length : 0;
		int itemSpan = spanFor(kinds);

		for(int i = 0; i < GROUPS; i++) {
			this.inputSlots[i] = baseInput + i * INPUT_SLOTS;
			this.outputSlots[i] = baseOutput + i * OUTPUT_SLOTS;
		}

		// 幽灵物品只画在每种原料的起始槽上
		for(int kind = 0; kind < kinds && kind < GROUPS; kind++) {
			this.inputSlots[kind] = baseInput + kind * itemSpan * INPUT_SLOTS;
		}

		super.update(speed, power, extraCondition, blueprint);
	}

	/**
	 * MF 槽位映射：16 个输入槽、8 个输出槽<b>全部独立</b>。
	 *
	 * <p>幽灵物品按种类把 16 格均分成连续片区，<b>片区内每一格都画该原料的图标</b>，
	 * 所以整片 16 格都会闪烁。比如 3 种原料 → 5/5/6 格；16 种 → 每格一种。</p>
	 */
	private void updateMFSlots(GenericRecipe recipe) {

		int inCount = mfInputSlotCount();
		int outCount = mfOutputSlotCount();

		this.inputSlots = new int[inCount];
		this.outputSlots = new int[outCount];

		int kinds = recipe != null && recipe.inputItem != null ? recipe.inputItem.length : 0;

		for(int slot = 0; slot < inCount; slot++) {
			// 与收料校验、幽灵显示共用同一个分片公式，保证三者一致
			int kind = TileEntityLargeCatalyticChemicalFactory.mfKindOfSlot(slot, kinds);
			this.inputSlots[slot] = baseInput + slot;
			this.ghostKind[slot] = Math.max(0, kind);
		}

		for(int slot = 0; slot < outCount; slot++) {
			this.outputSlots[slot] = baseOutput + slot;
		}
	}

	/** MF 下每个输入格归属的原料序号，供 GUI 逐格画幽灵物品 */
	public final int[] ghostKind = new int[16];

	/* ============================== 输入 ============================== */

	@Override
	protected boolean hasInput(GenericRecipe recipe) {

		if(isMF()) return hasInputMF(recipe);

		if(recipe instanceof SolidFuelRecipe) return hasInputSolidFuel((SolidFuelRecipe) recipe);

		if(!supportsBatches(recipe, 1)) {
			this.batches = 1;
			return false;
		}

		// 原料足够就尽量并行，上限随模式：高并行 64x / 多配方 16x
		int cap = this.battery instanceof TileEntityLargeCatalyticChemicalFactory
				? ((TileEntityLargeCatalyticChemicalFactory) this.battery).maxBatches()
				: 64;

		int byInput = 1;
		for(int m = cap; m >= 2; m /= 2) {
			if(supportsBatches(recipe, m)) {
				byInput = m;
				break;
			}
		}

		// 并行份数还要受输出容量限制，装不下就自动降档
		int byOutput = maxOutputBatches(recipe);

		this.batches = Math.max(1, Math.min(byInput, byOutput));

		return true;
	}

	/** 输出槽/罐最多能装下几份配方，用来限制并行份数 */
	protected int maxOutputBatches(GenericRecipe recipe) {

		int limit = this.battery instanceof TileEntityLargeCatalyticChemicalFactory
				? ((TileEntityLargeCatalyticChemicalFactory) this.battery).maxBatches()
				: 64;

		if(recipe.outputItem != null && recipe.outputItem.length > 0) {
			int n = recipe.outputItem.length;
			int span = spanFor(n);

			for(int kind = 0; kind < n; kind++) {
				IOutput output = recipe.outputItem[kind];
				if(output == null) continue;

				int start = baseOutput + kind * span * OUTPUT_SLOTS;

				if(output.possibleMultiOutput()) {
					limit = Math.min(limit, emptySlotsInSpan(start, span));
					continue;
				}

				ItemStack single = output.getSingle();
				if(single == null) continue;

				int per = Math.max(1, single.stackSize);
				limit = Math.min(limit, capacityFor(start, span, single) / per);
			}
		}

		if(recipe.outputFluid != null && recipe.outputFluid.length > 0) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);

			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null || recipe.outputFluid[kind].fill <= 0) continue;

				int space = 0;
				for(int t = kind * span; t < (kind + 1) * span; t++) {
					space += outputTanks[t].getMaxFill() - outputTanks[t].getFill();
				}
				limit = Math.min(limit, space / recipe.outputFluid[kind].fill);
			}
		}

		return limit;
	}

	private int emptySlotsInSpan(int start, int span) {
		int empty = 0;
		int end = start + span * OUTPUT_SLOTS;
		for(int slot = start; slot < end; slot++) {
			if(slots[slot] == null) empty++;
		}
		return empty;
	}

	/** 当前原料是否够 {@code batches} 份 */
	protected boolean supportsBatches(GenericRecipe recipe, int multiplier) {

		if(recipe.inputItem != null && recipe.inputItem.length > 0) {
			int n = recipe.inputItem.length;
			int span = spanFor(n);

			for(int kind = 0; kind < n; kind++) {
				AStack ingredient = recipe.inputItem[kind];
				if(ingredient == null) continue;

				AStack scaled = ingredient.copy();
				scaled.stacksize = ingredient.stacksize * multiplier;

				int start = baseInput + kind * span * INPUT_SLOTS;
				if(!InventoryUtil.doesArrayHaveIngredients(slots, start, start + span * INPUT_SLOTS, scaled)) {
					return false;
				}
			}
		}

		if(recipe.inputFluid != null && recipe.inputFluid.length > 0) {
			int n = recipe.inputFluid.length;
			int span = fluidSpanFor(n, inputTanks.length);

			for(int kind = 0; kind < n; kind++) {
				if(recipe.inputFluid[kind] == null) continue;
				if(fluidAmount(kind, span) < recipe.inputFluid[kind].fill * multiplier) return false;
			}
		}

		return true;
	}

	@Override
	protected void consumeInput(GenericRecipe recipe) {

		if(isMF()) { consumeInputMF(recipe); return; }

		if(recipe instanceof SolidFuelRecipe) {
			consumeInputSolidFuel((SolidFuelRecipe) recipe);
			return;
		}

		if(recipe.inputItem != null && recipe.inputItem.length > 0) {
			int n = recipe.inputItem.length;
			int span = spanFor(n);

			for(int kind = 0; kind < n; kind++) {
				AStack ingredient = recipe.inputItem[kind];
				if(ingredient == null) continue;

				int remaining = ingredient.stacksize * batches;
				int start = baseInput + kind * span * INPUT_SLOTS;
				int end = start + span * INPUT_SLOTS;

				for(int slot = start; slot < end && remaining > 0; slot++) {
					ItemStack stack = slots[slot];
					if(stack == null || !ingredient.matchesRecipe(stack, true)) continue;

					int take = Math.min(remaining, stack.stackSize);
					stack.stackSize -= take;
					remaining -= take;
					if(stack.stackSize <= 0) slots[slot] = null;
				}
			}
		}

		if(recipe.inputFluid != null && recipe.inputFluid.length > 0) {
			int n = recipe.inputFluid.length;
			int span = fluidSpanFor(n, inputTanks.length);

			for(int kind = 0; kind < n; kind++) {
				if(recipe.inputFluid[kind] == null) continue;

				int remaining = recipe.inputFluid[kind].fill * batches;
				for(int t = kind * span; t < (kind + 1) * span && remaining > 0; t++) {
					int take = Math.min(remaining, inputTanks[t].getFill());
					inputTanks[t].setFill(inputTanks[t].getFill() - take);
					remaining -= take;
				}
			}
		}
	}

	/* ============================== 输出 ============================== */

	/** 给核心 TE 判断黄灯用的公开入口 */
	public boolean canFitOutputPublic(GenericRecipe recipe) {
		return canFitOutput(recipe);
	}

	@Override
	protected boolean canFitOutput(GenericRecipe recipe) {

		if(isMF()) return canFitOutputMF(recipe);

		if(recipe instanceof SolidFuelRecipe) return canFitOutputSolidFuel();

		if(recipe.outputItem != null && recipe.outputItem.length > 0) {
			int n = recipe.outputItem.length;
			int span = spanFor(n);

			for(int kind = 0; kind < n; kind++) {
				IOutput output = recipe.outputItem[kind];
				if(output == null) continue;

				int start = baseOutput + kind * span * OUTPUT_SLOTS;

				if(output.possibleMultiOutput()) {
					if(!hasEmptySlotInSpan(start, span)) return false;
					continue;
				}

				ItemStack single = output.getSingle();
				if(single == null) continue;
				if(capacityFor(start, span, single) < single.stackSize * batches) return false;
			}
		}

		if(recipe.outputFluid != null && recipe.outputFluid.length > 0) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);

			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null) continue;

				int space = 0;
				for(int t = kind * span; t < (kind + 1) * span; t++) {
					space += outputTanks[t].getMaxFill() - outputTanks[t].getFill();
				}
				if(space < recipe.outputFluid[kind].fill * batches) return false;
			}
		}

		return true;
	}

	@Override
	protected void produceItem(GenericRecipe recipe) {

		if(isMF()) { produceItemMF(recipe); return; }

		if(recipe instanceof SolidFuelRecipe) {
			produceItemSolidFuel();
			return;
		}

		if(recipe.outputItem != null && recipe.outputItem.length > 0) {
			int n = recipe.outputItem.length;
			int span = spanFor(n);

			for(int kind = 0; kind < n; kind++) {
				IOutput output = recipe.outputItem[kind];
				if(output == null) continue;

				ItemStack stack = output.collapse();
				if(stack == null) continue;

				int start = baseOutput + kind * span * OUTPUT_SLOTS;
				int remaining = stack.stackSize * batches;
				// 按物品自身的堆叠上限切块，堆叠上限 1 的物品一个槽只放一个
				int max = Math.min(stack.getMaxStackSize(), 64);

				while(remaining > 0) {
					ItemStack chunk = stack.copy();
					chunk.stackSize = Math.min(remaining, max);

					int inserted = insertIntoSpan(start, span, chunk);
					if(inserted <= 0) break; // canFitOutput 已经挡过，这里兜底防死循环
					remaining -= inserted;
				}
			}
		}

		if(recipe.outputFluid != null && recipe.outputFluid.length > 0) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);

			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null) continue;

				int remaining = recipe.outputFluid[kind].fill * batches;
				for(int t = kind * span; t < (kind + 1) * span && remaining > 0; t++) {
					int add = Math.min(remaining, outputTanks[t].getMaxFill() - outputTanks[t].getFill());
					outputTanks[t].setFill(outputTanks[t].getFill() + add);
					remaining -= add;
				}
			}
		}

		this.markDirty = true;
	}

	/* ============================== MF（多配方）处理 ============================== */
	//
	// 与 FF 的区别：物品不分 4 组，16 个输入槽 / 8 个输出槽全部独立。
	// 每种原料在整个输入范围里找料，产物按「自上而下、从左到右」顺序塞进输出槽。
	// 流体按种类均分 8 个罐。任一输出装不下就整组停机。

	/** MF 输入判定：全有才跑，并按 16x 上限逐级降档 */
	private boolean hasInputMF(GenericRecipe recipe) {

		if(recipe == null) { this.batches = 1; return false; }

		if(!supportsMF(recipe, 1)) { this.batches = 1; return false; }

		int cap = host() != null ? host().maxBatches() : 16;
		int byInput = 1;
		for(int m = cap; m >= 2; m /= 2) {
			if(supportsMF(recipe, m)) { byInput = m; break; }
		}

		int byOutput = maxOutputBatchesMF(recipe);
		this.batches = Math.max(1, Math.min(byInput, byOutput));
		return true;
	}

	/** MF：整个输入槽范围里够不够 multiplier 份 */
	private boolean supportsMF(GenericRecipe recipe, int multiplier) {

		if(recipe.inputItem != null) {
			for(AStack in : recipe.inputItem) {
				if(in == null) continue;
				if(countItemMF(in) < (long) in.stacksize * multiplier) return false;
			}
		}

		if(recipe.inputFluid != null) {
			int n = recipe.inputFluid.length;
			int span = fluidSpanFor(n, inputTanks.length);
			for(int kind = 0; kind < n; kind++) {
				if(recipe.inputFluid[kind] == null) continue;
				if(fluidAmount(kind, span) < (long) recipe.inputFluid[kind].fill * multiplier) return false;
			}
		}

		return true;
	}

	/** MF：某种原料在全部 16 个输入槽里的总量 */
	private long countItemMF(AStack ingredient) {
		long total = 0;
		int count = mfInputSlotCount();
		for(int i = 0; i < count; i++) {
			ItemStack stack = slots[baseInput + i];
			if(stack == null || !ingredient.matchesRecipe(stack, true)) continue;
			total += stack.stackSize;
		}
		return total;
	}

	/** MF 扣料：按合并清单逐项在整个输入范围里扣，绝不回头匹配别的配方 */
	private void consumeInputMF(GenericRecipe recipe) {

		if(recipe.inputItem != null) {
			int count = mfInputSlotCount();
			for(AStack in : recipe.inputItem) {
				if(in == null) continue;
				int remaining = in.stacksize * batches;
				for(int i = 0; i < count && remaining > 0; i++) {
					ItemStack stack = slots[baseInput + i];
					if(stack == null || !in.matchesRecipe(stack, true)) continue;
					int take = Math.min(remaining, stack.stackSize);
					stack.stackSize -= take;
					remaining -= take;
					if(stack.stackSize <= 0) slots[baseInput + i] = null;
				}
			}
		}

		if(recipe.inputFluid != null) {
			int n = recipe.inputFluid.length;
			int span = fluidSpanFor(n, inputTanks.length);
			for(int kind = 0; kind < n; kind++) {
				if(recipe.inputFluid[kind] == null) continue;
				int remaining = recipe.inputFluid[kind].fill * batches;
				for(int t = kind * span; t < (kind + 1) * span && remaining > 0; t++) {
					int take = Math.min(remaining, inputTanks[t].getFill());
					inputTanks[t].setFill(inputTanks[t].getFill() - take);
					remaining -= take;
				}
			}
		}
	}

	/**
	 * MF 输出判定：8 个输出槽当作一片连续空间，按顺序装。
	 *
	 * <p>任一项装不下就返回 false（整组停机），与「任意一个物品/流体满了就不工作」一致。</p>
	 */
	private boolean canFitOutputMF(GenericRecipe recipe) {

		if(recipe.outputItem != null && recipe.outputItem.length > 0) {
			// 用一份槽位快照模拟顺序填充，避免真写进去
			int count = mfOutputSlotCount();
			ItemStack[] sim = new ItemStack[count];
			for(int i = 0; i < count; i++) {
				ItemStack s = slots[baseOutput + i];
				sim[i] = s == null ? null : s.copy();
			}

			for(IOutput out : recipe.outputItem) {
				if(out == null) continue;
				ItemStack single = out.getSingle();
				if(single == null) return false; // 概率/多产物在 MF 下不支持，直接判不通过
				int remaining = single.stackSize * batches;
				if(!insertSim(sim, single, remaining)) return false;
			}
		}

		if(recipe.outputFluid != null) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);
			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null) continue;
				int space = 0;
				for(int t = kind * span; t < (kind + 1) * span; t++) {
					space += outputTanks[t].getMaxFill() - outputTanks[t].getFill();
				}
				if(space < recipe.outputFluid[kind].fill * batches) return false;
			}
		}

		return true;
	}

	/** 往快照里按「先叠同类、再用空格」的顺序塞，塞不下返回 false */
	private boolean insertSim(ItemStack[] sim, ItemStack type, int amount) {
		int remaining = amount;
		int max = Math.min(type.getMaxStackSize(), 64);

		for(int i = 0; i < sim.length && remaining > 0; i++) {
			ItemStack s = sim[i];
			if(s == null) continue;
			if(s.getItem() != type.getItem() || s.getItemDamage() != type.getItemDamage()) continue;
			if(!ItemStack.areItemStackTagsEqual(s, type)) continue;
			int add = Math.min(remaining, max - s.stackSize);
			if(add <= 0) continue;
			s.stackSize += add;
			remaining -= add;
		}

		for(int i = 0; i < sim.length && remaining > 0; i++) {
			if(sim[i] != null) continue;
			int add = Math.min(remaining, max);
			ItemStack s = type.copy();
			s.stackSize = add;
			sim[i] = s;
			remaining -= add;
		}

		return remaining <= 0;
	}

	/** MF 输出容量最多支持几份 */
	private int maxOutputBatchesMF(GenericRecipe recipe) {

		int limit = host() != null ? host().maxBatches() : 16;

		// 物品：按总容量粗算，再由 canFitOutputMF 精确挡
		if(recipe.outputItem != null && recipe.outputItem.length > 0) {
			int count = mfOutputSlotCount();
			int free = 0;
			for(int i = 0; i < count; i++) {
				ItemStack s = slots[baseOutput + i];
				if(s == null) free += 64;
				else free += Math.max(0, Math.min(s.getMaxStackSize(), 64) - s.stackSize);
			}
			int need = 0;
			for(IOutput out : recipe.outputItem) {
				if(out == null) continue;
				ItemStack single = out.getSingle();
				if(single == null) return 0;
				need += single.stackSize;
			}
			if(need > 0) limit = Math.min(limit, free / need);
		}

		if(recipe.outputFluid != null && recipe.outputFluid.length > 0) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);
			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null || recipe.outputFluid[kind].fill <= 0) continue;
				int space = 0;
				for(int t = kind * span; t < (kind + 1) * span; t++) {
					space += outputTanks[t].getMaxFill() - outputTanks[t].getFill();
				}
				limit = Math.min(limit, space / recipe.outputFluid[kind].fill);
			}
		}

		return limit;
	}

	/** MF 产出：物品自上而下从左到右顺序填，流体按种类均分灌 */
	private void produceItemMF(GenericRecipe recipe) {

		if(recipe.outputItem != null) {
			int count = mfOutputSlotCount();
			for(IOutput out : recipe.outputItem) {
				if(out == null) continue;
				ItemStack single = out.getSingle();
				if(single == null) continue;

				int remaining = single.stackSize * batches;
				int max = Math.min(single.getMaxStackSize(), 64);

				// 先叠已有同类
				for(int i = 0; i < count && remaining > 0; i++) {
					ItemStack s = slots[baseOutput + i];
					if(s == null) continue;
					if(s.getItem() != single.getItem() || s.getItemDamage() != single.getItemDamage()) continue;
					if(!ItemStack.areItemStackTagsEqual(s, single)) continue;
					int add = Math.min(remaining, Math.min(s.getMaxStackSize(), 64) - s.stackSize);
					if(add <= 0) continue;
					s.stackSize += add;
					remaining -= add;
				}

				// 再按顺序用空格
				for(int i = 0; i < count && remaining > 0; i++) {
					if(slots[baseOutput + i] != null) continue;
					int add = Math.min(remaining, max);
					ItemStack s = single.copy();
					s.stackSize = add;
					slots[baseOutput + i] = s;
					remaining -= add;
				}
			}
		}

		if(recipe.outputFluid != null) {
			int n = recipe.outputFluid.length;
			int span = fluidSpanFor(n, outputTanks.length);
			for(int kind = 0; kind < n; kind++) {
				if(recipe.outputFluid[kind] == null) continue;
				int remaining = recipe.outputFluid[kind].fill * batches;
				for(int t = kind * span; t < (kind + 1) * span && remaining > 0; t++) {
					int add = Math.min(remaining, outputTanks[t].getMaxFill() - outputTanks[t].getFill());
					outputTanks[t].setFill(outputTanks[t].getFill() + add);
					remaining -= add;
				}
			}
		}

		this.markDirty = true;
	}

	/* ============================== 罐型 ============================== */

	/** 同一种流体占用的所有罐都要统一罐型，否则多出来的罐还是 NONE，接不进去 */
	@Override
	public void setupTanks(GenericRecipe recipe) {

		// 没有配方：全部复位，否则界面会一直显示上一轮的流体
		if(recipe == null) {
			for(FluidTank tank : inputTanks) tank.resetTank();
			for(FluidTank tank : outputTanks) tank.resetTank();
			return;
		}

		if(recipe instanceof SolidFuelRecipe) {
			// 合并配方：输入罐不锁罐型，保留各罐实际种类（空罐由流体接口按需认领）；
			// 这条配方没有流体产物，输出罐全部复位
			for(int t = 0; t < outputTanks.length; t++) outputTanks[t].resetTank();
			return;
		}

		int inN = recipe.inputFluid != null ? recipe.inputFluid.length : 0;
		int inSpan = fluidSpanFor(inN, inputTanks.length);

		for(int kind = 0; kind < inN; kind++) {
			for(int t = kind * inSpan; t < (kind + 1) * inSpan; t++) {
				if(recipe.inputFluid[kind] != null) inputTanks[t].conform(recipe.inputFluid[kind]);
				else inputTanks[t].resetTank();
			}
		}

		int outN = recipe.outputFluid != null ? recipe.outputFluid.length : 0;
		int outSpan = fluidSpanFor(outN, outputTanks.length);

		for(int kind = 0; kind < outN; kind++) {
			for(int t = kind * outSpan; t < (kind + 1) * outSpan; t++) {
				if(recipe.outputFluid[kind] != null) outputTanks[t].conform(recipe.outputFluid[kind]);
				else outputTanks[t].resetTank();
			}
		}

		// 新配方用不到的尾部罐也要复位：上一轮用了 4 种流体、这轮只用 2 种时，
		// 第 3、4 罐会留着旧罐型，存量虽空但界面仍显示旧流体。
		int inUsed = Math.min(inputTanks.length, inN * inSpan);
		for(int t = inUsed; t < inputTanks.length; t++) inputTanks[t].resetTank();

		int outUsed = Math.min(outputTanks.length, outN * outSpan);
		for(int t = outUsed; t < outputTanks.length; t++) outputTanks[t].resetTank();
	}

	/* ============================== 合并固体燃料配方 ============================== */

	/** 本轮各罐折算出的固体燃料数（按输入罐索引，长度随实际罐数：FF 4 / MF 8） */
	private final int[] sfFuel;
	/** 本轮各罐余料烧出的灰烬数（按输入罐索引） */
	private final int[] sfAsh;
	/** 本轮固体燃料总数 */
	private int sfTotalFuel;
	/** 本轮灰烬总数 */
	private int sfTotalAsh;

	/**
	 * 各罐独立折算：能产几块固体燃料就产几块，不够一块的余料自动烧成灰。
	 * 输出容量不够时按罐整份缩减本轮产量，绝不超塞。
	 */
	private boolean hasInputSolidFuel(SolidFuelRecipe recipe) {

		int totalFuel = 0;
		int totalAsh = 0;

		for(int t = 0; t < GROUPS; t++) {
			sfFuel[t] = 0;
			sfAsh[t] = 0;

			FluidTank tank = inputTanks[t];
			int per = recipe.perFuel(tank.getTankType());
			if(per <= 0 || tank.getFill() <= 0) continue;

			int fuel = tank.getFill() / per;
			int leftover = tank.getFill() - fuel * per;
			// 余料不够再产一块燃料时烧成灰；正好整除就不烧
			int ash = leftover > 0 ? 1 : 0;

			sfFuel[t] = fuel;
			sfAsh[t] = ash;
			totalFuel += fuel;
			totalAsh += ash;
		}

		if(totalFuel <= 0 && totalAsh <= 0) {
			this.batches = 1;
			return false;
		}

		// 装不下就按罐整份缩减（每罐要么全做要么不做），直到燃料 + 灰烬合起来装得下。
		//
		// 这里必须把燃料和灰烬放进同一批输出槽一起模拟：它们共用 baseOutput 起的那 8 格，
		// 若分别算「燃料装得下」和「灰烬装得下」，两个条件会同时成立、合起来却溢出，
		// 多出来的部分被 insertStackAllOutputs 的 break 静默丢掉（正是离心机那个坑）。
		while(!fitsSolidFuelOutput(totalFuel, totalAsh)) {
			int victim = -1;
			int best = 0;
			for(int t = 0; t < GROUPS; t++) {
				int units = sfFuel[t] + sfAsh[t];
				if(units > best) {
					best = units;
					victim = t;
				}
			}
			if(victim < 0) break;
			totalFuel -= sfFuel[victim];
			totalAsh -= sfAsh[victim];
			sfFuel[victim] = 0;
			sfAsh[victim] = 0;
		}

		if(totalFuel <= 0 && totalAsh <= 0) {
			this.batches = 1;
			return false;
		}

		this.sfTotalFuel = totalFuel;
		this.sfTotalAsh = totalAsh;
		this.batches = Math.max(1, totalFuel + totalAsh);
		return true;
	}

	/** 按本轮折算结果扣各罐流体：燃料部分按比例扣，余料整罐烧掉 */
	private void consumeInputSolidFuel(SolidFuelRecipe recipe) {

		for(int t = 0; t < GROUPS; t++) {
			if(sfFuel[t] <= 0 && sfAsh[t] <= 0) continue;

			FluidTank tank = inputTanks[t];
			int per = recipe.perFuel(tank.getTankType());
			if(per <= 0) continue;

			int used = sfFuel[t] * per;
			if(sfAsh[t] > 0) used = tank.getFill(); // 余料转灰：整罐清空
			tank.setFill(Math.max(0, tank.getFill() - used));
		}
	}

	/** 输出槽是否装得下本轮的燃料 + 灰烬（共用同一批输出槽，必须合起来算） */
	private boolean canFitOutputSolidFuel() {
		return fitsSolidFuelOutput(sfTotalFuel, sfTotalAsh);
	}

	/**
	 * 在输出槽快照上按真实写入顺序（produceItemSolidFuel：先燃料、后灰烬）模拟塞一遍，
	 * 两种产物全部塞得下才返回 true。分别校验会导致「各自都装得下、合起来溢出」→ 灰烬被吞。
	 */
	private boolean fitsSolidFuelOutput(int fuel, int ash) {

		if(fuel <= 0 && ash <= 0) return true;

		int count = GROUPS * OUTPUT_SLOTS;
		ItemStack[] sim = new ItemStack[count];
		for(int i = 0; i < count; i++) {
			ItemStack s = slots[baseOutput + i];
			sim[i] = s == null ? null : s.copy();
		}

		if(fuel > 0 && !insertSim(sim, new ItemStack(com.hbm.items.ModItems.solid_fuel), fuel)) return false;
		if(ash > 0 && !insertSim(sim, SolidFuelRecipe.ash(1), ash)) return false;

		return true;
	}

	/** 把本轮的固体燃料和灰烬放进输出槽（全部 4 组输出槽共用） */
	private void produceItemSolidFuel() {

		if(sfTotalFuel > 0) {
			insertStackAllOutputs(new ItemStack(com.hbm.items.ModItems.solid_fuel, sfTotalFuel));
		}
		if(sfTotalAsh > 0) {
			insertStackAllOutputs(SolidFuelRecipe.ash(sfTotalAsh));
		}

		this.markDirty = true;
	}

	/** 把整批物品塞进全部输出槽，按堆叠上限切块 */
	private void insertStackAllOutputs(ItemStack stack) {

		int max = Math.min(stack.getMaxStackSize(), 64);
		int remaining = stack.stackSize;

		while(remaining > 0) {
			ItemStack chunk = stack.copy();
			chunk.stackSize = Math.min(remaining, max);

			int inserted = insertIntoSpan(baseOutput, GROUPS, chunk);
			if(inserted <= 0) break; // canFitOutput 已经挡过，兜底防死循环
			remaining -= inserted;
		}
	}

	/* ============================== 工具 ============================== */

	/** 某种流体在它占用的所有罐里的总量 */
	private int fluidAmount(int kind, int span) {
		int total = 0;
		for(int t = kind * span; t < (kind + 1) * span; t++) total += inputTanks[t].getFill();
		return total;
	}

	private boolean hasEmptySlotInSpan(int start, int span) {
		int end = start + span * OUTPUT_SLOTS;
		for(int slot = start; slot < end; slot++) {
			if(slots[slot] == null) return true;
		}
		return false;
	}

	/** 这组输出槽还能装多少个这种物品 */
	private int capacityFor(int start, int span, ItemStack stack) {
		int end = start + span * OUTPUT_SLOTS;
		int max = Math.min(stack.getMaxStackSize(), 64);
		int capacity = 0;

		for(int slot = start; slot < end; slot++) {
			ItemStack existing = slots[slot];
			if(existing == null) {
				capacity += max;
				continue;
			}
			if(existing.getItem() != stack.getItem()) continue;
			if(existing.getItemDamage() != stack.getItemDamage()) continue;
			if(!ItemStack.areItemStackTagsEqual(existing, stack)) continue;
			capacity += Math.max(0, max - existing.stackSize);
		}

		return capacity;
	}

	/** 把这批物品塞进这组输出槽，返回实际放进去的数量 */
	private int insertIntoSpan(int start, int span, ItemStack stack) {

		int end = start + span * OUTPUT_SLOTS;
		int max = Math.min(stack.getMaxStackSize(), 64);
		int remaining = stack.stackSize;

		// 先并入已有的同类物品（同物品、同 meta、同 NBT），每个槽不超过堆叠上限
		for(int slot = start; slot < end && remaining > 0; slot++) {
			ItemStack existing = slots[slot];
			if(existing == null) continue;
			if(existing.getItem() != stack.getItem()) continue;
			if(existing.getItemDamage() != stack.getItemDamage()) continue;
			if(!ItemStack.areItemStackTagsEqual(existing, stack)) continue;

			int add = Math.min(remaining, max - existing.stackSize);
			if(add <= 0) continue;

			existing.stackSize += add;
			remaining -= add;
		}

		// 剩下的放进空格，一槽最多放 max 个
		for(int slot = start; slot < end && remaining > 0; slot++) {
			if(slots[slot] != null) continue;

			ItemStack placed = stack.copy();
			placed.stackSize = Math.min(remaining, max);
			slots[slot] = placed;
			remaining -= placed.stackSize;
		}

		return stack.stackSize - remaining;
	}
}

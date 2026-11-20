package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.CrackingRecipes;
import com.hbm.inventory.recipes.FractionRecipes;
import com.hbm.inventory.recipes.RadiolysisRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.HybridFractionRecipes;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.item.ItemStack;

/**
 * 混合催化分馏塔的 NEI 页。
 *
 * <p>三路数据全部收进来：</p>
 * <ol>
 *   <li>{@link HybridFractionRecipes#RECIPES} —— 本附属的自定义配方（可带催化剂、最多 4 流体 + 1 物品）；</li>
 *   <li>{@link FractionRecipes#fractions} —— 原版分馏表，所有往这张表里注册过的配方（含其他附属）都会出现，不需要催化剂；</li>
 *   <li>{@link CrackingRecipes#cracking} —— 催化裂化，<b>需要催化剂</b>，页面里放原版催化转换器。</li>
 * </ol>
 *
 * <pre>
 *  [机器] [进1]  →  [出1] [出2]
 *  [催化] [进2]     [物5] [出3] [出4]
 * </pre>
 *
 * <p>槽位没有内容时用 HBM 的「没有东西！」占位；不需要催化剂时催化槽留空。
 * 分馏 / 裂化的数值按本机每个周期（1000 mB 进料）折算，所以产出是原表的 10 倍。</p>
 */
public class HybridFractionTowerHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.hybrid_fraction_tower";

	/** 每周期进料量，原版分馏表是按 100 mB 定义的，这里放大 10 倍 */
	private static final int CYCLE_INPUT = 1_000;
	private static final int CYCLE_FACTOR = 10;

	private static final int MACHINE_X = 6;
	private static final int MACHINE_Y = 6;
	private static final int IN1_X = 28;
	private static final int IN1_Y = 6;
	private static final int CATALYST_X = 6;
	private static final int CATALYST_Y = 28;
	private static final int IN2_X = 28;
	private static final int IN2_Y = 28;
	private static final int ARROW_X = 52;
	private static final int ARROW_Y = 10;

	/** 出料槽位：上排 2 个、下排 3 个，从左到右、从上到下依次填 */
	private static final int[][] OUT_POS = {{90, 6}, {112, 6}, {90, 28}, {112, 28}, {134, 28}};

	public class TowerRecipe extends CachedRecipe {

		public final PositionedStack machine;
		/** null = 该位置留空 */
		public final PositionedStack catalyst;
		public final PositionedStack in1;
		public final PositionedStack in2;
		/** 竖排出料：流体1 / 流体2 / 物品5 / 流体3 / 流体4 */
		public final List<PositionedStack> outs = new ArrayList<PositionedStack>();

		public TowerRecipe(Object cat, FluidStack i1, FluidStack i2, ItemStack[] out, ItemStack result) {

			this.machine = new PositionedStack(new ItemStack(ModBlocks.machine_hybrid_catalytic_fraction_tower), MACHINE_X, MACHINE_Y);
			// cat 既可以是单个 ItemStack，也可以是 ItemStack[]；
			// 传数组时 NEI 会每 20 tick 自动切换一次显示
			this.catalyst = new PositionedStack(cat == null ? new ItemStack(ModItems.nothing) : cat, CATALYST_X, CATALYST_Y);
			this.in1 = new PositionedStack(i1 == null ? new ItemStack(ModItems.nothing) : icon(i1), IN1_X, IN1_Y);
			this.in2 = new PositionedStack(i2 == null ? new ItemStack(ModItems.nothing) : icon(i2), IN2_X, IN2_Y);

			// 有内容的产物按「从左到右、从上到下」依次排，剩下的槽补「没有东西」
			List<ItemStack> content = new ArrayList<ItemStack>();

			if(out != null) {
				for(int i = 0; i < out.length; i++) {
					if(out[i] != null) content.add(out[i]);
				}
			}

			if(result != null) content.add(result.copy());

			for(int i = 0; i < OUT_POS.length; i++) {
				ItemStack stack = i < content.size() ? content.get(i) : new ItemStack(ModItems.nothing);
				this.outs.add(new PositionedStack(stack, OUT_POS[i][0], OUT_POS[i][1]));
			}
		}

		@Override
		public List<PositionedStack> getIngredients() {
			List<PositionedStack> in = new ArrayList<PositionedStack>();
			in.add(machine);
			in.add(catalyst);
			in.add(in1);
			in.add(in2);
			// 必须走 getCycledIngredients，NEI 才会轮换 —— 它不会自己动：
			// TemplateRecipeHandler 只维护 cycleticks，真正切换显示的是这个调用，
			// 由它按 cycleticks / 20（= 1 秒）给每个栈换一个 permutation。
			// 少了这一句，数组型催化剂（铂配方 = 铂 + CMB）就会永远停在第一个元素上。
			return getCycledIngredients(cycleticks / 20, in);
		}

		@Override
		public PositionedStack getResult() {
			return this.outs.get(0);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			return this.outs.subList(1, this.outs.size());
		}
	}

	/* ---- 收集配方 ---- */

	private void buildAll() {

		arecipes.clear();

		// 1) 本附属自定义配方
		for(HybridFractionRecipes.Recipe recipe : HybridFractionRecipes.RECIPES) {

			ItemStack[] out = {
				icon(recipe.out[0]), icon(recipe.out[1]), icon(recipe.out[2]), icon(recipe.out[3])
			};

			arecipes.add(new TowerRecipe(
				catalystsFor(HybridFractionRecipes.catalystTier(recipe.catalyst)),
				recipe.in1, recipe.in2, out, recipe.item));
		}

		// 2) 原版分馏表（含所有往这张表注册过的配方）
		for(Map.Entry<FluidType, Pair<FluidStack, FluidStack>> entry : FractionRecipes.fractions.entrySet()) {

			if(entry.getKey() == null || entry.getValue() == null) continue;

			ItemStack[] out = {
				icon(scale(entry.getValue().getKey())), icon(scale(entry.getValue().getValue())), null, null
			};

			arecipes.add(new TowerRecipe(catalystsFor(0), new FluidStack(entry.getKey(), CYCLE_INPUT), null, out, null));
		}

		// 3) 催化裂化，需要催化剂。本机只借用裂化塔的产物表：
		//    不吃蒸汽、也不副产低压蒸汽（原版裂化塔是 100mB 进料 + 200mB 蒸汽 → 馏分 + 2mB 低压蒸汽）
		Object crackingCatalysts = catalystsFor(1);

		for(Map.Entry<FluidType, Pair<FluidStack, FluidStack>> entry : CrackingRecipes.cracking.entrySet()) {

			if(entry.getKey() == null || entry.getValue() == null) continue;

			ItemStack[] out = {
				icon(scale(entry.getValue().getKey())),
				icon(scale(entry.getValue().getValue())),
				null,
				null
			};

			arecipes.add(new TowerRecipe(crackingCatalysts, new FluidStack(entry.getKey(), CYCLE_INPUT), null, out, null));
		}

		// 4) 辐射裂解：必须用 CMB 催化剂，不用蒸汽。
		//    直接吃原版的 NEI 表，原版加了新条目这里自动跟着变。
		Object cmbCatalyst = catalystsFor(3);

		for(Map.Entry<Object, Object[]> entry : RadiolysisRecipes.getRecipesForNEI().entrySet()) {

			ItemStack in = (ItemStack) entry.getKey();
			if(in == null) continue;

			FluidType type = com.hbm.inventory.fluid.Fluids.fromID(in.getItemDamage());
			if(type == null || type == com.hbm.inventory.fluid.Fluids.NONE) continue;

			ItemStack[] raw = (ItemStack[]) entry.getValue();
			ItemStack[] out = {raw != null && raw.length > 0 ? raw[0] : null,
							   raw != null && raw.length > 1 ? raw[1] : null, null, null};

			arecipes.add(new TowerRecipe(cmbCatalyst, new FluidStack(type, CYCLE_INPUT), null, out, null));
		}
	}

	/** 流体图标；空气体（NONE）返回 null，由调用方替换成「没有东西！」 */
	private static ItemStack icon(FluidStack stack) {

		if(stack == null || stack.type == null || stack.type == com.hbm.inventory.fluid.Fluids.NONE) return null;

		return ItemFluidIcon.make(stack.type, stack.fill);
	}

	/**
	 * 能跑某项配方（要求 {@code need} 级）的所有催化剂。
	 *
	 * <p>等级高的兼容等级低的，所以返回的是 need 级及以上的列表；
	 * NEI 会把数组里的图标按 1 秒一个循环闪烁。不需要催化剂时返回「没有东西」。</p>
	 */
	private static Object catalystsFor(int need) {

		if(need <= 0) return new ItemStack(ModItems.nothing);

		List<ItemStack> list = new ArrayList<ItemStack>();

		if(need <= 1) list.add(new ItemStack(ModItems.catalytic_converter));
		if(need <= 2) list.add(new ItemStack(com.makentmgreatagain.init.ModItems.platinum_converter));
		if(need <= 3) list.add(new ItemStack(com.makentmgreatagain.init.ModItems.cmb_converter));

		return list.isEmpty() ? new ItemStack(ModItems.nothing) : list.toArray(new ItemStack[list.size()]);
	}

	private static FluidStack scale(FluidStack stack) {

		if(stack == null || stack.type == null || stack.type == com.hbm.inventory.fluid.Fluids.NONE) return null;

		return new FluidStack(stack.type, stack.fill * CYCLE_FACTOR);
	}

	/* ---- 查询匹配 ---- */

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.machine_hybrid_catalytic_fraction_tower);
	}

	private static boolean isCatalyst(ItemStack stack) {

		if(stack == null) return false;

		net.minecraft.item.Item item = stack.getItem();

		return item == ModItems.catalytic_converter
			|| item == com.makentmgreatagain.init.ModItems.platinum_converter
			|| item == com.makentmgreatagain.init.ModItems.cmb_converter;
	}

	/** 流体图标对应的流体；不是流体图标返回 null */
	private static FluidType fluidTypeOf(ItemStack stack) {

		if(stack == null || stack.getItem() != ModItems.fluid_icon) return null;

		FluidType type = com.hbm.inventory.fluid.Fluids.fromID(stack.getItemDamage());
		return type == null || type == com.hbm.inventory.fluid.Fluids.NONE ? null : type;
	}

	private static boolean sameItem(ItemStack a, ItemStack b) {

		if(a == null || b == null) return false;
		if(a.getItem() != b.getItem()) return false;
		return !a.getHasSubtypes() || a.getItemDamage() == b.getItemDamage();
	}

	private static boolean sameFluid(FluidStack fluid, ItemStack stack) {

		FluidType type = fluidTypeOf(stack);
		return fluid != null && type != null && fluid.type == type;
	}

	/** 自定义配方是否产出这个物品/流体图标 */
	private static boolean produces(HybridFractionRecipes.Recipe recipe, ItemStack stack) {

		if(stack == null) return false;
		if(recipe.item != null && sameItem(recipe.item, stack)) return true;

		for(int i = 0; i < 4; i++) {
			if(sameFluid(recipe.out[i], stack)) return true;
		}

		return false;
	}

	/** 自定义配方是否吃这个物品/流体图标 */
	private static boolean consumes(HybridFractionRecipes.Recipe recipe, ItemStack stack) {
		return sameFluid(recipe.in1, stack) || sameFluid(recipe.in2, stack);
	}

	/** 原版分馏/裂化的一对产物里有没有该流体 */
	private static boolean containsFluid(Pair<FluidStack, FluidStack> pair, FluidType type) {

		if(pair == null || type == null) return false;

		FluidStack a = pair.getKey();
		FluidStack b = pair.getValue();

		return (a != null && a.type == type) || (b != null && b.type == type);
	}

	private static boolean containsItem(ItemStack[] stacks, ItemStack stack) {

		if(stacks == null || stack == null) return false;

		for(ItemStack out : stacks) {
			if(sameItem(out, stack)) return true;
		}

		return false;
	}

	/** 按单个物品/流体图标筛配方：crafting=true 查产物，false 查原料 */
	private void buildMatching(ItemStack stack, boolean crafting) {

		arecipes.clear();
		if(stack == null) return;

		// 1) 本附属自定义配方
		for(HybridFractionRecipes.Recipe recipe : HybridFractionRecipes.RECIPES) {

			boolean match = crafting ? produces(recipe, stack) : consumes(recipe, stack);
			if(!match) continue;

			ItemStack[] out = {
				icon(recipe.out[0]), icon(recipe.out[1]), icon(recipe.out[2]), icon(recipe.out[3])
			};

			arecipes.add(new TowerRecipe(
				catalystsFor(HybridFractionRecipes.catalystTier(recipe.catalyst)),
				recipe.in1, recipe.in2, out, recipe.item));
		}

		FluidType type = fluidTypeOf(stack);
		if(type == null) return;

		// 2) 原版分馏表
		for(Map.Entry<FluidType, Pair<FluidStack, FluidStack>> entry : FractionRecipes.fractions.entrySet()) {

			if(entry.getKey() == null || entry.getValue() == null) continue;

			boolean match = crafting ? containsFluid(entry.getValue(), type) : entry.getKey() == type;
			if(!match) continue;

			ItemStack[] out = {
				icon(scale(entry.getValue().getKey())), icon(scale(entry.getValue().getValue())), null, null
			};

			arecipes.add(new TowerRecipe(catalystsFor(0), new FluidStack(entry.getKey(), CYCLE_INPUT), null, out, null));
		}

		// 3) 催化裂化
		Object crackingCatalysts = catalystsFor(1);

		for(Map.Entry<FluidType, Pair<FluidStack, FluidStack>> entry : CrackingRecipes.cracking.entrySet()) {

			if(entry.getKey() == null || entry.getValue() == null) continue;

			boolean match = crafting ? containsFluid(entry.getValue(), type) : entry.getKey() == type;
			if(!match) continue;

			ItemStack[] out = {
				icon(scale(entry.getValue().getKey())), icon(scale(entry.getValue().getValue())), null, null
			};

			arecipes.add(new TowerRecipe(crackingCatalysts, new FluidStack(entry.getKey(), CYCLE_INPUT), null, out, null));
		}

		// 4) 辐射裂解（输入是流体图标，输出是物品）
		Object cmbCatalyst = catalystsFor(3);

		for(Map.Entry<Object, Object[]> entry : RadiolysisRecipes.getRecipesForNEI().entrySet()) {

			ItemStack in = (ItemStack) entry.getKey();
			if(in == null) continue;

			FluidType inputType = com.hbm.inventory.fluid.Fluids.fromID(in.getItemDamage());
			if(inputType == null || inputType == com.hbm.inventory.fluid.Fluids.NONE) continue;

			ItemStack[] raw = (ItemStack[]) entry.getValue();
			boolean match = crafting ? containsItem(raw, stack) : sameItem(in, stack);
			if(!match) continue;

			ItemStack[] out = {raw != null && raw.length > 0 ? raw[0] : null,
							   raw != null && raw.length > 1 ? raw[1] : null, null, null};

			arecipes.add(new TowerRecipe(cmbCatalyst, new FluidStack(inputType, CYCLE_INPUT), null, out, null));
		}
	}

	/* ---- NEI 入口 ---- */

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if(KEY.equals(outputId)) {
			buildAll();
		} else if(results != null && results.length > 0 && results[0] instanceof ItemStack) {
			buildMatching((ItemStack) results[0], true);
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		buildMatching(result, true);
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if(KEY.equals(inputId)) {
			buildAll();
		} else if(ingredients != null && ingredients.length > 0 && ingredients[0] instanceof ItemStack) {
			loadUsageRecipes((ItemStack) ingredients[0]);
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {

		// 机器本体、催化剂：这台机器能跑的全部配方
		if(isMachine(ingredient) || isCatalyst(ingredient)) {
			buildAll();
			return;
		}

		// 进料（包括自定义配方和原版分馏/裂化/辐射裂解）
		buildMatching(ingredient, false);
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.hybrid_fraction_tower.name");
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		// 左边 2×2：机器 / 一号进料、催化剂 / 二号进料
		drawSlot(MACHINE_X, MACHINE_Y);
		drawSlot(IN1_X, IN1_Y);
		drawSlot(CATALYST_X, CATALYST_Y);
		drawSlot(IN2_X, IN2_Y);

		drawTexturedModalRect(ARROW_X, ARROW_Y, 59, 87, 18, 36);

		for(int[] pos : OUT_POS) {
			drawSlot(pos[0], pos[1]);
		}
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}
}

package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.HeatChemicalRecipe;
import com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 大型催化化工厂的 NEI 页。
 *
 * <p>布局沿用 HBM 通用配方页：左边输入、中间机器、右边输出，最多 8 进 8 出。
 * 配方需要催化剂时，在机器上方（原版蓝图那一格）画出催化剂图标。</p>
 *
 * <p>自建页面而不是继承 HBM 的 {@code NEIGenericRecipeHandler}：附属 jar 不会带上
 * com.hbm 的改动，依赖新加的父类方法会在运行时抛 NoSuchMethodError。</p>
 */
public class LargeChemicalFactoryHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.largeChemicalFactory";

	/** 机器图标位置；有催化剂时整体下移 7 像素，给上方图标腾位置 */
	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;
	/** 催化剂图标位置（和蓝图同一格） */
	private static final int EXTRA_X = 75;
	private static final int EXTRA_Y = 10;

	public class RecipeSet extends TemplateRecipeHandler.CachedRecipe {

		public final GenericRecipe recipe;
		public final PositionedStack[] input;
		public final PositionedStack[] output;
		public final PositionedStack machine;
		/** 需要催化剂时才有 */
		public final PositionedStack extra;
		/** 是否需要催化剂（决定箭头长度和机器图标位置） */
		public final boolean hasExtra;

		public RecipeSet(GenericRecipe recipe) {

			this.recipe = recipe;

			int inputSize = (recipe.inputItem != null ? recipe.inputItem.length : 0)
					+ (recipe.inputFluid != null ? recipe.inputFluid.length : 0);
			int outputSize = (recipe.outputItem != null ? recipe.outputItem.length : 0)
					+ (recipe.outputFluid != null ? recipe.outputFluid.length : 0);

			int[][] inPos = getInputSlotPositions(inputSize);
			this.input = new PositionedStack[inputSize];
			int index = 0;
			if(recipe.inputItem != null) for(AStack stack : recipe.inputItem) {
				this.input[index] = new PositionedStack(stack.extractForNEI(), inPos[index][0], inPos[index][1]);
				index++;
			}
			if(recipe.inputFluid != null) for(FluidStack fluid : recipe.inputFluid) {
				this.input[index] = new PositionedStack(ItemFluidIcon.make(fluid), inPos[index][0], inPos[index][1]);
				index++;
			}

			int[][] outPos = getOutputSlotPositions(outputSize);
			this.output = new PositionedStack[outputSize];
			index = 0;
			if(recipe.outputItem != null) for(IOutput output : recipe.outputItem) {
				this.output[index] = new PositionedStack(output.getAllPossibilities(), outPos[index][0], outPos[index][1]);
				index++;
			}
			if(recipe.outputFluid != null) for(FluidStack fluid : recipe.outputFluid) {
				this.output[index] = new PositionedStack(ItemFluidIcon.make(fluid), outPos[index][0], outPos[index][1]);
				index++;
			}

			this.extra = catalystFor(recipe);
			this.hasExtra = this.extra != null;
			this.machine = new PositionedStack(
					new ItemStack(ModBlocks.machine_large_catalytic_chemical_factory),
					MACHINE_X, hasExtra ? MACHINE_Y + 7 : MACHINE_Y);
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return getCycledIngredients(cycleticks / 20, Arrays.asList(this.input));
		}

		@Override
		public PositionedStack getResult() {
			return null;
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			List<PositionedStack> other = new ArrayList<PositionedStack>();
			other.addAll(Arrays.asList(this.output));
			other.add(this.machine);
			if(this.extra != null) other.add(this.extra);
			return getCycledIngredients(cycleticks / 20, other);
		}
	}

	/* ---- 槽位坐标（与 HBM 通用页一致） ---- */

	public int[][] getInputSlotPositions(int count) {

		if(count == 1) return new int[][] {{48, 24}};
		if(count == 2) return new int[][] {{30, 24}, {48, 24}};
		if(count == 3) return new int[][] {{12, 24}, {30, 24}, {48, 24}};
		if(count == 4) return new int[][] {{30, 15}, {48, 15}, {30, 33}, {48, 33}};
		if(count == 5) return new int[][] {{12, 15}, {30, 15}, {48, 15}, {12, 33}, {30, 33}};
		if(count == 6) return new int[][] {{12, 15}, {30, 15}, {48, 15}, {12, 33}, {30, 33}, {48, 33}};

		int[][] slots = new int[count][2];
		int cols = (count + 2) / 3;

		for(int i = 0; i < count; i++) {
			slots[i][0] = 12 + (i % cols) * 18 - (cols == 4 ? 18 : 0);
			slots[i][1] = 6 + (i / cols) * 18;
		}

		return slots;
	}

	public int[][] getOutputSlotPositions(int count) {

		switch(count) {
		case 1: return new int[][] {{102, 24}};
		case 2: return new int[][] {{102, 24}, {120, 24}};
		case 3: return new int[][] {{102, 24}, {120, 24}, {138, 24}};
		case 4: return new int[][] {{102, 15}, {120, 15}, {102, 33}, {120, 33}};
		case 5: return new int[][] {{102, 15}, {120, 15}, {102, 33}, {120, 33}, {138, 24}};
		case 6: return new int[][] {{102, 6}, {120, 6}, {102, 24}, {120, 24}, {102, 42}, {120, 42}};
		case 7: return new int[][] {{102, 6}, {120, 6}, {102, 24}, {120, 24}, {102, 42}, {120, 42}, {138, 24}};
		case 8: return new int[][] {{102, 6}, {120, 6}, {102, 24}, {120, 24}, {102, 42}, {120, 42}, {138, 24}, {138, 42}};
		}

		return new int[count][2];
	}

	/* ---- 查询匹配 ---- */

	private static List<GenericRecipe> all() {
		// NEI 逐条展示：机器表 + 被合并配方收起来的逐条热解配方。
		// 合并配方本身没有输入输出数组，摆不出页面，跳过。
		List<GenericRecipe> all = new ArrayList<GenericRecipe>();
		for(GenericRecipe recipe : LargeChemicalFactoryRecipes.get().neiRecipes()) {
			if(recipe instanceof com.makentmgreatagain.recipes.SolidFuelRecipe) continue;
			all.add(recipe);
		}
		return all;
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_large_catalytic_chemical_factory);
	}

	private static boolean isCatalyst(ItemStack stack) {

		if(stack == null) return false;

		Item item = stack.getItem();

		return item == ModItems.catalytic_converter
			|| item == com.makentmgreatagain.init.ModItems.platinum_converter
			|| item == com.makentmgreatagain.init.ModItems.cmb_converter;
	}

	private static boolean sameFluid(FluidStack fluid, ItemStack stack) {

		if(fluid == null || stack == null || stack.getItem() != ModItems.fluid_icon) return false;

		FluidType type = com.hbm.inventory.fluid.Fluids.fromID(stack.getItemDamage());
		return type != null && fluid.type == type;
	}

	/** 配方是否产出这个物品/流体 */
	private static boolean produces(GenericRecipe recipe, ItemStack stack) {

		if(recipe.outputItem != null) for(IOutput output : recipe.outputItem) {
			if(output == null) continue;
			for(ItemStack out : output.getAllPossibilities()) {
				if(NEIServerUtils.areStacksSameTypeCrafting(out, stack)) return true;
			}
		}

		if(recipe.outputFluid != null) for(FluidStack fluid : recipe.outputFluid) {
			if(sameFluid(fluid, stack)) return true;
		}

		return false;
	}

	/** 配方是否吃这个物品/流体 */
	private static boolean consumes(GenericRecipe recipe, ItemStack stack) {

		if(recipe.inputItem != null) for(AStack input : recipe.inputItem) {
			for(ItemStack in : input.extractForNEI()) {
				if(NEIServerUtils.areStacksSameTypeCrafting(in, stack)) return true;
			}
		}

		if(recipe.inputFluid != null) for(FluidStack fluid : recipe.inputFluid) {
			if(sameFluid(fluid, stack)) return true;
		}

		return false;
	}

	/** 配方要求的催化剂图标；不需要催化剂时返回 null */
	private static PositionedStack catalystFor(GenericRecipe recipe) {

		if(!(recipe instanceof HeatChemicalRecipe)) return null;

		int tier = ((HeatChemicalRecipe) recipe).requiredCatalyst;
		if(tier <= 0) return null;

		ItemStack stack = tier >= 3
				? new ItemStack(com.makentmgreatagain.init.ModItems.cmb_converter)
				: tier == 2
						? new ItemStack(com.makentmgreatagain.init.ModItems.platinum_converter)
						: new ItemStack(ModItems.catalytic_converter);

		return new PositionedStack(stack, EXTRA_X, EXTRA_Y);
	}

	private void buildAll() {

		arecipes.clear();
		for(GenericRecipe recipe : all()) arecipes.add(new RecipeSet(recipe));
	}

	/* ---- NEI 入口 ---- */

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {

		if(KEY.equals(outputId)) {
			buildAll();
		} else if(results != null && results.length > 0 && results[0] instanceof ItemStack) {
			loadCraftingRecipes((ItemStack) results[0]);
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {

		arecipes.clear();
		if(result == null) return;

		for(GenericRecipe recipe : all()) {
			if(produces(recipe, result)) arecipes.add(new RecipeSet(recipe));
		}
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

		arecipes.clear();
		if(ingredient == null) return;

		// 机器本体、催化剂：列出这台机器能跑的全部配方
		if(isMachine(ingredient) || isCatalyst(ingredient)) {
			buildAll();
			return;
		}

		for(GenericRecipe recipe : all()) {
			if(consumes(recipe, ingredient)) arecipes.add(new RecipeSet(recipe));
		}
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return ModBlocks.machine_large_catalytic_chemical_factory.getLocalizedName();
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public void drawBackground(int recipe) {

		super.drawBackground(recipe);

		RecipeSet rec = (RecipeSet) this.arecipes.get(recipe);

		for(PositionedStack pos : rec.input) drawSlot(pos.relx, pos.rely);
		for(PositionedStack pos : rec.output) drawSlot(pos.relx, pos.rely);

		if(rec.hasExtra) {
			// 催化剂格不画槽位底图：竖框（77,87,18,50）本身就是它的底，
			// 而且 drawSlot 的精灵（5,87,18,18）上边和左边各有 1 像素深色描边，
			// 图标只有 16×16 盖不住，露出那 1 像素就成了催化剂上方/左侧的一道黑线。
			// HBM 自己的通用页对模板格（蓝图）也是只画竖框，不画槽位底图。
			drawTexturedModalRect(74, 7, 77, 87, 18, 50);
		} else {
			drawTexturedModalRect(74, 14, 59, 87, 18, 36);
		}
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {

		RecipeSet rec = (RecipeSet) this.arecipes.get(recipe);
		GenericRecipe r = rec.recipe;

		Minecraft mc = Minecraft.getMinecraft();

		if(r.duration > 0) {
			String text = I18nUtil.resolveKey("gui.recipe.duration") + ": " + (r.duration / 20D) + "s";
			mc.fontRenderer.drawString(text, 164 - mc.fontRenderer.getStringWidth(text), 45, 0x404040);
		}

		if(r.power > 0) {
			String text = I18nUtil.resolveKey("makentmgreatagain.gui.recipe.power") + ": " + BobMathUtil.getShortNumber(r.power) + "HE/t";
			mc.fontRenderer.drawString(text, 164 - mc.fontRenderer.getStringWidth(text), 57, 0x404040);
		}

		if(r instanceof HeatChemicalRecipe) {
			int heat = ((HeatChemicalRecipe) r).requiredHeat;
			if(heat > 0) {
				String text = I18nUtil.resolveKey("gui.recipe.heat") + ": " + BobMathUtil.getShortNumber(heat) + "TU";
				// 与右侧「耗电」同一行（y=57），原先 45 会与耗时挤在一起
				mc.fontRenderer.drawString(text, 6, 57, 0x404040);
			}
		}
	}
}

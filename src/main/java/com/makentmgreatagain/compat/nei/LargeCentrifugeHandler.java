package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.CentrifugeRecipe;
import com.makentmgreatagain.recipes.LargeCentrifugeRecipes;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 大型离心机的 NEI 页。
 *
 * <p>布局：左边最多 3 个进料（2 物品 + 1 流体），中间机器，右边 2 列 × 最多 4 行共 8 个产物
 * （最多 8 物品 + 4 流体）。1 进 8 出的极限配方也摆得下。</p>
 *
 * <p>展示 {@link LargeCentrifugeRecipes#getAll()}：直属自定义配方（白钨酸、六氟化铀、六氟化钚）
 * 加上原版离心机转换过来的全部配方。</p>
 */
public class LargeCentrifugeHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.largeCentrifuge";

	/** 机器图标位置 */
	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;

	/** 进料槽坐标，和本体 NEI 通用页一致 */
	private static int[][] getInputPositions(int count) {

		switch(count) {
		case 1: return new int[][] {{48, 24}};
		case 2: return new int[][] {{30, 24}, {48, 24}};
		case 3: return new int[][] {{12, 24}, {30, 24}, {48, 24}};
		}

		return new int[count][2];
	}

	/**
	 * 产物槽坐标：横向 2 格、竖向最多 4 格（共 8 个），居中于中间的箭头框。
	 *
	 * <p>NEI 每条配方只有 65px 高，4 行放不下 18px 的行距，所以行距取 16px；
	 * 超过 8 个产物时继续往右加一组 2 列，保证不超出页面。</p>
	 */
	private static int[][] getOutputPositions(int count) {

		int rows = Math.min(4, (count + 1) / 2);
		int startY = 24 - (rows - 1) * 8;

		int[][] slots = new int[count][2];
		for(int i = 0; i < count; i++) {
			int block = i / 8;
			int local = i % 8;
			slots[i][0] = 102 + (block * 2 + local % 2) * 18;
			slots[i][1] = startY + (local / 2) * 16;
		}
		return slots;
	}

	public class RecipeSet extends TemplateRecipeHandler.CachedRecipe {

		public final CentrifugeRecipe recipe;
		public final PositionedStack[] input;
		public final PositionedStack[] output;
		public final PositionedStack machine;

		public RecipeSet(CentrifugeRecipe recipe) {

			this.recipe = recipe;

			int inputSize = recipe.inputItems.size() + (recipe.inputFluid != null ? 1 : 0);
			this.input = new PositionedStack[inputSize];
			int[][] inPos = getInputPositions(inputSize);

			int index = 0;
			for(AStack stack : recipe.inputItems) {
				this.input[index] = new PositionedStack(stack.extractForNEI(), inPos[index][0], inPos[index][1]);
				index++;
			}
			if(recipe.inputFluid != null) {
				this.input[index] = new PositionedStack(ItemFluidIcon.make(recipe.inputFluid), inPos[index][0], inPos[index][1]);
			}

			int outputSize = recipe.outputItems.size() + recipe.outputFluids.size();
			this.output = new PositionedStack[outputSize];
			int[][] outPos = getOutputPositions(outputSize);

			index = 0;
			for(ItemStack stack : recipe.outputItems) {
				this.output[index] = new PositionedStack(stack.copy(), outPos[index][0], outPos[index][1]);
				index++;
			}
			for(FluidStack fluid : recipe.outputFluids) {
				this.output[index] = new PositionedStack(ItemFluidIcon.make(fluid), outPos[index][0], outPos[index][1]);
				index++;
			}

			this.machine = new PositionedStack(new ItemStack(ModBlocks.large_centrifuge), MACHINE_X, MACHINE_Y);
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
			return getCycledIngredients(cycleticks / 20, other);
		}
	}

	/* ---- 查询匹配 ---- */

	private static List<CentrifugeRecipe> all() {
		return LargeCentrifugeRecipes.getAll();
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.large_centrifuge);
	}

	private static boolean sameFluid(FluidStack fluid, ItemStack stack) {

		if(fluid == null || stack == null || stack.getItem() != com.hbm.items.ModItems.fluid_icon) return false;

		FluidType type = Fluids.fromID(stack.getItemDamage());
		return type != null && fluid.type == type;
	}

	/** 配方是否产出这个物品/流体 */
	private static boolean produces(CentrifugeRecipe recipe, ItemStack stack) {

		for(ItemStack out : recipe.outputItems) {
			if(NEIServerUtils.areStacksSameTypeCrafting(out, stack)) return true;
		}

		for(FluidStack fluid : recipe.outputFluids) {
			if(sameFluid(fluid, stack)) return true;
		}

		return false;
	}

	/** 配方是否吃这个物品/流体 */
	private static boolean consumes(CentrifugeRecipe recipe, ItemStack stack) {

		for(AStack input : recipe.inputItems) {
			for(ItemStack in : input.extractForNEI()) {
				if(NEIServerUtils.areStacksSameTypeCrafting(in, stack)) return true;
			}
		}

		return recipe.inputFluid != null && sameFluid(recipe.inputFluid, stack);
	}

	private void buildAll() {

		arecipes.clear();
		for(CentrifugeRecipe recipe : all()) arecipes.add(new RecipeSet(recipe));
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

		for(CentrifugeRecipe recipe : all()) {
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

		// 机器本体：列出这台机器能跑的全部配方
		if(isMachine(ingredient)) {
			buildAll();
			return;
		}

		for(CentrifugeRecipe recipe : all()) {
			if(consumes(recipe, ingredient)) arecipes.add(new RecipeSet(recipe));
		}
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return ModBlocks.large_centrifuge.getLocalizedName();
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

		// 中间的执行箭头框（本体 NEI 通用页同款素材，机器图标压在框里）
		drawTexturedModalRect(74, 14, 59, 87, 18, 36);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {

		RecipeSet rec = (RecipeSet) this.arecipes.get(recipe);
		CentrifugeRecipe r = rec.recipe;

		Minecraft mc = Minecraft.getMinecraft();

		if(r.duration > 0) {
			String text = I18nUtil.resolveKey("gui.recipe.duration") + ": " + (r.duration / 20D) + "s";
			mc.fontRenderer.drawString(text, 164 - mc.fontRenderer.getStringWidth(text), 45, 0x404040);
		}

		if(r.power > 0) {
			String text = I18nUtil.resolveKey("makentmgreatagain.gui.recipe.power") + ": " + BobMathUtil.getShortNumber(r.power) + "HE/t";
			mc.fontRenderer.drawString(text, 164 - mc.fontRenderer.getStringWidth(text), 57, 0x404040);
		}
	}
}

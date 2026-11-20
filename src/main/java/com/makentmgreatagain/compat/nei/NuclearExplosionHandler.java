package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.Arrays;
import java.util.List;

import com.hbm.lib.RefStrings;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.NukeGeneratorData;
import com.makentmgreatagain.recipes.NukeGeneratorData.Breeding;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 次临界核爆炸发生器的 NEI 增殖页。
 *
 * <p>只展示「原料 → 产物」以及这条配方的最低 / 需求中子通量，一行一条：
 * 左边原料，中间短箭头，右边产物，机器图标放在箭头正下方。核爆参数（电力、钠、通量）
 * 属于核心，不是这里的内容。</p>
 *
 * <p>数据源就是运行时用的 {@link NukeGeneratorData#getRecipes()}，NEI 与机器不会各写一份。</p>
 */
public class NuclearExplosionHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.nuclear_explosion";

	private static final int INPUT_X = 30;
	private static final int OUTPUT_X = 96;
	private static final int SLOT_Y = 16;
	/** 机器图标放在中间箭头下面 */
	private static final int MACHINE_X = 64;
	private static final int MACHINE_Y = 37;
	/** 短箭头，和图里两支槽位同高 */
	private static final int ARROW_X = 63;
	private static final int ARROW_Y = 15;

	/** 一页 = 一条增殖配方 */
	public class BreedRecipe extends CachedRecipe {

		public final PositionedStack machine;
		public final PositionedStack input;
		public final PositionedStack output;
		public final int minFlux;
		public final int reqFlux;

		public BreedRecipe(Breeding recipe) {
			this.machine = new PositionedStack(
					new ItemStack(ModBlocks.machine_nuclear_explosion_generator), MACHINE_X, MACHINE_Y);
			this.input = new PositionedStack(recipe.input, INPUT_X, SLOT_Y);
			this.output = new PositionedStack(recipe.output, OUTPUT_X, SLOT_Y);
			this.minFlux = recipe.minFlux;
			this.reqFlux = recipe.reqFlux;
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return Arrays.asList(this.machine, this.input);
		}

		@Override
		public PositionedStack getResult() {
			return this.output;
		}
	}

	/* ---- 查询匹配 ---- */

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.machine_nuclear_explosion_generator);
	}

	private void buildAll() {
		arecipes.clear();
		for(Breeding recipe : NukeGeneratorData.getRecipes()) {
			arecipes.add(new BreedRecipe(recipe));
		}
	}

	private void buildByOutput(ItemStack result) {
		arecipes.clear();
		if(result == null) return;
		for(Breeding recipe : NukeGeneratorData.getRecipes()) {
			if(recipe.output.getItem() == result.getItem()) arecipes.add(new BreedRecipe(recipe));
		}
	}

	private void buildByInput(ItemStack ingredient) {
		arecipes.clear();
		if(ingredient == null) return;
		for(Breeding recipe : NukeGeneratorData.getRecipes()) {
			if(recipe.input.getItem() == ingredient.getItem()) arecipes.add(new BreedRecipe(recipe));
		}
	}

	/* ---- NEI 入口 ---- */

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if(KEY.equals(outputId)) {
			buildAll();
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		buildByOutput(result);
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if(KEY.equals(inputId)) {
			buildAll();
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		if(isMachine(ingredient)) buildAll();
		else buildByInput(ingredient);
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.nuclear_explosion.name");
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		drawSlot(INPUT_X, SLOT_Y);
		drawSlot(OUTPUT_X, SLOT_Y);
		drawSlot(MACHINE_X, MACHINE_Y);
		// 短箭头（gui_nei.png 里 18×18 的那支），正好在两支槽位中间
		drawTexturedModalRect(ARROW_X, ARROW_Y, 23, 87, 18, 18);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		BreedRecipe rec = (BreedRecipe) arecipes.get(recipe);
		// 面板是浅灰的，字只能用深灰；上下各一行，避开中间箭头和机器图标
		Minecraft.getMinecraft().fontRenderer.drawString(
				I18nUtil.resolveKey("nei.nuclear_explosion.min", rec.minFlux), 4, 2, 0x404040);
		Minecraft.getMinecraft().fontRenderer.drawString(
				I18nUtil.resolveKey("nei.nuclear_explosion.req", rec.reqFlux), 4, 55, 0x404040);
	}
}

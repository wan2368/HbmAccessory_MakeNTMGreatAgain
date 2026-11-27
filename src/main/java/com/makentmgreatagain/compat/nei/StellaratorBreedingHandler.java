package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.StellaratorBreeding;
import com.makentmgreatagain.recipes.StellaratorBreeding.FluidRecipe;
import com.makentmgreatagain.recipes.StellaratorBreeding.ItemRecipe;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 仿星器的 NEI「中子增殖」页。
 *
 * <h3>为什么单开一页</h3>
 * <p>这些配方只在中子通量下成立，而中子通量只有仿星器的等离子体反应能提供，
 * 所以它们<b>不属于</b>任何通用机器：既不是 {@code FluidBreederRecipes}（炼油厂那三条），
 * 也不是 {@code OutgasserRecipes}（照射室）。放进通用表就会在别的机器页里冒出来误导玩家，
 * 因此这里独立一个页签，页名直接带「仿星器」。</p>
 *
 * <h3>版面</h3>
 * <p>沿用 HBM 聚变页（{@code FusionRecipeHandler} → {@code NEIGenericRecipeHandler}）的布局：
 * 机器图标压在箭头中间、输入在左、产物在右，一页两条。液相配方是「流体图标 → 流体图标」，
 * 固相配方是「物品 → 物品 / 流体」，图标本身的 tooltip 已经带 mB 数量，所以只在左上角
 * 额外标一条中子通量门槛——这是唯一不写在图标上的信息。</p>
 */
public class StellaratorBreedingHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.stellarator_breeding";

	/** 与 HBM 通用页一致：机器压在竖箭头中间 */
	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;
	private static final int ARROW_X = 74;
	private static final int ARROW_Y = 14;

	private static final int IN_X = 48;
	private static final int IN_Y = 24;
	private static final int OUT_X = 102;
	private static final int OUT2_X = 120;
	private static final int OUT_Y = 24;

	public class BreedRecipe extends CachedRecipe {

		public final PositionedStack machine;
		public final PositionedStack input;
		public final List<PositionedStack> outputs = new ArrayList<PositionedStack>();
		public final double flux;

		/** 液相：{@code inputAmount} mB 原料 → {@code outputAmount} mB 产物 */
		public BreedRecipe(FluidRecipe recipe) {
			this.machine = new PositionedStack(new ItemStack(ModBlocks.stellarator), MACHINE_X, MACHINE_Y);
			this.input = new PositionedStack(ItemFluidIcon.make(recipe.input, recipe.inputAmount), IN_X, IN_Y);
			this.outputs.add(new PositionedStack(ItemFluidIcon.make(recipe.output, recipe.outputAmount), OUT_X, OUT_Y));
			this.flux = recipe.flux;
		}

		/** 固相：一件原料 → 一个物品和/或一罐流体；只有其中一个时靠左，两个都有时分两格 */
		public BreedRecipe(ItemRecipe recipe) {
			this.machine = new PositionedStack(new ItemStack(ModBlocks.stellarator), MACHINE_X, MACHINE_Y);

			List<ItemStack> in = recipe.input.extractForNEI();
			this.input = new PositionedStack(in.toArray(new ItemStack[in.size()]), IN_X, IN_Y);

			if(recipe.output != null) {
				this.outputs.add(new PositionedStack(recipe.output, OUT_X, OUT_Y));
			}
			if(recipe.fluidOutput != null) {
				int x = recipe.output == null ? OUT_X : OUT2_X;
				this.outputs.add(new PositionedStack(ItemFluidIcon.make(recipe.fluidOutput), x, OUT_Y));
			}

			this.flux = recipe.flux;
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return Arrays.asList(this.input);
		}

		@Override
		public PositionedStack getResult() {
			return this.outputs.get(0);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			List<PositionedStack> other = new ArrayList<PositionedStack>(this.outputs.subList(1, this.outputs.size()));
			other.add(this.machine);
			return other;
		}
	}

	/* ---- 建表 ---- */

	/**
	 * 矿辞里一件物品都没注册时（整合包删了某种材料）这条配方在机器里也永远匹配不上，
	 * 而且 {@code PositionedStack} 拿到空列表会直接抛异常——两边都跳掉最省事。
	 */
	private static boolean hasInput(ItemRecipe recipe) {

		List<ItemStack> in = recipe.input.extractForNEI();

		return in != null && !in.isEmpty();
	}

	private void buildAll() {
		arecipes.clear();
		for(FluidRecipe recipe : StellaratorBreeding.FLUID) arecipes.add(new BreedRecipe(recipe));
		for(ItemRecipe recipe : StellaratorBreeding.ITEM) {
			if(hasInput(recipe)) arecipes.add(new BreedRecipe(recipe));
		}
	}

	/**
	 * 按「产物」查（对着物品按 R）。
	 *
	 * <p>对着本机方块按 R 也算：R 本来是查「什么东西能做出它」，机器自己当然没有配方，
	 * 但玩家的习惯是拿着机器去查它有什么配方，所以这里直接给出全部路线。</p>
	 */
	private void buildByOutput(ItemStack result) {

		arecipes.clear();
		if(result == null) return;

		if(isMachine(result)) {
			buildAll();
			return;
		}

		// 流体图标：直接比 damage（= FluidType.ordinal()，ItemFluidIcon.make 就是这么写的），
		// 不走 Fluids.fromID，免得任何一个流体 id 与图标 damage 不一致时静默查不到
		if(isFluidIcon(result)) {
			int damage = result.getItemDamage();
			for(FluidRecipe recipe : StellaratorBreeding.FLUID) {
				if(recipe.output.ordinal() == damage) arecipes.add(new BreedRecipe(recipe));
			}
			for(ItemRecipe recipe : StellaratorBreeding.ITEM) {
				if(hasInput(recipe) && recipe.fluidOutput != null && recipe.fluidOutput.type.ordinal() == damage) {
					arecipes.add(new BreedRecipe(recipe));
				}
			}
			return;
		}

		for(ItemRecipe recipe : StellaratorBreeding.ITEM) {
			if(hasInput(recipe) && recipe.output != null && NEIServerUtils.areStacksSameTypeCrafting(recipe.output, result)) {
				arecipes.add(new BreedRecipe(recipe));
			}
		}
	}

	/** 按「原料」查（对着物品按 U） */
	private void buildByInput(ItemStack ingredient) {

		arecipes.clear();
		if(ingredient == null) return;

		if(isFluidIcon(ingredient)) {
			int damage = ingredient.getItemDamage();
			for(FluidRecipe recipe : StellaratorBreeding.FLUID) {
				if(recipe.input.ordinal() == damage) arecipes.add(new BreedRecipe(recipe));
			}
			return;
		}

		for(ItemRecipe recipe : StellaratorBreeding.ITEM) {
			if(hasInput(recipe) && recipe.input.matchesRecipe(ingredient, true)) {
				arecipes.add(new BreedRecipe(recipe));
			}
		}
	}

	private static boolean isFluidIcon(ItemStack stack) {
		return stack != null && stack.getItem() == ModItems.fluid_icon;
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.stellarator);
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
		// 对着仿星器按 U 看到全部增殖路线，其余情况按原料筛
		if(isMachine(ingredient)) buildAll();
		else buildByInput(ingredient);
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.stellarator_breeding.name");
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public int recipiesPerPage() {
		// 十五条配方，一页一条要翻十五页；面板高度只够两条
		return 2;
	}

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		BreedRecipe rec = (BreedRecipe) arecipes.get(recipe);

		drawSlot(IN_X, IN_Y);
		for(PositionedStack out : rec.outputs) drawSlot(out.relx, out.rely);
		drawSlot(MACHINE_X, MACHINE_Y);

		// HBM 通用配方页那支竖箭头
		drawTexturedModalRect(ARROW_X, ARROW_Y, 59, 87, 18, 36);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		BreedRecipe rec = (BreedRecipe) arecipes.get(recipe);
		// 通量门槛是图标上看不到的信息。写在面板底部的灰区里（面板实际内容从 y≈13 开始、
		// 到 y≈64 结束，y=2 那条是图集上的对齐标记，不是可写区域）
		Minecraft.getMinecraft().fontRenderer.drawString(
				I18nUtil.resolveKey("nei.stellarator_breeding.flux", (int) rec.flux), 8, 50, 0x404040);
	}
}

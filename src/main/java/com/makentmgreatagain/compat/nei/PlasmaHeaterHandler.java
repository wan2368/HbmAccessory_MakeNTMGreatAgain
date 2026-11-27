package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.gui.GUIPlasmaHeater;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.HydrogenFusion;
import com.makentmgreatagain.recipes.HydrogenFusion.HeaterFeed;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 等离子加热器的 NEI 配方页。
 *
 * <p>展示常温原料流体如何通过等离子加热器转化为对应等离子体：
 * <ul>
 * <li>机器图标居中（75, 31），背后是 HBM 标准竖箭头；</li>
 * <li>左边是原料流体槽（单流体在 48,24；双流体在 30,24 与 48,24）；</li>
 * <li>右边是等离子体输出槽（102, 24）；</li>
 * <li>下方右侧展示加热每 tick 的电量消耗；</li>
 * <li>鼠标悬停加热器图标时展示原料配比与详细说明。</li>
 * </ul>
 * </p>
 */
public class PlasmaHeaterHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.plasma_heater";

	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;
	private static final int ARROW_X = 74;
	private static final int ARROW_Y = 14;

	private static final int OUT_X = 102;
	private static final int OUT_Y = 24;

	public class HeaterCachedRecipe extends CachedRecipe {

		public final String recipeKey;
		public final HeaterFeed feed;
		public final long power;
		public final PositionedStack machine;
		public final List<PositionedStack> inputs = new ArrayList<PositionedStack>();
		public final PositionedStack output;

		public HeaterCachedRecipe(String recipeKey, HeaterFeed feed, long power) {
			this.recipeKey = recipeKey;
			this.feed = feed;
			this.power = power;
			this.machine = new PositionedStack(new ItemStack(ModBlocks.plasma_heater), MACHINE_X, MACHINE_Y);

			// 输入原料流体
			if(feed.inFluid.length == 1) {
				this.inputs.add(new PositionedStack(ItemFluidIcon.make(feed.inFluid[0], feed.inAmount[0]), 48, 24));
			} else if(feed.inFluid.length >= 2) {
				this.inputs.add(new PositionedStack(ItemFluidIcon.make(feed.inFluid[0], feed.inAmount[0]), 30, 24));
				this.inputs.add(new PositionedStack(ItemFluidIcon.make(feed.inFluid[1], feed.inAmount[1]), 48, 24));
			}

			// 输出等离子体
			this.output = new PositionedStack(ItemFluidIcon.make(feed.outFluid, feed.outAmount), OUT_X, OUT_Y);
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return this.inputs;
		}

		@Override
		public PositionedStack getResult() {
			return this.output;
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			return Arrays.asList(this.machine);
		}
	}

	/* ---- 建表与查询 ---- */

	private void ensureRecipesRegistered() {
		if(!HydrogenFusion.isRegistered()) {
			HydrogenFusion.addRecipe();
		}
	}

	private void buildAll() {
		arecipes.clear();
		ensureRecipesRegistered();

		// 代表性进料：氢气、氢-硼、反物质、约束态反物质、反Sa326
		String[] repRecipes = {
				HydrogenFusion.RECIPE_HE3,
				HydrogenFusion.RECIPE_HB,
				HydrogenFusion.RECIPE_AMAT,
				HydrogenFusion.RECIPE_PAMAT,
				HydrogenFusion.RECIPE_ASCHRAB
		};
		long[] powers = {
				HydrogenFusion.POWER,
				HydrogenFusion.HB_POWER,
				HydrogenFusion.AMAT_POWER,
				HydrogenFusion.PAMAT_POWER,
				HydrogenFusion.ASCHRAB_POWER
		};

		for(int i = 0; i < repRecipes.length; i++) {
			HeaterFeed feed = HydrogenFusion.heaterFeed(repRecipes[i]);
			if(feed != null) {
				arecipes.add(new HeaterCachedRecipe(repRecipes[i], feed, powers[i]));
			}
		}
	}

	private void buildByOutput(ItemStack result) {
		arecipes.clear();
		if(result == null) return;

		if(isMachine(result)) {
			buildAll();
			return;
		}

		ensureRecipesRegistered();

		if(isFluidIcon(result)) {
			int damage = result.getItemDamage();
			buildAll();
			List<CachedRecipe> matched = new ArrayList<CachedRecipe>();
			for(CachedRecipe cr : arecipes) {
				HeaterCachedRecipe hcr = (HeaterCachedRecipe) cr;
				if(hcr.feed.outFluid.ordinal() == damage) {
					matched.add(hcr);
				}
			}
			arecipes.clear();
			arecipes.addAll(matched);
		}
	}

	private void buildByInput(ItemStack ingredient) {
		arecipes.clear();
		if(ingredient == null) return;

		if(isMachine(ingredient)) {
			buildAll();
			return;
		}

		ensureRecipesRegistered();

		if(isFluidIcon(ingredient)) {
			int damage = ingredient.getItemDamage();
			buildAll();
			List<CachedRecipe> matched = new ArrayList<CachedRecipe>();
			for(CachedRecipe cr : arecipes) {
				HeaterCachedRecipe hcr = (HeaterCachedRecipe) cr;
				for(FluidType in : hcr.feed.inFluid) {
					if(in.ordinal() == damage) {
						matched.add(hcr);
						break;
					}
				}
			}
			arecipes.clear();
			arecipes.addAll(matched);
		}
	}

	private static boolean isFluidIcon(ItemStack stack) {
		return stack != null && stack.getItem() == ModItems.fluid_icon;
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.plasma_heater);
	}

	/* ---- NEI 回调 ---- */

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
		buildByInput(ingredient);
	}

	@Override
	public void loadTransferRects() {
		LinkedList<RecipeTransferRect> rects = new LinkedList<RecipeTransferRect>();
		LinkedList<Class<? extends GuiContainer>> guis = new LinkedList<Class<? extends GuiContainer>>();

		// 等离子加热器 GUI 中配方选择区 (42, 76, 18, 18) 与等离子缓冲柱 (88, 17, 26, 52)
		rects.add(new RecipeTransferRect(new Rectangle(42, 76, 18, 18), KEY));
		rects.add(new RecipeTransferRect(new Rectangle(88, 17, 26, 52), KEY));
		guis.add(GUIPlasmaHeater.class);

		RecipeTransferRectHandler.registerRectsToGuis(guis, rects);
	}

	/* ---- 渲染 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.plasma_heater.name");
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public int recipiesPerPage() {
		return 2;
	}

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		HeaterCachedRecipe rec = (HeaterCachedRecipe) arecipes.get(recipe);

		for(PositionedStack in : rec.inputs) drawSlot(in.relx, in.rely);
		drawSlot(rec.output.relx, rec.output.rely);
		drawSlot(MACHINE_X, MACHINE_Y);

		// HBM 标准竖向向下箭头
		drawTexturedModalRect(ARROW_X, ARROW_Y, 59, 87, 18, 36);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		HeaterCachedRecipe rec = (HeaterCachedRecipe) arecipes.get(recipe);
		FontRenderer font = Minecraft.getMinecraft().fontRenderer;

		int side = 164;
		String powerStr = I18nUtil.resolveKey("nei.plasma_heater.power", BobMathUtil.getShortNumber(rec.power));
		font.drawString(powerStr, side - font.getStringWidth(powerStr), 54, 0x902020);
	}

	@Override
	public List<String> handleItemTooltip(GuiRecipe gui, ItemStack stack, List<String> currenttip, int recipe) {
		if(recipe >= 0 && recipe < this.arecipes.size()) {
			HeaterCachedRecipe rec = (HeaterCachedRecipe) this.arecipes.get(recipe);
			if(stack != null && isMachine(stack) && rec.feed != null) {
				currenttip.add(EnumChatFormatting.YELLOW + I18nUtil.resolveKey(rec.recipeKey));
				StringBuilder feedDesc = new StringBuilder();
				for(int i = 0; i < rec.feed.inFluid.length; i++) {
					if(i > 0) feedDesc.append(" + ");
					feedDesc.append(rec.feed.inAmount[i]).append("mB ").append(rec.feed.inFluid[i].getLocalizedName());
				}
				currenttip.add(EnumChatFormatting.AQUA + "进料: " + feedDesc.toString());
				currenttip.add(EnumChatFormatting.GREEN + "产出: " + rec.feed.outAmount + "mB " + rec.feed.outFluid.getLocalizedName());
				currenttip.add(EnumChatFormatting.RED + "功耗: " + BobMathUtil.getShortNumber(rec.power) + "HE/t");
			}
		}
		return currenttip;
	}
}

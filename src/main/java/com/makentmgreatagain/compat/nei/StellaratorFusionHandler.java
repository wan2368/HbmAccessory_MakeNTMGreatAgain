package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.gui.GUIStellarator;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.recipes.HydrogenFusion;
import com.makentmgreatagain.recipes.HydrogenFusionRecipe;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 仿星器聚变反应的 NEI 配方页。
 *
 * <p>布局与原版聚变页（{@code FusionRecipeHandler} / {@code NEIGenericRecipeHandler}）
 * 风格完全一致：
 * <ul>
 * <li>机器图标居中（75, 31），背后是 HBM 标准竖箭头；</li>
 * <li>左边是输入槽（48, 24），展示输入的等离子体流体；</li>
 * <li>右边是产物槽（102, 24），展示输出的流体产物及附加物品产物；</li>
 * <li>下方两侧清晰展示关键参数：磁约束力量、等离子体热量、启动耗时与能量消耗；</li>
 * <li>鼠标悬停仿星器图标时展示完整的参数悬浮说明。</li>
 * </ul>
 * </p>
 */
public class StellaratorFusionHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.stellarator_fusion";

	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;
	private static final int ARROW_X = 74;
	private static final int ARROW_Y = 14;

	private static final int IN_X = 48;
	private static final int IN_Y = 24;
	private static final int OUT_X = 102;
	private static final int OUT_ITEM_X = 120;
	private static final int OUT_Y = 24;

	public class FusionCachedRecipe extends CachedRecipe {

		public final HydrogenFusionRecipe recipe;
		public final PositionedStack machine;
		public final PositionedStack input;
		public final List<PositionedStack> outputs = new ArrayList<PositionedStack>();

		public FusionCachedRecipe(HydrogenFusionRecipe recipe) {
			this.recipe = recipe;
			this.machine = new PositionedStack(new ItemStack(ModBlocks.stellarator), MACHINE_X, MACHINE_Y);

			// 输入：等离子体
			if(recipe.inputFluid != null && recipe.inputFluid.length > 0) {
				FluidStack in = recipe.inputFluid[0];
				this.input = new PositionedStack(ItemFluidIcon.make(in), IN_X, IN_Y);
			} else {
				this.input = null;
			}

			// 输出：流体产物
			if(recipe.outputFluid != null && recipe.outputFluid.length > 0) {
				this.outputs.add(new PositionedStack(ItemFluidIcon.make(recipe.outputFluid[0]), OUT_X, OUT_Y));
			}

			// 输出：物品产物（若有流体则偏移到右侧，若无流体则居首）
			if(recipe.outputItem != null && recipe.outputItem.length > 0) {
				int x = this.outputs.isEmpty() ? OUT_X : OUT_ITEM_X;
				this.outputs.add(new PositionedStack(recipe.outputItem[0].getAllPossibilities(), x, OUT_Y));
			}
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return this.input != null ? Arrays.asList(this.input) : new ArrayList<PositionedStack>();
		}

		@Override
		public PositionedStack getResult() {
			return this.outputs.isEmpty() ? null : this.outputs.get(0);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			List<PositionedStack> others = new ArrayList<PositionedStack>();
			if(this.outputs.size() > 1) {
				others.addAll(this.outputs.subList(1, this.outputs.size()));
			}
			others.add(this.machine);
			return others;
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

		String[] recipeNames = {
				HydrogenFusion.RECIPE_HE3,
				HydrogenFusion.RECIPE_HE4,
				HydrogenFusion.RECIPE_HB,
				HydrogenFusion.RECIPE_AMAT,
				HydrogenFusion.RECIPE_PAMAT,
				HydrogenFusion.RECIPE_ASCHRAB
		};

		for(String name : recipeNames) {
			GenericRecipe gr = FusionRecipes.INSTANCE.recipeNameMap.get(name);
			if(gr instanceof HydrogenFusionRecipe) {
				arecipes.add(new FusionCachedRecipe((HydrogenFusionRecipe) gr));
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

		boolean isFluid = isFluidIcon(result);
		int damage = result.getItemDamage();

		for(Object o : FusionRecipes.INSTANCE.recipeOrderedList) {
			if(o instanceof HydrogenFusionRecipe) {
				HydrogenFusionRecipe r = (HydrogenFusionRecipe) o;
				if(!HydrogenFusion.isOurRecipe(r.getInternalName())) continue;

				boolean match = false;
				if(isFluid && r.outputFluid != null) {
					for(FluidStack fs : r.outputFluid) {
						if(fs.type.ordinal() == damage) { match = true; break; }
					}
				}
				if(!match && r.outputItem != null) {
					for(IOutput iout : r.outputItem) {
						for(ItemStack out : iout.getAllPossibilities()) {
							if(NEIServerUtils.areStacksSameTypeCrafting(out, result)) { match = true; break; }
						}
						if(match) break;
					}
				}

				if(match) arecipes.add(new FusionCachedRecipe(r));
			}
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
			for(Object o : FusionRecipes.INSTANCE.recipeOrderedList) {
				if(o instanceof HydrogenFusionRecipe) {
					HydrogenFusionRecipe r = (HydrogenFusionRecipe) o;
					if(!HydrogenFusion.isOurRecipe(r.getInternalName())) continue;

					if(r.inputFluid != null) {
						for(FluidStack fs : r.inputFluid) {
							if(fs.type.ordinal() == damage) {
								arecipes.add(new FusionCachedRecipe(r));
								break;
							}
						}
					}
				}
			}
		}
	}

	private static boolean isFluidIcon(ItemStack stack) {
		return stack != null && stack.getItem() == ModItems.fluid_icon;
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(ModBlocks.stellarator);
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

		// 仿星器 GUI 中间箭头区 (94, 34, 18, 10)
		rects.add(new RecipeTransferRect(new Rectangle(94, 34, 18, 10), KEY));
		guis.add(GUIStellarator.class);

		RecipeTransferRectHandler.registerRectsToGuis(guis, rects);
	}

	/* ---- 渲染 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.stellarator_fusion.name");
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

		FusionCachedRecipe rec = (FusionCachedRecipe) arecipes.get(recipe);

		if(rec.input != null) drawSlot(rec.input.relx, rec.input.rely);
		for(PositionedStack out : rec.outputs) drawSlot(out.relx, out.rely);
		drawSlot(MACHINE_X, MACHINE_Y);

		// HBM 标准竖向向下箭头
		drawTexturedModalRect(ARROW_X, ARROW_Y, 59, 87, 18, 36);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		FusionCachedRecipe rec = (FusionCachedRecipe) arecipes.get(recipe);
		HydrogenFusionRecipe r = rec.recipe;
		FontRenderer font = Minecraft.getMinecraft().fontRenderer;

		// 左下角：约束力与热量输出
		String confinementStr = I18nUtil.resolveKey("nei.stellarator_fusion.confinement", BobMathUtil.getShortNumber(r.getConfinementPower()));
		font.drawString(confinementStr, 6, 44, 0x404040);

		String heatStr = I18nUtil.resolveKey("nei.stellarator_fusion.heat", BobMathUtil.getShortNumber(r.outputTemp));
		font.drawString(heatStr, 6, 54, 0x904090);
	}

	@Override
	public List<String> handleItemTooltip(GuiRecipe gui, ItemStack stack, List<String> currenttip, int recipe) {
		if(recipe >= 0 && recipe < this.arecipes.size()) {
			FusionCachedRecipe rec = (FusionCachedRecipe) this.arecipes.get(recipe);
			if(stack != null && isMachine(stack) && rec.recipe != null) {
				HydrogenFusionRecipe r = rec.recipe;
				currenttip.add(EnumChatFormatting.YELLOW + r.getLocalizedName());
				currenttip.add(EnumChatFormatting.RED + I18nUtil.resolveKey("gui.recipe.fusionPower") + ": " + BobMathUtil.getShortNumber(r.power) + "HE/t");
				currenttip.add(EnumChatFormatting.BLUE + I18nUtil.resolveKey("nei.stellarator_fusion.confinement", BobMathUtil.getShortNumber(r.getConfinementPower())) + "HE/t");
				currenttip.add(EnumChatFormatting.AQUA + I18nUtil.resolveKey("nei.stellarator_fusion.startup", (r.getStartupTicks() / 20)) + " (" + r.getStartupTicks() + " tick)");
				currenttip.add(EnumChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("gui.recipe.fusionOut") + ": " + BobMathUtil.getShortNumber(r.outputTemp) + "TU/t");
				currenttip.add(EnumChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("gui.recipe.fusionFlux") + ": " + (((int)(r.neutronFlux * 10)) / 10D) + " flux/t");
			}
		}
		return currenttip;
	}
}

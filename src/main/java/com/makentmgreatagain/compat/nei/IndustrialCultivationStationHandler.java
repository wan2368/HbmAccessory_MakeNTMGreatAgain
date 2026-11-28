package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.blocks.generic.BlockNTMFlower.EnumFlowerType;
import com.hbm.lib.RefStrings;
import com.hbm.util.i18n.I18nUtil;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 工业种植站的 NEI 配方页。
 *
 * <p>展示「一株作物种子 -> 一次自动收获的产出」：
 * <ul>
 * <li>机器图标居中（75, 31），背后是 HBM 标准竖箭头；</li>
 * <li>左边是作物槽里的种子（48, 24）；</li>
 * <li>右边是输出槽里的产物（102, 24）。</li>
 * </ul>
 *
 * <p>页面里不写字、也不注册 RecipeTransferRect：机器 GUI 上不要出现 NEI 的配方按钮。</p>
 *
 * <p>注意：下面的产出表必须与
 * {@code TileEntityIndustrialCultivationStation#computeHarvestDrops} 保持一致，
 * 那边改动时这里要同步。</p>
 */
public class IndustrialCultivationStationHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.industrial_cultivation_station";

	private static final int MACHINE_X = 75;
	private static final int MACHINE_Y = 31;
	private static final int ARROW_X = 74;
	private static final int ARROW_Y = 14;

	private static final int IN_X = 48;
	private static final int IN_Y = 24;
	private static final int OUT_X = 102;
	private static final int OUT_Y = 24;

	/** 一条配方：种下去的种子 + 一次收获的产物（基础产量，不含肥料 / 二氧化碳加成） */
	private static final class CropRecipe {
		public final ItemStack seed;
		public final ItemStack output;

		CropRecipe(ItemStack seed, ItemStack output) {
			this.seed = seed;
			this.output = output;
		}
	}

	public class CultivationCachedRecipe extends CachedRecipe {

		public final CropRecipe recipe;
		public final PositionedStack machine;
		public final PositionedStack seed;
		public final PositionedStack output;

		public CultivationCachedRecipe(CropRecipe recipe) {
			this.recipe = recipe;
			this.machine = new PositionedStack(new ItemStack(com.makentmgreatagain.init.ModBlocks.industrial_cultivation_station), MACHINE_X, MACHINE_Y);
			this.seed = new PositionedStack(recipe.seed, IN_X, IN_Y);
			this.output = new PositionedStack(recipe.output, OUT_X, OUT_Y);
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return Arrays.asList(this.seed);
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

	/**
	 * 工业种植站支持的作物表。
	 *
	 * <p>顺序与 {@code computeHarvestDrops} 的分支顺序一致，便于对照维护。</p>
	 */
	private static List<CropRecipe> baseRecipes() {
		List<CropRecipe> list = new ArrayList<CropRecipe>();

		// 原版作物
		list.add(new CropRecipe(new ItemStack(Items.wheat_seeds), new ItemStack(Items.wheat, 2)));
		list.add(new CropRecipe(new ItemStack(Items.carrot), new ItemStack(Items.carrot, 3)));
		list.add(new CropRecipe(new ItemStack(Items.potato), new ItemStack(Items.potato, 3)));
		list.add(new CropRecipe(new ItemStack(Items.melon_seeds), new ItemStack(Items.melon, 4)));
		list.add(new CropRecipe(new ItemStack(Items.pumpkin_seeds), new ItemStack(Blocks.pumpkin, 1)));
		list.add(new CropRecipe(new ItemStack(Items.reeds), new ItemStack(Items.reeds, 3)));
		list.add(new CropRecipe(new ItemStack(Items.nether_wart), new ItemStack(Items.nether_wart, 3)));
		list.add(new CropRecipe(new ItemStack(Items.dye, 1, 3), new ItemStack(Items.dye, 3, 3)));	// 可可豆

		// 仙人掌与蘑菇
		list.add(new CropRecipe(new ItemStack(Blocks.cactus), new ItemStack(Blocks.cactus, 2)));
		list.add(new CropRecipe(new ItemStack(Blocks.brown_mushroom), new ItemStack(Blocks.brown_mushroom, 2)));
		list.add(new CropRecipe(new ItemStack(Blocks.red_mushroom), new ItemStack(Blocks.red_mushroom, 2)));

		// HBM 大麻（WEED）
		list.add(new CropRecipe(new ItemStack(com.hbm.blocks.ModBlocks.plant_flower, 1, EnumFlowerType.WEED.ordinal()),
				new ItemStack(com.hbm.blocks.ModBlocks.plant_flower, 2, EnumFlowerType.WEED.ordinal())));

		// 原版向日葵：一株只掉 1 个自身，不产种子
		list.add(new CropRecipe(new ItemStack(Blocks.double_plant, 1, 0), new ItemStack(Blocks.double_plant, 1, 0)));

		return list;
	}

	private void buildAll() {
		arecipes.clear();
		for(CropRecipe recipe : baseRecipes()) {
			arecipes.add(new CultivationCachedRecipe(recipe));
		}
	}

	private void buildByOutput(ItemStack result) {
		arecipes.clear();
		if(result == null) return;

		if(isMachine(result)) {
			buildAll();
			return;
		}

		for(CropRecipe recipe : baseRecipes()) {
			if(NEIServerUtils.areStacksSameTypeCrafting(recipe.output, result)) {
				arecipes.add(new CultivationCachedRecipe(recipe));
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

		for(CropRecipe recipe : baseRecipes()) {
			if(NEIServerUtils.areStacksSameTypeCrafting(recipe.seed, ingredient)) {
				arecipes.add(new CultivationCachedRecipe(recipe));
			}
		}
	}

	private static boolean isMachine(ItemStack stack) {
		return stack != null && stack.getItem() == Item.getItemFromBlock(com.makentmgreatagain.init.ModBlocks.industrial_cultivation_station);
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

	/* ---- 渲染 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.industrial_cultivation_station.name");
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

		CultivationCachedRecipe rec = (CultivationCachedRecipe) arecipes.get(recipe);

		drawSlot(rec.seed.relx, rec.seed.rely);
		drawSlot(rec.output.relx, rec.output.rely);
		drawSlot(MACHINE_X, MACHINE_Y);

		// HBM 标准竖向向下箭头
		drawTexturedModalRect(ARROW_X, ARROW_Y, 59, 87, 18, 36);
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}
}

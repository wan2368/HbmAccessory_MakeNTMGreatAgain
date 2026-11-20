package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.i18n.I18nUtil;

import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 海面立管钻机的 NEI 页：深海结核开采。
 *
 * <p>展示「机器 + 深海结核混合物 → 海水/尾砂浆/重砂浆 + 三种精矿」，
 * 在 NEI 里对着三种精矿或三种流体按 R、对着钻机或结核方块按 U 都会打开这一页。</p>
 *
 * <p>复用 HBM 的 NEI 背景（{@code gui_nei.png}）：左槽放机器和结核，中间画箭头，
 * 右边两排输出——上排三种流体，下排三种精矿，下面标概率。</p>
 */
public class RiserRigHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.riser_rig";

	/** 输入槽：钻机、结核方块 */
	private static final int RIG_X = 12;
	private static final int RIG_Y = 24;
	private static final int ORE_X = 30;
	private static final int ORE_Y = 24;

	/** 输出列：三列等距，液体在上排、物品在下排 */
	private static final int[] OUT_X = {96, 120, 144};
	private static final int FLUID_Y = 6;
	private static final int ITEM_Y = 42;

	public class RiserRecipe extends CachedRecipe {

		public final PositionedStack rig;
		public final PositionedStack ore;
		public final List<PositionedStack> outputs = new ArrayList<PositionedStack>();

		public RiserRecipe() {
			this.rig = new PositionedStack(new ItemStack(ModBlocks.riser_rig), RIG_X, RIG_Y);
			this.ore = new PositionedStack(new ItemStack(ModBlocks.deep_sea_nodule), ORE_X, ORE_Y);

			// 与 TileEntityRiserRig.ORE_FLUID_YIELD 保持一致
			addFluid(0, ModFluids.SEA_WATER.type, 8_000);
			addFluid(1, ModFluids.TAILINGS_SLURRY.type, 3_000);
			addFluid(2, ModFluids.HEAVY_SAND_SLURRY.type, 1_000);

			this.outputs.add(new PositionedStack(new ItemStack(com.makentmgreatagain.init.ModItems.cobalt_polymetallic_concentrate), OUT_X[0], ITEM_Y));
			this.outputs.add(new PositionedStack(new ItemStack(com.makentmgreatagain.init.ModItems.heavy_sand_concentrate), OUT_X[1], ITEM_Y));
			this.outputs.add(new PositionedStack(new ItemStack(com.makentmgreatagain.init.ModItems.rare_earth_concentrate), OUT_X[2], ITEM_Y));
		}

		private void addFluid(int column, FluidType type, int amount) {
			if(type == null) return;
			this.outputs.add(new PositionedStack(ItemFluidIcon.make(type, amount), OUT_X[column], FLUID_Y));
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return Arrays.asList(this.rig, this.ore);
		}

		@Override
		public PositionedStack getResult() {
			return this.outputs.get(0);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			return this.outputs.subList(1, this.outputs.size());
		}
	}

	/* ---- 查询匹配 ---- */

	private static boolean isFluidOutput(ItemStack stack) {
		if(stack == null || stack.getItem() != ModItems.fluid_icon) return false;
		FluidType type = Fluids.fromID(stack.getItemDamage());
		return type == ModFluids.SEA_WATER.type
				|| type == ModFluids.TAILINGS_SLURRY.type
				|| type == ModFluids.HEAVY_SAND_SLURRY.type;
	}

	private static boolean isItemOutput(ItemStack stack) {
		if(stack == null) return false;
		Item item = stack.getItem();
		return item == com.makentmgreatagain.init.ModItems.cobalt_polymetallic_concentrate
				|| item == com.makentmgreatagain.init.ModItems.heavy_sand_concentrate
				|| item == com.makentmgreatagain.init.ModItems.rare_earth_concentrate;
	}

	private static boolean isMachineOrOre(ItemStack stack) {
		if(stack == null) return false;
		Item item = stack.getItem();
		return item == Item.getItemFromBlock(ModBlocks.riser_rig)
				|| item == Item.getItemFromBlock(ModBlocks.deep_sea_nodule);
	}

	private void buildOne() {
		arecipes.clear();
		arecipes.add(new RiserRecipe());
	}

	/* ---- NEI 入口 ---- */

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if(KEY.equals(outputId)) {
			buildOne();
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		if(isItemOutput(result) || isFluidOutput(result)) {
			buildOne();
		}
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if(KEY.equals(inputId)) {
			buildOne();
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		if(isMachineOrOre(ingredient)) {
			buildOne();
		}
	}

	/* ---- 绘制 ---- */

	@Override
	public String getRecipeName() {
		return I18nUtil.resolveKey("nei.riser_rig.name");
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		drawSlot(RIG_X, RIG_Y);
		drawSlot(ORE_X, ORE_Y);
		// HBM 通用配方页同一支箭头
		drawTexturedModalRect(72, 14, 59, 87, 18, 36);
		for(int x : OUT_X) {
			drawSlot(x, FLUID_Y);
			drawSlot(x, ITEM_Y);
		}
	}

	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x - 1, y - 1, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		Minecraft.getMinecraft().fontRenderer.drawString(
				I18nUtil.resolveKey("nei.riser_rig.cycle"), 6, 7, 0x404040);

		// 流体图标下方标产量、精矿图标下方标概率
		int yFluid = FLUID_Y + 18;
		int yItem = ITEM_Y + 18;
		drawAmount(OUT_X[0], yFluid, "8000");
		drawAmount(OUT_X[1], yFluid, "3000");
		drawAmount(OUT_X[2], yFluid, "1000");
		drawAmount(OUT_X[0], yItem, "30%");
		drawAmount(OUT_X[1], yItem, "40%");
		drawAmount(OUT_X[2], yItem, "65%");
	}

	private static void drawAmount(int x, int y, String text) {
		Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, 0x404040);
	}
}

package com.makentmgreatagain.compat.nei;

import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;
import com.hbm.util.i18n.I18nUtil;

import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.util.EnvironmentPumpMedia;
import com.makentmgreatagain.util.EnvironmentPumpMedia.Medium;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 环境地下泵的 NEI 页。
 *
 * 只回答一个问题：这台泵能抽出哪些流体。页面上不放群系、产量、耗电 ——
 * 那些都在 F1 手册里（环境地下泵 · 介质对照表），NEI 里塞进去只会变成一大坨字。
 * 五个等级各占一页，同一等级的介质排成一行，不同等级之间靠页首标签区分。
 *
 * 之所以自己画而不用 NEIUniversalHandler：那个模板是「左进右出」的固定布局，
 * 而泵的大部分介质没有输入液体，左半边会整片空掉，看起来像坏了。
 */
public class EnvironmentPumpHandler extends TemplateRecipeHandler {

	public static final String KEY = "makentmgreatagain.environment_pump";

	/** 泵图标所在格（PositionedStack 用格子上沿坐标，格框往外扩 1 像素） */
	private static final int PUMP_X = 7;
	private static final int PUMP_Y = 24;
	/** 产物格起点与间距，与泵同一行 */
	private static final int OUT_X = 47;
	private static final int OUT_Y = 24;
	private static final int OUT_STEP = 18;

	private static final int TIER_MIN = 1;
	private static final int TIER_MAX = 5;

	/** 一页 = 一个等级：泵 + 该等级的介质（同一种流体只出现一次，三种辐射坑共用一个图标） */
	public class PumpRecipe extends CachedRecipe {

		public final int tier;
		public final PositionedStack pump;
		public final List<FluidType> fluids = new ArrayList<FluidType>();
		public final List<PositionedStack> outputs = new ArrayList<PositionedStack>();

		public PumpRecipe(int tier, List<FluidType> types) {
			this.tier = tier;
			this.pump = new PositionedStack(new ItemStack(ModBlocks.environment_pump), PUMP_X, PUMP_Y);

			for(int i = 0; i < types.size(); i++) {
				FluidType type = types.get(i);
				this.fluids.add(type);
				// 数量给 0，图标就只显示流体本身，不显示 mB —— 产量归手册管
				this.outputs.add(new PositionedStack(ItemFluidIcon.make(type, 0), OUT_X + i * OUT_STEP, OUT_Y));
			}
		}

		@Override
		public PositionedStack getResult() {
			return null;
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return Arrays.asList(pump);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			return outputs;
		}
	}

	@Override
	public String getRecipeName() {
		return new ItemStack(ModBlocks.environment_pump).getDisplayName();
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei.png";
	}

	// ---------------- 数据 ----------------

	/** 该等级能抽到的流体，按介质表顺序去重 */
	private static List<FluidType> fluidsOfTier(int tier) {
		List<FluidType> list = new ArrayList<FluidType>();
		Set<FluidType> seen = new HashSet<FluidType>();

		for(Medium m : EnvironmentPumpMedia.all()) {
			if(m.tier != tier || m.output == null || m.output == Fluids.NONE) continue;
			if(seen.add(m.output)) list.add(m.output);
		}

		return list;
	}

	private void buildAll() {
		arecipes.clear();
		for(int tier = TIER_MIN; tier <= TIER_MAX; tier++) {
			List<FluidType> types = fluidsOfTier(tier);
			if(!types.isEmpty()) arecipes.add(new PumpRecipe(tier, types));
		}
	}

	/** 只留下含有该物品的等级页：用于「用在哪」和「怎么来」两种查询 */
	private void buildMatching(ItemStack stack, boolean matchPump) {
		arecipes.clear();
		if(stack == null) return;

		// 对泵按 U 才列出全部介质；按 R 是「怎么做出来的」，那里只该有合成表
		if(matchPump && stack.getItem() == Item.getItemFromBlock(ModBlocks.environment_pump)) {
			buildAll();
			return;
		}
		if(stack.getItem() != ModItems.fluid_icon) return;

		FluidType type = Fluids.fromID(stack.getItemDamage());
		for(int tier = TIER_MIN; tier <= TIER_MAX; tier++) {
			List<FluidType> types = fluidsOfTier(tier);
			if(types.contains(type)) arecipes.add(new PumpRecipe(tier, types));
		}
	}

	// ---------------- NEI 入口 ----------------

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
		buildMatching(result, false);
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
		buildMatching(ingredient, true);
	}

	// ---------------- 绘制 ----------------

	@Override
	public void drawBackground(int recipe) {
		super.drawBackground(recipe);

		PumpRecipe rec = (PumpRecipe) arecipes.get(recipe);
		drawSlot(PUMP_X - 1, PUMP_Y - 1);
		for(PositionedStack stack : rec.outputs) {
			drawSlot(stack.relx - 1, stack.rely - 1);
		}
	}

	/** 与 HBM 通用配方页同一个空槽贴图 */
	private static void drawSlot(int x, int y) {
		drawTexturedModalRect(x, y, 5, 87, 18, 18);
	}

	@Override
	public void drawExtras(int recipe) {
		PumpRecipe rec = (PumpRecipe) arecipes.get(recipe);
		// 面板是浅灰的，字只能用深灰
		Minecraft.getMinecraft().fontRenderer.drawString(
				I18nUtil.resolveKey("nei.environment_pump.tier." + rec.tier), 6, 8, 0x404040);
	}
}

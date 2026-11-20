package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.makentmgreatagain.init.HBMCompat;
import com.makentmgreatagain.init.ModFluids;
import com.makentmgreatagain.init.ModItems;

import net.minecraft.item.ItemStack;

/**
 * 大型离心机的独立配方表。
 *
 * <p>这里只放这台机器自己的配方，不并入化工厂/离心机本体。
 * 添加新配方就在静态块里照抄一条：最多 2 种物品 + 1 种流体进料，
 * 最多 8 个物品 + 4 种流体产出。</p>
 *
 * <p>除了自定义配方，原版离心机的所有配方也会被自动识别并转成这台机器的配方。</p>
 */
public class LargeCentrifugeRecipes {

	public static final List<CentrifugeRecipe> RECIPES = new ArrayList<CentrifugeRecipe>();

	/** 原版配方转成的大型机配方缓存，键就是原版表里的 AStack */
	private static final Map<AStack, CentrifugeRecipe> vanillaCache = new HashMap<AStack, CentrifugeRecipe>();

	/** 原版配方转换后的列表，给 NEI 展示用 */
	private static final List<CentrifugeRecipe> VANILLA = new ArrayList<CentrifugeRecipe>();
	private static int vanillaSize = -1;

	static {

		// 白钨酸离心：水洗后分出钨粉与砷渣，同时产出酸液和蒸汽
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.tungstic_acid, 4))
			.fluid(new FluidStack(Fluids.WATER, 1_000))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_tungsten, 2),
					  new ItemStack(ModItems.arsenic_trioxide, 1))
			.outFluids(new FluidStack(Fluids.SULFURIC_ACID, 500),
					   new FluidStack(Fluids.STEAM, 500),
					   new FluidStack(Fluids.WATER, 250),
					   new FluidStack(Fluids.HYDROGEN, 100))
			.power(10_000L)
			.duration(180));

		// 六氟化铀：直接离心出氟石粉与铀粒，不需要中转
		RECIPES.add(new CentrifugeRecipe()
			.fluid(new FluidStack(Fluids.UF6, 2_400))
			.outItems(new ItemStack(com.hbm.items.ModItems.fluorite, 6),
					  new ItemStack(com.hbm.items.ModItems.nugget_u238, 12),
					  new ItemStack(com.hbm.items.ModItems.nugget_u235, 2))
			.power(10_000L)
			.duration(180));

		// 六氟化钚：直接离心出氟石粉、钚238、反应堆级钚与钚239
		RECIPES.add(new CentrifugeRecipe()
			.fluid(new FluidStack(Fluids.PUF6, 1_800))
			.outItems(new ItemStack(com.hbm.items.ModItems.fluorite, 5),
					  new ItemStack(com.hbm.items.ModItems.nugget_pu238, 4),
					  new ItemStack(com.hbm.items.ModItems.nugget_pu_mix, 6),
					  new ItemStack(com.hbm.items.ModItems.nugget_pu239, 2))
			.power(10_000L)
			.duration(180));
	}

	/**
	 * 附属产线的配套配方，由 ModRecipes.init 在兼容层就绪后调用。
	 *
	 * <p>盐酸要靠 HBMCompat.hcl() 运行时取，不能写进静态块。</p>
	 */
	public static void registerRecipes() {

		FluidType hcl = HBMCompat.hcl();

		// 灰尘、铁粉、铀粉、水
		RECIPES.add(new CentrifugeRecipe()
			.fluid(new FluidStack(ModFluids.TAILINGS_SLURRY.type, 2_000))
			.outItems(new ItemStack(com.hbm.items.ModItems.dust, 3),
					  new ItemStack(com.hbm.items.ModItems.powder_iron, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_uranium, 1))
			.outFluids(new FluidStack(Fluids.WATER, 1_200))
			.power(10_000L)
			.duration(180));

		// 重砂精矿、灰尘、水
		RECIPES.add(new CentrifugeRecipe()
			.fluid(new FluidStack(ModFluids.HEAVY_SAND_SLURRY.type, 1_500))
			.outItems(new ItemStack(ModItems.heavy_sand_concentrate, 2),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.outFluids(new FluidStack(Fluids.WATER, 900))
			.power(10_000L)
			.duration(180));

		// 重砂精矿、铂族精矿、灰尘、水
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(com.hbm.items.ModItems.powder_coal, 1))
			.fluid(new FluidStack(ModFluids.HEAVY_SAND_SLURRY.type, 1_500))
			.outItems(new ItemStack(ModItems.heavy_sand_concentrate, 3),
					  new ItemStack(ModItems.pgm_concentrate, 1),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.outFluids(new FluidStack(Fluids.WATER, 600))
			.power(10_000L)
			.duration(180));

		// 灰尘、氯气、盐酸、四氯化钛（四氯化钛经兼容层取用：旧版 HBM 由附属补注册）
		com.hbm.inventory.fluid.FluidType ticl4 = com.makentmgreatagain.compat.HBMVersionCompat.titaniumTetrachloride();
		if(hcl != Fluids.NONE && ticl4 != Fluids.NONE) {
			RECIPES.add(new CentrifugeRecipe()
				.fluid(new FluidStack(ModFluids.TAILINGS_CHLORIDE.type, 1_000))
				.outItems(new ItemStack(com.hbm.items.ModItems.dust, 3))
				.outFluids(new FluidStack(Fluids.CHLORINE, 300),
						   new FluidStack(hcl, 150),
						   new FluidStack(ticl4, 300))
				.power(10_000L)
				.duration(180));
		}

		// 钛粉、锆粉、铂族精矿、铍粉、灰尘
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.heavy_sand_concentrate, 2))
			.fluid(new FluidStack(Fluids.SOLVENT, 500))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_titanium, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_zirconium, 1),
					  new ItemStack(ModItems.pgm_concentrate, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_beryllium, 1),
					  new ItemStack(com.hbm.items.ModItems.dust, 2))
			.power(10_000L)
			.duration(180));

		// 小撮钕粉、小撮镧粉、铈粉、灰尘
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.rare_earth_concentrate, 2))
			.fluid(new FluidStack(Fluids.SULFURIC_ACID, 500))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 2),
					  new ItemStack(com.hbm.items.ModItems.powder_lanthanium_tiny, 2),
					  new ItemStack(com.hbm.items.ModItems.powder_cerium, 1),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.power(10_000L)
			.duration(180));

		// 钍粉、铀粉、稀土母液、灰尘
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.thorium_concentrate, 2))
			.fluid(new FluidStack(Fluids.NITRIC_ACID, 500))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_thorium, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_uranium, 1),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.outFluids(new FluidStack(ModFluids.RE_MOTHER_LIQUOR.type, 400))
			.power(10_000L)
			.duration(180));

		// 金粉、铋粉、粗铂液、灰尘
		if(hcl != Fluids.NONE) {
			RECIPES.add(new CentrifugeRecipe()
				.items(new ComparableStack(ModItems.pgm_concentrate, 1))
				.fluid(new FluidStack(hcl, 400))
				.outItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 2),
						  new ItemStack(com.hbm.items.ModItems.powder_bismuth, 1),
						  new ItemStack(com.hbm.items.ModItems.dust, 1))
				.outFluids(new FluidStack(ModFluids.CRUDE_PLATINUM.type, 800))
				.power(10_000L)
				.duration(180));
		}

		// 镉粉、铜粉、硫酸锌溶液
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.powder_zinc_isp, 2))
			.fluid(new FluidStack(ModFluids.CADMIUM_LIQUOR.type, 1_000))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_cadmium, 5),
					  new ItemStack(com.hbm.items.ModItems.powder_copper, 1))
			.outFluids(new FluidStack(ModFluids.ZINC_SULFATE.type, 700))
			.power(10_000L)
			.duration(180));

		// 金粉、铋粒、硫粉、硫酸锌溶液
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.powder_zinc_isp, 2))
			.fluid(new FluidStack(ModFluids.GOLD_THIOSULFATE.type, 1_000))
			.outItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 9),
					  new ItemStack(com.hbm.items.ModItems.nugget_bismuth, 2),
					  new ItemStack(com.hbm.items.ModItems.sulfur, 1))
			.outFluids(new FluidStack(ModFluids.ZINC_SULFATE.type, 700))
			.power(10_000L)
			.duration(180));

		// 铂粉、金粉、硫酸
		RECIPES.add(new CentrifugeRecipe()
			.fluid(new FluidStack(ModFluids.CRUDE_PLATINUM.type, 1_000))
			.outItems(new ItemStack(ModItems.powder_platinum, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_gold, 1))
			.outFluids(new FluidStack(Fluids.SULFURIC_ACID, 400))
			.power(10_000L)
			.duration(180));

		// 氢氧化钴、石膏、铜粉、盐水
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(com.hbm.items.ModItems.powder_calcium, 2))
			.fluid(new FluidStack(ModFluids.COBALT_LIQUOR.type, 1_000))
			.outItems(new ItemStack(ModItems.cobalt_hydroxide, 2),
					  new ItemStack(ModItems.gypsum, 1),
					  new ItemStack(com.hbm.items.ModItems.powder_copper, 1))
			.outFluids(new FluidStack(ModFluids.SALT_WATER.type, 300))
			.power(10_000L)
			.duration(180));

		// 混合稀土氢氧化物、盐水、灰尘
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(com.hbm.items.ModItems.powder_sodium, 3))
			.fluid(new FluidStack(ModFluids.RE_MOTHER_LIQUOR.type, 1_000))
			.outItems(new ItemStack(ModItems.mixed_re_hydroxide, 2),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.outFluids(new FluidStack(ModFluids.SALT_WATER.type, 600))
			.power(10_000L)
			.duration(180));

		// 白砷、硫酸钠、灰尘
		RECIPES.add(new CentrifugeRecipe()
			.items(new ComparableStack(ModItems.arsenic_cake, 2))
			.fluid(new FluidStack(Fluids.WATER, 500))
			.outItems(new ItemStack(ModItems.arsenic_trioxide, 2),
					  new ItemStack(ModItems.powder_sodium_sulfate, 1),
					  new ItemStack(com.hbm.items.ModItems.dust, 1))
			.power(10_000L)
			.duration(180));
	}

	/** 找吃这个物品的配方：先查自定义表，再查原版离心机配方 */
	public static CentrifugeRecipe forItem(ItemStack stack) {

		if(stack == null) return null;

		for(CentrifugeRecipe recipe : RECIPES) {
			if(recipe.matchesItem(stack)) return recipe;
		}

		return forVanillaItem(stack);
	}

	private static CentrifugeRecipe forVanillaItem(ItemStack stack) {

		for(Map.Entry<AStack, ItemStack[]> entry : CentrifugeRecipes.recipes.entrySet()) {
			if(!entry.getKey().matchesRecipe(stack, true)) continue;

			CentrifugeRecipe cached = vanillaCache.get(entry.getKey());
			if(cached == null) {
				cached = new CentrifugeRecipe()
						.items(entry.getKey())
						.outItems(entry.getValue())
						.power(10_000L)
						.duration(180);
				vanillaCache.put(entry.getKey(), cached);
			}

			return cached;
		}

		return null;
	}

	/** 自定义配方 + 原版离心机全部转换配方，NEI 页面用 */
	public static List<CentrifugeRecipe> getAll() {

		// 原版表在 init 阶段才填好，这里按条目数判断是否需要重建
		if(vanillaSize != CentrifugeRecipes.recipes.size()) {
			vanillaSize = CentrifugeRecipes.recipes.size();
			VANILLA.clear();
			vanillaCache.clear();

			for(Map.Entry<AStack, ItemStack[]> entry : CentrifugeRecipes.recipes.entrySet()) {
				CentrifugeRecipe recipe = new CentrifugeRecipe()
						.items(entry.getKey())
						.outItems(entry.getValue())
						.power(10_000L)
						.duration(180);
				VANILLA.add(recipe);
				vanillaCache.put(entry.getKey(), recipe);
			}
		}

		List<CentrifugeRecipe> all = new ArrayList<CentrifugeRecipe>(RECIPES);
		all.addAll(VANILLA);
		return all;
	}

	/** 找吃这种流体的配方：只返回纯流体配方，要求物品的配方由物品线负责 */
	public static CentrifugeRecipe forFluid(FluidType type, int amount) {

		if(type == null) return null;

		for(CentrifugeRecipe recipe : RECIPES) {
			if(!recipe.inputItems.isEmpty()) continue;
			if(recipe.matchesFluid(type, amount)) return recipe;
		}

		return null;
	}

	/**
	 * 同上，但<b>压力必须精确匹配</b>。
	 *
	 * <p>同一种流体可能同时存在「不限压」和「2PU」两条配方，只按类型找会选错。
	 * 机器侧用这个重载，按进料罐当前 PU 取对应配方。</p>
	 */
	public static CentrifugeRecipe forFluid(FluidType type, int amount, int pressure) {

		if(type == null) return null;

		for(CentrifugeRecipe recipe : RECIPES) {
			if(!recipe.inputItems.isEmpty()) continue;
			if(recipe.inputFluid == null || recipe.inputFluid.pressure != pressure) continue;
			if(recipe.matchesFluid(type, amount)) return recipe;
		}

		return null;
	}
}


package com.makentmgreatagain.init;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.items.ItemCoating;
import com.makentmgreatagain.recipes.RecipeRegistrar;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

import static com.hbm.inventory.OreDictManager.ANY_RESISTANTALLOY;
import static com.hbm.inventory.OreDictManager.CMB;
import static com.hbm.inventory.OreDictManager.CU;
import static com.hbm.inventory.OreDictManager.DNT;
import static com.hbm.inventory.OreDictManager.DURA;
import static com.hbm.inventory.OreDictManager.EUPH;
import static com.hbm.inventory.OreDictManager.SA326;
import static com.hbm.inventory.OreDictManager.W;

/**
 * 六级机器覆层：接手 HBM 那四个 {@code fusion_shield_*} 遗留物的位置。
 *
 * <h3>和原版的关系</h3>
 * <p>原版的聚变反应堆覆层是「耐久 + 熔点」的消耗件，但读取它的机器代码在 5852 里已经不存在了
 * （全库只剩下 {@code TileEntityPADipole} 里一条没人用的 import）。所以这里不复用
 * {@code ItemFusionShield}，而是新写 {@link ItemCoating}：<b>机器部件，带内部流体容量</b>，
 * 前五级从 512,000 mB 逐级翻倍到 8,192,000 mB；第六级「曼巴覆层」是彩蛋件，
 * 容量只有 24 mB、没有配方，材质用耐摔合金的橄榄土黄。</p>
 *
 * <p>目前只做到「物品 + 装配机配方」，还没有机器会去读它——这一步等以后适配再做。
 * 物品侧的 NBT 接口（{@code getStored/setStored}）已经就位，将来接机器时可以直接当储罐。</p>
 *
 * <h3>配方（装配机）</h3>
 * <ul>
 *   <li>钨金属覆层：16 钨块 + 64 高速钢螺栓 + 64 铜板；</li>
 *   <li>CMB覆层铸造：16 CMB钢板 + 16 焊接钨板 + 16 Sa326锭；</li>
 *   <li>Threx覆层：64 Threx锭 + 64 高强度碳化钨锭 + 32 焊接锝钢板（锝钢/镉钢通用）；</li>
 *   <li>叶绿覆层：64 叶绿粉 + 8 焊接CMB钢板 + 64 Threx锭；</li>
 *   <li>Dnt覆层：16 双聚中子态素锭 + 64 叶绿粉 + 32 Ep锭；</li>
 *   <li>曼巴覆层：没有配方，只能靠创造模式或 /give 拿。</li>
 * </ul>
 *
 * <p>配方同样必须放在 {@code injectAllFallback()} 里注册：HBM 的装配机表在 init 之后会按 JSON
 * 重建，init 阶段塞进去的条目会被冲掉（和核装药核心踩的是同一个坑）。</p>
 */
public class ModCoatings {

	/** id → 已注册的覆层物品。 */
	private static final Map<String, ItemCoating> COATINGS = new LinkedHashMap<String, ItemCoating>();

	private static boolean registered;

	private ModCoatings() { }

	/** 取某个覆层，未注册时返回 null。 */
	public static ItemCoating get(String id) {
		return COATINGS.get(id);
	}

	/** 这个物品栈是不是本模组的机器覆层（机器覆层槽的准入判定）。 */
	public static boolean isCoating(ItemStack stack) {
		return stack != null && stack.getItem() instanceof ItemCoating;
	}

	private static final class Tier {
		final String id;
		final long capacity;
		final int level;
		final EnumRarity rarity;
		/** 装配机耗时（tick，20 tick = 1 秒）。 */
		final int duration;
		/** 装配机功率（HE/t），机器缓冲会自动开成 {@code power * 100}。 */
		final long power;
		/** 配方输入，按物品顺序摆进装配机。 */
		final AStack[] inputs;

		Tier(String id, long capacity, int level, EnumRarity rarity,
				int duration, long power, AStack... inputs) {
			this.id = id;
			this.capacity = capacity;
			this.level = level;
			this.rarity = rarity;
			this.duration = duration;
			this.power = power;
			this.inputs = inputs;
		}
	}

	/**
	 * 前五级的数值。工期/功率对齐原版装配机的常见档位，不按「容量翻倍」硬翻：
	 * 原版普通零件 60t/100、贵零件 400t/4,000、ferroplating 1,200t/10,000、
	 * 终极件 3,000t/250,000。这里跨 15 秒 ~ 60 秒、500 ~ 10,000 HE/t，
	 * 顶层刚好等于原版 ferroplating，一分钟一件、总耗 1,200 万 HE。
	 */
	private static Tier[] tiers() {
		return new Tier[] {
			new Tier("tungsten", 512_000L, 1, EnumRarity.common, 300, 500,
				new OreDictStack(W.block(), 16),
				new OreDictStack(DURA.bolt(), 64),
				new OreDictStack(CU.plate(), 64)),

			new Tier("cmb", 1_024_000L, 2, EnumRarity.uncommon, 400, 1_000,
				new OreDictStack(CMB.plate(), 16),
				new OreDictStack(W.plateWelded(), 16),
				new OreDictStack(SA326.ingot(), 16)),

			new Tier("threx", 2_048_000L, 3, EnumRarity.rare, 600, 2_000,
				new ComparableStack(ModItems.ingot_threx, 64),
				new ComparableStack(ModItems.powder_cobalt_tungsten_ingot, 64),
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 32)),

			new Tier("chlorophyte", 4_096_000L, 4, EnumRarity.rare, 800, 5_000,
				new ComparableStack(com.hbm.items.ModItems.powder_chlorophyte, 64),
				new OreDictStack(CMB.plateWelded(), 8),
				new ComparableStack(ModItems.ingot_threx, 64)),

			new Tier("dnt", 8_192_000L, 5, EnumRarity.epic, 1_200, 10_000,
				new OreDictStack(DNT.ingot(), 16),
				new ComparableStack(com.hbm.items.ModItems.powder_chlorophyte, 64),
				new OreDictStack(EUPH.ingot(), 32)),

			// 曼巴覆层：没有配方（duration = 0），容量只有 24 mB，只能靠创造模式或 /give 拿
			new Tier("mamba", 24L, 6, EnumRarity.epic, 0, 0),
		};
	}

	/* ------------------------------------------------------------------ 注册 */

	/** 注册物品。配方在 postInit 由 {@link #addRecipes()} 单独注入。 */
	public static void register() {
		if(registered) return;
		registered = true;

		for(Tier tier : tiers()) {
			ItemCoating item = new ItemCoating(tier.capacity, tier.level, tier.rarity);
			item.setUnlocalizedName("coating_" + tier.id);
			item.setTextureName("makentmgreatagain:coating_" + tier.id);
			item.setCreativeTab(ModCreativeTabs.TAB_GEAR);

			GameRegistry.registerItem(item, "coating_" + tier.id);
			COATINGS.put(tier.id, item);
		}

		System.out.println("[MakeNTMGreatAgain] Machine coatings registered (" + COATINGS.size() + " items).");
	}

	/* ------------------------------------------------------------------ 配方 */

	/** 装配机配方，由 {@code injectAllFallback()} 的 {@code step("coatings")} 调用。 */
	public static void addRecipes() {
		for(Tier tier : tiers()) {
			ItemCoating item = COATINGS.get(tier.id);
			if(item == null) continue;
			// duration = 0 表示没有配方（曼巴覆层）
			if(tier.duration <= 0) continue;

			GenericRecipe recipe = new GenericRecipe("makentmgreatagain.coating." + tier.id)
				.setup(tier.duration, tier.power)
				.outputItems(new ItemStack(item))
				.inputItems(tier.inputs);

			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
		}
	}
}

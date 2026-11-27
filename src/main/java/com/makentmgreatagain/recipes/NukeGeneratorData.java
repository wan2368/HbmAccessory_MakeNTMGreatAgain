package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.items.ModItems;
import com.makentmgreatagain.init.ModNukeCores;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 次临界核爆发生器的数值表。
 *
 * <p>全部数据来自《次临界核爆发生器》设计文档：</p>
 * <ul>
 *   <li>核心：电力倍率（m）、单次爆轰产生的高温液态钠、单次爆轰的中子通量；</li>
 *   <li>增殖配方：输入 → 产物、最低通量、需求通量。</li>
 * </ul>
 *
 * <p>表里的 Item 都是延迟取的（{@link #ensureInit()} 在第一次被用到时才填），
 * 因为 HBM 的物品字段要到 preInit 之后才非空。</p>
 */
public final class NukeGeneratorData {

	/** 一个核心的爆轰参数 */
	public static final class CoreStat {
		/** 电力消耗倍率 m */
		public final int power;
		/** 单次爆轰产生的液态钠（mB），同时也是消耗量 */
		public final int sodium;
		/** 单次爆轰的中子通量 */
		public final int flux;

		CoreStat(int power, int sodium, int flux) {
			this.power = power;
			this.sodium = sodium;
			this.flux = flux;
		}
	}

	/** 一条增殖配方 */
	public static final class Breeding {
		public final ItemStack input;
		public final ItemStack output;
		/** 最低中子通量，低于它这次爆轰对这一条无效 */
		public final int minFlux;
		/** 需求中子通量，进度到了就消耗原料出产物 */
		public final int reqFlux;

		Breeding(ItemStack input, ItemStack output, int minFlux, int reqFlux) {
			this.input = input;
			this.output = output;
			this.minFlux = minFlux;
			this.reqFlux = reqFlux;
		}
	}

	/** 电力倍率 1 对应的电力消耗（HE）。内部电力 100M，所以 m=80 的氦气凝核心一次要 80M。 */
	public static final long POWER_PER_M = 1_000_000L;

	/**
	 * 全局平衡倍率：下表多数沿用设计文档原值（氘化锂为单独定稿值），
	 * {@link #core(String, int, int, int)} 在登记时统一放大，只放大一次、不叠加。
	 *
	 * <ul>
	 *   <li>中子通量 ×3.0（原 ×1.5 再翻一倍，等效 +200%）</li>
	 *   <li>液态钠 ×1.75（原 ×2.5 再降 30%；一次爆轰的进料与高温钠产出同步缩放）</li>
	 * </ul>
	 *
	 * <p>{@link #SCALE_EXEMPT} 里的核心（氘化锂 / 气凝氦 / 野火 / 富集奥斯）不吃倍率，用表内数值。</p>
	 */
	private static final double FLUX_SCALE = 3.0D;
	private static final double SODIUM_SCALE = 1.75D;

	/** 不吃上述倍率的四个核心：氘化锂、气凝氦、野火，以及富集奥斯。
	 *  <p>氘化锂、气凝氦、野火是设计文档里的特殊核心，数值本身就是定稿；
	 *  氘化锂后来按用户指定改为钠 3,500,000 / 通量 240,000（直接写在上表，不经倍率）。</p>
	 *  富集奥斯（4,000,000 mB）当初按 ×2.5 会变成 10,000,000 mB，超过最高档覆层（DNT）8,192,000 mB
	 *  的罐容量，会被 getStartStatus 判成「冷却剂不足」而永久无法启动，故整体保持设计原值。
	 *  注：现行倍率已是 ×1.75，富集奥斯若参与会变成 7,000,000 mB，其实已在 DNT 容量之内，
	 *  想让它也吃倍率，把 "australium_greater" 从下面这行删掉即可。 */
	private static final java.util.Set<String> SCALE_EXEMPT =
			new java.util.HashSet<String>(java.util.Arrays.asList(
					"lithium_deuteride", "helium", "balefire", "australium_greater"));

	private static final Map<Item, CoreStat> CORES = new LinkedHashMap<Item, CoreStat>();
	/** 物品 → 核心 id（禁爆判定用）。 */
	private static final Map<Item, String> IDS = new LinkedHashMap<Item, String>();
	private static final List<Breeding> RECIPES = new ArrayList<Breeding>();
	private static boolean inited;

	private NukeGeneratorData() { }

	/** 首次使用时填表；HBM / 本模组的物品此时都已注册完毕 */
	public static void ensureInit() {

		if(inited) return;
		inited = true;

		core("u235",               1,   150_000,   4_000);
		core("pu239",              1,   160_000,   5_000);
		core("u233",               2,   240_000,   8_000);
		core("pu241",              5,   600_000,  12_000);
		core("am241",              6,   800_000,  16_000);
		core("am242",              6, 1_200_000,  24_000);
		core("schrabidium",        8, 1_600_000,  40_000);
		core("sa327",             10, 2_400_000,  60_000);
		core("australium_greater",16, 4_000_000,  80_000);
		// 氘化锂：用户直接给了绝对值（不经倍率），钠 3,500,000 / 通量 240,000
		core("lithium_deuteride", 40, 3_500_000, 240_000);
		core("helium",            80, 6_400_000,  50_000);
		core("balefire",          50, 8_000_000, 400_000);
		core("mamba",             24,         0,       0);

		// 设计文档只有上面 13 种，剩下 7 种燃料 / 裂变产物核心没有给数值，
		// 这里按同类核素往低一档取，保证界面不出现 N/A、按钮能启动；要改直接调这几行。
		core("uranium_fuel",       1,   150_000,   4_000);
		core("plutonium_fuel",     1,   160_000,   5_000);
		core("thorium_fuel",       1,   140_000,   3_000);
		core("sr90",               1,   100_000,   2_000);
		core("cs137",              1,   120_000,   2_000);
		core("i131",               1,    80_000,   1_000);
		core("co60",               2,   200_000,   6_000);

		breed(Items.coal,             ModItems.oil_tar,             0,        200);
		// 钍232→钍燃料棒已按用户要求删除（钍232→铀233 保留）
		breed(Items.mushroom_stew,    ModItems.glowing_stew,        0,      2_000);
		breed(Items.gold_ingot,       ModItems.ingot_au198,         0,     10_000);
		breed(ModItems.ingot_u238,    ModItems.ingot_pu239,      1_000,    5_000);
		breed(ModItems.ingot_th232,   ModItems.ingot_u233,       1_000,    5_000);
		breed(ModItems.ingot_pu240,   ModItems.ingot_pu241,      3_000,   12_000);
		breed(ModItems.ingot_pu241,   ModItems.ingot_am241,      4_000,   15_000);
		breed(ModItems.ingot_am241,   ModItems.ingot_am242,      4_000,   16_000);
		breed(ModItems.ingot_ra226,   ModItems.ingot_actinium,   5_000,    8_000);
		breed(ModItems.ingot_lead,    ModItems.ingot_pb209,      5_000,   20_000);
		breed(ModItems.ingot_bismuth, ModItems.ingot_polonium,   6_000,   15_000);
		breed(ModItems.ingot_neptunium, ModItems.powder_tennessine, 12_000, 50_000);
		breed(ModItems.powder_tennessine, ModItems.ingot_schrabidium, 14_000, 60_000);
		breed(ModItems.ingot_schrabidium, ModItems.ingot_solinium, 16_000, 40_000);
		breed(ModItems.ingot_solinium,    ModItems.ingot_gh336,    32_000,  80_000);
		breed(ModItems.ingot_gh336,       ModItems.ingot_australium, 45_000, 100_000);
		breed(ModItems.billet_australium_lesser, ModItems.ingot_euphemium, 100_000, 1_000_000);
	}

	private static void core(String id, int power, int sodium, int flux) {
		Item item = ModNukeCores.get(id);
		if(item != null) {
			// 表里写的是设计文档原值，这里统一套全局倍率（豁免核心除外），只放大一次
			boolean exempt = id != null && SCALE_EXEMPT.contains(id);
			int scaledSodium = exempt ? sodium : (int) Math.round(sodium * SODIUM_SCALE);
			int scaledFlux = exempt ? flux : (int) Math.round(flux * FLUX_SCALE);
			CORES.put(item, new CoreStat(power, scaledSodium, scaledFlux));
			IDS.put(item, id);
		}
	}

	private static void breed(Item in, Item out, int minFlux, int reqFlux) {
		if(in == null || out == null) return;
		// 全局平衡：所有增殖配方的两个通量数值统一 ×0.5（下面登记表里留的还是设计文档原值）
		RECIPES.add(new Breeding(new ItemStack(in), new ItemStack(out), minFlux / 2, reqFlux / 2));
	}

	/** 这个核心的爆轰参数，没登记返回 null */
	public static CoreStat getCore(ItemStack stack) {
		if(stack == null) return null;
		ensureInit();
		return CORES.get(stack.getItem());
	}

	/** 这个核心是不是禁爆核心（铯137/碘131/铀燃料/钍燃料）：能进槽但不能启动爆轰。 */
	public static boolean isNonExplosive(ItemStack stack) {
		if(stack == null) return false;
		ensureInit();
		return NukeCoreBalanceSpec.isNonExplosive(IDS.get(stack.getItem()));
	}

	/** 一次爆轰的电力消耗（HE） */
	public static long powerCost(CoreStat stat) {
		return stat.power * POWER_PER_M;
	}

	/** 这个物品对应的增殖配方，没有返回 null */
	public static Breeding findRecipe(ItemStack stack) {
		if(stack == null) return null;
		ensureInit();
		for(Breeding r : RECIPES) {
			if(r.input.getItem() == stack.getItem()) return r;
		}
		return null;
	}

	public static List<Breeding> getRecipes() {
		ensureInit();
		return RECIPES;
	}
}

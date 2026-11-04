package com.makentmgreatagain.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.world.biome.BiomeGenCraterBase;
import com.hbm.world.biome.BiomeGenNoMansLand;

import com.makentmgreatagain.init.ModFluids;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 环境地下泵的介质表。
 *
 * 一个 Medium 就是一套「运行配方」：产出什么、产出多少、要吃什么液体、吃多少、每 tick 耗多少电。
 * 群系只负责选出 Medium，群系本身不携带任何数值。
 *
 * 范围限定为 MC 原版群系 + HBM 群系，其余一律落到 WATER（一级抽水）。
 */
public final class EnvironmentPumpMedia {

	/** 一次结算的周期：100 tick = 5 秒 */
	public static final int CYCLE_TICKS = 100;
	/** 单次输入/输出上限 */
	public static final int MAX_TRANSFER = 32_000;

	public static class Medium {
		public final int index;
		public final String id;
		/** 贴图键，多个 Medium 可共用（如三种辐射坑） */
		public final String texKey;
		public final FluidType output;
		public final int outputAmount;
		/** 副产物，可为 null */
		public final FluidType output2;
		public final int output2Amount;
		public final FluidType input;
		public final int inputAmount;
		/** 折算等级系数之前的原始耗电，仅用于 HUD 显示 */
		public final long basePower;
		public final long power;
		public final int tier;

		private Medium(int index, String id, String texKey, FluidType out, int outAmt,
				FluidType out2, int out2Amt, FluidType in, int inAmt, long basePower, long power, int tier) {
			this.index = index;
			this.id = id;
			this.texKey = texKey;
			this.output = out;
			this.outputAmount = outAmt;
			this.output2 = out2;
			this.output2Amount = out2Amt;
			this.input = in;
			this.inputAmount = inAmt;
			this.basePower = basePower;
			this.power = power;
			this.tier = tier;
		}
	}

	private static final List<Medium> ALL = new ArrayList<Medium>();
	private static final Map<String, Medium> BY_BIOME = new HashMap<String, Medium>();
	private static final Map<String, Medium> BY_ID = new HashMap<String, Medium>();

	private static Medium add(String id, String texKey, FluidType out, int outAmt, FluidType in, int inAmt, long basePower, int tier) {
		return add(id, texKey, out, outAmt, null, 0, in, inAmt, basePower, tier);
	}

	private static Medium add(String id, String texKey, FluidType out, int outAmt, FluidType out2, int out2Amt,
			FluidType in, int inAmt, long basePower, int tier) {
		// 等级系数在这里一次性折进 power，运行期只剩高度惩罚一个变量
		long power = Math.round(basePower * tierMultiplier(tier));
		Medium m = new Medium(ALL.size(), id, texKey, out, outAmt, out2, out2Amt, in, inAmt, basePower, power, tier);
		ALL.add(m);
		BY_ID.put(id, m);
		return m;
	}

	// ---------------- 介质定义（运行配方表） ----------------
	/*
	 * 参数顺序（写错会静默产生错误配方，改之前先对一遍）：
	 *   add(id, 贴图键, 产物, 产物量/5s, 输入液体, 输入量/5s, 基础耗电 HE/t, 等级)
	 *   add(id, 贴图键, 产物, 产物量/5s, 副产物, 副产量/5s, 输入液体, 输入量/5s, 基础耗电 HE/t, 等级)
	 * 无输入传 null + 0；无副产物时用上面那个短重载。
	 * 数值单位统一为“每 5 秒一次结算”，不是每 tick。
	 *
	 * 基础耗电只是原料，实际耗电 = 基础耗电 × 等级系数（1 + 0.5 × 等级），见 tierMultiplier()。
	 * 下面每个条目的注释里写的“实际 xx HE/t”就是算完之后真正扣电的数。
	 */

	/** Ⅰ 水 —— 产 水 32,000｜无输入｜实际 960 HE/t（640 × 1.5）｜平原，以及所有未识别群系的兜底 */
	public static final Medium WATER = add("water", "water", Fluids.WATER, 32_000, null, 0, 640, 1);

	/** 海水取自附属已注册的 SEA_WATER；若该流体未就绪则退化为普通水 */
	private static FluidType saltType() {
		FluidType t = ModFluids.SEA_WATER == null ? null : ModFluids.SEA_WATER.type;
		return (t == null || t == Fluids.NONE) ? Fluids.WATER : t;
	}

	/** Ⅰ 盐水 —— 产 盐水 16,000｜无输入｜实际 1,350 HE/t｜必须真的临水（八向环 ≥5 命中），否则退化产水 */
	public static final Medium SALTWATER = add("saltwater", "saltwater", saltType(), 16_000, null, 0, 900, 1);

	/** Ⅱ 木油 —— 产 木油 8,000｜耗 水 2,000｜实际 2,000 HE/t（1,000 × 2.0） */
	public static final Medium WOODOIL = add("woodoil", "woodoil", Fluids.WOODOIL, 8_000, Fluids.WATER, 2_000, 1_000, 2);
	/** Ⅱ 胶体 —— 产 胶体 8,000｜耗 水 2,000｜实际 2,000 HE/t */
	public static final Medium COLLOID = add("colloid", "colloid", Fluids.COLLOID, 8_000, Fluids.WATER, 2_000, 1_000, 2);
	/** Ⅱ 乙醇 —— 产 乙醇 8,000｜耗 水 2,000｜实际 2,000 HE/t */
	public static final Medium ETHANOL = add("ethanol", "ethanol", Fluids.ETHANOL, 8_000, Fluids.WATER, 2_000, 1_000, 2);

	/** Ⅲ 沥青 —— 产 沥青 2,000｜耗 水 5,000｜实际 5,000 HE/t（2,000 × 2.5）｜沙漠油砂层 */
	public static final Medium BITUMEN = add("bitumen", "bitumen", Fluids.BITUMEN, 2_000, Fluids.WATER, 5_000, 2_000, 3);
	/** Ⅲ 碱液 —— 产 碱液 2,000｜耗 水 5,000｜实际 5,000 HE/t */
	public static final Medium LYE = add("lye", "lye", Fluids.LYE, 2_000, Fluids.WATER, 5_000, 2_000, 3);
	/** Ⅲ 岩浆 —— 产 岩浆 2,000｜耗 水 5,000（触水即固化，用于换取下界作业成本）｜实际 5,000 HE/t */
	public static final Medium LAVA = add("lava", "lava", Fluids.LAVA, 2_000, Fluids.WATER, 5_000, 2_000, 3);
	/** Ⅲ 水银 —— 产 水银 2,000｜耗 水 5,000｜实际 5,000 HE/t｜无人区土壤里的重金属污染层 */
	public static final Medium MERCURY = add("mercury", "mercury", Fluids.MERCURY, 2_000, Fluids.WATER, 5_000, 2_000, 3);

	/** Ⅳ 钙溶液 —— 产 钙溶液 500｜耗 水 2,000｜实际 10,500 HE/t（3,500 × 3.0）｜钙的下游用途窄，产量压到同级最低 */
	public static final Medium CALCIUM = add("calcium", "calcium", Fluids.CALCIUM_SOLUTION, 500, Fluids.WATER, 2_000, 3_500, 4);
	/**
	 * Ⅳ 氦-4 —— 产 氦-4 100｜耗 水 1,000｜实际 10,500 HE/t（3,500 × 3.0）
	 *
	 * 产量刻意压到同级最低：氦-4 的主来源是聚变堆副产（D-D / D-T 各 1,000 mB / 100t，
	 * 约 200 mB/s）与 PUREX 的 ICF 靶丸回收（每个 1,250），泵只是「没开聚变时的小补」。
	 * 泵不消耗燃料、只烧电，若给到与聚变同量级就成了无限白送。
	 */
	public static final Medium HELIUM = add("helium", "helium", Fluids.HELIUM4, 100, Fluids.WATER, 1_000, 3_500, 4);
	/** Ⅳ 末影果汁 —— 产 末影果汁 1,000｜耗 水 2,000｜实际 10,500 HE/t（末地专属） */
	public static final Medium ENDER = add("ender", "enderjuice", Fluids.ENDERJUICE, 1_000, Fluids.WATER, 2_000, 3_500, 4);
	/** Ⅳ 铝土矿溶液 —— 产 铝土矿溶液 1,000 + 副产 水 3,000｜耗 碱液 2,000（1 进 2 出）｜实际 10,500 HE/t */
	public static final Medium BAUXITE = add("bauxite", "bauxite", Fluids.BAUXITE_SOLUTION, 1_000, Fluids.WATER, 3_000, Fluids.LYE, 2_000, 3_500, 4);
	/** Ⅳ 硫酸盐溶液 · 坑外 —— 产 硫酸盐溶液 1,000｜耗 水 2,000｜实际 9,000 HE/t（贴图与另两坑共用） */
	public static final Medium VITRIOL_OUTER = add("vitriol_outer", "vitriol", Fluids.VITRIOL, 1_000, Fluids.WATER, 2_000, 3_000, 4);
	/** Ⅳ 硫酸盐溶液 · 坑中 —— 同上产量，仅耗电更高｜实际 13,500 HE/t */
	public static final Medium VITRIOL = add("vitriol", "vitriol", Fluids.VITRIOL, 1_000, Fluids.WATER, 2_000, 4_500, 4);
	/** Ⅳ 硫酸盐溶液 · 坑内 —— 同上产量，仅耗电更高｜实际 18,000 HE/t */
	public static final Medium VITRIOL_INNER = add("vitriol_inner", "vitriol", Fluids.VITRIOL, 1_000, Fluids.WATER, 2_000, 6_000, 4);

	/**
	 * Ⅴ 可燃冰 —— 产 天然气 2,000｜耗 水 2,000｜实际 14,000 HE/t（4,000 × 3.5）
	 *
	 * 永久冻土带下面的甲烷水合物，真实存在的矿藏（西伯利亚、阿拉斯加、南海北部）。
	 * 产量刻意卡在「够化工厂用、跑不动联合循环燃气轮机」：一台联合循环燃气轮机满负荷吃 1,000 mB/s，
	 * 而泵只给 400 mB/s，所以它只能当化工原料，不能当发电燃料，避免和油页岩抢发电的位置。
	 */
	public static final Medium GAS_HYDRATE = add("gashydrate", "gashydrate", Fluids.GAS, 2_000, Fluids.WATER, 2_000, 4_000, 5);
	/**
	 * Ⅴ 冻土卤水 —— 产 冷凝胶 1,000｜耗 水 1,000｜实际 21,000 HE/t（6,000 × 3.5）
	 *
	 * 冷针叶林的冻土层里封着的低温卤水，抽上来直接就是冷凝胶。
	 * HBM 原有的冷凝胶配方（工业搅拌机：冷却液 1,800 + 冷冻粉 1）只能靠冰，冷冻粉要拿浮冰粉碎；
	 * 这条给它第二个来源，顺带把 DFC 发射器与接收器的冷凝胶补给从「挖冰」变成「建泵」。
	 */
	public static final Medium BRINE = add("brine", "brine", Fluids.CRYOGEL, 1_000, Fluids.WATER, 1_000, 6_000, 5);

	/** Ⅴ 经验汁 —— 产 经验汁 500｜耗 水 10,000｜实际 21,000 HE/t */
	public static final Medium XPJUICE = add("xpjuice", "xpjuice", Fluids.XPJUICE, 500, Fluids.WATER, 10_000, 6_000, 5);

	/*
	 * 群系绑定表。键是 biomeName 去空格转小写，突变群系（"Desert M"）在 normalize 里回退到母群系。
	 * 未列出的群系一律落到 WATER。
	 */
	static {
		// 平原 → 水（兜底）
		bind(WATER, "plains");
		// 森林 / 针叶林 / 森林丘陵 / 针叶林丘陵 / 白桦林 / 白桦林丘陵 / 黑森林 / 巨型针叶林 / 巨型针叶林丘陵 → 木油
		bind(WOODOIL, "forest", "taiga", "foresthills", "taigahills", "birchforest", "birchforesthills",
				"roofedforest", "megataiga", "megataigahills");
		// 沼泽 → 胶体
		bind(COLLOID, "swampland");
		// 丛林 / 丛林丘陵 / 丛林边缘 → 乙醇
		bind(ETHANOL, "jungle", "junglehills", "jungleedge");
		// 沙漠 / 沙漠丘陵 → 沥青（油砂层）
		bind(BITUMEN, "desert", "deserthills");
		// 热带草原 / 热带高原 → 碱液
		bind(LYE, "savanna", "savannaplateau");
		// 下界 → 岩浆（维度判定优先，此行仅作 biomeName 兜底）
		bind(LAVA, "hell");
		// HBM 无人区 → 水银
		bind(MERCURY, "nomansland");
		// 山地 / 山地边缘 / 山地+ → 钙溶液
		bind(CALCIUM, "extremehills", "extremehillsedge", "extremehillsplus");
		// 石滩 → 氦-4（花岗岩基岩中的放射性衰变产物）
		bind(HELIUM, "stonebeach");
		// 恶地 / 恶地高原 F / 恶地高原 → 铝土矿溶液
		bind(BAUXITE, "mesa", "mesaplateauf", "mesaplateau");
		// 末地 → 末影果汁（维度判定优先）
		bind(ENDER, "sky");
		// 冰原 / 冰山 / 冰刺之地 → 可燃冰（永久冻土带下的甲烷水合物）
		bind(GAS_HYDRATE, "iceplains", "icemountains", "iceplainsspikes");
		// 冷针叶林 / 冷针叶林丘陵 → 冻土卤水（针叶林土壤层厚，卤水在冻土里富集）
		bind(BRINE, "coldtaiga", "coldtaigahills");
		// 海洋 / 深海 / 冰冻深海 / 冻洋 / 河流 / 冻河 / 沙滩 / 寒冷沙滩 → 盐水（需临水校验通过）
		// 冻洋也是海，只是水面结了冰。冰的材质不是 Material.water，探水会自动穿到冰层下面去找。
		bind(SALTWATER, "ocean", "deepocean", "frozendeepocean", "frozenocean",
				"river", "frozenriver", "beach", "coldbeach");
		// 蘑菇岛 / 蘑菇岛岸 → 经验汁
		bind(XPJUICE, "mushroomisland", "mushroomislandshore");
	}

	private static void bind(Medium m, String... names) {
		for(String n : names) BY_BIOME.put(n, m);
	}

	private EnvironmentPumpMedia() { }

	// ---------------- 查询 ----------------

	public static Medium byIndex(int index) {
		return index >= 0 && index < ALL.size() ? ALL.get(index) : WATER;
	}

	public static Medium byId(String id) {
		Medium m = BY_ID.get(id);
		return m == null ? WATER : m;
	}

	/** 整张介质表的只读视图，供 NEI 之类的展示层遍历（不要再往里加东西） */
	public static List<Medium> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static int count() {
		return ALL.size();
	}

	/**
	 * 水平采样环：内圈半径 5、外圈半径 10，每圈八向。
	 *
	 * 原来只有半径 6 的一圈、且只在机器上下 2 格内找水，结果是把泵架在海面上方几格
	 * （码头、栈桥、海上平台）就探不到水，整片海被退化成普通水。外圈用来兜住
	 * 「泵站在岸边或平台上、水面在十几格外」的情况。
	 */
	private static final int[][] RING = {
			{10, 0}, {-10, 0}, {0, 10}, {0, -10}, {7, 7}, {7, -7}, {-7, 7}, {-7, -7},
			{5, 0}, {-5, 0}, {0, 5}, {0, -5} };
	/** 十二向里只要四向见水就算临海，避免一个方向被礁石或码头挡住就判死 */
	private static final int RING_REQUIRED = 4;

	/**
	 * 单点判定：开阔水面 > 维度 > HBM 群系 > biomeName。
	 *
	 * 顺序不能反。海中央露出一小块沙子时，脚下报告的群系是沙漠，四周却全是水，
	 * 按群系走会抽出沥青 —— 玩家看到的分明是海。所以先看水，再看群系。
	 */
	public static Medium resolve(World world, int x, int y, int z) {
		if(openWater(world, x, y, z)) return SALTWATER;

		Medium m = resolveRaw(world, x, y, z);
		if(m == SALTWATER && !nearWater(world, x, y, z)) return WATER;
		return m;
	}

	private static Medium resolveRaw(World world, int x, int y, int z) {
		int dim = world.provider.dimensionId;
		if(dim == -1) return LAVA;
		if(dim == 1) return ENDER;

		BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
		if(biome == null) return WATER;

		if(biome instanceof BiomeGenCraterBase.BiomeGenCraterInner) return VITRIOL_INNER;
		if(biome instanceof BiomeGenCraterBase.BiomeGenCraterOuter) return VITRIOL_OUTER;
		if(biome instanceof BiomeGenCraterBase) return VITRIOL;
		if(biome instanceof BiomeGenNoMansLand) return MERCURY;

		Medium m = BY_BIOME.get(normalize(biome.biomeName));
		return m == null ? WATER : m;
	}

	/** 3×3 采样取多数票，避免群系边界来回跳变 */
	public static Medium sample(World world, int x, int y, int z) {

		// 开阔水面优先于群系投票：海里的沙洲、小岛、礁石常常带着沙漠或沙滩群系，
		// 3×3 里九票全是陆地，但玩家明明站在海上。
		if(openWater(world, x, y, z)) return SALTWATER;

		Map<Medium, Integer> votes = new HashMap<Medium, Integer>();
		Medium best = WATER;
		int bestCount = 0;

		for(int dx = -1; dx <= 1; dx++) {
			for(int dz = -1; dz <= 1; dz++) {
				Medium m = resolveRaw(world, x + dx, y, z + dz);
				Integer c = votes.get(m);
				int n = c == null ? 1 : c + 1;
				votes.put(m, n);
				if(n > bestCount) { bestCount = n; best = m; }
			}
		}

		// 海水只验证一次（中心点），不参与 9 次采样
		if(best == SALTWATER && !nearWater(world, x, y, z)) return WATER;
		return best;
	}

	/** 环采样：十二向里至少四向在机器上下 16 格内探到水，就算“真的在海边” */
	private static boolean nearWater(World world, int x, int y, int z) {
		int hits = 0;
		for(int[] d : RING) {
			if(columnHasWater(world, x + d[0], y, z + d[1])) {
				hits++;
				if(hits >= RING_REQUIRED) return true;
			}
		}
		return false;
	}

	/**
	 * 外圈：半径 16，八向。只回答一个问题 —— 这块地是不是被大片水包着。
	 *
	 * 水里的沙洲、小岛、礁石经常带着陆地群系（沙漠、沙滩、甚至森林），
	 * 只看群系就会把它们判成沥青或普通水，可玩家眼睛看到的是一整片海。
	 */
	private static final int[][] FAR_RING = {
			{16, 0}, {-16, 0}, {0, 16}, {0, -16}, {11, 11}, {11, -11}, {-11, 11}, {-11, -11} };
	/** 八向里六向见水才算开阔水域：小池塘过不了这一关，海和小岛能过 */
	private static final int FAR_REQUIRED = 6;

	private static boolean openWater(World world, int x, int y, int z) {
		int hits = 0;
		for(int[] d : FAR_RING) {
			if(columnHasWater(world, x + d[0], y, z + d[1])) {
				hits++;
				if(hits >= FAR_REQUIRED) return true;
			}
		}
		return false;
	}

	/**
	 * 单列找水：向下探 16 格、向上探 4 格。
	 *
	 * 向下探这么多是为了穿透冰盖 —— 冻洋、冻河的表面是 ice 方块，材质不是 water，
	 * 只按 ±2 格找就永远探不到，整片冰海会被判成普通水。向上留 4 格是为了
	 * 泵体低于水面（建在海底或水里）的情况。
	 */
	private static boolean columnHasWater(World world, int x, int y, int z) {
		for(int dy = -16; dy <= 4; dy++) {
			Block b = world.getBlock(x, y + dy, z);
			if(b != null && b.getMaterial() == Material.water) return true;
		}
		return false;
	}

	private static String normalize(String name) {
		if(name == null) return "";
		String s = name;
		// 原版突变群系形如 "Desert M"，去掉后缀回退到母群系
		if(s.endsWith(" M")) s = s.substring(0, s.length() - 2);
		return s.toLowerCase(Locale.ROOT).replace(" ", "");
	}

	// ---------------- 高度惩罚 ----------------

	/** 正常高度区间：主世界 45~75，下界 25~60，末地 40~80 */
	public static int[] heightBand(World world) {
		int dim = world.provider.dimensionId;
		if(dim == -1) return new int[] {25, 60};
		if(dim == 1) return new int[] {40, 80};
		return new int[] {45, 75};
	}

	/** 偏离档数：每 5 格一档，不封顶 */
	public static int penaltySteps(World world, int y) {
		int[] band = heightBand(world);
		int d = 0;
		if(y < band[0]) d = band[0] - y;
		else if(y > band[1]) d = y - band[1];
		return d / 5;
	}

	/**
	 * 等级耗电系数：一级 ×1.5、二级 ×2.0、三级 ×2.5、四级 ×3.0、五级 ×3.5。
	 * 即每高一级，在基础耗电上多叠 50%。
	 */
	public static double tierMultiplier(int tier) {
		return 1D + 0.5D * tier;
	}

	/** 耗电系数：原有每档 +25%，再单独乘一条每档 +5% */
	public static double penaltyMultiplier(World world, int y) {
		int s = penaltySteps(world, y);
		return (1D + 0.25D * s) * (1D + 0.05D * s);
	}

	/** 输入惩罚：每档 +5%，即越界后要往泵里灌更多介质才能转 */
	public static double penaltyInputFactor(World world, int y) {
		return 1D + 0.05D * penaltySteps(world, y);
	}

	/** 产出惩罚：每档 -5%，20 档归零；归零后机器不再运行，避免白烧电 */
	public static double penaltyOutputFactor(World world, int y) {
		double f = 1D - 0.05D * penaltySteps(world, y);
		return f <= 0D ? 0D : f;
	}
}

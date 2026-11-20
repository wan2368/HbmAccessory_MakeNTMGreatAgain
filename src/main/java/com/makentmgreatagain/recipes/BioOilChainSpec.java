package com.makentmgreatagain.recipes;

/** 木浆制油三段链规格（纯逻辑，便于单元测试）。
 *  数值为用户定稿；配方注册（ModRecipes.addBioOilChainRecipes）必须与此处一致。
 *
 *  ① 浆料解聚（化学工厂，六种木浆各一条）：
 *     木浆2000 + 蒸汽1500 + 氢500 → 生物原油2000 + 水1000｜200t / 1500HE/t
 *  ② 加氢脱氧（大型催化化工厂，TU 20万，催化剂1级=催化转化器）：
 *     生物原油1000 + 氢1500 → 粗合成油1500 + CO₂500｜150t / 3000HE/t
 *  ③ 异构重整（大型催化化工厂，TU 35万，催化剂2级=铂合金转换器）：
 *     粗合成油1000 + 乙醇1000 + 氢1000 + 氧1000 → 原油2500 + 水1000｜100t / 4000HE/t
 */
public final class BioOilChainSpec {

	private BioOilChainSpec() { }

	// ① 浆料解聚：输入 [木浆, 蒸汽, 氢]，输出 [生物原油, 水]
	public static final int[] DEPOLY_IN = { 2_000, 1_500, 500 };
	public static final int[] DEPOLY_OUT = { 2_000, 1_000 };
	public static final int DEPOLY_DURATION = 200;
	public static final long DEPOLY_POWER = 1_500L;
	public static final int DEPOLY_HEAT = 0;
	public static final int DEPOLY_CATALYST = 0;

	// ② 加氢脱氧：输入 [生物原油, 氢]，输出 [粗合成油, CO₂]
	public static final int[] HYDRO_IN = { 1_000, 1_500 };
	public static final int[] HYDRO_OUT = { 1_500, 500 };
	public static final int HYDRO_DURATION = 150;
	public static final long HYDRO_POWER = 3_000L;
	public static final int HYDRO_HEAT = 200_000;
	public static final int HYDRO_CATALYST = 1;

	// 木油直制（大化工，跳过制浆）：输入 [木油, 氢, 蒸汽]，输出 [生物原油, 水, CO₂]
	public static final int[] WOOD_IN = { 2_000, 1_500, 1_000 };
	public static final int[] WOOD_OUT = { 1_125, 1_000, 500 };
	public static final int WOOD_DURATION = 400;
	public static final long WOOD_POWER = 8_000L;
	public static final int WOOD_HEAT = 300_000;
	public static final int WOOD_CATALYST = 1;

	// ③ 异构重整：输入 [粗合成油, 乙醇, 氢, 氧]，输出 [原油, 水]
	public static final int[] REFORM_IN = { 1_000, 1_000, 1_000, 1_000 };
	public static final int[] REFORM_OUT = { 2_500, 1_000 };
	public static final int REFORM_DURATION = 100;
	public static final long REFORM_POWER = 4_000L;
	public static final int REFORM_HEAT = 350_000;
	public static final int REFORM_CATALYST = 2;

	/** n 台③段大化工并联的原油产量（mB/t）。 */
	public static double oilPerTick(int machines) {
		return (double) REFORM_OUT[0] * machines / REFORM_DURATION;
	}
}
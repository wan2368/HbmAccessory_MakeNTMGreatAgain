package com.makentmgreatagain.recipes;

import com.makentmgreatagain.init.ModFluids;

/** 木浆制油三段链 TDD 测试。
 *  用户定稿数值：
 *  ① 浆料解聚（化学工厂，六种木浆各一条）：木浆2000+蒸汽1500+氢500 → 生物原油2000+水1000｜200t/1500HE
 *  ② 加氢脱氧（大化工，TU20万，催化剂1级）：生物原油1000+氢1500 → 粗合成油1500+CO₂500｜150t/3000HE
 *  ③ 异构重整（大化工，TU35万，催化剂2级）：粗合成油1000+乙醇1000+氢1000+氧1000 → 原油2500+水1000｜100t/4000HE
 *  新流体：BIO_CRUDE 生物原油 id2065、RAW_SYNTH_OIL 粗合成油 id2066，均 LOW 级可燃。 */
public class BioOilChainSpecTest {

	private static int passed = 0;

	private static void check(boolean cond, String name) {
		if(!cond) throw new AssertionError("FAIL: " + name);
		passed++;
	}

	private static void stage(int[] in, int[] out, int duration, long power, int heat, int catalyst,
			int[] expIn, int[] expOut, int expDur, long expPow, int expHeat, int expCat, String tag) {
		check(java.util.Arrays.equals(in, expIn), tag + " inputs");
		check(java.util.Arrays.equals(out, expOut), tag + " outputs");
		check(duration == expDur, tag + " duration");
		check(power == expPow, tag + " power");
		check(heat == expHeat, tag + " heat");
		check(catalyst == expCat, tag + " catalyst");
	}

	public static void main(String[] args) {

		// ① 化学工厂：无热量门槛、无催化剂
		stage(BioOilChainSpec.DEPOLY_IN, BioOilChainSpec.DEPOLY_OUT,
			BioOilChainSpec.DEPOLY_DURATION, BioOilChainSpec.DEPOLY_POWER,
			BioOilChainSpec.DEPOLY_HEAT, BioOilChainSpec.DEPOLY_CATALYST,
			new int[] { 2_000, 1_500, 500 }, new int[] { 2_000, 1_000 },
			200, 1_500L, 0, 0, "depoly");

		// ② 大化工：TU 20万，1级催化转化器
		stage(BioOilChainSpec.HYDRO_IN, BioOilChainSpec.HYDRO_OUT,
			BioOilChainSpec.HYDRO_DURATION, BioOilChainSpec.HYDRO_POWER,
			BioOilChainSpec.HYDRO_HEAT, BioOilChainSpec.HYDRO_CATALYST,
			new int[] { 1_000, 1_500 }, new int[] { 1_500, 500 },
			150, 3_000L, 200_000, 1, "hydro");

		// ③ 大化工：TU 35万，2级铂合金转换器
		stage(BioOilChainSpec.REFORM_IN, BioOilChainSpec.REFORM_OUT,
			BioOilChainSpec.REFORM_DURATION, BioOilChainSpec.REFORM_POWER,
			BioOilChainSpec.REFORM_HEAT, BioOilChainSpec.REFORM_CATALYST,
			new int[] { 1_000, 1_000, 1_000, 1_000 }, new int[] { 2_500, 1_000 },
			100, 4_000L, 350_000, 2, "reform");

		// 木油直制路线（大化工，TU 30万，催化剂1级）：木油2000+氢1500+蒸汽1000 → 生物原油1125+水1000+CO₂500｜400t/8000HE
		stage(BioOilChainSpec.WOOD_IN, BioOilChainSpec.WOOD_OUT,
			BioOilChainSpec.WOOD_DURATION, BioOilChainSpec.WOOD_POWER,
			BioOilChainSpec.WOOD_HEAT, BioOilChainSpec.WOOD_CATALYST,
			new int[] { 2_000, 1_500, 1_000 }, new int[] { 1_125, 1_000, 500 },
			400, 8_000L, 300_000, 1, "wood");

		// 链式衔接：①产生物原油 ≥ ②耗；②产粗合成油 ≥ ③耗；木油路线产生物原油 ≥ ②耗
		check(BioOilChainSpec.DEPOLY_OUT[0] >= BioOilChainSpec.HYDRO_IN[0], "chain bio crude linked");
		check(BioOilChainSpec.HYDRO_OUT[0] >= BioOilChainSpec.REFORM_IN[0], "chain synth oil linked");
		check(BioOilChainSpec.WOOD_OUT[0] >= BioOilChainSpec.HYDRO_IN[0], "wood route bio crude linked");

		// 单台③速率 25mB/t；两台并联 50mB/t 超过抽油机 30mB/t
		check(BioOilChainSpec.oilPerTick(1) == 25.0, "single reform 25mB/t");
		check(BioOilChainSpec.oilPerTick(2) == 50.0, "dual reform 50mB/t");
		check(BioOilChainSpec.oilPerTick(2) > 30.0, "beats pumpjack");

		// 新流体定义：名称与 ID 按定稿，ID 不与现有 2064 以下及 2999 冲突
		check("BIO_CRUDE".equals(ModFluids.BIO_CRUDE.name), "bio crude name");
		check(ModFluids.BIO_CRUDE.id == 2065, "bio crude id");
		check("RAW_SYNTH_OIL".equals(ModFluids.RAW_SYNTH_OIL.name), "synth oil name");
		check(ModFluids.RAW_SYNTH_OIL.id == 2066, "synth oil id");

		System.out.println("ALL " + passed + " TESTS PASSED");
	}
}
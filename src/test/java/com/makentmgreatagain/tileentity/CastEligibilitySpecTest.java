package com.makentmgreatagain.tileentity;

/** 铸造准入 TDD 测试：合金配方下原料不铸锭、只铸合金；无配方时各种都铸。 */
public class CastEligibilitySpecTest {

	private static int passed = 0;

	private static void check(boolean cond, String name) {
		if(!cond) throw new AssertionError("FAIL: " + name);
		passed++;
	}

	public static void main(String[] args) {

		// 无配方：任何金属都可铸（单金属生产）
		check(CastEligibilitySpec.canCast(false, false, false), "no recipe casts anything");

		// 有配方：输出金属（合金）可铸
		check(CastEligibilitySpec.canCast(true, true, false), "recipe output castable");

		// 有配方：原料金属不可铸（bug 复现： previously cast into own ingots）
		check(!CastEligibilitySpec.canCast(true, false, true), "recipe input must NOT cast");

		// 有配方：既非原料也非输出的缓冲金属不可铸
		check(!CastEligibilitySpec.canCast(true, false, false), "unrelated metal must NOT cast under recipe");

		// 有配方：既是原料又是输出（罕见）以输出为准，可铸
		check(CastEligibilitySpec.canCast(true, true, true), "output wins over input");

		// 无模具自动浇锭：熔融量攒满一锭（INGOT=72单位）即铸，不等9模/10秒节奏
		int ingot = 72;
		check(CastEligibilitySpec.autoIngotCount(ingot, ingot) == 1, "exactly one ingot auto-casts");
		check(CastEligibilitySpec.autoIngotCount(ingot * 3 + 10, ingot) == 3, "three ingots plus remainder");
		check(CastEligibilitySpec.autoIngotCount(ingot - 1, ingot) == 0, "below one ingot waits");
		check(CastEligibilitySpec.autoIngotCount(0, ingot) == 0, "empty buffer waits");
		check(CastEligibilitySpec.autoIngotCount(ingot * 2, 0) == 0, "zero cost guard");
		check(CastEligibilitySpec.autoIngotCount(ingot * 2, -5) == 0, "negative cost guard");
		// 输出空间不足时按空间截断（free 以锭数计）
		check(CastEligibilitySpec.autoIngotCount(ingot * 5, ingot, 2) == 2, "clamped by output space");
		check(CastEligibilitySpec.autoIngotCount(ingot * 5, ingot, 0) == 0, "no space no cast");

		System.out.println("ALL " + passed + " TESTS PASSED");
	}
}
package com.makentmgreatagain.recipes;

/** 电锅炉准入过滤 TDD 测试：带 PWR/ICF 属性的流体禁止电锅炉加热。 */
public class ElectricBoilerFilterSpecTest {

	private static int passed = 0;

	private static void check(boolean cond, String name) {
		if(!cond) throw new AssertionError("FAIL: " + name);
		passed++;
	}

	public static void main(String[] args) {

		// 带通量调节（MUG/重水/铅/钍盐）→ 反应堆专用，电锅炉禁止
		check(ElectricBoilerFilterSpec.isReactorCoolant(true, 1.0, 0.0), "pwr moderator excluded");
		check(ElectricBoilerFilterSpec.isReactorCoolant(true, 0.0, 0.0), "moderator without eff still excluded");

		// 登记 PWR 或 ICF 效率（冷却液/四氟甲烷/钠/血液/铅）→ 反应堆专用，禁止
		check(ElectricBoilerFilterSpec.isReactorCoolant(false, 1.0, 0.0), "pwr eff excluded");
		check(ElectricBoilerFilterSpec.isReactorCoolant(false, 0.0, 1.25), "icf eff excluded");
		check(ElectricBoilerFilterSpec.isReactorCoolant(false, 0.75, 4.0), "pwr+icf eff excluded");

		// 普通流体（水/空气/石油系/盐水：只登记 BOILER/HEATEXCHANGER）→ 允许
		check(!ElectricBoilerFilterSpec.isReactorCoolant(false, 0.0, 0.0), "plain fluid allowed");

		System.out.println("ALL " + passed + " TESTS PASSED");
	}
}
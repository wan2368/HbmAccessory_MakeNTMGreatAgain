package com.makentmgreatagain.tileentity;

/** 熔融预留额度 TDD 测试：比例份额 + 总量 75% 硬顶。 */
public class MoltenCapSpecTest {

	private static int passed = 0;

	private static void check(boolean cond, String name) {
		if(!cond) throw new AssertionError("FAIL: " + name);
		passed++;
	}

	public static void main(String[] args) {

		// 比例拆分：192000 按 1:2 分 → 64000 / 128000
		check(MoltenCapSpec.proportionalCap(192000, new int[]{1, 2}, 0) == 64000, "proportional 1:2 idx0");
		check(MoltenCapSpec.proportionalCap(192000, new int[]{1, 2}, 1) == 128000, "proportional 1:2 idx1");

		// 不等比例 9:1 → 172800 / 19200
		check(MoltenCapSpec.proportionalCap(192000, new int[]{9, 1}, 0) == 172800, "proportional 9:1 idx0");
		check(MoltenCapSpec.proportionalCap(192000, new int[]{9, 1}, 1) == 19200, "proportional 9:1 idx1");

		// 单种不超 totalCap：单一输入 → 全额
		check(MoltenCapSpec.proportionalCap(192000, new int[]{5}, 0) == 192000, "single input full cap");

		// 各份额之和 == totalCap（按比例填满不越界）
		int[] req = {2, 3, 5};
		long sum = 0;
		for(int i = 0; i < req.length; i++) sum += MoltenCapSpec.proportionalCap(192000, req, i);
		check(sum == 192000, "shares sum to totalCap");

		// 非法 index / 空需求 → 0
		check(MoltenCapSpec.proportionalCap(192000, new int[]{1, 2}, 5) == 0, "index out of range");
		check(MoltenCapSpec.proportionalCap(192000, new int[]{}, 0) == 0, "empty requirements");

		// 总量硬顶：剩余空间内全额加入
		check(MoltenCapSpec.allowedAddition(192000, 100000, 500) == 500, "add within space");
		// 超出剩余空间：截断到剩余
		check(MoltenCapSpec.allowedAddition(192000, 191900, 500) == 100, "add clamped to space");
		// 已达顶：不再加入
		check(MoltenCapSpec.allowedAddition(192000, 192000, 500) == 0, "add at cap");
		// 超顶：0
		check(MoltenCapSpec.allowedAddition(192000, 200000, 500) == 0, "add over cap");
		// 非正数量：0
		check(MoltenCapSpec.allowedAddition(192000, 0, 0) == 0, "add zero amount");
		check(MoltenCapSpec.allowedAddition(192000, 0, -5) == 0, "add negative amount");

		// P0 越界防护：合金配方金属数 < 槽位类型数（5）时，超出配方金属的槽位必须跳过比例校验，
		// 否则 rec.input[type] 数组越界崩溃（先放料后选2金属配方即触发）
		check(MoltenCapSpec.alloySlotInRecipe(2, 0, 5), "alloy slot 0 in 2-metal recipe");
		check(MoltenCapSpec.alloySlotInRecipe(2, 1, 5), "alloy slot 1 in 2-metal recipe");
		check(!MoltenCapSpec.alloySlotInRecipe(2, 2, 5), "alloy slot 2 beyond 2-metal recipe");
		check(!MoltenCapSpec.alloySlotInRecipe(2, 4, 5), "alloy slot 4 beyond 2-metal recipe");
		check(MoltenCapSpec.alloySlotInRecipe(5, 4, 5), "alloy slot 4 in 5-metal recipe");
		check(!MoltenCapSpec.alloySlotInRecipe(1, 1, 5), "single-metal recipe is not alloy path");
		check(!MoltenCapSpec.alloySlotInRecipe(0, 0, 5), "no recipe");
		check(!MoltenCapSpec.alloySlotInRecipe(2, -1, 5), "negative slot");

		System.out.println("ALL " + passed + " TESTS PASSED");
	}
}
package com.makentmgreatagain.tileentity;

/** 电力高炉熔融预留额度规格（纯逻辑，便于单元测试）。
 *  比例决定每种金属份额；所有熔融物总和硬顶 totalCap（75%）。 */
public final class MoltenCapSpec {

	private MoltenCapSpec() { }

	/** 按配方输入比例把 totalCap 拆分给第 index 种金属的预留上限，单种不超 totalCap。 */
	public static long proportionalCap(long totalCap, int[] requirements, int index) {
		if(requirements == null || index < 0 || index >= requirements.length) return 0;
		long totalReq = 0;
		for(int r : requirements) totalReq += r;
		if(totalReq <= 0) return 0;
		long cap = totalCap * requirements[index] / totalReq;
		return Math.min(cap, totalCap);
	}

	/** 合金比例校验的槽位门控：只有「多金属配方（inputLen>1）且槽位类型在配方金属范围内」
	 *  才走 proportionalCap / rec.input[type]。配方金属数少于机器槽位类型数（5）时，
	 *  超出范围的槽位必须跳过，否则数组越界崩溃。 */
	public static boolean alloySlotInRecipe(int inputLen, int type, int inputTypes) {
		return inputLen > 1 && type >= 0 && type < inputLen && type < inputTypes;
	}

	/** 总量硬顶：当前总量 currentTotal 下，本次最多可加入的量（不超 totalCap）。 */
	public static int allowedAddition(long totalCap, long currentTotal, int amount) {
		if(amount <= 0) return 0;
		long space = totalCap - currentTotal;
		if(space <= 0) return 0;
		return (int) Math.min(amount, space);
	}
}
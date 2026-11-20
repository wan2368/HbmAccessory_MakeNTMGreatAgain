package com.makentmgreatagain.tileentity;

/** 电力高炉铸造准入规格（纯逻辑，便于单元测试）。
 *  选了合金配方时只铸配方输出金属，原料金属留在缓冲里等组合；未选配方时各种都铸。 */
public final class CastEligibilitySpec {

	private CastEligibilitySpec() { }

	/** 该金属当前是否允许铸造成物品。 */
	public static boolean canCast(boolean hasRecipe, boolean isRecipeOutput, boolean isRecipeInput) {
		if(!hasRecipe) return true;
		return isRecipeOutput;
	}

	/** 无模具自动浇锭：熔融量攒满几锭就铸几锭（不等 9 模/10 秒节奏）。
	 *  cost 为一锭的单位量（INGOT=72）；非正 cost 返回 0 防除零。 */
	public static int autoIngotCount(int moltenAmount, int cost) {
		if(cost <= 0 || moltenAmount <= 0) return 0;
		return moltenAmount / cost;
	}

	/** 同上，再按输出空间（可容纳的锭数）截断。 */
	public static int autoIngotCount(int moltenAmount, int cost, int freeIngots) {
		return Math.min(autoIngotCount(moltenAmount, cost), Math.max(0, freeIngots));
	}
}
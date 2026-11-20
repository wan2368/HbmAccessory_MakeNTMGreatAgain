package com.makentmgreatagain.recipes;

/** 电锅炉准入过滤规格（纯逻辑，便于单元测试）。
 *  带 PWR/ICF 反应堆属性（中子通量调节、堆内冷却效率）的流体不允许在电锅炉加热，
 *  必须走 PWR 或 ICF。 */
public final class ElectricBoilerFilterSpec {

	private ElectricBoilerFilterSpec() { }

	/** 是否为反应堆专用冷却剂（电锅炉禁用）。
	 *  @param hasPwrModerator 流体带 FT_PWRModerator（PWR 中子通量倍率）
	 *  @param pwrEff FT_Heatable 的 PWR 效率（0 = 未登记）
	 *  @param icfEff FT_Heatable 的 ICF 效率（0 = 未登记） */
	public static boolean isReactorCoolant(boolean hasPwrModerator, double pwrEff, double icfEff) {
		return hasPwrModerator || pwrEff > 0D || icfEff > 0D;
	}
}
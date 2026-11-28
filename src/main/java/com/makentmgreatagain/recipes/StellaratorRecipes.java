package com.makentmgreatagain.recipes;

import com.hbm.inventory.recipes.FusionRecipes;

/**
 * 仿星器 / 等离子加热器专用聚变配方表。
 *
 * <p>独立于 HBM 原版的 {@link FusionRecipes#INSTANCE}，避免覆盖原版托卡马克聚变堆的
 * 原生聚变配方；同时等离子加热器的配方选择器也只展示本模组的等离子聚变配方。</p>
 */
public class StellaratorRecipes extends FusionRecipes {

	public static final StellaratorRecipes INSTANCE = new StellaratorRecipes();

	private StellaratorRecipes() { }

	@Override
	public String getFileName() {
		return "mngaStellarator.json";
	}

	@Override
	public void registerDefaults() {
		// 由 HydrogenFusion.addRecipe() 统一填充
	}
}

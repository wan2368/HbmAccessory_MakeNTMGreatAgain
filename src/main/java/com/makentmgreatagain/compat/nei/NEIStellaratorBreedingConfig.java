package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 仿星器中子增殖页的 NEI 插件入口。
 *
 * <p>类名同样必须 NEI 开头、Config 结尾，NEI 才会扫描并实例化。
 * 这一页刻意和聚变页（{@code FusionRecipeHandler}）分开：增殖配方只在中子通量下成立，
 * 而通量只有仿星器能提供，混进通用页会让别的机器页显示不存在的配方。</p>
 */
public class NEIStellaratorBreedingConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		StellaratorBreedingHandler handler = new StellaratorBreedingHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Stellarator Breeding";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

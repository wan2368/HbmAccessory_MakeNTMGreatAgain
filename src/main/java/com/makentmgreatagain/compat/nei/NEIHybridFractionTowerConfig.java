package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 混合催化分馏塔的 NEI 插件入口。
 *
 * <p>类名必须 NEI 开头、Config 结尾，NEI 才会扫描并实例化。</p>
 */
public class NEIHybridFractionTowerConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		HybridFractionTowerHandler handler = new HybridFractionTowerHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Hybrid Catalytic Fractionating Tower";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

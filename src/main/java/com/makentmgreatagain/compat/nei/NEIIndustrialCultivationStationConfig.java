package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 工业种植站的 NEI 插件入口。
 *
 * <p>类名必须 NEI 开头、Config 结尾，NEI 才会扫描并实例化。</p>
 */
public class NEIIndustrialCultivationStationConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		IndustrialCultivationStationHandler handler = new IndustrialCultivationStationHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Industrial Cultivation Station";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 等离子加热器配方页的 NEI 插件入口。
 *
 * <p>类名必须以 NEI 开头、Config 结尾，NEI 自动扫描并实例化。</p>
 */
public class NEIPlasmaHeaterConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		PlasmaHeaterHandler handler = new PlasmaHeaterHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Plasma Heater";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

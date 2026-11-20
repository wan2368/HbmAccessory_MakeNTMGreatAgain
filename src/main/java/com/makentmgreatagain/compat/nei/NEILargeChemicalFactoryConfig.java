package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 大型催化化工厂的 NEI 插件入口。
 *
 * <p>类名必须 NEI 开头、Config 结尾，NEI 才会扫描并实例化。</p>
 */
public class NEILargeChemicalFactoryConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		LargeChemicalFactoryHandler handler = new LargeChemicalFactoryHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Large Catalytic Chemical Factory";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

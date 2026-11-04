package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * NEI 插件入口。
 *
 * 类名必须以 NEI 开头、Config 结尾：NEI 在 NEIClientConfig 里用一个按文件名匹配的
 * ClassDiscoverer（startsWith("NEI") && endsWith("Config.class")）扫描所有模组容器，
 * 名字对上了才会实例化并调 loadConfig。所以这个类不需要 HBM 那边做任何注册，
 * 也不能改名 —— 改成 EnvironmentPumpNEIConfig 就永远不会被加载。
 *
 * 只在客户端被调起，服务端不会碰到这个包里的任何类。
 */
public class NEIPumpConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		EnvironmentPumpHandler handler = new EnvironmentPumpHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Environmental Pump";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 海面立管钻机的 NEI 插件入口。
 *
 * <p>类名同样必须 NEI 开头、Config 结尾，NEI 才会扫描并实例化；
 * 与 {@link NEIPumpConfig} 各自独立注册互不影响。</p>
 */
public class NEIRiserRigConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		RiserRigHandler handler = new RiserRigHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Riser Rig";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

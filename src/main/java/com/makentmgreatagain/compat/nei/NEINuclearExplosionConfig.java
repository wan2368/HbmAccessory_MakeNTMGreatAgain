package com.makentmgreatagain.compat.nei;

import com.makentmgreatagain.MakeNTMGreatAgain;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * 次临界核爆炸发生器 NEI 页的插件入口。
 *
 * <p>类名必须 NEI 开头、Config 结尾，NEI 的 ClassDiscoverer 才会按文件名扫到它
 * （规则见 {@link NEIPumpConfig} 的注释）。</p>
 */
public class NEINuclearExplosionConfig implements IConfigureNEI {

	@Override
	public void loadConfig() {
		NuclearExplosionHandler handler = new NuclearExplosionHandler();
		API.registerRecipeHandler(handler);
		API.registerUsageHandler(handler);
	}

	@Override
	public String getName() {
		return "MakeNTMGreatAgain - Subcritical Nuclear Explosion Generator";
	}

	@Override
	public String getVersion() {
		return MakeNTMGreatAgain.VERSION;
	}
}

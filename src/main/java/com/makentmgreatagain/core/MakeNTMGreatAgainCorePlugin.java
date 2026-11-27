package com.makentmgreatagain.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.makentmgreatagain.core"})
public class MakeNTMGreatAgainCorePlugin implements IFMLLoadingPlugin {

	@Override
	public String[] getASMTransformerClass() {
		return new String[]{
				"com.makentmgreatagain.core.ArcFurnaceTransformer",
				"com.makentmgreatagain.core.AnvilTemplateTransformer",
				"com.makentmgreatagain.core.PinyinSearchTransformer",
				"com.makentmgreatagain.core.CoFHArbiterTransformer",
				"com.makentmgreatagain.core.BedrockOreGuardTransformer",
				"com.makentmgreatagain.core.HelicopterGuardTransformer",
				"com.makentmgreatagain.core.CoreFluidTankTransformer",
				"com.makentmgreatagain.core.PipeAnchorCtorTransformer",
				"com.makentmgreatagain.core.PrismThrexResistanceTransformer",
				"com.makentmgreatagain.core.NeutrinoNightVisionTransformer",
				"com.makentmgreatagain.core.ParallelUpgradeTransformer"
		};
	}

	@Override public String getModContainerClass() { return null; }
	@Override public String getSetupClass() { return null; }
	@Override public void injectData(Map<String, Object> data) { }
	@Override public String getAccessTransformerClass() { return null; }
}

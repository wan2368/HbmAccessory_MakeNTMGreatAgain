package com.makentmgreatagain.client;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.render.loader.HFRWavefrontObject;
import com.makentmgreatagain.MakeNTMGreatAgain;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;

/**
 * 附属包自己的 3D 模型与贴图资源。
 */
@SideOnly(Side.CLIENT)
public class AddonModels {

	private AddonModels() { }

	/** 重生版巨尻-9000 储罐的模型贴图 */
	public static final ResourceLocation bat9000_tex = new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/bat9000_reborn.png");

	private static IModelCustom bat9000 = null;

	/** 首次调用时读取 obj 并建 VBO，之后复用 */
	public static IModelCustom bat9000Model() {
		if(bat9000 == null) {
			bat9000 = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/bat9000_reborn.obj")).asVBO();
		}
		return bat9000;
	}

	/** 环境地下泵：机体沿用电动水泵的模型 */
	private static IModelCustom pump = null;

	public static IModelCustom pumpModel() {
		if(pump == null) {
			pump = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/pump.obj")).asVBO();
		}
		return pump;
	}
	
	public static final ResourceLocation pump_default_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/environment_pump/environment_pump.png");

	/** 所有介质共用同一张贴图，形参保留是为了以后真要分图时不用改调用方。 */
	public static ResourceLocation pumpTexture(FluidType type) {
		return pump_default_tex;
	}

	/** 重生版换热器：5×3×1 机体，顶部 3×3 热面 */
	public static final ResourceLocation heatex_tex = new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/heatex_5x3.png");

	private static IModelCustom heatex = null;

	public static IModelCustom heatexModel() {
		if(heatex == null) {
			heatex = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/heatex_5x3.obj")).asVBO();
		}
		return heatex;
	}
}

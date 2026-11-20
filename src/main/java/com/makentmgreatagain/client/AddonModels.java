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

	/** 小型电解机：2×2×3 机体，底部两侧共四个流体 / 电力插座 */
	public static final ResourceLocation electrolyzer_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/electrolyzer_2x3.png");

	private static IModelCustom electrolyzer = null;

	public static IModelCustom electrolyzerModel() {
		if(electrolyzer == null) {
			electrolyzer = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/electrolyzer_2x3.obj")).asVBO();
		}
		return electrolyzer;
	}

	/** 电弧加热器：3×3 基座 + 正前方 1 格接线箱，2 格高；顶盖、接线箱、机体都用原版电加热器贴图，UV 与原版一一对应 */
	public static final ResourceLocation arc_heater_top_tex =
			new ResourceLocation("hbm", "textures/models/machines/electric_heater.png");

	/** 机体窗口里的立式铜管取自原版大型电弧炉，使用原版电弧炉贴图 */
	public static final ResourceLocation arc_heater_coil_tex =
			new ResourceLocation("hbm", "textures/models/machines/arc_furnace.png");

	private static IModelCustom arcHeaterBody = null;
	private static IModelCustom arcHeaterTop = null;
	private static IModelCustom arcHeaterCoil = null;

	public static IModelCustom arcHeaterBodyModel() {
		if(arcHeaterBody == null) {
			arcHeaterBody = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/archeater_body.obj")).asVBO();
		}
		return arcHeaterBody;
	}

	public static IModelCustom arcHeaterTopModel() {
		if(arcHeaterTop == null) {
			arcHeaterTop = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/archeater_top.obj")).asVBO();
		}
		return arcHeaterTop;
	}

	public static IModelCustom arcHeaterCoilModel() {
		if(arcHeaterCoil == null) {
			arcHeaterCoil = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/archeater_coil.obj")).asVBO();
		}
		return arcHeaterCoil;
	}

	/** 大型离心机：沿用旧版回旋加速器的模型和贴图（Body + B1~B4 五个分组） */
	public static final ResourceLocation centrifuge_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_centrifuge.png");
	public static final ResourceLocation centrifuge_ashes =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_centrifuge_ashes.png");
	public static final ResourceLocation centrifuge_book =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_centrifuge_book.png");
	public static final ResourceLocation centrifuge_gavel =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_centrifuge_gavel.png");
	public static final ResourceLocation centrifuge_coin =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_centrifuge_coin.png");

	private static IModelCustom centrifuge = null;

	public static IModelCustom centrifugeModel() {
		if(centrifuge == null) {
			centrifuge = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/large_centrifuge.obj")).asVBO();
		}
		return centrifuge;
	}

	/** 海面立管钻机：7×7 平台 + 16 格高桁架井架 */
	public static final ResourceLocation riser_rig_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/riser_rig.png");

	private static IModelCustom riserRig = null;

	public static IModelCustom riserRigModel() {
		if(riserRig == null) {
			riserRig = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/riser_rig.obj")).asVBO();
		}
		return riserRig;
	}

	/** 钻机向下延伸的输送管道（1 格高管段，单独贴图） */
	public static final ResourceLocation riser_pipe_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/riser_pipe.png");

	private static IModelCustom riserPipe = null;

	public static IModelCustom riserPipeModel() {
		if(riserPipe == null) {
			riserPipe = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/riser_pipe.obj")).asVBO();
		}
		return riserPipe;
	}

	/** 混合催化分馏塔：5×5×6，八棱柱塔身 + 底部 8 个检修口 */
	public static final ResourceLocation hybrid_catalytic_fraction_tower_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/hybrid_catalytic_fraction_tower.png");
	private static IModelCustom hybridCatalyticFractionTower = null;

	public static IModelCustom hybridCatalyticFractionTowerModel() {
		if(hybridCatalyticFractionTower == null) {
			hybridCatalyticFractionTower = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/hybrid_catalytic_fraction_tower.obj")).asVBO();
		}
		return hybridCatalyticFractionTower;
	}

	/** 可控核爆炸发生器：13×13×17，模型按材质拆成 6 个 OBJ，渲染时逐个换贴图 */
	public static final String[] neg_keys = {"hull", "dark", "hazard", "vent", "panel", "port"};
	public static final ResourceLocation[] neg_tex = {
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator_dark.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator_hazard.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator_vent.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator_panel.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/nuclear_explosion_generator_port.png")};
	private static final IModelCustom[] negModels = new IModelCustom[neg_keys.length];

	public static IModelCustom negModel(int i) {
		if(negModels[i] == null) {
			negModels[i] = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID,
					"models/machines/nuclear_explosion_generator_" + neg_keys[i] + ".obj")).asVBO();
		}
		return negModels[i];
	}

	/** 地核熔岩开采机（旧版 NTM 1.0.27 开发中机器「AMS发射极」的模型），5×5×6，另有熔毁模型/贴图 */
	public static final ResourceLocation core_lava_extractor_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/core_lava_extractor.png");
	public static final ResourceLocation core_lava_extractor_destroyed_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/core_lava_extractor_destroyed.png");
	private static IModelCustom coreLavaExtractor = null;
	private static IModelCustom coreLavaExtractorDestroyed = null;

	public static IModelCustom coreLavaExtractorModel() {
		if(coreLavaExtractor == null) {
			coreLavaExtractor = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/core_lava_extractor.obj")).asVBO();
		}
		return coreLavaExtractor;
	}

	public static IModelCustom coreLavaExtractorDestroyedModel() {
		if(coreLavaExtractorDestroyed == null) {
			coreLavaExtractorDestroyed = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/core_lava_extractor_destroyed.obj")).asVBO();
		}
		return coreLavaExtractorDestroyed;
	}

	/** 流体冷却机：3×3×7 机体，几何取 1.0.27_X5778 紧凑型压缩机的 condenser.obj（带 Fan1/Fan2 两个风扇）。
	 * 贴图必须是 compressor_compact.png 的涂装（黑黄警示条纹），不是蒸汽冷凝器的 condenser.png（深绿散热片）。 */
	public static final ResourceLocation fluid_cooler_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/fluid_cooler.png");

	private static IModelCustom fluidCooler = null;

	public static IModelCustom fluidCoolerModel() {
		if(fluidCooler == null) {
			fluidCooler = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/fluid_cooler.obj")).asVBO();
		}
		return fluidCooler;
	}

	/** 排液管：本体排液管砍掉伸出两格后的 1×1×1 底座，底部内凹 2px 的圆形排口，UV 与原版一致 */
	public static final ResourceLocation drain_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/drain.png");

	/** 小型钨排液管：同一张图按钨色加深后的版本 */
	public static final ResourceLocation drain_tungsten_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/drain_tungsten.png");

	private static IModelCustom drain = null;

	public static IModelCustom drainModel() {
		if(drain == null) {
			drain = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/drain.obj")).asVBO();
		}
		return drain;
	}

	/** 大型催化化工厂：8×4×8 机体，1.0.27_X4980 遗留型号的几何（Main 机体 + Fan1/Fan2 两个顶置风扇）与原始贴图 */
	public static final ResourceLocation large_catalytic_chemical_factory_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/large_catalytic_chemical_factory.png");

	private static IModelCustom largeCatalyticChemicalFactory = null;

	public static IModelCustom largeCatalyticChemicalFactoryModel() {
		if(largeCatalyticChemicalFactory == null) {
			largeCatalyticChemicalFactory = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/large_catalytic_chemical_factory.obj")).asVBO();
		}
		return largeCatalyticChemicalFactory;
	}

	/** 弃用单方块电容的 OBJ 模型（已复制进附属，五个分组 Top/Side/Bottom/InnerTop/InnerSide）。
	 *  渲染器用 {@link com.hbm.render.util.ObjUtil#renderPartWithIcon} 逐分组贴图标，
	 *  需要 HFRWavefrontObject 本体而非 VBO，所以这里不调 asVBO()。 */
	private static HFRWavefrontObject capacitor = null;

	public static HFRWavefrontObject capacitorModel() {
		if(capacitor == null) {
			capacitor = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/blocks/capacitor.obj")).noSmooth();
		}
		return capacitor;
	}

	/** 工业燃气轮机：4×13×4，Blender 模型按连通岛重排 UV 到一张 512×512 图集。
	 * 图集全部取自本体 industrial_turbine.png 的像素与配色：铆接深灰装甲 / 绿色涂装 / 浅灰金属件 /
	 * 混凝土基座 / 黑白散热百叶 / 横向加强肋 / 深色内框，另附仪表、辐射、警示、黄色格栅四个备用贴花格。 */
	public static final ResourceLocation industrial_gas_turbine_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/industrial_gas_turbine.png");

	private static IModelCustom industrialGasTurbine = null;

	public static IModelCustom industrialGasTurbineModel() {
		if(industrialGasTurbine == null) {
			industrialGasTurbine = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/industrial_gas_turbine.obj")).asVBO();
		}
		return industrialGasTurbine;
	}

	/** 电力高炉：5×5×7，炉基 → 炉缸 → 炉腹 → 炉身收分 → 炉喉 → 顶盖平台 + 上料钟 */
	public static final ResourceLocation electric_blast_furnace_tex =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/models/machines/electric_blast_furnace.png");

	private static IModelCustom electricBlastFurnace = null;

	public static IModelCustom electricBlastFurnaceModel() {
		if(electricBlastFurnace == null) {
			electricBlastFurnace = new HFRWavefrontObject(new ResourceLocation(MakeNTMGreatAgain.MODID, "models/machines/electric_blast_furnace.obj")).asVBO();
		}
		return electricBlastFurnace;
	}
}

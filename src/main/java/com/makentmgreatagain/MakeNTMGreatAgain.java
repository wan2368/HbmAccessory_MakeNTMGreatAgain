package com.makentmgreatagain;

//配方注册
import api.hbm.recipe.IRecipeRegisterListener;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.makentmgreatagain.container.ContainerAlloyShredder;
import com.makentmgreatagain.container.ContainerFluidCooler;
import com.makentmgreatagain.container.ContainerHeatexReborn;
import com.makentmgreatagain.container.ContainerRiserRig;
import com.makentmgreatagain.handler.ModFuelHandler;
import com.makentmgreatagain.init.HBMCompat;
import com.makentmgreatagain.init.ModAchievements;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluidLoaders;
import com.makentmgreatagain.init.ModFluids;
import com.makentmgreatagain.init.MNGAMetals;
import com.makentmgreatagain.init.ModItems;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.init.ModNukeCores;
import com.makentmgreatagain.init.ModPlatinumRecipes;
import com.makentmgreatagain.config.GlobalConfig;
import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModRecipes;
import com.makentmgreatagain.init.ModTarkovFood;
import com.makentmgreatagain.locator.LocatorNetwork;
import com.makentmgreatagain.recipes.AnvilAssemblyTemplates;
import com.makentmgreatagain.recipes.HybridFractionRecipes;
import com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes;
import com.makentmgreatagain.recipes.ScheeliteRecipes;
import com.makentmgreatagain.world.AddonStructures;
import com.makentmgreatagain.world.WorldGenCoreFissure;
import com.makentmgreatagain.world.WorldGenDeepSeaNodule;
import com.makentmgreatagain.world.WorldGenLeadZincOre;
import com.makentmgreatagain.world.WorldGenXenCrystal;
import com.makentmgreatagain.world.WorldGenRefractoryGoldOre;
import com.makentmgreatagain.world.WorldGenSaltBlocks;
import com.makentmgreatagain.world.WorldGenScheeliteOre;
import com.makentmgreatagain.proxy.CommonProxy;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 附属模组主类
 * 这个类是整个模组的入口。Forge 启动时会扫描所有 jar，找到带 @Mod 注解的类并实例化它，
 * 然后按固定顺序回调下面这些带 @EventHandler 的方法。
 *   modid  模组唯一标识，全小写、不能和别的模组重复。资源路径 assets/<modid>/ 必须和它一致。
 *   version 版本号，只显示用。
 *   dependencies "required-after:hbm" 的意思是「必须装 hbm，且 hbm 要先于本模组加载」。
 *     少了这句，玩家没装 HBM 时游戏会在加载中途抛空指针崩溃，而不是给出清晰的提示。
 */
@Mod(
        modid = MakeNTMGreatAgain.MODID,
        name = MakeNTMGreatAgain.NAME,
        version = MakeNTMGreatAgain.VERSION,
        dependencies = "required-after:hbm",
        acceptedMinecraftVersions = "[1.7.10]"
)
public class MakeNTMGreatAgain implements IGuiHandler {

    public static final String MODID = "makentmgreatagain";
    public static final String NAME = "Make NTM Great Again";
    public static final String VERSION = "1.0.0";

    /** 统一用这个 logger 打日志，输出会带上模组名，比 System.out.println 好排查。 */
    public static final Logger LOG = LogManager.getLogger(NAME);

    /** Forge 注入的模组实例，用于注册本模组的 GUI 处理器 */
    @Instance(MakeNTMGreatAgain.MODID)
    public static MakeNTMGreatAgain instance;

    /** GUI ID：对应本模组的第一个 GUI（高级粉碎机） */
    public static final int GUI_ALLOY_SHREDDER = 0;
    /** GUI ID：集成式换热加热器 */
    public static final int GUI_HEATEX_REBORN = 1;
    /** GUI ID：海面立管钻机 */
    public static final int GUI_RISER_RIG = 2;
    /** GUI ID：混合催化分馏塔 */
    public static final int GUI_HYBRID_CATALYTIC_FRACTION_TOWER = 3;
    /** GUI ID：流体冷却机 */
    public static final int GUI_FLUID_COOLER = 4;
    /** GUI ID：大型催化化工厂 */
    public static final int GUI_LARGE_CATALYTIC_CHEMICAL_FACTORY = 5;
    /** GUI ID：大型离心机 */
    public static final int GUI_LARGE_CENTRIFUGE = 6;
    /** GUI ID：大型电池组控制器 */
    public static final int GUI_BATTERY_ARRAY = 7;
    /** GUI ID：地核熔岩开采机 */
    public static final int GUI_CORE_LAVA_EXTRACTOR = 8;
    /** GUI ID：电力高炉 */
    public static final int GUI_ELECTRIC_BLAST_FURNACE = 9;
    /** GUI ID：电锅炉（单方块，移植自原版） */
    public static final int GUI_ELECTRIC_BOILER = 10;
    /** GUI ID：可控核爆炸发生器 */
    public static final int GUI_NUCLEAR_EXPLOSION_GENERATOR = 11;
    /** GUI ID：工业燃气轮机 */
    public static final int GUI_INDUSTRIAL_GAS_TURBINE = 12;
    /** GUI ID：大型火力发电站 */
    public static final int GUI_LARGE_THERMAL_POWER_STATION = 13;
    /** GUI ID：仿星器 */
    public static final int GUI_STELLARATOR = 15;
    /** GUI ID：等离子加热器 */
    public static final int GUI_PLASMA_HEATER = 16;
    /** GUI ID：可调节排液管 */
    public static final int GUI_MACHINE_DRAIN = 17;
    /** GUI ID：流体传送器 */
    public static final int GUI_XEN_FLUID_TRANSCEIVER = 18;
    /** GUI ID：物品传送器 */
    public static final int GUI_XEN_ITEM_TRANSCEIVER = 19;

    /** 让 Forge 把"打开 GUI"转发到本模组实例 */
    public void registerGuiHandler() {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, this);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if(id == GUI_ALLOY_SHREDDER) {
            if(world.getTileEntity(x, y, z) instanceof TileEntityAlloyShredder) {
                return new ContainerAlloyShredder(player.inventory, (TileEntityAlloyShredder) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_HEATEX_REBORN) {
            if(world.getTileEntity(x, y, z) instanceof TileEntityHeatexReborn) {
                return new ContainerHeatexReborn(player.inventory, (TileEntityHeatexReborn) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_RISER_RIG) {
            if(world.getTileEntity(x, y, z) instanceof TileEntityRiserRig) {
                return new ContainerRiserRig(player.inventory, (TileEntityRiserRig) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_HYBRID_CATALYTIC_FRACTION_TOWER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower) {
                return new com.makentmgreatagain.container.ContainerHybridCatalyticFractionTower(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_FLUID_COOLER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityFluidCooler) {
                return new ContainerFluidCooler(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityFluidCooler) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_NUCLEAR_EXPLOSION_GENERATOR) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator) {
                return new com.makentmgreatagain.container.ContainerNuclearExplosionGenerator(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_LARGE_CATALYTIC_CHEMICAL_FACTORY) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory) {
                return new com.makentmgreatagain.container.ContainerLargeCatalyticChemicalFactory(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_LARGE_CENTRIFUGE) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge) {
                return new com.makentmgreatagain.container.ContainerLargeCentrifuge(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_BATTERY_ARRAY) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityBatteryArray) {
                return new com.makentmgreatagain.container.ContainerBatteryArray(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityBatteryArray) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_CORE_LAVA_EXTRACTOR) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor) {
                return new com.makentmgreatagain.container.ContainerCoreLavaExtractor(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_ELECTRIC_BLAST_FURNACE) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace) {
                return new com.makentmgreatagain.container.ContainerElectricBlastFurnace(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_ELECTRIC_BOILER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityElectricBoiler) {
                return new com.makentmgreatagain.container.ContainerElectricBoiler(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityElectricBoiler) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_INDUSTRIAL_GAS_TURBINE) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine) {
                return new com.makentmgreatagain.container.ContainerIndustrialGasTurbine(
                        player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_LARGE_THERMAL_POWER_STATION) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation)
                return new com.makentmgreatagain.container.ContainerLargeThermalPowerStation(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation) world.getTileEntity(x, y, z));
        }
        if(id == GUI_STELLARATOR) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityStellarator)
                return new com.makentmgreatagain.container.ContainerStellarator(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityStellarator) world.getTileEntity(x, y, z));
        }
        if(id == GUI_PLASMA_HEATER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityPlasmaHeater)
                return new com.makentmgreatagain.container.ContainerPlasmaHeater(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityPlasmaHeater) world.getTileEntity(x, y, z));
        }
        if(id == GUI_MACHINE_DRAIN) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityMachineDrain)
                return new com.makentmgreatagain.container.ContainerMachineDrain(
                        (com.makentmgreatagain.tileentity.TileEntityMachineDrain) world.getTileEntity(x, y, z));
        }
        if(id == GUI_XEN_FLUID_TRANSCEIVER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver)
                return new com.makentmgreatagain.container.ContainerXenFluidTransceiver(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver) world.getTileEntity(x, y, z));
        }
        if(id == GUI_XEN_ITEM_TRANSCEIVER) {
            if(world.getTileEntity(x, y, z) instanceof com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver)
                return new com.makentmgreatagain.container.ContainerXenItemTransceiver(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver) world.getTileEntity(x, y, z));
        }
        return null;
    }

    /**
     * 客户端界面由 SidedProxy 创建，这里只转发。
     *
     * <p>绝不能在本类直接 new GUI：主类专用服务端也会加载，一旦常量池里出现 GuiContainer
     * 的子类引用，服务端做类校验时就要去读客户端类，直接 NoClassDefFoundError 开服崩溃。
     * 交给 ClientProxy 以后，这段代码在服务端根本不会被读到（真正实现见 proxy 里的同名方法）。</p>
     */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return proxy.getClientGuiElement(id, player, world, x, y, z);
    }

    /**
     * SidedProxy 是 1.7.10 处理「客户端/服务端代码分离」的标准手段。
     * 客户端启动时装载 ClientProxy，专用服务端装载 CommonProxy。
     * 这样客户端专属代码（渲染、贴图）不会被服务端加载到，避免开服崩溃。
     */
    @SidedProxy(
            clientSide = "com.makentmgreatagain.proxy.ClientProxy",
            serverSide = "com.makentmgreatagain.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
		proxy.preInit(event);

		// 网络通道（服务端发送香烟效果通知前必须已注册）
		ModNetwork.init();
		LocatorNetwork.init();

		// 注册物品必须在 preInit 阶段完成，晚于这个阶段 Forge 不再接受新物品注册
		ModItems.init();

		// 燃料表：HBM 的燃料表是硬编码白名单，附属新增的可燃物品（高纯炭黑）必须自己注册
		GameRegistry.registerFuelHandler(new ModFuelHandler());

		// 销毁罐的流体处理逻辑（必须在任何机器开始跑之前挂上）
		ModFluidLoaders.init();

		// 注册方块
		ModBlocks.init();

		// 注册自定义 HBM 流体（盐水）。必须在 HBM 的 Fluids.init() 之后，preInit 正好安全
		ModFluids.init();

		// ===== [坩埚/铸造] ① 挂载点：矿物词典（preInit 末尾）=====
		// 三材质的矿物词典（耐摔合金 / 铒 / 铂）。必须在物品与方块都注册完之后，
		// 它是坩埚熔炼与铸造模具识别这些物品的唯一依据。
		// 改坩埚相关的东西前先看 init/MNGAMetals.java 类头的「【坩埚/铸造】速查」。
		MNGAMetals.registerOreDict();

		// 白钨矿副产的熔炼：铍精矿 / 锆精矿 一比一出锭（不依赖配方表加载顺序，preInit 直接注册）
		ScheeliteRecipes.addFurnaceRecipes();

		// 混合催化分馏塔的自定义配方（双流体进 → 4 流体 + 1 物品出）；表空时机器退回原版分馏
		HybridFractionRecipes.addRecipes();

		// 全局玩法配置（config/MNGA/global.cfg）：长流程模式、配置保护开关等跨系统开关。
		// 必须先于 ModConfig.init：ModConfig 的 worldgen.cfg 版本检查要读
		// preserveConfigOnUpdate（配置保护开关在本文件里）。
		GlobalConfig.init(event.getModConfigurationDirectory());
		// 世界生成配置（config/MNGA/worldgen.cfg）+ 注册含盐方块生成
		ModConfig.init(event.getModConfigurationDirectory());
		GameRegistry.registerWorldGenerator(new WorldGenSaltBlocks(), 0);
		// 铅锌矿：小簇、只主世界、只替换石头（ISP 法提镉的矿石来源）
		GameRegistry.registerWorldGenerator(new WorldGenLeadZincOre(), 0);
		// 含砷金矿：比铅锌矿浅、更密，硫代硫酸盐浸金的矿石来源
		GameRegistry.registerWorldGenerator(new WorldGenRefractoryGoldOre(), 0);
		// 白钨矿：大矿囊、稀生成（默认约 12 区块一条），湿法炼钨的矿石来源
		GameRegistry.registerWorldGenerator(new WorldGenScheeliteOre(), 0);
		// 深海结核混合物：只海洋群系、深水海床表层，成片生成
		GameRegistry.registerWorldGenerator(new WorldGenDeepSeaNodule(), 0);
		// 地核熔岩裂隙：仅下界，y=0 基岩层 3×3 一片 + 深岩盖层/岩丘，平均 100 区块一个
		GameRegistry.registerWorldGenerator(new WorldGenCoreFissure(), 0);
		// 稳定X晶体簇：仅末地，全世界共 8 个，根部必须贴末地石
		GameRegistry.registerWorldGenerator(new WorldGenXenCrystal(), 0);

		// 注册本模组 GUI 处理器（方块打开界面用）
		registerGuiHandler();

		// 成就总表必须在物品与方块注册之后（图标直接引用它们），
		// 注册进 Minecraft 原生成就系统，单独占一个名为 MNGA 的成就页
		ModAchievements.init();

		SerializableRecipe.additionalListeners.add(new IRecipeRegisterListener() {
			@Override
			public void onRecipeLoad(String recipeClassName) {
				if(recipeClassName.equals("BlastFurnaceRecipesNT")) {
					ModRecipes.addAdvancedAlloyRecipe();
					ModRecipes.addGypsumBlastRecipe();// 石膏 + 焦炭 → 硫 + 钙粉
					ModRecipes.addGypsumBrickBlastRecipe();// 黏土 + 石膏 → 砖
					ModRecipes.addStrontiumBlastRecipe();// 氧化锶 + 铝粉 → 锶粉 + 炉渣
					ModRecipes.addLeadZincBlastRecipe();// 铅锌烧结块 + 焦炭 → 铅锭 + 蓝粉
				}
			if(recipeClassName.equals("ArcFurnaceRecipes")) {
				ModRecipes.addTungstenCarbideArcRecipe();
				ModRecipes.addTungstencobaltArcRecipe();
			}
				if(recipeClassName.equals("ElectrolyserFluidRecipes")) {
					ModRecipes.addSaltWaterElectrolyserRecipe();
					ModRecipes.addHCLElectrolyserRecipe();// ★ 必须挂在它自己的分支
					ModRecipes.addSaltSeaWaterElectrolyserRecipe();// 浓缩海水 → 氯气 + 碱液
					ModRecipes.addZincElectrolyserRecipe();// 硫酸锌液 → 硫酸 + 锌粉 x2（1.5 倍原料换 2 倍产出）
					ModRecipes.addDeepSeaFluidElectrolyserRecipes();// 尾砂浆 / 重砂浆电解：水、氢气、阳极泥
				}
				if(recipeClassName.equals("CombinationRecipes")) {
					ModRecipes.addSulfurCombinationRecipe();// 复式炼焦炉：硫粉 → 二氧化硫
				}
				if(recipeClassName.equals("PyroOvenRecipes")) {
					ModRecipes.addSulfurPyroRecipe();// 二氧化硫
					ScheeliteRecipes.addPyroOvenRecipes();// 三氧化钨、钨粉、白砷
					ModRecipes.addArsineCrackingRecipe();// 砷粒、液氢
					ModRecipes.addDeepSeaPyroRecipes();// 碱熔砂、稀土熟料、铂粉
				}
				if(recipeClassName.equals("FusionRecipes")) {
					com.makentmgreatagain.recipes.HydrogenFusion.addRecipe();// 氢-氢聚变
				}
				if(recipeClassName.equals("ChemicalPlantRecipes")) {
					ModRecipes.addBaltFireRecipe();
					ModRecipes.addAceticAcidRecipe();
					ModRecipes.addSyntheticEthanolRecipes();// 工业合成乙醇：乙烯水合(简单)+合成气两步(难)
					ModRecipes.addEthylAcetateRecipe();
					ModRecipes.addPropyleneOxideRecipes();// 环氧丙烷、高性能溶剂、有机溶剂、冷却液、聚合物锭、硬质塑料锭
					ModRecipes.addHeavyOilChemicalRecipes();// 润滑油、沥青、高纯炭黑
					ModRecipes.addTungstenCarbideRecipe();
					ModRecipes.addCokeSyngasRecipes();// 石油/煤/褐煤焦炭 + 氢气 + 水 → 合成气
					ScheeliteRecipes.addChemicalPlantRecipes();// 钨酸、白砷、铍精矿、锆精矿
					ModRecipes.addCobaltCarbideRecipe();
					ModRecipes.addSaltWaterRecipe();      // 需要 dustSalt，没有就自动跳过
					ModRecipes.addHCLChemicalPlantRecipe();
					// 浓缩海水、碱液、盐水、溴粉、石膏、钙溶液
					ModRecipes.addSeawaterConcentrationRecipes();
					ModRecipes.addSeawaterSaltRecipes();
					ModRecipes.addSeawaterGypsumRecipes();
					ModRecipes.addSeawaterAdsorptionRecipes();
					ModRecipes.addGypsumBoneMealRecipe();// 骨粉
					// 木框架、六种木浆
					ModRecipes.addWoodFrameRecipe();
					ModRecipes.addWoodMashChemicalRecipe();
					ModRecipes.addWoodPulpRecipes();
					ModRecipes.addBioOilChainRecipes();// 木浆制油三段链：解聚→加氢脱氧→异构重整
					// 高纯炭黑、富勒烯溶液、石墨电极、碳粉盒、橡胶锭、石墨锭
					ModRecipes.addPolarBranchRecipes();
					// 天青石、小撮钕粉
					ModRecipes.addStrontiumChemicalRecipe();
					ModRecipes.addRareEarthNeodymiumRecipe();
					// 氦-4、二氧化碳
					ModRecipes.addGasHeliumRecipe();
					// 甘油、硝基苯、硝基甲烷、二硝基苯、航空汽油、硝酸甘油、四氧化二氮、甲醇、硝酸
					ModRecipes.addPetrochemRecipes();
					// 铅锌精矿、硫酸、镉粉、锌粉、白砷、砷化氢
					ModRecipes.addLeadZincChemicalRecipes();
					// 盐酸、硫酸钠
					ModRecipes.addChlorAlkaliDechlorRecipe();
					ModRecipes.addDeepSeaChemicalRecipes();// 尾氯化气、粗铂液、钴料液
					// ===== 原先只有兜底、缺监听的补齐（/ntmreload 重建后不再消失）=====
					ModRecipes.addPetrochemDownstreamRecipes();// 石油下游9条（PU/电木/MTBE/高级燃料/N2O4二硝基/PUREX/生物柴油/苯胺/溶剂）
					ModRecipes.addThiosulfateGoldRecipes();// 无氰浸金：硫代硫酸盐合成/浸出/置换/脱氯
					ModRecipes.addCokerGasDownstreamRecipes();// 焦化气下游（大化工独占+热解石墨）
					ModRecipes.addRubberBranchRecipes();// 胶体→生胶→橡胶锭
					ModRecipes.addCalciumBranchRecipes();// 钙溶液→石灰石/石膏
					ModRecipes.addPeekChemicalRecipes();// 氢氟酸、氟苯、PEEK 锭

					// 等本体表和附属配方都补齐后，再重建大型机的配方表
					LargeChemicalFactoryRecipes.invalidate();
					LargeChemicalFactoryRecipes.get();
				}
				if(recipeClassName.equals("HydrotreatingRecipes")) {
					ModRecipes.addAnilineHydrotreatingRecipe();// 苯胺、水
					ModRecipes.addBTXUpstreamRecipes();// 芳香烃、高硫天然气
					ModRecipes.addHeavyOilHydrocrackingRecipe();// 柴油、高硫天然气
				}
				if(recipeClassName.equals("CrackingRecipes")) {
					ModRecipes.addHeavyOilCrackingRecipes();// 裂化油、石油气、芳香烃
				}
				// 真空表与重整表没有别的注入点，挂在这里模板文件才会带上
				if(recipeClassName.equals("VacuumRefineryRecipes") || recipeClassName.equals("ReformingRecipes")) {
					ModRecipes.addBTXUpstreamRecipes();// 减压重油、重整油、减压轻油、重整气、芳香烃、液氢
				}
				if(recipeClassName.equals("PUREXRecipes")) {
					ModRecipes.addSeawaterPUREXRecipe();// 铀粉、硝酸
					ModRecipes.addBismuthRecoveryRecipes();// 铋粒
					ModRecipes.addDeepSeaPUREXRecipes();// 钍精矿、混合稀土氢氧化物(直接沉淀)
				}
				if(recipeClassName.equals("FusionRecipes")) {
					//ModRecipes.addFusionHelium3Recipe();// 氦-3（定义已注释停用，此处同步注释）
				}
				if(recipeClassName.equals("ParticleAcceleratorRecipes")) {
					ModRecipes.addPositronAcceleratorRecipe();// 正电子：氢胶囊×2 低动量
				}
				if(recipeClassName.equals("SolidificationRecipes")) {
					ModRecipes.addWaterToSnowballRecipe();
					ModRecipes.addSreaWaterToSaltRecipe();
					ModRecipes.addCoreLineVanillaRecipes();// 地核线：铁系固化（幂等put）
				}
				if(recipeClassName.equals("MixerRecipes")) {
					ModRecipes.addSaltWaterMixerRecipe();// 需要 dustSalt，没有就自动跳过
					ModRecipes.addHCLMixerRecipe();
					ModRecipes.addSyntheticEthanolMixerRecipes();// 乙烯水合/甲醇羰基化/乙酸加氢，与化工厂同配比
					ModRecipes.addWoodMashMixerRecipe();// 木质浆料
					ModRecipes.addCoreLineVanillaRecipes();// 地核线：原浆+盐酸→钴料液（幂等put）
				}
				if(recipeClassName.equals("CrystallizerRecipes")) {
					ModRecipes.addWoodPulpCrystallizerRecipes();// 六种原木
					ModRecipes.addDeepSeaCrystallizerRecipes();// 钍晶体、钴晶体
					ModRecipes.addMagicPowderRecipe();// 末影果汁+青金石→魔法粉
					ModRecipes.addEnderBranchRecipe();// 末影果汁+末地石→末影珍珠
					ModRecipes.addXenCrystalAcidRecipe();// 稳定X晶体+经验汁→人造X晶体
					ModRecipes.addXenCrystalSILEXRecipe();// 稳定X晶体+可见光→概率分离
				}
				if(recipeClassName.equals("ShredderRecipes")) {
					ModRecipes.addGraphiteShredderRecipe();
					ScheeliteRecipes.addShredderRecipes();// 白钨矿粉
					ModRecipes.addSaltSandShredderRecipe();
					ModRecipes.addSaltRedSandShredderRecipe();
					ModRecipes.addSaltGravelShredderRecipe();// 盐粉
					ModRecipes.addDeepSeaShredderRecipes();// 重砂混粉、稀土混粉、多金属混粉
					ModRecipes.addPeekShredderRecipes();// PEEK 粉
				}
				if(recipeClassName.equals("CentrifugeRecipes")) {
					ModRecipes.addSaltFromSandRecipe();
					ModRecipes.addSaltFromRedSandRecipe();
					ModRecipes.addSaltFromGravelRecipe();
					ModRecipes.addDeepSeaCentrifugeRecipes();// 锆粉、铌粉、铂族精矿、铀粉
					ModRecipes.addIronFromRedSandRecipe();// 红沙→氧化铁粉
				}
				if(recipeClassName.equals("FractionRecipes")) {
					ModRecipes.addDeepSeaFractionRecipe();// 四氯化钛、尾氯重馏分
				}
				if(recipeClassName.equals("ElectrolyserMetalRecipes")) {
					ModRecipes.addDeepSeaMetalElectrolyserRecipe();// 铂熔体、金粉
					ModRecipes.addCoreLineVanillaRecipes();// 地核线：铁晶体电解（幂等put）
				}
				if(recipeClassName.equals("AnvilRecipes")) {
					AnvilAssemblyTemplates.register();
				}
				if(recipeClassName.equals("AnnihilatorRecipes")) {
					ModRecipes.addCoreLava528Annihilator(); // 528：烧1024铂锭得地核开采蓝图
					ModRecipes.addStellarator528Annihilator(); // 528：烧1024反Sa326单元得仿星器&加热器蓝图
				}
			if(recipeClassName.equals("AssemblyMachineRecipes")) {
				ModRecipes.addEnvironmentPumpAssemblyRecipe(); // 环境地下泵
				ModRecipes.addSeaRiseRigRecipe();              // 海面立管钻机
				ModRecipes.addLargeCatalyticChemicalFactoryRecipe();   //大型化工
				ModRecipes.addShredderAssemblyRecipe();   // 高级粉碎机
				ModRecipes.addGasTurbineAssemblyRecipe(); // 工业燃气轮机
				ModRecipes.addThermalStationAssemblyRecipe(); // 大型火力发电站
				ModRecipes.addSupercriticalMHDAssemblyRecipe(); // 超临界磁流体膨胀发电机
				ModRecipes.addBat9000AssemblyRecipe();    // 大型储罐
				ModRecipes.addElectrolyzerRebornAssemblyRecipe(); // 小型电解机
				ModRecipes.addHybridFractionTowerAssemblyRecipe(); // 混合催化分馏塔
				ModRecipes.addArcHeaterAssemblyRecipe();          // 电弧加热器
				ModRecipes.addPolarBranchRecipes();       // 碳粉盒、橡胶锭
				ModRecipes.addFluidCoolerRecipe();       // 流体冷却机
				ModRecipes.addLargeCentrifugeAssemblyRecipe(); // 大型离心机
				ModPlatinumRecipes.injectAssembly();      // 铂细线替代：金线配方复制一份、产出翻倍
				// ===== 原先只有兜底、缺监听的装配配方补齐 =====
				ModRecipes.addHeatexRebornAssemblyRecipe();        // 集成换热
				ModRecipes.addNuclearExplosionGeneratorRecipe();   // 核爆发生器
				ModNukeCores.addRecipes();                         // 核装药核心（1:1）
				ModNukeCores.applyHazards();                       // 核心危险数据（按配方累加）
				com.makentmgreatagain.init.ModCoatings.addRecipes(); // 五级机器覆层
				ModRecipes.addBatteryCellAssemblyRecipes();        // 电池/电容元件
				ModRecipes.addCoreLavaExtractorRecipe();           // 地核开采机（含528蓝图门槛）
				ModRecipes.addStellaratorRecipe();                 // 仿星器（含528蓝图门槛）
				ModRecipes.addPlasmaHeaterRecipe();                // 等离子加热器（共用528蓝图）
				ModRecipes.addElectricBlastFurnaceRecipe();        // 电力高炉
				}
				// ===== 铂细线替代（金/铅细线 → 铂细线，产出翻倍，原配方保留）=====
				// 这三台机器要挂监听器，否则玩家删掉 config/hbmRecipes 的 json 后
				// HBM 会重建默认表，而重建过程不经过 postInit 的兜底注入，铂版就没了。
				if(recipeClassName.equals("SolderingRecipes")) {
					ModPlatinumRecipes.injectSoldering();
				}
				if(recipeClassName.equals("ArcWelderRecipes")) {
					ModPlatinumRecipes.injectMotor();
				}
				if(recipeClassName.equals("PrecAssRecipes")) {
					ModPlatinumRecipes.injectPrecAss();
				}
				if(recipeClassName.equals("RefineryRecipes")) {
					HBMCompat.addHotBrineRefineryRecipe();    // 氢气、氯气、盐酸、低压蒸汽、钠粉
				}
				if(recipeClassName.equals("OutgasserRecipes")) {
					ModRecipes.removeThoriumFuelOutgasserRecipes(); // 钍232→钍燃料棒已按用户要求删除
				}
			}
		});
	}
    @EventHandler
    public void init(FMLInitializationEvent event) {
        // 检查 Mixin 是否真的把 IEnergyHandler 注入到了 TileEntityLoadedBase
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // HBM 的配方注册表在这里才完整，接入 HBM 机器配方的逻辑由 proxy.postInit 触发
        proxy.postInit(event);

        // 自然遗迹：把结构注册进 HBM 的 NBTStructure 静态表，由 HBM 的生成器顺带生成。
        // 必须晚于 HBM 的 init（那边才把 NBTStructure.Component 登记进 MapGenStructureIO），
        // 结构文件不存在时这里只打一条 warn 并跳过，不会崩。
        AddonStructures.register();

        // 塔可夫食物进补给堆：ItemPool 在 HBM 的 PostLoad 里才建表并可能被 json 整表替换，
        // 只能在 postInit 之后往里追加（早于此时 pools 还是空的）。
        ModTarkovFood.addLoot();
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // 所有模组的 postInit 都跑完了，这时候再重建大型机的配方表，保证本体表 + 独占配方都齐全
        LargeChemicalFactoryRecipes.invalidate();
        LargeChemicalFactoryRecipes.get();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        LocatorNetwork.serverStopped();
    }
}

/*运行测试：
$env:JAVA_HOME='D:\JDK8'; $env:Path='D:\JDK8\bin;' + $env:Path; .\gradlew.bat runClient
*/
/*打包jar：
$env:JAVA_HOME='D:\JDK8'; $env:Path='D:\JDK8\bin;' + $env:Path; .\gradlew.bat buildAddonJar reobf
*/

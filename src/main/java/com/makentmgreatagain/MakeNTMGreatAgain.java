package com.makentmgreatagain;

//配方注册
import api.hbm.recipe.IRecipeRegisterListener;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.makentmgreatagain.container.ContainerAlloyShredder;
import com.makentmgreatagain.container.ContainerHeatexReborn;
import com.makentmgreatagain.gui.GUIAlloyShredder;
import com.makentmgreatagain.gui.GUIHeatexReborn;
import com.makentmgreatagain.handler.ModFuelHandler;
import com.makentmgreatagain.init.HBMCompat;
import com.makentmgreatagain.init.ModAchievements;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluidLoaders;
import com.makentmgreatagain.init.ModFluids;
import com.makentmgreatagain.init.ModItems;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModRecipes;
import com.makentmgreatagain.recipes.AnvilAssemblyTemplates;
import com.makentmgreatagain.world.WorldGenLeadZincOre;
import com.makentmgreatagain.world.WorldGenRefractoryGoldOre;
import com.makentmgreatagain.world.WorldGenSaltBlocks;
import com.makentmgreatagain.proxy.CommonProxy;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
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
    public static final String VERSION = "1.0.0-beta.4";

    /** 统一用这个 logger 打日志，输出会带上模组名，比 System.out.println 好排查。 */
    public static final Logger LOG = LogManager.getLogger(NAME);

    /** Forge 注入的模组实例，用于注册本模组的 GUI 处理器 */
    @Instance(MakeNTMGreatAgain.MODID)
    public static MakeNTMGreatAgain instance;

    /** GUI ID：对应本模组的第一个 GUI（高级粉碎机） */
    public static final int GUI_ALLOY_SHREDDER = 0;
    /** GUI ID：集成式换热加热器 */
    public static final int GUI_HEATEX_REBORN = 1;

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
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if(id == GUI_ALLOY_SHREDDER) {
            if(world.getTileEntity(x, y, z) instanceof TileEntityAlloyShredder) {
                return new GUIAlloyShredder(player.inventory, (TileEntityAlloyShredder) world.getTileEntity(x, y, z));
            }
        }
        if(id == GUI_HEATEX_REBORN) {
            if(world.getTileEntity(x, y, z) instanceof TileEntityHeatexReborn) {
                return new GUIHeatexReborn(player.inventory, (TileEntityHeatexReborn) world.getTileEntity(x, y, z));
            }
        }
        return null;
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

		// 世界生成配置（config/MNGA/worldgen.cfg）+ 注册含盐方块生成
		ModConfig.init(event.getModConfigurationDirectory());
		GameRegistry.registerWorldGenerator(new WorldGenSaltBlocks(), 0);
		// 铅锌矿：小簇、只主世界、只替换石头（ISP 法提镉的矿石来源）
		GameRegistry.registerWorldGenerator(new WorldGenLeadZincOre(), 0);
		// 含砷金矿：比铅锌矿浅、更密，硫代硫酸盐浸金的矿石来源
		GameRegistry.registerWorldGenerator(new WorldGenRefractoryGoldOre(), 0);

		// 注册本模组 GUI 处理器（方块打开界面用）
		registerGuiHandler();

		// 成就注册必须在物品注册之后（图标引用香烟物品）
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
					ModRecipes.addEnvironmentPumpAssemblyRecipe();
				}
				if(recipeClassName.equals("ElectrolyserFluidRecipes")) {
					ModRecipes.addSaltWaterElectrolyserRecipe();
					ModRecipes.addHCLElectrolyserRecipe();// ★ 必须挂在它自己的分支
					ModRecipes.addSaltSeaWaterElectrolyserRecipe();// 浓缩海水 → 氯气 + 碱液
					ModRecipes.addZincElectrolyserRecipe();// 硫酸锌液 → 硫酸 + 锌粉 x2（1.5 倍原料换 2 倍产出）
				}
				if(recipeClassName.equals("CombinationRecipes")) {
					ModRecipes.addSulfurCombinationRecipe();// 复式炼焦炉：硫粉 → 二氧化硫
				}
				if(recipeClassName.equals("PyroOvenRecipes")) {
					ModRecipes.addSulfurPyroRecipe();// 热解炉：硫粉 + 氧 → 二氧化硫（产量更高）
				}
				if(recipeClassName.equals("ChemicalPlantRecipes")) {
					ModRecipes.addTungstenCarbideRecipe();
					ModRecipes.addCobaltCarbideRecipe();
					ModRecipes.addSaltWaterRecipe();      // 需要 Mekanism 的 dustSalt，没装则内部自动跳过
					ModRecipes.addHCLChemicalPlantRecipe();
					// 海水处理路线（浓缩 / 分盐 / 提溴 / 石膏 / 吸附）
					ModRecipes.addSeawaterConcentrationRecipes();
					ModRecipes.addSeawaterSaltRecipes();
					ModRecipes.addSeawaterGypsumRecipes();
					ModRecipes.addSeawaterAdsorptionRecipes();
					ModRecipes.addGypsumBoneMealRecipe();// 石膏拌骨粉 → 骨粉增产
					// 木浆路线：木框架 + 制浆 + 化工厂版浆料
					ModRecipes.addWoodFrameRecipe();
					ModRecipes.addWoodMashChemicalRecipe();
					ModRecipes.addWoodPulpRecipes();
					// 极地线：天然气 + 离子凝胶 → 高纯炭黑，再接富勒烯与石墨电极
					ModRecipes.addPolarBranchRecipes();
					// 锶线：盐水 + 硫酸 → 天青石；稀土：矿碎块 + 硫酸 → 钕粉
					ModRecipes.addStrontiumChemicalRecipe();
					ModRecipes.addRareEarthNeodymiumRecipe();
					// 气田线：天然气深冷提氦
					ModRecipes.addGasHeliumRecipe();
					// 石油三期：八种新流体
					ModRecipes.addPetrochemRecipes();
					// 铅锌线（ISP 法提镉）
					ModRecipes.addLeadZincChemicalRecipes();
					// 氯碱线：亚硫酸钠脱氯（氯气 → 盐酸 + 硫酸钠）
					ModRecipes.addChlorAlkaliDechlorRecipe();
				}
				if(recipeClassName.equals("HydrotreatingRecipes")) {
					ModRecipes.addAnilineHydrotreatingRecipe();// 硝基苯 + 氢 → 苯胺
				}
				if(recipeClassName.equals("PUREXRecipes")) {
					ModRecipes.addSeawaterPUREXRecipe();// 含矿液 → 铀粉 x6 + 硝酸回收
					ModRecipes.addBismuthRecoveryRecipes();// ZIRNOX 乏燃料副产铋粒
				}
				if(recipeClassName.equals("FusionRecipes")) {
					ModRecipes.addFusionHelium3Recipe();// D+D 中子支路 → 氦-3
				}
				if(recipeClassName.equals("SolidificationRecipes")) {
					ModRecipes.addWaterToSnowballRecipe();
					ModRecipes.addSreaWaterToSaltRecipe();
				}
				if(recipeClassName.equals("MixerRecipes")) {
					ModRecipes.addSaltWaterMixerRecipe();// 需要 Mek 的 dustSalt，没装则内部自动跳过
					ModRecipes.addHCLMixerRecipe();
					ModRecipes.addWoodMashMixerRecipe();// 木油 + 水 → 木质浆料
				}
				if(recipeClassName.equals("CrystallizerRecipes")) {
					ModRecipes.addWoodPulpCrystallizerRecipes();// 木框架 + 木浆 → 原木 x4
				}
				if(recipeClassName.equals("ShredderRecipes")) {
					ModRecipes.addGraphiteShredderRecipe();
					ModRecipes.addSaltSandShredderRecipe();
					ModRecipes.addSaltRedSandShredderRecipe();
					ModRecipes.addSaltGravelShredderRecipe();// 石墨锭→石墨粉（覆盖自动生成的 dust 配方）
				}
				if(recipeClassName.equals("CentrifugeRecipes")) {
					ModRecipes.addSaltFromSandRecipe();
					ModRecipes.addSaltFromRedSandRecipe();
					ModRecipes.addSaltFromGravelRecipe();
				}
				if(recipeClassName.equals("AnvilRecipes")) {
					AnvilAssemblyTemplates.register();
				}
				if(recipeClassName.equals("AssemblyMachineRecipes")) {
					ModRecipes.addShredderAssemblyRecipe();   // 高级粉碎机本体（装配机/大型装配机共用）
					ModRecipes.addBat9000AssemblyRecipe();    // 巨尻-9000 储罐（重生版）
					ModRecipes.addPolarBranchRecipes();       // 高纯炭黑 → 碳部件 / 补强橡胶
				}
				if(recipeClassName.equals("RefineryRecipes")) {
					HBMCompat.addHotBrineRefineryRecipe();    // 炼油厂热盐水分解（跨版本兼容类）
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
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}

/*运行测试：
$env:JAVA_HOME='D:\JDK8'; $env:Path='D:\JDK8\bin;' + $env:Path; .\gradlew.bat runClient
*/
/*打包jar：
$env:JAVA_HOME='D:\JDK8'; $env:Path='D:\JDK8\bin;' + $env:Path; .\gradlew.bat buildAddonJar reobf
*/

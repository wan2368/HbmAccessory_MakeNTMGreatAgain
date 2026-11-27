package com.makentmgreatagain.proxy;

import com.hbm.qmaw.QMAWLoader;
import com.hbm.render.item.ItemRenderTransformer;
import com.hbm.render.tileentity.RenderMassStorage;
import com.hbm.tileentity.machine.storage.TileEntityMachineFluidTank;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.client.ItemRenderBat9000Reborn;
import com.makentmgreatagain.client.QMAWManualHook;
import com.makentmgreatagain.client.AddonModels;
import com.makentmgreatagain.client.RenderFluidCooler;
import com.makentmgreatagain.client.RenderMachineDrain;
import com.makentmgreatagain.client.RenderArcHeater;
import com.makentmgreatagain.client.RenderCoreLavaExtractor;
import com.makentmgreatagain.client.RenderIndustrialGasTurbine;
import com.makentmgreatagain.client.RenderLargeThermalPowerStation;
import com.makentmgreatagain.client.RenderStellarator;
import com.makentmgreatagain.client.RenderPlasmaHeater;
import com.makentmgreatagain.client.RenderLargeCentrifuge;
import com.makentmgreatagain.client.RenderBat9000Reborn;
import com.makentmgreatagain.client.RenderLargeCatalyticChemicalFactory;
import com.makentmgreatagain.client.RenderElectricBlastFurnace;
import com.makentmgreatagain.gui.GUILargeCatalyticChemicalFactory;
import com.makentmgreatagain.client.RenderPipeAnchorAdvanced;
import com.makentmgreatagain.client.RenderRiserPipe;
import com.makentmgreatagain.client.RenderRiserRig;
import com.makentmgreatagain.client.RenderElectrolyzerReborn;
import com.makentmgreatagain.client.RenderEnvironmentPump;
import com.makentmgreatagain.client.RenderHeatexReborn;
import com.makentmgreatagain.client.RenderHybridCatalyticFractionTower;
import com.makentmgreatagain.client.RenderNuclearExplosionGenerator;
import com.makentmgreatagain.client.RenderModFluidTank;
import com.makentmgreatagain.gui.GUIAlloyShredder;
import com.makentmgreatagain.gui.GUIFluidCooler;
import com.makentmgreatagain.gui.GUINuclearExplosion;
import com.makentmgreatagain.gui.GUIHeatexReborn;
import com.makentmgreatagain.gui.GUIHybridCatalyticFractionTower;
import com.makentmgreatagain.gui.GUIRiserRig;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModItems;
import com.makentmgreatagain.render.ScreenEffectManager;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityFluidCooler;
import com.makentmgreatagain.tileentity.TileEntityMachineDrain;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;
import com.makentmgreatagain.tileentity.TileEntityArcHeater;
import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;
import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;
import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;
import com.makentmgreatagain.tileentity.TileEntityStellarator;
import com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace;
import com.makentmgreatagain.tileentity.TileEntityElectrolyzerReborn;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;
import com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;
import com.makentmgreatagain.tileentity.TileEntityIronMassStorage;
import com.makentmgreatagain.tileentity.TileEntityPipeAnchorAdvanced;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;
import com.makentmgreatagain.tileentity.TileEntityRiserPipe;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // 先执行父类逻辑，再做客户端专属的事
        super.preInit(event);

        // 这里将来放渲染器注册等客户端代码
        registerManualSource();
    }
	
    private static void registerManualSource() {

        File self = findSelfFile();

        if (self == null) {
            MakeNTMGreatAgain.LOG.warn("[QMAW] 没找到附属 jar 本身，改由 QMAWManualHook 按类路径补页");
            return;
        }

        QMAWLoader.registerModFileURL(self);
        MakeNTMGreatAgain.LOG.info("[QMAW] 已登记附属手册来源: " + self.getAbsolutePath());
    }
	
    private static File findSelfFile() {

        try {
            ModContainer container = FMLCommonHandler.instance().findContainerFor(MakeNTMGreatAgain.MODID);
            if (container != null) {
                File src = container.getSource();
                if (src != null && src.isFile()) return src;
            }
        } catch (Throwable t) {
            MakeNTMGreatAgain.LOG.warn("[QMAW] 读取 FML 容器来源失败", t);
        }

        try {
            File code = new File(MakeNTMGreatAgain.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (code.isFile()) return code;
        } catch (Throwable t) {
            MakeNTMGreatAgain.LOG.warn("[QMAW] 读取 CodeSource 失败", t);
        }

        return null;
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // 悬浮背包：模式切换按键（默认左 Ctrl）注册 + 客户端按下事件
        com.makentmgreatagain.client.HoverJetpackKeys.register();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.client.HoverJetpackKeys());

        // DRX 香烟全屏效果渲染器：黑幕 + 屏幕文字，仅客户端注册
        MinecraftForge.EVENT_BUS.register(new ScreenEffectManager());

        // 中微子眼镜：第一人称没入岩浆时减弱浓雾和火焰遮挡
        MinecraftForge.EVENT_BUS.register(new com.makentmgreatagain.client.NeutrinoLavaVision());
        com.makentmgreatagain.client.NeutrinoLocatorClient.register();

        // 核爆屏幕抖动：相机设置事件里加随机偏移
        MinecraftForge.EVENT_BUS.register(new com.makentmgreatagain.render.ClientScreenShake());

        // 核装药核心的 hover 数据：按住 Shift 显示中子通量 / 耗电量 / 液态钠
        MinecraftForge.EVENT_BUS.register(new com.makentmgreatagain.client.NukeCoreTooltip());

        // 大型火力发电站燃料简介：16 种燃料物品提示行「热值: XXX」
        MinecraftForge.EVENT_BUS.register(new com.makentmgreatagain.client.ThermalFuelTooltip());

        // 高强度碳化钨锭：物品栏图标放大 20%（替换 GUI/HUD 渲染实例，失败回退原尺寸）
        com.makentmgreatagain.client.BigIconRender.install();

        // 乱码X晶体：SILEX NEI 配方页不显示它（机器分离照常），替换 HBM 的 SILEX handler
        com.makentmgreatagain.compat.nei.SILEXHiddenHandler.install();

        // 耐摔合金掉落物：原版 RenderItem 会对掉落物做 billboard 旋转，绕 Y 轴转多少都被
        // 抵消，所以必须自己画 quad 才能看出「顺时针自旋」，见 client/RenderItemHelicopter
        cpw.mods.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.makentmgreatagain.entity.EntityItemHelicopter.class,
                new com.makentmgreatagain.client.RenderItemHelicopter());

        // 弃用单方块电容：用附属自己的 OBJ 模型与五面贴图渲染，不依赖原版 RenderCapacitor
        cpw.mods.fml.client.registry.RenderingRegistry.registerBlockHandler(
                new com.makentmgreatagain.client.RenderAddonCapacitor());

        // 无线充电站：标准立方体 + 灯条自发光 overlay
        cpw.mods.fml.client.registry.RenderingRegistry.registerBlockHandler(
                new com.makentmgreatagain.client.RenderWirelessStation());

        // 晶体簇走原版 renderType 1（交叉平面），不需要自定义方块渲染器

        // 手册兜底通道：挂得比 HBM 的 QMAWLoader 晚，每次资源重载都在它之后补页
        IReloadableResourceManager resMan = (IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        QMAWManualHook hook = new QMAWManualHook();
        resMan.registerReloadListener(hook);
        MinecraftForge.EVENT_BUS.register(hook);
        hook.injectMissingPages();

        // NEI 页签的「属于哪个模组」：不发这条 IMC，悬浮提示里 mod 名会显示 Unknown，
        // 并且把 HandlerName/HandlerID/HandlerOrder 三行调试信息一并常驻出来。
        // 原理与字段坑见 compat/nei/NEIHandlerInfos 的类注释。
        com.makentmgreatagain.compat.nei.NEIHandlerInfos.send();
    }

    /**
     * 接管储罐渲染，让盐水用自己的 tank_WATER.png
     */
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityMachineFluidTank.class,
                new RenderModFluidTank());

        // 铁质大规模存储单元：正面悬浮显示存储物 + 存量，直接复用本体 RenderMassStorage
        // （它的朝向计算依赖 metadata / 4，本方块沿用 0/4/8/12 的布局）
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityIronMassStorage.class, new RenderMassStorage());

        // 「巨尻-9000」储罐（重生版）：世界里的 3D 罐体 + 四角危险品标志 + 液量条
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityBat9000Reborn.class,
                new RenderBat9000Reborn());

        // 物品形态（手持/物品栏/掉落物）也画同一套模型，替代方块图标
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.block_bat9000_reborn),
                new ItemRenderBat9000Reborn());

        // 环境地下泵：贴图随当前产出流体切换，只能由 TESR 决定
        RenderEnvironmentPump pumpRenderer = new RenderEnvironmentPump();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEnvironmentPump.class, pumpRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.environment_pump),
                pumpRenderer.getRenderer());

        // 重生版换热器：5×3×1 机体 + 顶部 3×3 热面
        RenderHeatexReborn heatexRenderer = new RenderHeatexReborn();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHeatexReborn.class, heatexRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.heatex_reborn),
                heatexRenderer.getRenderer());

        // 流体冷却机：3×3×7 机体，模型沿用本体紧凑型压缩机的 obj
        RenderFluidCooler fluidCoolerRenderer = new RenderFluidCooler();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFluidCooler.class, fluidCoolerRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_fluid_cooler),
                fluidCoolerRenderer.getRenderer());

        // 排液管：1×1×1 底座，底部内凹 2px 排口，UV 沿用原版 drain.obj
        RenderMachineDrain drainRenderer = new RenderMachineDrain();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineDrain.class, drainRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_drain),
                drainRenderer.getRenderer());
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_drain_tungsten),
                drainRenderer.itemRenderer(AddonModels.drain_tungsten_tex));
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_drain_adjustable_steel),
                drainRenderer.itemRenderer(AddonModels.drain_adjustable_tex));
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_drain_adjustable_tungsten),
                drainRenderer.itemRenderer(AddonModels.drain_adjustable_tungsten_tex));

        // 流体传送器 / 物品传送器：1×1×1 EIO 风格外框 + 悬浮旋转 X 晶体
        com.makentmgreatagain.client.RenderXenTransceiver xenTransceiverRenderer = new com.makentmgreatagain.client.RenderXenTransceiver();
        ClientRegistry.bindTileEntitySpecialRenderer(com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver.class, xenTransceiverRenderer);
        ClientRegistry.bindTileEntitySpecialRenderer(com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver.class, xenTransceiverRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.xen_fluid_transceiver),
                xenTransceiverRenderer.itemRenderer(AddonModels.xen_fluid_transceiver_tex));
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.xen_item_transceiver),
                xenTransceiverRenderer.itemRenderer(AddonModels.xen_item_transceiver_tex));

        // 混合催化分馏塔：5×5×6 八棱柱，底部 8 个检修口
        RenderHybridCatalyticFractionTower fractionTowerRenderer = new RenderHybridCatalyticFractionTower();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHybridCatalyticFractionTower.class, fractionTowerRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_hybrid_catalytic_fraction_tower),
                fractionTowerRenderer.getRenderer());

        // 可控核爆炸发生器：13×13×17，模型按材质拆成 6 个 OBJ 逐个换贴图
        RenderNuclearExplosionGenerator nukeGeneratorRenderer = new RenderNuclearExplosionGenerator();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityNuclearExplosionGenerator.class, nukeGeneratorRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_nuclear_explosion_generator),
                nukeGeneratorRenderer.getRenderer());

        // 大型催化化工厂：8×4×8 机体 + 顶部两个风扇
        RenderLargeCatalyticChemicalFactory chemfacRenderer = new RenderLargeCatalyticChemicalFactory();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeCatalyticChemicalFactory.class, chemfacRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_large_catalytic_chemical_factory),
                chemfacRenderer.getRenderer());

        // 电力高炉：5×5×7 机体
        RenderElectricBlastFurnace ebfRenderer = new RenderElectricBlastFurnace();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityElectricBlastFurnace.class, ebfRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.machine_electric_blast_furnace),
                ebfRenderer.getRenderer());

        // 小型电解机：2×2×3 机体，模型原点在足迹中心，渲染时额外平移半格
        RenderElectrolyzerReborn electrolyzerRenderer = new RenderElectrolyzerReborn();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityElectrolyzerReborn.class, electrolyzerRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.electrolyzer_reborn),
                electrolyzerRenderer.getRenderer());

        // 电弧加热器：3×2×3 机体，模型 3.5 格高，背部接线柱伸出半格
        RenderArcHeater arcHeaterRenderer = new RenderArcHeater();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityArcHeater.class, arcHeaterRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.arc_heater),
                arcHeaterRenderer.getRenderer());

        // 大型离心机：旧版回旋加速器模型，5×5×3
        RenderLargeCentrifuge centrifugeRenderer = new RenderLargeCentrifuge();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeCentrifuge.class, centrifugeRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.large_centrifuge),
                centrifugeRenderer.getRenderer());

        // 地核熔岩开采机（旧版 AMS发射极）：5×5×6，熔毁换烧毁模型 + 效率>0 时向下画光束
        RenderCoreLavaExtractor coreLavaExtractorRenderer = new RenderCoreLavaExtractor();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCoreLavaExtractor.class, coreLavaExtractorRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.core_lava_extractor),
                coreLavaExtractorRenderer.getRenderer());

        // 工业燃气轮机：4×13×4 外观多方块，模型原点在核心格底面中心，仅按 metadata 旋转
        RenderIndustrialGasTurbine gasTurbineRenderer = new RenderIndustrialGasTurbine();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityIndustrialGasTurbine.class, gasTurbineRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.industrial_gas_turbine),
                gasTurbineRenderer.getRenderer());

        // 大型火力发电站：6×4×5 外观多方块，模型原点在核心格底面中心，仅按 metadata 旋转
        RenderLargeThermalPowerStation thermalPowerStationRenderer = new RenderLargeThermalPowerStation();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityLargeThermalPowerStation.class, thermalPowerStationRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.large_thermal_power_station),
                thermalPowerStationRenderer.getRenderer());

        // 仿星器：31×29×9 外观多方块，模型原点在核心格底面中心，仅按 metadata 旋转
        RenderStellarator stellaratorRenderer = new RenderStellarator();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityStellarator.class, stellaratorRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.stellarator),
                stellaratorRenderer.getRenderer());

        // 等离子加热器：21×7×8 长条机器，模型原点在核心格底面中心
        RenderPlasmaHeater plasmaHeaterRenderer = new RenderPlasmaHeater();
        ClientRegistry.bindTileEntitySpecialRenderer(com.makentmgreatagain.tileentity.TileEntityPlasmaHeater.class, plasmaHeaterRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.plasma_heater),
                plasmaHeaterRenderer.getRenderer());

        // 超临界磁流体膨胀发电机：11×5×5 无 GUI 多方块
        com.makentmgreatagain.client.RenderSupercriticalMHDGenerator mhdRenderer = new com.makentmgreatagain.client.RenderSupercriticalMHDGenerator();
        ClientRegistry.bindTileEntitySpecialRenderer(com.makentmgreatagain.tileentity.TileEntitySupercriticalMHDGenerator.class, mhdRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.supercritical_mhd_generator),
                mhdRenderer.getRenderer());

        // 海面立管钻机：7×7×16 平台井架
        RenderRiserRig riserRigRenderer = new RenderRiserRig();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRiserRig.class, riserRigRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.riser_rig),
                riserRigRenderer.getRenderer());

        // 泵送时落下的输送管道方块
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRiserPipe.class, new RenderRiserPipe());

        // 碳化钨剑：32×32 贴图在手里的面片是固定尺寸，显得小。
        // 复用本体大剑（steel_sword/cobalt_sword 等）同款变换参数：
        // 第一人称 1.36×、第三人称 1.7×，物品栏图标与掉落物不受影响。
        double[] rtp = new double[] {0, 180, -90};
        double[] ttp_high = new double[] {0.125, 0.625, 0};
        double[] stp = new double[] {1.7, 1.7, 0.85};
        double[] rfp = new double[] {0, 180, -90};
        double[] tfp = new double[] {1.13, 5.2, -0.26};
        double[] sfp = new double[] {1.36, 1.36, 0.68};
        double[] rir = new double[] {0, 0, 0};
        double[] tir = new double[] {0, 0, 0};
        double[] sir = new double[] {1, 1, 1};
        MinecraftForgeClient.registerItemRenderer(ModItems.cobalt_tungsten_sword,
				
                new ItemRenderTransformer(rtp, ttp_high, stp, rfp, tfp, sfp, rir, tir, sir));
		
		MinecraftForgeClient.registerItemRenderer(ModItems.alloy_sword_reborn,

			new ItemRenderTransformer(rtp, ttp_high, stp, rfp, tfp, sfp, rir, tir, sir));
		
		MinecraftForgeClient.registerItemRenderer(ModItems.helicopter_pickaxe,

			new ItemRenderTransformer(rtp, ttp_high, stp, rfp, tfp, sfp, rir, tir, sir));

		MinecraftForgeClient.registerItemRenderer(ModItems.helicopter_axe,

			new ItemRenderTransformer(rtp, ttp_high, stp, rfp, tfp, sfp, rir, tir, sir));

        // 高级管锚（25m）：
        RenderPipeAnchorAdvanced advancedAnchorRenderer = new RenderPipeAnchorAdvanced(
                Item.getItemFromBlock(ModBlocks.pipe_anchor_advanced),
                new ResourceLocation("makentmgreatagain", "textures/models/network/pipe_anchor_advanced.png"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPipeAnchorAdvanced.class, advancedAnchorRenderer);
        MinecraftForgeClient.registerItemRenderer(
                Item.getItemFromBlock(ModBlocks.pipe_anchor_advanced), advancedAnchorRenderer.getRenderer());
    }

    /**
     * 客户端 GUI 工厂：GUI 类只在这里 new。
     */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {

        if(id == MakeNTMGreatAgain.GUI_ALLOY_SHREDDER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityAlloyShredder) {
                return new GUIAlloyShredder(player.inventory, (TileEntityAlloyShredder) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_HEATEX_REBORN) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityHeatexReborn) {
                return new GUIHeatexReborn(player.inventory, (TileEntityHeatexReborn) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_RISER_RIG) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityRiserRig) {
                return new GUIRiserRig(player.inventory, (TileEntityRiserRig) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_HYBRID_CATALYTIC_FRACTION_TOWER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityHybridCatalyticFractionTower) {
                return new GUIHybridCatalyticFractionTower(player.inventory, (TileEntityHybridCatalyticFractionTower) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_FLUID_COOLER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityFluidCooler) {
                return new GUIFluidCooler(player.inventory, (TileEntityFluidCooler) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_NUCLEAR_EXPLOSION_GENERATOR) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityNuclearExplosionGenerator) {
                return new GUINuclearExplosion(player.inventory, (TileEntityNuclearExplosionGenerator) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_LARGE_CATALYTIC_CHEMICAL_FACTORY) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityLargeCatalyticChemicalFactory) {
                return new GUILargeCatalyticChemicalFactory(player.inventory, (TileEntityLargeCatalyticChemicalFactory) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_LARGE_CENTRIFUGE) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof TileEntityLargeCentrifuge) {
                return new com.makentmgreatagain.gui.GUILargeCentrifuge(player.inventory, (TileEntityLargeCentrifuge) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_BATTERY_ARRAY) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityBatteryArray) {
                return new com.makentmgreatagain.gui.GUIBatteryArray(player.inventory, (com.makentmgreatagain.tileentity.TileEntityBatteryArray) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_CORE_LAVA_EXTRACTOR) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor) {
                return new com.makentmgreatagain.gui.GUICoreLavaExtractor(player.inventory, (com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_ELECTRIC_BLAST_FURNACE) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace) {
                return new com.makentmgreatagain.gui.GUIElectricBlastFurnace(player.inventory, (com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_ELECTRIC_BOILER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityElectricBoiler) {
                return new com.makentmgreatagain.gui.GUIElectricBoiler(player.inventory, (com.makentmgreatagain.tileentity.TileEntityElectricBoiler) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_INDUSTRIAL_GAS_TURBINE) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine) {
                return new com.makentmgreatagain.gui.GUIIndustrialGasTurbine(player.inventory, (com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_LARGE_THERMAL_POWER_STATION) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation)
                return new com.makentmgreatagain.gui.GUILargeThermalPowerStation(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation) te);
        }

        if(id == MakeNTMGreatAgain.GUI_STELLARATOR) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityStellarator) {
                return new com.makentmgreatagain.gui.GUIStellarator(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityStellarator) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_PLASMA_HEATER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityPlasmaHeater) {
                return new com.makentmgreatagain.gui.GUIPlasmaHeater(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityPlasmaHeater) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_MACHINE_DRAIN) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityMachineDrain) {
                return new com.makentmgreatagain.gui.GUIMachineDrain(
                        (com.makentmgreatagain.tileentity.TileEntityMachineDrain) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_XEN_FLUID_TRANSCEIVER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver) {
                return new com.makentmgreatagain.gui.GUIXenFluidTransceiver(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver) te);
            }
        }

        if(id == MakeNTMGreatAgain.GUI_XEN_ITEM_TRANSCEIVER) {
            TileEntity te = world.getTileEntity(x, y, z);
            if(te instanceof com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver) {
                return new com.makentmgreatagain.gui.GUIXenItemTransceiver(player.inventory,
                        (com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver) te);
            }
        }

        return null;
    }
}

package com.makentmgreatagain.proxy;

import com.hbm.qmaw.QMAWLoader;
import com.hbm.tileentity.machine.storage.TileEntityMachineFluidTank;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.client.ItemRenderBat9000Reborn;
import com.makentmgreatagain.client.QMAWManualHook;
import com.makentmgreatagain.client.RenderBat9000Reborn;
import com.makentmgreatagain.client.RenderEnvironmentPump;
import com.makentmgreatagain.client.RenderHeatexReborn;
import com.makentmgreatagain.client.RenderModFluidTank;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.render.ScreenEffectManager;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.Item;
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

        // 手册兜底通道：挂得比 HBM 的 QMAWLoader 晚，每次资源重载都在它之后补页
        IReloadableResourceManager resMan = (IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        QMAWManualHook hook = new QMAWManualHook();
        resMan.registerReloadListener(hook);
        hook.injectMissingPages();
    }

    /**
     * 接管储罐渲染，让盐水用自己的 tank_WATER.png。
     *
     * 必须放在 postInit：HBM 在自己的 preInit/init 里已经给 TileEntityMachineFluidTank
     * 注册过 RenderFluidTank，这里再 bind 一次会把 map 里的值覆盖掉（后注册生效）。
     *
     * 同理，重生版巨尻-9000 的方块实体渲染器与物品渲染器也在这里注册：
     * 此时 ModBlocks.init() 早已跑完（本方法由主类 postInit 调用），
     * Item.getItemFromBlock 才能拿到真实的方块物品；模型本身是懒加载的，
     * 只有第一次真正渲染时才读 obj 建 VBO，那时 GL 上下文必然存在。
     */
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityMachineFluidTank.class,
                new RenderModFluidTank());

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
    }
}

package com.makentmgreatagain.proxy;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.compat.NTMCompat;
import com.makentmgreatagain.hook.CigaretteDeathHandler;
import com.makentmgreatagain.hook.CigaretteKillHandler;
import com.makentmgreatagain.hook.ShredderBladeHandler;
import com.makentmgreatagain.init.ModRecipes;
import com.makentmgreatagain.recipes.AnvilAssemblyTemplates;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * 服务端代理（CommonProxy）。
 *
 * Forge 把「客户端和服务端都要跑」的逻辑放在这里；客户端独有的逻辑
 * （贴图、渲染器、音效）放在 ClientProxy 里覆盖对应方法。
 * 专用服务端启动时只加载这个类，所以这里不能出现客户端专属代码，否则开服会崩。
 *
 * 四个生命周期方法的执行顺序固定：preInit - init - postInit - serverStarting。
 */
public class CommonProxy {

    /** 最早执行：读配置、创建物品和方块、注册到 GameRegistry。 */
    public void preInit(FMLPreInitializationEvent event) {
        MakeNTMGreatAgain.LOG.info("MakeNTMGreatAgain preInit, version " + MakeNTMGreatAgain.VERSION);
    }

    /** 主初始化：注册本模组自己的合成配方（原版工作台配方，不依赖 HBM）。 */
    public void init(FMLInitializationEvent event) {
        ModRecipes.init();
        // 玩家死亡时清零香烟计数（复活后的"第一次"重新从第一档开始）
        MinecraftForge.EVENT_BUS.register(new CigaretteDeathHandler());
        // DRX 香烟特效后的延迟击杀（ServerTickEvent 走 FML 总线）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new CigaretteKillHandler());
        // 高级合金刀片磨满自动变损坏刀片（ServerTickEvent 走 FML 总线）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new ShredderBladeHandler());
        // Hover jetpack: drive flight logic when worn standalone in the chest slot
        // PlayerTickEvent 是 FML 事件总线，注册到 Forge 总线会永远收不到（导致单穿不工作）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.hook.HoverJetpackStandaloneHandler());
    }

    /**
     * 最后初始化：其他模组此时已全部加载完毕。
     * 「接入 HBM 机器配方」必须放在这里，放早了会拿到 HBM 还没注册好的 null 对象。
     */
    public void postInit(FMLPostInitializationEvent event) {
        NTMCompat.init();
        // 兜底注入：HBM 的 onRecipeLoad 只在"无配置文件→registerDefaults"分支触发；
        // 玩家改过 config/hbmRecipes 后监听器不跑，这里在配方表建好后再幂等注入一遍。
        // 必须在 NTMCompat.init() 之后（它往高炉/化工厂注册的配方名也要参与判重）。
        ModRecipes.injectAllFallback();
		AnvilAssemblyTemplates.register();
    }

    /** 服务端启动时执行，注册命令用。 */
    public void serverStarting(FMLServerStartingEvent event) {
    }
}

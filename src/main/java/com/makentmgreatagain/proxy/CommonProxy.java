package com.makentmgreatagain.proxy;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.compat.NTMCompat;
import com.makentmgreatagain.hook.CigaretteKillHandler;
import com.makentmgreatagain.hook.ShredderBladeHandler;
import com.makentmgreatagain.init.ModRecipes;
import com.makentmgreatagain.recipes.AnvilAssemblyTemplates;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
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
        registerEntities();
    }

    /**
     * 实体注册。
     *
     * <p>目前只有一个：耐摔合金系列的掉落物 {@code EntityItemHelicopter}
     * （自旋 + 120 转/分后升空，行为见 {@code entity/EntityItemHelicopter}）。
     * 物品侧通过 {@code Item.createEntity} 换成本实体，所以必须先在这里注册，
     * 否则客户端收到 spawn 包时找不到对应的类，物品会直接不显示。</p>
     *
     * <p>{@code updateFrequency} 给 2 而不是默认的 3：下落段最快 1.5 格/tick，
     * 同步间隔再大就能看出位置一跳一跳。</p>
     */
    private static void registerEntities() {
        cpw.mods.fml.common.registry.EntityRegistry.registerModEntity(
                com.makentmgreatagain.entity.EntityItemHelicopter.class,
                "mnga_helicopter_item", 0, MakeNTMGreatAgain.instance, 64, 2, true);
    }

    /** 主初始化：注册本模组自己的合成配方（原版工作台配方，不依赖 HBM）。 */
    public void init(FMLInitializationEvent event) {
        ModRecipes.init();
        // DRX 香烟特效后的延迟击杀（ServerTickEvent 走 FML 总线）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new CigaretteKillHandler());
        // 高级合金刀片磨满自动变损坏刀片（ServerTickEvent 走 FML 总线）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new ShredderBladeHandler());
        // Hover jetpack: drive flight logic when worn standalone in the chest slot
        // PlayerTickEvent 是 FML 事件总线，注册到 Forge 总线会永远收不到（导致单穿不工作）
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.hook.HoverJetpackStandaloneHandler());

        // 悬浮背包模式切换包：1.7.10 的包处理跑在 netty 线程，NBT 写入经此泵搬回服务端主线程
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.packet.HoverJetpackModePacket.ServerTaskPump());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.packet.ExtractorSwitchPacket.ServerTaskPump());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.packet.NukeRunPacket.ServerTaskPump());

        // 全套移动速度（加速/减速）：必须走 PlayerTick，才能做到脱甲/死亡后自动摘掉修饰符
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new com.makentmgreatagain.hook.ArmorSpeedHandler());

        // 耐摔合金全套：免伤（摔落除外）、摔死 manout 音效、空手攻击震撼走 Forge 总线；
        // 防火是 PlayerTickEvent，属 FML 总线 —— 同一个实例要挂两处，缺一条对应规则就永远收不到
        com.makentmgreatagain.hook.HelicopterArmorHandler heliArmorHandler = new com.makentmgreatagain.hook.HelicopterArmorHandler();
        MinecraftForge.EVENT_BUS.register(heliArmorHandler);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(heliArmorHandler);

        // 耐摔合金的字节码守卫（拦直接改血量 / onDeath / setDead）到这里才启用：
        // 护甲材质与处理器都已就绪，早于此时 setHealth 就可能被调用，会撞上类加载时序
        com.makentmgreatagain.hook.HelicopterGuardHook.armed = true;

        registerAchievements();
    }

    /**
     * 成就触发器的双总线注册。
     *
     * 同一个实例要挂两处：PlayerInteractEvent / BlockEvent 在 Forge 总线，
     * PlayerTickEvent 在 FML 总线。只注册其一会有触发点永远收不到。
     */
    private static void registerAchievements() {
        com.makentmgreatagain.achievement.AchHandler handler = new com.makentmgreatagain.achievement.AchHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(handler);
    }

    /**
     * 最后初始化：其他模组此时已全部加载完毕。
     * 「接入 HBM 机器配方」必须放在这里，放早了会拿到 HBM 还没注册好的 null 对象。
     */
    public void postInit(FMLPostInitializationEvent event) {
        // ===== Threx 的辐射隔绝：切到 PRISM 3D 系统 =====
        // 默认的 ChunkRadiationHandlerSimple 按区块存一个标量，没有方向也没有空间结构，
        // 物理上不存在「挡住辐射」这回事，只能靠方块去扣减区块的值（吸收）。
        // PRISM 把世界切成 16^3 子区块，按方块的爆炸抗性给三个轴各算一组阻力，扩散时
        // 按 e^(-阻力/10000) 衰减 —— 这是 HBM 里唯一能让「围起来就隔住」成立的系统。
        //
        // 必须放 postInit：HBM 的 RadiationConfig.loadFromConfig 已在 config 阶段跑过，
        // 在这里覆盖才不会被它改回 Simple；而且此时世界还没加载，换掉不丢运行中的数据。
        // 副作用：PRISM 没有实现 handleWorldDestruction，切过去后「辐射让草变废土、
        // 树叶枯萎」那套世界侵蚀会停；旧存档里 Simple 的辐射值也不会被读取。
        if(!(com.hbm.handler.radiation.ChunkRadiationManager.proxy
                instanceof com.hbm.handler.radiation.ChunkRadiationHandlerPRISM)) {
            com.hbm.handler.radiation.ChunkRadiationManager.proxy =
                    new com.hbm.handler.radiation.ChunkRadiationHandlerPRISM();
            MakeNTMGreatAgain.LOG.info("[Threx] 已切换到 PRISM 3D 辐射系统：方块按爆炸抗性提供辐射阻力");
        }

        // PRISM 把每格阻力截断在 100，黑曜石/加固 CMB/Threx 会算成同一个数。
        // 这里把 Threx 的实例交给 ASM 补丁（core/PrismThrexResistanceTransformer），
        // 让它单独拿到更高的阻力值，其余方块保持 HBM 原样。
        com.makentmgreatagain.hook.ThrexRadResistance.threxBlock =
                com.makentmgreatagain.init.ModBlocks.block_threx;
        com.makentmgreatagain.hook.ThrexRadResistance.threxShieldBrickBlock =
                com.makentmgreatagain.init.ModBlocks.threx_shield_brick;
        com.makentmgreatagain.hook.ThrexRadResistance.threxShieldGlassBlock =
                com.makentmgreatagain.init.ModBlocks.threx_shield_glass;

        // 高级合金五件套耐久 2000 → 4500。HBM 那几件是 @Deprecated 遗留物品，
        // 本体材质改不动（maxUses 在构造时就写进 Item.maxDamage），只能在这里覆写。
        com.makentmgreatagain.init.MNGAMaterials.raiseAlloyToolDurability();
        // HBM 的 DamageResistanceHandler.init() 在它自己的 init 阶段会 clearSystem() 再建表，
        // 所以 DT/DR 只能在这里注册（晚于 HBM，早于玩家进图，且每局都会重新加一遍）
        com.makentmgreatagain.init.MNGAArmorStats.register();
        // 复合材料储罐：把 HBM 的「易燃易爆 / 遇水反应 / 自燃」换成带护腿检查的包装类型。
        // 必须在 HBM 把所有 hazard 注册完之后做，postInit 是能拿到完整注册表的最早时机。
        com.makentmgreatagain.hazard.CompositeTankHazardGuard.install();
        NTMCompat.init();
        // ===== 流体冷却机配对表 =====
        // 扫描 Fluids.metaOrder，把 HBM 全部「可加热 / 可冷却」配对收录进来，再补上
        // HBM 没登记的（四氟甲烷 → 冷四氟甲烷）。必须晚于所有模组的流体注册，
        // 否则 metaOrder 里没有它们的自定义流体，槽位识别码会把那些流体判成无效。
        com.makentmgreatagain.recipes.FluidCoolerRecipes.init();
        // ===== 电锅炉配方表 =====
        // 扫描 HBM + 附属全部「可加热流体」（FT_Heatable 首步），电锅炉从原版
        // 只有 石油/裂化石油 两条扩展为全部可加热流体。时机同流体冷却机配对表。
        com.makentmgreatagain.recipes.ElectricBoilerRecipes.init();
        MakeNTMGreatAgain.LOG.info("ElectricBoilerRecipes: "
                + com.makentmgreatagain.recipes.ElectricBoilerRecipes.count() + " boilable fluids registered.");
        // ===== [坩埚/铸造] ② 挂载点：熔融材质（postInit，晚于 HBM 全部配方注册）=====
        // 三材质的熔融体注册进 HBM 的 Mats。必须晚于 HBM 全部配方注册：
        // HBM 有多处遍历 Mats.orderedList 的自动注册循环，会按 mat.id 去索引
        // HBM 自己的物品 metadata，而本模组的材质在那儿没有对应物品
        com.makentmgreatagain.init.MNGAMetals.registerMaterials();
        // 兜底注入：HBM 的 onRecipeLoad 只在"无配置文件→registerDefaults"分支触发；
        // 玩家改过 config/hbmRecipes 后监听器不跑，这里在配方表建好后再幂等注入一遍。
        // 必须在 NTMCompat.init() 之后（它往高炉/化工厂注册的配方名也要参与判重）。
        // ==== 配方账本（recipe_ledger.json）====
        // 前后夹住 injectAllFallback：begin 负责「把上次自己写的旧版本摘掉、把玩家改过的表原样留下」，
        // end 负责「回滚没重建成功的、执行禁用名单、把本次结果写成新基线」。
        // 没有它的话，玩家启用过 config/hbmRecipes 模板后，附属配方会被 json 快照冻在旧版本上，
        // 升级再多次也不会变。详见 com.makentmgreatagain.recipes.RecipeLedger 类注释。
        com.makentmgreatagain.recipes.RecipeLedger.get().begin();
        ModRecipes.injectAllFallback();
        com.makentmgreatagain.recipes.RecipeLedger.get().end();
        // 所有配方（本体化工厂 + 热解炉附属配方 + 独占配方）都就绪后，重建大型机的配方表。
        // NEI 插件可能更早拿过这张表，但拿到的是同一个实例，重建后内容会一起刷新。
        com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes.invalidate();
        com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes.get();
		AnvilAssemblyTemplates.register();
        // ===== [坩埚/铸造] ③ 挂载点：铸造表刷新（必须是 postInit 的最后一件事）=====
        // 铸造表是懒加载 + 一次性缓存，若在本模组注册材质之前被别处触发过，
        // 三个新材质就永远进不了铸造页。这里清缓存重建，并打印每个材质的可铸形态。
        // 日志判读：搜 "MNGAMetals: castable"，三材质的分子应为 13（氦为 2）。分母是 HBM 模具总数，
        // 会随版本变，别记死；具体清单见 MNGAMetals 类注释。分子为 0 说明矿辞或材质名没对上。
        // 改坩埚相关的东西前先看 init/MNGAMetals.java 类头的「【坩埚/铸造】速查」。
        com.makentmgreatagain.init.MNGAMetals.refreshCastingTable();
    }

    /** 服务端启动时执行，注册命令用。 */
    public void serverStarting(FMLServerStartingEvent event) {
    }

    /**
     * 客户端 GUI 工厂，主类的 getClientGuiElement 一律转发到这里。
     *
     * <p>基类返回 null，因为专用服务端根本不会走这条链（Forge 只在客户端请求界面时
     * 调 getClientGuiElement）。方法留着只是为了主类能编译通过，实现见 {@link ClientProxy}。</p>
     *
     * <p>规矩：任何 {@code com.makentmgreatagain.gui.*} / {@code client.*} 的 new 都只能写在
     * ClientProxy 里。主类一旦直接 new GUI，专用服务端做类校验时就会去加载 GuiContainer，
     * 直接 NoClassDefFoundError 开服崩溃。</p>
     *
     * <p>本方法**不能**标 {@code @SideOnly(Side.CLIENT)}：标了以后服务端会把这个方法从类里
     * 物理删除，而主类是无条件转发的，调用时会变成 NoSuchMethodError。返回 null 才是安全的。</p>
     */
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}

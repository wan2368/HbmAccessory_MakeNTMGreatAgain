package com.makentmgreatagain.world;

import java.lang.reflect.Method;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * Duna（HBM 航天版的火星）宿主岩查找与维度识别。
 *
 * <p>本附属包内嵌的 com.hbm 源码子集里没有 dim 包和 duna 方块字段，
 * 编译期引不到 {@code ModBlocks.duna_rock}，只能运行时查找。</p>
 *
 * <h3>注册名的坑（上一版查不到的原因）</h3>
 * <p>HBM 注册 duna_rock 时传的注册名是 {@code getUnlocalizedName()}，
 * 而它返回的是<b>带 "tile." 前缀</b>的 {@code tile.duna_rock}。
 * 所以按 "duna_rock" 查必然为 null —— 先查 "tile.duna_rock"，再兜底原写法。</p>
 *
 * <h3>最后兜底：反射 getStone()</h3>
 * <p>HBM 每个天体维度的 provider 都有 {@code getStone()}（Duna 返回 duna_rock），
 * 自家 {@code WorldGeneratorDuna} 也是这么拿宿主岩的。名字查不到就调用它，
 * 对 HBM 版本改名/改注册方式免疫。</p>
 *
 * <p>宿主岩查不到返回 null，调用方据此跳过 Duna 生成，
 * 绝不退化成往别的模组的岩层里塞矿。</p>
 */
public final class DunaHost {

    private static Block cached;
    private static boolean tried;

    private DunaHost() {
    }

    /**
     * 这个维度是不是 Duna。
     *
     * <p>两条路：配置 ID 命中（快路径），或 provider 类名是 WorldProviderDuna。
     * 后者不依赖 {@code dunaDimensionId} 与航天版配置同步 —— 整合包改过维度号
     * 也认，两边都没命中就当不是 Duna，不生成。</p>
     */
    public static boolean isDuna(World world, int configuredDim) {
        if(world.provider.dimensionId == configuredDim) return true;
        return world.provider.getClass().getName().endsWith("WorldProviderDuna");
    }

    /** Duna 的"石头"：hbm 的 duna_rock。查不到返回 null。仅在 Duna 维度上调用。 */
    public static Block rock(World world) {
        if(!tried) {
            tried = true;
            cached = GameRegistry.findBlock("hbm", "tile.duna_rock");
            if(cached == null) cached = GameRegistry.findBlock("hbm", "duna_rock");
            if(cached == null) cached = viaGetStone(world);
            System.out.println("[MNGA worldgen] Duna host rock = " + cached);
        }
        return cached;
    }

    /** 兜底：调 provider 自己的 getStone()。HBM 的类没混淆，方法名稳定。 */
    private static Block viaGetStone(World world) {
        try {
            Method m = world.provider.getClass().getMethod("getStone");
            Object result = m.invoke(world.provider);
            if(result instanceof Block) return (Block)result;
        } catch(Exception ignored) {
            // 不是 HBM 的 provider / 方法签名变了，返回 null 走跳过逻辑
        }
        return null;
    }
}

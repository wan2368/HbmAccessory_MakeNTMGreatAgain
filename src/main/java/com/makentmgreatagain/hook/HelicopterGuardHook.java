package com.makentmgreatagain.hook;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

/**
 * 耐摔合金全套的「绕过伤害管线」闸门，给 coremod 补丁
 * （{@code HelicopterGuardTransformer}）在字节码层调用。
 *
 * <h2>为什么需要它</h2>
 * Forge 的 {@code LivingAttackEvent} / {@code LivingHurtEvent} 只覆盖
 * {@code attackEntityFrom → damageEntity} 这条正规管线。别的模组要秒杀玩家时
 * 常常直接 {@code setHealth(0)} 再 {@code onDeath(...)}（HBM 自己的 digamma
 * 处决就是这么写的，见 {@code HbmLivingProps}），整条事件链根本不触发。
 * 所以免伤事件拦不住这类死亡，必须在方法入口下闸。
 *
 * <h2>参数一律用 Object</h2>
 * 与 {@link LongProcessHook} 同一套约定：coremod 由系统类加载器加载，补丁里
 * 不能出现 MC 的混淆类型名（dev 与生产环境不同），用 Object 传引用可以绕过
 * 描述符差异，字节码里也不需要 CHECKCAST。
 *
 * <h2>失败一律放行</h2>
 * 这几个方法被插在 {@code setHealth} 这种每 tick 每实体都跑的热路径上，
 * 任何异常都必须吞掉并返回 true（不干预原版行为），绝不能让守卫本身把游戏搞崩。
 *
 * <h2>拦不到的情况</h2>
 * 别的模组用反射 / Unsafe 直接写 {@code health} 字段时不经过任何方法，
 * 字节码闸门无从下手 —— 那种情况由 {@code HelicopterArmorHandler} 的
 * 每 tick 血量兜底拉回。
 */
public final class HelicopterGuardHook {

    /**
     * 守卫总开关，由 {@code CommonProxy.postInit} 置 true。
     *
     * <p>置 true 之前所有闸门直接放行：{@code setHealth} 在模组初始化完成前
     * 就可能被调用，那时护甲材质还没建好，提前引用模组类会有类加载时序风险。</p>
     */
    public static volatile boolean armed = false;

    /**
     * 摔落放行标记。
     *
     * <p>{@code setHealth} 不带 DamageSource，无法自己判断这次掉血是不是摔的。
     * 而 {@code LivingAttackEvent} 一定在 {@code damageEntity → setHealth} 之前
     * 同线程同步触发，所以处理器在放行摔落时打上这个标记，闸门据此放行。
     * 这样「只受摔落伤害」在字节码层也成立。</p>
     *
     * <p>用玩家所在世界的 {@code getTotalWorldTime()} 做时间基准：它在两端语义
     * 一致、每 tick 递增。不能用 {@code MinecraftServer.getTickCounter()}——
     * 那在专用服务器上返回的是别的计数，标记会永不过期，摔落之后免伤一直开着。</p>
     */
    private static final ThreadLocal<EntityPlayer> FALL_PASS = new ThreadLocal<EntityPlayer>();
    private static final ThreadLocal<Long> FALL_TIME = new ThreadLocal<Long>();

    private HelicopterGuardHook() {
    }

    /**
     * 处理器放行摔落伤害时调用：允许紧接着的那一次掉血。
     *
     * <p>标记保留 1 tick 而不是"一次性消费"：摔落的 {@code setHealth} 与
     * 每 tick 血量兜底在同一 tick 内先后发生，标记必须活到本 tick 结束，
     * 否则兜底会把刚摔掉的血又拉满，摔落伤害等于无效。</p>
     */
    public static void markFallPass(EntityPlayer player) {
        FALL_PASS.set(player);
        FALL_TIME.set(worldTime(player));
    }

    /** 这个玩家是否刚被放行过摔落（兜底据此不拉回血量）。 */
    public static boolean hasFallPass(EntityPlayer player) {
        return isFresh(player);
    }

    /**
     * 标记是否仍有效；过期就地清掉，避免悬空标记让免伤长期失效。
     *
     * <p>窗口取 {@link HelicopterGuardRules#FALL_PASS_TICKS} 而不是 1 tick：摔死要依次走
     * {@code setHealth(0) → onDeath → setDead}，中间还隔着死亡动画与事件派发。
     * 只活 1 tick 会让后半段过期、被守卫拦成「0 血活死人」，摔不死。</p>
     */
    private static boolean isFresh(EntityPlayer player) {
        if (FALL_PASS.get() != player) return false;
        Long marked = FALL_TIME.get();
        if (marked == null || !HelicopterGuardRules.isFallPassFresh(marked, worldTime(player))) {
            FALL_PASS.remove();
            FALL_TIME.remove();
            return false;
        }
        return true;
    }

    /** 闸门用：摔落放行则允许这次掉血（不立即清标记，同 tick 兜底还要读）。 */
    private static boolean consumeFallPass(EntityPlayer player) {
        return isFresh(player);
    }

    /** 玩家所在世界的总时间；取不到就返回 0，标记会立刻过期（保守放行免伤）。 */
    private static long worldTime(EntityPlayer player) {
        try {
            return player.worldObj == null ? 0L : player.worldObj.getTotalWorldTime();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /**
     * 这次 {@code setHealth} 能不能执行。
     *
     * <p>只拦「掉血」，治疗一律放行 —— 否则吃药回血、复活补满血、
     * 本处理器自己的兜底治疗都会被一起挡掉。摔落掉血靠一次性标记放行。</p>
     *
     * @param entityObj 实际是 EntityLivingBase
     * @param newHealth 调用方想写进去的血量
     * @return true 放行，false 让字节码直接 return（这次改血量整个不发生）
     */
    public static boolean allowSetHealth(Object entityObj, float newHealth) {
        if (!armed) return true;
        try {
            if (!(entityObj instanceof EntityPlayer)) return true;
            EntityPlayer player = (EntityPlayer) entityObj;
            if (!HelicopterArmorHandler.wearsHelicopterSet(player)) return true;

            // 死亡流程进行中一律放行：摔死已经合法发生，后续的清零 / 重生补满
            // 都是收尾动作，拦下只会让玩家卡在死亡态。
            if (player.deathTime > 0) return true;

            // 治疗 / 持平放行，只挡掉血
            if (newHealth >= player.getHealth()) return true;
            // 摔落照常掉血
            if (consumeFallPass(player)) return true;

            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 这次 {@code onDeath} 能不能执行。
     *
     * <p>摔死放行（套装本来就是「只摔得死」），其余一律拦下：包括 /kill 的
     * outOfWorld、HBM 的 digamma / radiation 处决、以及任何模组直接调 onDeath。</p>
     *
     * @param entityObj 实际是 EntityLivingBase
     * @param sourceObj 实际是 DamageSource，可能为 null
     */
    public static boolean allowDeath(Object entityObj, Object sourceObj) {
        if (!armed) return true;
        try {
            if (!(entityObj instanceof EntityPlayer)) return true;
            EntityPlayer player = (EntityPlayer) entityObj;
            if (!HelicopterArmorHandler.wearsHelicopterSet(player)) return true;
            if (isFall(sourceObj)) return true;
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 这次 {@code setDead} 能不能执行。
     *
     * <p><b>只拦死亡驱动的那一种</b>：血量已经见底时的 setDead 才是「被 kill」。
     * 退出游戏、切换维度也会调 setDead，那时血量正常，必须放行，
     * 否则会留下幽灵实体甚至卡住登出。</p>
     *
     * <p><b>死亡动画已经在跑就一律放行</b>：原版 {@code EntityLivingBase.onEntityUpdate}
     * 要等 {@code deathTime} 数到 20 才调 setDead 移除实体。那时摔落放行窗口
     * （{@link HelicopterGuardRules#FALL_PASS_TICKS} tick）早已过期、血量还是 0，
     * 若继续按「血量见底」拦下，实体就永远删不掉 —— 玩家卡成倾斜的红色尸体，
     * 重生界面也弹不出来。所以只要 deathTime &gt; 0，说明死亡流程已经合法启动，
     * 后面的收尾必须让它走完。</p>
     */
    public static boolean allowSetDead(Object entityObj) {
        if (!armed) return true;
        try {
            if (!(entityObj instanceof EntityPlayer)) return true;
            EntityPlayer player = (EntityPlayer) entityObj;
            if (player.deathTime > 0) return true;   // 死亡流程收尾，放行
            return HelicopterGuardRules.allowSetDead(
                    player.getHealth(),
                    isFresh(player),
                    HelicopterArmorHandler.wearsHelicopterSet(player));
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 是不是摔落伤害：先比实例，再比 damageType（别的模组可能自造同类型 source）。 */
    private static boolean isFall(Object sourceObj) {
        if (sourceObj == DamageSource.fall) return true;
        if (sourceObj instanceof DamageSource) {
            return "fall".equals(((DamageSource) sourceObj).damageType);
        }
        return false;
    }

    // ---------------- 防踢出世界 ----------------

    /**
     * 这次「把玩家踢出服务器」能不能执行。
     *
     * <p>别的模组常用 {@code NetHandlerPlayServer.kickPlayerFromServer(...)} 当处决手段
     * （比 death 更狠：直接断连接）。穿全套时一律拦下，玩家不会被踢出世界。</p>
     *
     * <p>只拦穿着本套装的玩家，其他玩家的原版踢出（超时、封禁、白名单）完全不受影响。</p>
     *
     * @param playerObj 实际是 EntityPlayerMP，可能为 null
     */
    public static boolean allowKick(Object playerObj) {
        if (!armed) return true;
        try {
            if (!(playerObj instanceof EntityPlayer)) return true;
            return !HelicopterArmorHandler.wearsHelicopterSet((EntityPlayer) playerObj);
        } catch (Throwable ignored) {
            return true;
        }
    }

    // ---------------- 防扒护甲 ----------------

    /**
     * 「本次护甲槽写入由玩家自己发起」的线程标记。
     *
     * <p>为什么需要它：{@code setCurrentItemOrArmor} 不带调用者信息，无法自己区分
     * 「玩家脱甲」和「别的生物扒甲」。玩家的操作一定走服务端交互包
     * （{@code NetHandlerPlayServer.processClickWindow} 等）或 GUI 点击，
     * 处理器在那些入口置位，闸门据此放行；其余调用方（模组 AI、直接改槽）一律拒绝。</p>
     *
     * <p>用 ThreadLocal 而不是全局布尔：服务端可能并发处理多个玩家的包。</p>
     */
    private static final ThreadLocal<Boolean> SELF_ARMOR_EDIT = new ThreadLocal<Boolean>();

    /** 进入「玩家自己操作护甲」的临界区（交互包 / GUI 点击入口调用）。 */
    public static void beginSelfArmorEdit() {
        SELF_ARMOR_EDIT.set(Boolean.TRUE);
    }

    /** 离开临界区。必须与 {@link #beginSelfArmorEdit} 成对，放在 finally 里。 */
    public static void endSelfArmorEdit() {
        SELF_ARMOR_EDIT.remove();
    }

    /** 当前线程是否处在「玩家自己操作护甲」的临界区内。 */
    public static boolean isSelfArmorEdit() {
        return SELF_ARMOR_EDIT.get() == Boolean.TRUE;
    }

    /**
     * 这次护甲槽写入能不能执行。
     *
     * <p>拦的是「其他生物 / 模组直接扒甲」：穿着本套装时，非玩家自身发起的
     * 护甲槽写入一律拒绝，护甲不会被别人脱掉或替换。玩家自己脱甲照常。</p>
     *
     * @param entityObj 实际是 EntityPlayer
     * @param slot      setCurrentItemOrArmor 的槽号（0 手持，1~4 护甲）
     */
    public static boolean allowArmorSlotWrite(Object entityObj, int slot) {
        if (!armed) return true;
        try {
            if (!(entityObj instanceof EntityPlayer)) return true;
            EntityPlayer player = (EntityPlayer) entityObj;
            return HelicopterGuardRules.allowArmorSlotWrite(
                    slot,
                    isSelfArmorEdit(),
                    HelicopterArmorHandler.wearsAnyHelicopterPiece(player));
        } catch (Throwable ignored) {
            return true;
        }
    }
}
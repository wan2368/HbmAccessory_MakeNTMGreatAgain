package com.makentmgreatagain.hook;

/**
 * 耐摔合金守卫「纯判定规则」的独立单元测试（不依赖 Minecraft / Forge）。
 *
 * <p>用 {@code javac + java} 直接跑，main 里逐条断言，失败抛 AssertionError。
 * TDD 的 RED 阶段：先写期望行为，再实现 {@link HelicopterGuardRules} 让它变绿。</p>
 *
 * <p>覆盖四件事：</p>
 * <ol>
 *   <li>摔落放行窗口：跨 tick 仍有效（摔死流程要跨 tick），超时失效；</li>
 *   <li>setDead 判定：摔落放行、非摔落拦下、血量正常放行（登出/切维度）；</li>
 *   <li>isDead 撤销判定：非摔落且血量正常才撤销，摔死不撤销；</li>
 *   <li>扒甲来源判定：玩家自己放行，其他生物/模组拒绝。</li>
 * </ol>
 */
public final class HelicopterGuardRulesTest {

    private static int passed = 0;

    public static void main(String[] args) {
        testFallWindowSpansTicks();
        testFallWindowExpires();
        testFallWindowNotMarked();
        testSetDeadAllowsFall();
        testSetDeadBlocksNonFall();
        testSetDeadAllowsHealthy();
        testRevokeDeadFlag();
        testRevokeNeverDuringDeathAnimation();
        testHealthTopUpSkipsDeathAnimation();
        testArmorStripSelfAllowed();
        testArmorStripOthersBlocked();
        testArmorSlotRange();
        testNoArmorRestoreAtDeathInstant();
        testCrushTargetsOnlyNonPlayers();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    // ---- 摔落放行窗口 ----

    private static void testFallWindowSpansTicks() {
        // 摔死流程跨 tick：标记后 3 tick 内仍应放行
        check(HelicopterGuardRules.isFallPassFresh(100L, 100L), "同 tick 应放行");
        check(HelicopterGuardRules.isFallPassFresh(100L, 101L), "下一 tick 应放行");
        check(HelicopterGuardRules.isFallPassFresh(100L, 103L), "3 tick 内应放行");
        passed++;
    }

    private static void testFallWindowExpires() {
        check(!HelicopterGuardRules.isFallPassFresh(100L, 120L), "超时窗口应失效");
        passed++;
    }

    private static void testFallWindowNotMarked() {
        check(!HelicopterGuardRules.isFallPassFresh(-1L, 100L), "未打标记不应放行");
        passed++;
    }

    // ---- setDead 判定 ----

    private static void testSetDeadAllowsFall() {
        // 摔死：血量 0 + 摔落放行窗口内 → 必须放行，否则摔不死
        check(HelicopterGuardRules.allowSetDead(0.0F, true, true), "摔死应放行 setDead");
        passed++;
    }

    private static void testSetDeadBlocksNonFall() {
        // 非摔落 + 血量见底 + 穿全套 → 拦下
        check(!HelicopterGuardRules.allowSetDead(0.0F, false, true), "非摔落强制标死应拦下");
        passed++;
    }

    private static void testSetDeadAllowsHealthy() {
        // 血量正常（登出/切维度）→ 一律放行，哪怕没穿全套
        check(HelicopterGuardRules.allowSetDead(20.0F, false, true), "血量正常应放行");
        check(HelicopterGuardRules.allowSetDead(0.0F, false, false), "没穿全套应放行");
        passed++;
    }

    // ---- isDead 撤销判定 ----

    private static void testRevokeDeadFlag() {
        // 非摔落、血量正常、穿全套、死亡动画没在跑 → 撤销强制标死
        check(HelicopterGuardRules.shouldRevokeDeadFlag(20.0F, false, true, 0), "强制标死应撤销");
        // 摔死不撤销（套装设定就是只摔得死）
        check(!HelicopterGuardRules.shouldRevokeDeadFlag(0.0F, true, true, 0), "摔死不应撤销");
        // 没穿全套不干预
        check(!HelicopterGuardRules.shouldRevokeDeadFlag(20.0F, false, false, 0), "没穿全套不应干预");
        // 血量为 0 且非摔落：属于濒死，交给血量兜底，不在此撤销
        check(!HelicopterGuardRules.shouldRevokeDeadFlag(0.0F, false, true, 0), "零血非摔落不在此撤销");
        passed++;
    }

    /**
     * 回归：死亡动画正在跑时绝不能撤销 isDead / 清 deathTime。
     *
     * <p>这是「摔死后倾斜红色假死」的根因：原版 deathTime 每 tick 自增、到 20 才真正
     * 移除实体并弹重生界面。撤销逻辑把它清零，死亡流程就永远走不到第 20 tick，
     * 玩家卡成倾斜的红色尸体。开启死亡不掉落时重生会重建玩家、血量恢复成 20，
     * 于是「血量正常」这条判据成立，正好踩中这个坑。</p>
     */
    private static void testRevokeNeverDuringDeathAnimation() {
        for (int deathTime = 1; deathTime <= 25; deathTime++) {
            check(!HelicopterGuardRules.shouldRevokeDeadFlag(20.0F, false, true, deathTime),
                    "死亡动画中不应撤销, deathTime=" + deathTime);
        }
        // 摔落窗口过期后（死亡动画仍在跑）同样不能撤销
        check(!HelicopterGuardRules.shouldRevokeDeadFlag(20.0F, false, true, 5),
                "摔落窗口过期但死亡动画在跑，仍不应撤销");
        passed++;
    }

    /** 血量兜底也不能在死亡动画期间把血量拉满，否则死亡判定被反复打断。 */
    private static void testHealthTopUpSkipsDeathAnimation() {
        check(!HelicopterGuardRules.shouldTopUpHealth(5.0F, 20.0F, false, true, 3),
                "死亡动画中不应拉回血量");
        check(!HelicopterGuardRules.shouldTopUpHealth(5.0F, 20.0F, true, true, 0),
                "摔落窗口内不应拉回血量");
        check(HelicopterGuardRules.shouldTopUpHealth(5.0F, 20.0F, false, true, 0),
                "平时掉血应拉回");
        check(!HelicopterGuardRules.shouldTopUpHealth(20.0F, 20.0F, false, true, 0),
                "满血无需拉回");
        check(!HelicopterGuardRules.shouldTopUpHealth(5.0F, 20.0F, false, false, 0),
                "没穿全套不干预");
        passed++;
    }

    // ---- 扒甲来源判定 ----

    private static void testArmorStripSelfAllowed() {
        // 玩家自己脱甲（槽 1~4，来源是自己）→ 放行
        check(HelicopterGuardRules.allowArmorSlotWrite(2, true, true), "自己脱甲应放行");
        passed++;
    }

    private static void testArmorStripOthersBlocked() {
        // 其他生物/模组扒甲（来源不是自己）→ 拒绝
        check(!HelicopterGuardRules.allowArmorSlotWrite(2, false, true), "他人扒甲应拒绝");
        // 没穿这套不干预
        check(HelicopterGuardRules.allowArmorSlotWrite(2, false, false), "没穿这套应放行");
        passed++;
    }

    private static void testArmorSlotRange() {
        // 槽 0 是手持栏，不属于护甲，一律放行
        check(HelicopterGuardRules.allowArmorSlotWrite(0, false, true), "手持栏应放行");
        // 槽 5+ 越界，不干预
        check(HelicopterGuardRules.allowArmorSlotWrite(5, false, true), "越界槽应放行");
        // 护甲槽边界 1 和 4 都要管
        check(!HelicopterGuardRules.allowArmorSlotWrite(1, false, true), "槽1应拦");
        check(!HelicopterGuardRules.allowArmorSlotWrite(4, false, true), "槽4应拦");
        passed++;
    }

    /**
     * 回归：摔死瞬间不得补护甲，否则地上掉一份、槽里补一份 = 复制一套。
     *
     * <p>原版 {@code EntityLivingBase.onDeath} 只置 {@code dead = true}，
     * {@code deathTime} 要到下一 tick 才自增、{@code isDead} 要等 20 tick 后
     * {@code setDead()} 才置位。所以「死亡动画在跑」这条判据在死亡那一 tick
     * 完全不成立，必须额外看 {@code dead} 标记与血量。</p>
     */
    private static void testNoArmorRestoreAtDeathInstant() {
        // 死亡瞬间：dead=true、deathTime=0、isDead=false、血量 0
        check(HelicopterGuardRules.isDeathInProgress(0.0F, true, false, 0),
                "dead 标记应判为死亡中");
        check(HelicopterGuardRules.isDeathInProgress(0.0F, false, false, 0),
                "零血应判为死亡中");
        check(HelicopterGuardRules.isDeathInProgress(20.0F, false, true, 0),
                "isDead 应判为死亡中");
        check(HelicopterGuardRules.isDeathInProgress(20.0F, false, false, 3),
                "死亡动画应判为死亡中");
        // 正常活着：四条都不成立
        check(!HelicopterGuardRules.isDeathInProgress(20.0F, false, false, 0),
                "活着不应判为死亡中");
        // 摔伤掉血但没死：不能误判成死亡，否则兜底整体停摆
        check(!HelicopterGuardRules.isDeathInProgress(5.0F, false, false, 0),
                "摔伤未死不应判为死亡中");
        passed++;
    }

    /** 落地杀伤：玩家一律放过，非玩家生物一律处决。 */
    private static void testCrushTargetsOnlyNonPlayers() {
        check(!HelicopterGuardRules.shouldCrush(true), "玩家不应被砸");
        check(HelicopterGuardRules.shouldCrush(false), "非玩家生物应被砸");
        passed++;
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private HelicopterGuardRulesTest() {
    }
}
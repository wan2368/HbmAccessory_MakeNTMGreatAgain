package com.makentmgreatagain.entity;

/**
 * 耐摔合金掉落物「两端轨迹一致」的独立单元测试（不依赖 Minecraft / Forge）。
 *
 * <p>TDD 的 RED 阶段：先写期望行为，再实现 {@link HelicopterDropSync} 让它变绿。</p>
 *
 * <h2>要锁住的 bug</h2>
 * FML 客户端建实体的顺序是：{@code new EntityItemHelicopter(world)}（此时 posY=0，
 * 构造器把 spawnY 也设成 0）→ 之后才 {@code setLocationAndAngles} 设真实位置。
 * 于是客户端的 {@code spawnY} 是 0、服务端是真实落点高度，
 * 轨迹公式 {@code remaining = (spawnY + 24) - posY} 两边算出的目标高度不同，
 * 客户端先按错误目标走一遍、再被服务端位置包拽回来 —— 表现为
 * 「飞上去后卡顿着从下往上重走一遍，然后才坠落」。
 *
 * <p>所以 spawnY / liftEnabled / ticksExisted 必须随生成包同步，
 * 且「读到的起点」要能覆盖构造器里那个 0。</p>
 */
public final class HelicopterDropSyncTest {

    private static int passed = 0;

    public static void main(String[] args) {
        testSpawnYRoundTrip();
        testSpawnYOverridesConstructorZero();
        testLiftEnabledRoundTrip();
        testAgeRoundTrip();
        testTargetHeightSameOnBothSides();
        testNegativeSpawnY();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    /** 起点高度往返：写进去多少，读出来就是多少（含小数）。 */
    private static void testSpawnYRoundTrip() {
        double[] values = { 0.0D, 64.0D, 125.5D, -3.25D, 1234.75D };
        for (double y : values) {
            byte[] buf = HelicopterDropSync.write(y, true, 0);
            HelicopterDropSync.State s = HelicopterDropSync.read(buf);
            check(Math.abs(s.spawnY - y) < 1e-9D, "spawnY 往返不一致: " + y + " -> " + s.spawnY);
        }
        passed++;
    }

    /**
     * 核心回归：构造器给的 0 必须能被同步值覆盖。
     *
     * <p>模拟客户端流程：先按 posY=0 建实体（spawnY=0），再读生成包拿到真实起点。</p>
     */
    private static void testSpawnYOverridesConstructorZero() {
        double constructorSpawnY = 0.0D;          // 客户端 new EntityItemHelicopter(world) 的结果
        double serverSpawnY = 70.5D;              // 服务端真实落点

        byte[] buf = HelicopterDropSync.write(serverSpawnY, true, 12);
        HelicopterDropSync.State s = HelicopterDropSync.read(buf);
        double resolved = HelicopterDropSync.resolveSpawnY(constructorSpawnY, s);

        check(Math.abs(resolved - serverSpawnY) < 1e-9D,
                "同步起点必须覆盖构造器的 0，实际: " + resolved);
        passed++;
    }

    /** 演出开关往返：护甲的 false 必须能传到客户端，否则客户端仍按飞行渲染。 */
    private static void testLiftEnabledRoundTrip() {
        check(HelicopterDropSync.read(HelicopterDropSync.write(10.0D, true, 0)).liftEnabled,
                "true 应往返为 true");
        check(!HelicopterDropSync.read(HelicopterDropSync.write(10.0D, false, 0)).liftEnabled,
                "false 应往返为 false");
        passed++;
    }

    /** 年龄往返：客户端要接着服务端已经走过的 tick 数继续，不能从 0 重放。 */
    private static void testAgeRoundTrip() {
        int[] ages = { 0, 1, 55, 144, 6000 };
        for (int age : ages) {
            HelicopterDropSync.State s = HelicopterDropSync.read(HelicopterDropSync.write(5.0D, true, age));
            check(s.ticksExisted == age, "ticksExisted 往返不一致: " + age + " -> " + s.ticksExisted);
        }
        passed++;
    }

    /**
     * 两端目标高度必须相同 —— 这是「不再重走一遍」的充要条件。
     *
     * <p>同步前：客户端 spawnY=0 → 目标 24；服务端 spawnY=70.5 → 目标 94.5。
     * 同步后：两边都是 94.5。</p>
     */
    private static void testTargetHeightSameOnBothSides() {
        double serverSpawnY = 70.5D;
        double riseHeight = 24.0D;

        double serverTarget = serverSpawnY + riseHeight;

        // 同步前（bug）：客户端用构造器的 0
        double buggyClientTarget = 0.0D + riseHeight;
        check(Math.abs(serverTarget - buggyClientTarget) > 1e-9D,
                "用例前提：同步前两端目标本应不同");

        // 同步后：客户端读到真实起点
        HelicopterDropSync.State s = HelicopterDropSync.read(
                HelicopterDropSync.write(serverSpawnY, true, 30));
        double fixedClientTarget = HelicopterDropSync.resolveSpawnY(0.0D, s) + riseHeight;

        check(Math.abs(serverTarget - fixedClientTarget) < 1e-9D,
                "同步后两端目标必须一致: " + serverTarget + " vs " + fixedClientTarget);
        passed++;
    }

    /** 负高度（虚空 / 自定义地形）也要能正确往返，不能被当成「没同步」。 */
    private static void testNegativeSpawnY() {
        HelicopterDropSync.State s = HelicopterDropSync.read(HelicopterDropSync.write(-12.5D, true, 0));
        check(Math.abs(HelicopterDropSync.resolveSpawnY(0.0D, s) - (-12.5D)) < 1e-9D,
                "负起点应被正确采用");
        passed++;
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private HelicopterDropSyncTest() {
    }
}
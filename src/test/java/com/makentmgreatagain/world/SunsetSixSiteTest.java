package com.makentmgreatagain.world;

/**
 * 日落六号遗迹「选址核心」的独立单元测试（不依赖 Minecraft / Forge）。
 *
 * <p>用 {@code javac + java} 直接跑，main 里逐条断言，失败抛 AssertionError。
 * 这是 TDD 的 RED 阶段：先写期望行为，再实现 {@link SunsetSixSite} 让它变绿。</p>
 *
 * <p>覆盖三件事：</p>
 * <ol>
 *   <li>种子确定性锚点：同种子同维度永远同一点；落在 ±5000 内；16 对齐；不同种子会分散。</li>
 *   <li>岩浆浸没 + 上方覆盖：从底座起至少 40 格连续岩浆（结构高 30 + 上方 10），不暴露。</li>
 *   <li>边界：列太短、体内非岩浆、岩浆不足 40 格，一律拒绝。</li>
 * </ol>
 */
public final class SunsetSixSiteTest {

    private static int passed = 0;

    public static void main(String[] args) {
        testAnchorDeterministic();
        testAnchorInRange();
        testAnchorAligned();
        testAnchorDifferentSeedsDiffer();
        testBuriedValid();
        testBuriedRejectsExposedTop();
        testBuriedRejectsAirInBody();
        testBuriedRejectsShortColumn();
        testBuriedRejectsNonLavaBase();
        testBuriedAcceptsSolidCover();
        testBuriedRejectsLavaLessThan40();
        testBuriedRequires40Lava();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    // ---- 锚点 ----

    private static void testAnchorDeterministic() {
        int[] a = SunsetSixSite.anchor(12345L, -1);
        int[] b = SunsetSixSite.anchor(12345L, -1);
        check(a[0] == b[0] && a[1] == b[1], "同种子同维度锚点必须一致");
        int[] c = SunsetSixSite.anchor(12345L, 0);
        check(!(a[0] == c[0] && a[1] == c[1]), "不同维度锚点应不同");
        passed++;
    }

    private static void testAnchorInRange() {
        for(long seed = 0; seed < 200; seed++) {
            int[] p = SunsetSixSite.anchor(seed * 7919L, -1);
            check(p[0] >= -5000 && p[0] <= 5000, "anchor x 越界: " + p[0]);
            check(p[1] >= -5000 && p[1] <= 5000, "anchor z 越界: " + p[1]);
        }
        passed++;
    }

    private static void testAnchorAligned() {
        for(long seed = 0; seed < 200; seed++) {
            int[] p = SunsetSixSite.anchor(seed * 104729L, -1);
            check((p[0] & 15) == 0, "anchor x 必须 16 对齐: " + p[0]);
            check((p[1] & 15) == 0, "anchor z 必须 16 对齐: " + p[1]);
        }
        passed++;
    }

    private static void testAnchorDifferentSeedsDiffer() {
        int[] a = SunsetSixSite.anchor(1L, -1);
        int[] b = SunsetSixSite.anchor(999983L, -1);
        check(!(a[0] == b[0] && a[1] == b[1]), "不同种子锚点应当不同");
        passed++;
    }

    // ---- 岩浆浸没 + 上方覆盖 ----
    // 列编码：0=空气/可暴露, 1=岩浆, 2=实心覆盖（地狱岩等）

    private static void testBuriedValid() {
        // y0..59 岩浆，baseY=5 height=30 → 占据 5..34，往上到 44 共 40 格全岩浆
        int[] col = column(60, 1);
        check(SunsetSixSite.isBuriedInLava(col, 5, 30), "40 格连续岩浆应接受");
        passed++;
    }

    private static void testBuriedRejectsExposedTop() {
        // 40 格岩浆窗口内顶部之上是空气 → 暴露，拒绝
        int[] col = column(60, 1);
        for(int y = 35; y < 45; y++) col[y] = 0;
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "顶部露空应拒绝");
        passed++;
    }

    private static void testBuriedRejectsAirInBody() {
        int[] col = column(60, 1);
        col[20] = 0;
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "体内有空气应拒绝");
        passed++;
    }

    private static void testBuriedRejectsShortColumn() {
        // 列只有 40 高，baseY=5 → 40 格窗口要到 45，超出列范围
        int[] col = column(40, 1);
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "列不够 40 格窗口应拒绝");
        passed++;
    }

    private static void testBuriedRejectsNonLavaBase() {
        int[] col = column(60, 1);
        col[5] = 2; // 底部是实心而非岩浆
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "底部非岩浆应拒绝");
        passed++;
    }

    private static void testBuriedAcceptsSolidCover() {
        // 新要求：至少 40 格「全是岩浆」，顶部之上是实心（2）而非岩浆 → 拒绝
        int[] col = column(60, 1);
        for(int y = 35; y < 45; y++) col[y] = 2;
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "上方非岩浆（实心）应拒绝");
        passed++;
    }

    private static void testBuriedRejectsLavaLessThan40() {
        // 只有 38 格岩浆（baseY=5 起 5..42），第 43、44 格是空气 → 不足 40，拒绝
        int[] col = column(60, 1);
        col[43] = 0;
        col[44] = 0;
        check(!SunsetSixSite.isBuriedInLava(col, 5, 30), "岩浆不足 40 格应拒绝");
        passed++;
    }

    private static void testBuriedRequires40Lava() {
        // 恰好 40 格岩浆（5..44），第 45 格空气 → 窗口刚好满足，接受
        int[] col = column(60, 1);
        for(int y = 45; y < 60; y++) col[y] = 0;
        check(SunsetSixSite.isBuriedInLava(col, 5, 30), "恰好 40 格岩浆应接受");
        passed++;
    }

    // ---- 工具 ----

    private static int[] column(int height, int fill) {
        int[] col = new int[height];
        for(int i = 0; i < height; i++) col[i] = fill;
        return col;
    }

    private static void check(boolean cond, String msg) {
        if(!cond) throw new AssertionError(msg);
    }

    private SunsetSixSiteTest() {
    }
}
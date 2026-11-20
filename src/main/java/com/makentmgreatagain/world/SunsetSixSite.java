package com.makentmgreatagain.world;

/**
 * 日落六号遗迹（{@code mnga_structure_sunset_six}）的「选址核心」——纯逻辑，不依赖 Minecraft。
 *
 * <p>把两件事从世界生成里剥出来单独可测：</p>
 * <ol>
 *   <li><b>确定性锚点</b> {@link #anchor(long, int)}：由「世界种子 + 维度」算出唯一一个
 *       候选区块角点，落在 {@code ±5000} 内、16 对齐。同种子同维度永远同一点，
 *       于是「每世界仅一个」可以靠这个锚点 + 持久化标记实现，而不是满世界掷骰。</li>
 *   <li><b>岩浆浸没校验</b> {@link #isBuriedInLava(int[], int, int)}：判断一根竖直列
 *       能否把整座结构「埋进岩浆、且上方不暴露」。列用整数编码（0=空气/可暴露，
 *       1=岩浆，2=实心覆盖），与世界方块解耦，方便单元测试。</li>
 * </ol>
 *
 * <p>本类刻意不 import 任何 {@code net.minecraft} / {@code com.hbm}，
 * 因此可以用 {@code javac + java} 独立编译运行（见 {@code SunsetSixSiteTest}）。</p>
 */
public final class SunsetSixSite {

    /** 选址半径：以 (0,0) 为中心，x/z 各 ±5000 格。 */
    public static final int RANGE = 5000;

    /** 结构高度（NBT size.y）。日落六号是 9×30×10 的高塔。 */
    public static final int STRUCT_HEIGHT = 30;

    /** 顶部之上至少要有这么多格连续覆盖（岩浆或实心），才算「上方不暴露」。 */
    public static final int MIN_COVER = 10;

    /** 从底座起至少要有这么多格连续岩浆（结构高 30 + 上方 10），才算「在岩浆内」。 */
    public static final int MIN_LAVA_DEPTH = 40;

    /** 列编码：空气 / 可暴露（lava lake 表面的空洞）。 */
    public static final int AIR = 0;
    /** 列编码：岩浆（流动或静止都算）。 */
    public static final int LAVA = 1;
    /** 列编码：实心覆盖（地狱岩、基岩等不透明方块）。 */
    public static final int SOLID = 2;

    private SunsetSixSite() {
    }

    /**
     * 由种子与维度算出唯一候选锚点（区块角点，16 对齐），落在 ±{@link #RANGE} 内。
     *
     * <p>纯函数：同样的 {@code (seed, dimension)} 永远返回同样的 {@code {x, z}}。
     * 维度混进 hash，所以主世界与下界即使同种子也不会撞在同一格。</p>
     *
     * @return {@code {x, z}}，均为 16 的倍数，范围 {@code [-RANGE, RANGE]}。
     */
    public static int[] anchor(long seed, int dimension) {
        long h = seed;
        h ^= (long) dimension * 0xD6E8FEB86659FD93L;
        h ^= 0x5DEECE66DL;                 // salt：和别的结构落点 hash 区分开
        h = mix(h);

        // 取两段独立的 32 位，分别映射到 x / z，避免 x==z 的对角线偏斜
        long hx = mix(h ^ 0x9E3779B97F4A7C15L);
        long hz = mix(h ^ 0xC2B2AE3D27D4EB4FL);

        int span = RANGE / 16;             // 以「区块」为单位的可选格数
        int cx = (int) Math.floorMod(hx, (long) (2 * span + 1)) - span;
        int cz = (int) Math.floorMod(hz, (long) (2 * span + 1)) - span;

        return new int[] {cx * 16, cz * 16};
    }

    /**
     * 判断一根竖直列能否把结构「埋进岩浆且上方不暴露」。
     *
     * <p>规则（全满足才返回 true）：</p>
     * <ul>
     *   <li><b>至少 40 格连续岩浆</b>：{@code [baseY, baseY+MIN_LAVA_DEPTH)} 每一格都是
     *       {@link #LAVA}——结构高 30 整段浸在岩浆里，顶部之上还有 10 格岩浆盖着，
     *       既满足「必须在岩浆内」，也保证上方不暴露；</li>
     *   <li><b>列够高</b>：40 格窗口不能超出列范围。</li>
     * </ul>
     *
     * <p>{@code height} 参数保留以兼容调用方，但岩浆深度按 {@link #MIN_LAVA_DEPTH} 统一要求，
     * 不再随结构高度变化（用户要求「至少 40 格以上都要是岩浆」）。</p>
     *
     * @param column 竖直列编码数组，下标即 y（0 起）。
     * @param baseY  结构底座 y。
     * @param height 结构高度（仅用于校验合法性，岩浆深度固定 {@link #MIN_LAVA_DEPTH}）。
     */
    public static boolean isBuriedInLava(int[] column, int baseY, int height) {
        if(column == null || height <= 0 || baseY < 0) return false;

        int end = baseY + MIN_LAVA_DEPTH;  // 至少 40 格连续岩浆
        if(end > column.length) return false;

        // 从底座起至少 MIN_LAVA_DEPTH 格必须全是岩浆（体内 + 上方覆盖一并满足）
        for(int y = baseY; y < end; y++) {
            if(column[y] != LAVA) return false;
        }

        return true;
    }

    /** murmur3 收尾混合，把任意 hash 打散成均匀分布。与 AddonStructures.mix 同口径。 */
    private static long mix(long h) {
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return h;
    }
}
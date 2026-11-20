package com.makentmgreatagain.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * 矿囊撒点器：把一个球形的矿囊「撒」进世界里。
 *
 * <h3>为什么不用原版 WorldGenMinable</h3>
 * <p>原版那个是按椭球逐个格子遍历，形状规整但没法「只撒 N 个」——
 * 而铅锌矿和含砷金矿的矿囊刻意做得很小，用撒点更好控制密度：
 * 撒多少次基本就落多少格，调参是线性的。</p>
 *
 * <h3>形状</h3>
 * <p>{@code radius} 决定球的大小。判定是 {@code dx² + dy² + dz² <= radius² + 1}
 * （+1 是为了让 radius=1 时不至于退化成一个十字架）：</p>
 * <table border="1">
 *   <tr><th>radius</th><th>跨度</th><th>球内格数</th></tr>
 *   <tr><td>1</td><td>3x3x3</td><td>19</td></tr>
 *   <tr><td>2</td><td>5x5x5</td><td>57</td></tr>
 *   <tr><td>3</td><td>7x7x7</td><td>147</td></tr>
 * </table>
 *
 * <p>撒点是<b>有放回</b>的：同一格被抽中多次只算一次，所以最终落下的格数总是小于
 * 撒点次数。半径 2 的球撒 16 次 ≈ 落 14 格、撒 21 次 ≈ 落 17 格、撒 30 次 ≈ 落 24 格，
 * 想把 57 格塞满得撒上几百次。</p>
 *
 * <h3>只替换石头</h3>
 * <p>不碰空气、水、原版砾石矿脉、其他模组的矿 —— 该是谁的还是谁的。
 * flag 用 2（只同步给客户端，不触发方块更新），和原版世界生成一致。</p>
 *
 * <h3>两种落矿方式</h3>
 * <ul>
 *   <li>{@link #place} —— 撒点。小矿囊用，落多少格由撒点次数决定，调参线性。</li>
 *   <li>{@link #placeFilled} —— 实心填充。整个球体范围内的石头全换成矿，
 *       出一大片连着的大矿囊（白钨矿用这个）。</li>
 * </ul>
 */
public final class OreVeinPlacer {

    private OreVeinPlacer() {
    }

    /**
     * 在 (cx, cy, cz) 为中心生成一处球形矿囊。
     *
     * @param random   世界生成用的随机源
     * @param radius   球半径（格），>= 1
     * @param attempts 撒点次数（不是最终格数：重复命中的格子会被合并）
     * @param ore      要放的方块
     * @param minY     允许的最低高度（含）—— 越界的点直接丢弃，保证矿囊不会冒到配置区间外
     * @param maxY     允许的最高高度（含）
     */
    public static void place(World world, Random random, int cx, int cy, int cz,
                             int radius, int attempts, Block ore, int minY, int maxY) {
        placeOnHost(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY, Blocks.stone);
    }

    /**
     * 撒点矿囊，宿主岩可指定（Duna 等异星维度传各自的岩石方块）。
     *
     * @param host 只替换这一种方块；传 null 视为什么都不替换（安全退化）
     */
    public static int place(World world, Random random, int cx, int cy, int cz,
                             int radius, int attempts, Block ore, int minY, int maxY, Block host) {
        return placeOnHost(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY, host);
    }

    private static int placeOnHost(World world, Random random, int cx, int cy, int cz,
                             int radius, int attempts, Block ore, int minY, int maxY, Block host) {

        if(host == null) return 0;
        if(radius < 1) radius = 1;
        if(attempts < 1) return 0;

        int span = radius * 2 + 1;
        int r2 = radius * radius + 1;

        // 球内抽样 + 拒绝：抽到球外的点直接重抽，最多重抽 attempts * 8 次，
        // 防止 radius 很小（球外比例高）时死循环。
        int placed = 0;
        int orePlaced = 0;
        int guard = attempts * 8;
        while(placed < attempts && guard-- > 0) {

            int dx = random.nextInt(span) - radius;
            int dy = random.nextInt(span) - radius;
            int dz = random.nextInt(span) - radius;

            if(dx * dx + dy * dy + dz * dz > r2) continue;   // 球外的角落，不要

            int x = cx + dx;
            int y = cy + dy;
            int z = cz + dz;

            if(y < minY || y > maxY) continue;              // 不越出配置的高度区间

            if(world.getBlock(x, y, z) == host) {
                world.setBlock(x, y, z, ore, 0, 2);
                orePlaced++;
            }

            // 不管这一格是不是石头，都算占用了一次撒点 ——
            // 否则落在水/空气里的点会变相提高矿囊密度，同一种子下难以复现。
            placed++;
        }

        return orePlaced;
    }

    /**
     * 实心矿囊：把球体范围内的石头整片换成矿，不做撒点。
     *
     * <p>和 {@link #place} 的区别只有一点 —— 这里是「填满」：球内每一个石头格子都会变成矿，
     * 出来的是一大片连着的矿体，而不是散点。适合白钨矿这种要求「一整片」的矿脉。</p>
     *
     * <p>仍然只替换原版石头：球体切过洞穴、水脉、其他模组的矿时，那些格子原样保留，
     * 所以矿体中间可能带洞，但绝不会凭空造出悬空矿石。</p>
     *
     * <p>格数就是球内格数，与撒点次数无关：半径 4 → 257 格，半径 6 → 925 格，
     * 半径 8 → 2109 格（判定 {@code dx² + dy² + dz² <= radius²}）。</p>
     *
     * @return 实际替换掉的石头格数（球体切到空气/水的地方不计）
     */
    public static int placeFilled(World world, Random random, int cx, int cy, int cz,
                                  int radius, Block ore, int minY, int maxY) {
        return placeFilled(world, random, cx, cy, cz, radius, ore, minY, maxY, Blocks.stone);
    }

    /**
     * 实心矿囊，宿主岩可指定（Duna 传 hbm:duna_rock）。
     */
    public static int placeFilled(World world, Random random, int cx, int cy, int cz,
                                  int radius, Block ore, int minY, int maxY, Block host) {

        if(host == null) return 0;
        if(radius < 1) radius = 1;

        int r2 = radius * radius;
        int placed = 0;

        for(int dx = -radius; dx <= radius; dx++) {
            for(int dy = -radius; dy <= radius; dy++) {
                for(int dz = -radius; dz <= radius; dz++) {

                    if(dx * dx + dy * dy + dz * dz > r2) continue;

                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;

                    if(y < minY || y > maxY) continue;              // 不越出配置的高度区间

                    if(world.getBlock(x, y, z) == host) {
                        world.setBlock(x, y, z, ore, 0, 2);
                        placed++;
                    }
                }
            }
        }

        return placed;
    }
}

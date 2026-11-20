package com.makentmgreatagain.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.BiomeDictionary;

/**
 * 深海结核混合物的世界生成。
 *
 * <h3>生成逻辑</h3>
 * <ul>
 *   <li>只主世界、只海洋类群系（{@link BiomeDictionary.Type#WATER}）。</li>
 *   <li>每个海洋区块按 {@code deepSeaNoduleChancePercent} 掷一次概率；默认 2%，约 1/50 的海洋区块。</li>
 *   <li>中了以后生成 {@code deepSeaNodulePatchesMin~Max} 片（默认 1），
 *       每片限制在半径 {@code deepSeaNoduleRadiusMin~Max}（默认 6~7，最大跨度 13×13~15×15）的圆内。</li>
 *   <li><b>连通生长</b>：从圆心附近第一列可用海床开始，只从已经放下的结核格向四邻扩展，
 *       每片是一整块连在一起的结核，不会散成互不相邻的点。
 *       每片的目标数量 = 圆面积 × {@code deepSeaNoduleFillPercent}（默认 33%），
 *       半径 6~7 时约 30~48 格。</li>
 *   <li>只替换沙/砾石/黏土/泥土/石头/砂岩/硬化黏土，水深至少
 *       {@code deepSeaNoduleMinWaterDepth}（默认 20 格）。</li>
 * </ul>
 *
 * <p><b>为什么是连通的一片：</b>海面立管钻机采空一块结核后会继续采它周围范围内的结核，
 * 散落的点会让机器频繁“周围没矿了”而继续下钻；连成一片才能被同一台机器连续吃干。</p>
 *
 * <p>所有数值都在 {@code config/MNGA/worldgen.cfg} 的 {@code worldgen} 段，
 * 调密度优先改 {@code deepSeaNoduleChancePercent}，再改半径/片数。</p>
 *
 * <p>每个区块只在自己 16×16 的范围内放方块，不跨区块写，避免连锁加载。
 * 半径上限 7 也是为了保证整个圆能完整落在一个区块里。</p>
 */
public class WorldGenDeepSeaNodule implements IWorldGenerator {

    /** 1.7.10 主世界海平面。用来计算水深，扫描海床时从它下面一格开始。 */
    private static final int SEA_LEVEL = 63;

    /** 连通生长用的四邻方向 */
    private static final int[] DIR_X = {1, -1, 0, 0};
    private static final int[] DIR_Z = {0, 0, 1, -1};

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        if(!ModConfig.deepSeaNoduleWorldgenEnabled) return;
        if(world.provider.dimensionId != 0) return; // 只主世界

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        // 只海洋类群系。河/海滩如果水深不够，会在下面被 minDepth 筛掉。
        BiomeGenBase biome = world.getBiomeGenForCoords(x0 + 8, z0 + 8);
        if(!BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.WATER)) return;

        if(ModConfig.deepSeaNoduleChancePercent <= 0) return;
        if(random.nextInt(100) >= ModConfig.deepSeaNoduleChancePercent) return;

        int patchesMin = Math.max(1, ModConfig.deepSeaNodulePatchesMin);
        int patchesMax = Math.max(patchesMin, ModConfig.deepSeaNodulePatchesMax);
        int patches = patchesMin + random.nextInt(patchesMax - patchesMin + 1);

        // 半径硬夹在 1~7：8 以上就放不进一个 16×16 区块了
        int radiusMin = Math.max(1, Math.min(7, ModConfig.deepSeaNoduleRadiusMin));
        int radiusMax = Math.max(radiusMin, Math.min(7, ModConfig.deepSeaNoduleRadiusMax));

        float fill = Math.max(0, Math.min(100, ModConfig.deepSeaNoduleFillPercent)) / 100F;
        int minDepth = Math.max(1, Math.min(60, ModConfig.deepSeaNoduleMinWaterDepth));
        if(fill <= 0) return;

        for(int i = 0; i < patches; i++) {

            int radius = radiusMin + random.nextInt(radiusMax - radiusMin + 1);

            // 把圆心收进区块内部，保证整个圆都落在当前区块，不跨区块写方块
            int margin = radius;
            int span = 16 - margin * 2;
            int cx, cz;

            if(span <= 0) {
                cx = x0 + 8;
                cz = z0 + 8;
            } else {
                cx = x0 + margin + random.nextInt(span);
                cz = z0 + margin + random.nextInt(span);
            }

            int r2 = radius * radius;
            // 目标格数沿用原来的密度：圆面积 × 填充百分比
            int target = Math.max(1, (int) Math.round(Math.PI * r2 * fill));

            // 起点：离圆心最近的可用海床列（圆心那一格可能正好是别的方块）
            int seedX = 0, seedZ = 0, seedY = -1;
            int best = Integer.MAX_VALUE;

            for(int dx = -radius; dx <= radius; dx++) {
                for(int dz = -radius; dz <= radius; dz++) {

                    int d = dx * dx + dz * dz;
                    if(d > r2 || d >= best) continue;

                    int x = cx + dx;
                    int z = cz + dz;
                    int y = noduleColumnY(world, x, z, minDepth);
                    if(y < 0) continue;

                    best = d;
                    seedX = x;
                    seedZ = z;
                    seedY = y;
                }
            }
            if(seedY < 0) continue;

            // 连通生长：只从已经放下的格子向四邻扩展，每片必然是一整块连通矿区
            int size = radius * 2 + 1;
            boolean[] visited = new boolean[size * size];
            List<int[]> frontier = new ArrayList<int[]>();

            frontier.add(new int[] {seedX, seedZ});
            visited[gridIndex(seedX - cx, seedZ - cz, radius)] = true;

            int placed = 0;

            while(placed < target && !frontier.isEmpty()) {

                int[] cell = frontier.remove(random.nextInt(frontier.size()));
                int x = cell[0];
                int z = cell[1];

                int dx = x - cx;
                int dz = z - cz;

                if(dx * dx + dz * dz > r2) continue;
                if(x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) continue;

                int y = noduleColumnY(world, x, z, minDepth);
                if(y < 0) continue;

                // flag 2：通知周围方块，但不触发额外的方块更新链
                world.setBlock(x, y, z, ModBlocks.deep_sea_nodule, 0, 2);
                placed++;

                for(int dir = 0; dir < 4; dir++) {

                    int nx = x + DIR_X[dir];
                    int nz = z + DIR_Z[dir];
                    int ndx = nx - cx;
                    int ndz = nz - cz;

                    if(ndx * ndx + ndz * ndz > r2) continue;
                    if(nx < x0 || nx >= x0 + 16 || nz < z0 || nz >= z0 + 16) continue;

                    int idx = gridIndex(ndx, ndz, radius);
                    if(visited[idx]) continue;
                    visited[idx] = true;
                    frontier.add(new int[] {nx, nz});
                }
            }
        }
    }

    /** 网格下标：把相对圆心的 dx/dz 映射到 [0, size×size) */
    private int gridIndex(int dx, int dz, int radius) {
        int size = radius * 2 + 1;
        return (dx + radius) * size + (dz + radius);
    }

    /** 这一列是否适合放结核：返回海床顶格 y，不适合返回 -1 */
    private int noduleColumnY(World world, int x, int z, int minDepth) {

        int y = findSeafloor(world, x, z);
        if(y < 1) return -1;
        if(SEA_LEVEL - y < minDepth) return -1;
        if(!isSeafloorBlock(world.getBlock(x, y, z))) return -1;
        return y;
    }

    /**
     * 从海平面往下找海床最上面的固体方块。
     * 水/空气跳过；冰、海草等不是可替换海床的方块会在后面的白名单里被筛掉。
     */
    private int findSeafloor(World world, int x, int z) {
        for(int y = SEA_LEVEL - 1; y >= 1; y--) {
            Block b = world.getBlock(x, y, z);
            Material mat = b.getMaterial();
            if(mat == Material.water || mat == Material.air) continue;
            return y;
        }
        return -1;
    }

    /** 只替换这些自然海床方块，避免把矿石、海底遗迹、其他模组方块吃掉。 */
    private boolean isSeafloorBlock(Block b) {
        return b == Blocks.sand
                || b == Blocks.gravel
                || b == Blocks.clay
                || b == Blocks.dirt
                || b == Blocks.stone
                || b == Blocks.sandstone
                || b == Blocks.hardened_clay;
    }
}

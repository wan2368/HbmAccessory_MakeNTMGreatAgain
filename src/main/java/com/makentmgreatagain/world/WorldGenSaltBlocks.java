package com.makentmgreatagain.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenHills;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.BiomeDictionary;

/**
 * 含盐方块的世界生成（只主世界）。
 *
 * <p>设定：盐是“极度干燥蒸发”留下的，所以<b>有水的地方一律不生成</b>。
 * 判定采用最省性能的三层：</p>
 * <ol>
 *   <li>群系层：WATER / BEACH / SWAMP 整区块跳过（每区块 1 次查询）；</li>
 *   <li>方块层：要替换的方块上方必须是空气（水下沙盘自然被排除）；</li>
 *   <li>邻接层：只对真正要改的那一列检查“四向 + 上方”有没有水。</li>
 * </ol>
 *
 * <p>生成内容：</p>
 * <ul>
 *   <li><b>小片</b>：每区块 20% 概率、半径 6~15，片内每列 25% 把<b>地表 1 格</b>换成同类含盐方块；
 *       小片用“3x3 邻区块确定性圆”实现，每个区块只写自己的方块，边界不会切成直线。</li>
 *   <li><b>补生成</b>：只保留丘陵“该区块没有任何砾石地表”时补一小片盐砾石
 *       （概率 0.7、半径 x0.775）；沙/红沙不补（沙漠平顶山本来就到处是沙）。</li>
 *   <li><b>盐砾石矿脉</b>：<b>地下那层含盐沙砾的唯一来源</b>。把世界切成 NxN 区块一格
 *       （默认 7x7 = 49 区块），每格 70% 概率出 1~3 处扁椭圆矿体（约 4~6 个区块宽）、
 *       厚 3~12、中心 y 20~52，只替换石头，不动原版砾石矿脉。
 *       参数全在 {@code ModConfig.saltVein*}，调密度首选取 {@code saltVeinRegionSize}。</li>
 * </ul>
 *
 * <p>性能：每区块约 256 次高度图 + 256 次“上方”检查，加上最多数百次带条件的 getBlock；
 * 矿脉只在本区块与椭圆相交时填充。全部用 flag=2 放置，不触发邻接更新。</p>
 */
public class WorldGenSaltBlocks implements IWorldGenerator {

    /** 小片按 3x3 区块为一个“格子”，每格最多一个，保证间隔 */
    private static final int PATCH_CELL = 3;

    private static final int KIND_SAND = 0;
    private static final int KIND_RED_SAND = 1;
    private static final int KIND_GRAVEL = 2;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        if(!ModConfig.saltWorldgenEnabled) return;
        if(world.provider.dimensionId != 0) return;              // 只主世界

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        BiomeGenBase biome = world.getBiomeGenForCoords(x0 + 8, z0 + 8);

        // 海洋/河流整体跳过：盐是干燥蒸发留下的，水下不该有。
        // 但沙滩/沼泽只跳过「地表盐斑」，地下的矿脉照常生成 —— 否则海岸线一整条全是空档。
        if(isOceanBiome(biome)) return;

        boolean wetBiome = isWaterBiome(biome);

        long seed = world.getSeed();

        boolean hilly = isHillyBiome(biome);
        // 原油/油沙附近不铺地表盐斑（至少 3 个区块），但地下矿脉不受这条限制：
        // 油沙是湿地沉积，盐脉是更早的蒸发层，两者在剖面上本来就是上下关系。
        boolean oilNearby = (isDryBiome(biome) || hilly) && hasOilNearby(world, chunkX, chunkZ);

        // 沙漠/平顶山：圆形盐斑；丘陵不用圆，改成散点（见 scatterHills）
        if(!wetBiome && !oilNearby && isDryBiome(biome) && !hilly) {
            applyPatches(world, seed, chunkX, chunkZ);
        }

        if(ModConfig.saltGravelVeinEnabled) {
            applyVeins(world, seed, chunkX, chunkZ);
        }

        if(hilly && !wetBiome && !oilNearby) {
            // 丘陵：不生成圆形盐斑，改为地表散点
            scatterHills(world, new Random(seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L)), x0, z0);

            // 峡谷/洞穴壁上裸露的砾石按概率换掉（也是单块，不成片）
            exposeGravelInCaves(world, new Random(seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkZ * 0xC2B2AE3D27D4EB4FL)), x0, z0);
        }
    }

    // =====================================================================================
    //  小片：每区块 20% 概率、半径 6~15、片内 25%、只动地表 1 格
    // =====================================================================================

    private void applyPatches(World world, long seed, int chunkX, int chunkZ) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        // 3x3 区块为一个格子，每格最多一个小片；先算好周围 3x3 个格子的参数
        int cellX = floorDiv(chunkX, PATCH_CELL);
        int cellZ = floorDiv(chunkZ, PATCH_CELL);

        int[] centerX = new int[9];
        int[] centerZ = new int[9];
        int[] radius = new int[9];
        boolean[] active = new boolean[9];

        for(int dz = -1; dz <= 1; dz++) {
            for(int dx = -1; dx <= 1; dx++) {

                int index = (dz + 1) * 3 + (dx + 1);
                int ccx = cellX + dx;
                int ccz = cellZ + dz;

                Random patchRng = new Random(seed
                        ^ (ccx * 341873128712L)
                        ^ (ccz * 132897987541L)
                        ^ 0x5DEECE66DL);

                if(patchRng.nextFloat() * 100F >= ModConfig.patchChancePercent) continue;

                active[index] = true;
                int cellSize = PATCH_CELL << 4;   // 48 格
                centerX[index] = ccx * cellSize + patchRng.nextInt(cellSize);
                centerZ[index] = ccz * cellSize + patchRng.nextInt(cellSize);
                radius[index] = ModConfig.patchRadiusMin
                        + patchRng.nextInt(ModConfig.patchRadiusMax - ModConfig.patchRadiusMin + 1);
            }
        }

        for(int x = x0; x < x0 + 16; x++) {
            for(int z = z0; z < z0 + 16; z++) {

                boolean covered = false;
                for(int i = 0; i < 9 && !covered; i++) {
                    if(!active[i]) continue;
                    int ddx = x - centerX[i];
                    int ddz = z - centerZ[i];
                    if(ddx * ddx + ddz * ddz <= radius[i] * radius[i]) covered = true;
                }
                if(!covered) continue;

                // 每列 25%：用位置哈希代替 Random，零分配
                long hash = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ 0x9E3779B9L;
                hash ^= (hash >>> 33);
                hash *= 0xff51afd7ed558ccdL;
                hash ^= (hash >>> 33);
                int roll = (int) ((hash >>> 32) & 0x7FFFFFFFL) % 100;

                if(roll >= ModConfig.patchReplacePercent) continue;

                convertSurface(world, x, z);
            }
        }
    }

    /** 把某一列的地表 1 格换成同类含盐方块；水/水下/不匹配的直接跳过。 */
    private void convertSurface(World world, int x, int z) {

        int y = surfaceY(world, x, z);
        if(y < 0) return;

        int kind = kindOf(world, x, y, z);
        if(kind < 0) return;

        if(world.getBlock(x, y + 1, z).getMaterial() != Material.air) return;   // 上方必须是空气
        if(touchesWater(world, x, y, z)) return;                                // 四向 + 上方不能贴水

        world.setBlock(x, y, z, saltBlock(kind), 0, 2);
    }

    // =====================================================================================
    //  丘陵地表：散点，不聚成圆
    // =====================================================================================

    private void scatterHills(World world, Random rng, int x0, int z0) {

        for(int x = 0; x < 16; x++) {
            for(int z = 0; z < 16; z++) {

                int wx = x0 + x;
                int wz = z0 + z;
                int y = surfaceY(world, wx, wz);
                if(y < 0) continue;

                Block block = world.getBlock(wx, y, wz);
                boolean gravel = block == Blocks.gravel;

                if(!gravel && block != Blocks.stone && block != Blocks.dirt && block != Blocks.grass) continue;
                if(world.getBlock(wx, y + 1, wz).getMaterial() != Material.air) continue;
                if(touchesWater(world, wx, y, wz)) continue;

                // 砾石按配置概率散点替换；石/土只给四分之一的概率，避免山体大面积变盐
                int percent = gravel ? ModConfig.hillsScatterPercent : Math.max(1, ModConfig.hillsScatterPercent / 4);
                if(rng.nextInt(100) >= percent) continue;

                world.setBlock(wx, y, wz, ModBlocks.Block_Salt_Contain_Gravel, 0, 2);
            }
        }
    }

    // =====================================================================================
    //  盐砾石矿脉：每 regionSize x regionSize 区块一格，每格最多 VEIN_MAX 处
    // =====================================================================================

    /** 单格最多几处。再多就该调 saltVeinRegionSize 了，堆在循环里只是白烧 CPU。 */
    private static final int VEIN_MAX = 3;

    private void applyVeins(World world, long seed, int chunkX, int chunkZ) {

        // 区域边长读配置：11 = 121 区块一格（旧版），8 = 64 区块一格（默认），5 = 25 区块一格（很密）
        int regionSize = Math.max(3, ModConfig.saltVeinRegionSize);

        int regionX = floorDiv(chunkX, regionSize);
        int regionZ = floorDiv(chunkZ, regionSize);

        for(int drz = -1; drz <= 1; drz++) {
            for(int drx = -1; drx <= 1; drx++) {

                int rx = regionX + drx;
                int rz = regionZ + drz;

                Random regionRng = new Random(seed
                        ^ (rx * 341873128712L)
                        ^ (rz * 132897987541L)
                        ^ 0x2545F4914F6CDD1DL);

                int count = rollVeinCount(regionRng);

                for(int i = 0; i < count && i < VEIN_MAX; i++) {

                    int centerChunkX = rx * regionSize + regionRng.nextInt(regionSize);
                    int centerChunkZ = rz * regionSize + regionRng.nextInt(regionSize);

                    // 扁椭圆：12~14 x 28~32 → 面积约 1055~1407 格 = 4.1~5.5 个区块
                    int radiusA = 12 + regionRng.nextInt(3);    // 12~14
                    int radiusB = 28 + regionRng.nextInt(5);    // 28~32
                    boolean swap = regionRng.nextBoolean();
                    int radiusX = swap ? radiusB : radiusA;
                    int radiusZ = swap ? radiusA : radiusB;

                    int thickness = ModConfig.saltVeinThicknessMin
                            + regionRng.nextInt(ModConfig.saltVeinThicknessMax - ModConfig.saltVeinThicknessMin + 1);
                    int spanY = ModConfig.saltVeinMaxY - ModConfig.saltVeinMinY + 1;
                    int centerY = ModConfig.saltVeinMinY + regionRng.nextInt(spanY);
                    long veinSeed = regionRng.nextLong();

                    int centerBlockX = (centerChunkX << 4) + 8;
                    int centerBlockZ = (centerChunkZ << 4) + 8;

                    // 快速包围盒剔除，避免对不可能相交的区块做逐列计算
                    int bx0 = chunkX << 4;
                    int bz0 = chunkZ << 4;
                    if(centerBlockX + radiusX < bx0 || centerBlockX - radiusX > bx0 + 15) continue;
                    if(centerBlockZ + radiusZ < bz0 || centerBlockZ - radiusZ > bz0 + 15) continue;

                    // 只有真正会碰到矿脉时才做“原油附近”检查（带缓存，成本可控）。
                    // 这里是 continue 不是 return —— 旧版写 return 会让这一格剩下的矿脉全部作废，
                    // 在油沙密集的地方等于凭空少掉几处。
                    if(hasOilNearby(world, chunkX, chunkZ)) continue;

                    fillVeinPart(world, chunkX, chunkZ,
                            centerBlockX, centerBlockZ,
                            radiusX, radiusZ, thickness, centerY, veinSeed);
                }
            }
        }
    }

    /**
     * 先掷「这一格有没有矿脉」，有的话再掷 1~3 处。
     *
     * <p>旧版把概率写死成「55% 没有、30% 一处、12% 两处、3% 三处」，
     * 平均每 121 区块才 0.63 处，玩家基本撞不到。现在整条曲线交给
     * {@code saltVeinChancePercent}，默认 60% 且 8x8 一格 ≈ 每 65 区块 1 处。</p>
     */
    private int rollVeinCount(Random rng) {
        if(rng.nextFloat() * 100F >= ModConfig.saltVeinChancePercent) return 0;

        float roll = rng.nextFloat();
        if(roll < 0.15F) return 3;
        if(roll < 0.50F) return 2;
        return 1;
    }

    private void fillVeinPart(World world, int chunkX, int chunkZ,
            int centerX, int centerZ, int radiusX, int radiusZ,
            int thickness, int centerY, long veinSeed) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        for(int x = x0; x < x0 + 16; x++) {
            for(int z = z0; z < z0 + 16; z++) {

                double dx = (x - centerX) / (double) radiusX;
                double dz = (z - centerZ) / (double) radiusZ;
                double dist = dx * dx + dz * dz;
                if(dist > 1.0D) continue;

                int surface = world.getHeightValue(x, z);
                int half = (int) Math.max(1, thickness * 0.5D * Math.sqrt(1.0D - dist));
                int top = Math.min(centerY + half, surface - 3);
                int bottom = Math.max(4, centerY - half);
                if(top < bottom) continue;

                for(int y = bottom; y <= top; y++) {

                    if(world.getBlock(x, y, z) != Blocks.stone) continue;

                    long hash = veinSeed ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
                    hash ^= (hash >>> 33);
                    hash *= 0xff51afd7ed558ccdL;
                    hash ^= (hash >>> 33);

                    // 填充率：旧版写死 (hash & 7) == 0 → 固定留 12.5% 的孔。
                    // 现在读配置，默认 94%（留 6% 孔），矿体内还是能看到零星石头。
                    int roll = (int) (((hash >>> 32) & 0x7FFFFFFFL) % 100L);
                    if(roll >= ModConfig.saltVeinFillPercent) continue;

                    world.setBlock(x, y, z, ModBlocks.Block_Salt_Contain_Gravel, 0, 2);
                }
            }
        }
    }

    // =====================================================================================
    //  工具
    // =====================================================================================

    // =====================================================================================
    //  原油/油沙规避 & 峡谷裸露砾石
    // =====================================================================================

    private static final Map<Long, Boolean> OIL_CACHE = new HashMap<Long, Boolean>();

    /** 3 个区块内是否有 HBM 油沙/油污土/油砂（缓存在内存里，避免重复扫描）。 */
    private static boolean hasOilNearby(World world, int chunkX, int chunkZ) {

        long key = (world.getSeed() * 31L)
                + (chunkX * 341873128712L)
                ^ (chunkZ * 132897987541L);

        Boolean cached = OIL_CACHE.get(key);
        if(cached != null) return cached;

        boolean found = chunkHasOil(world, chunkX, chunkZ, false);

        if(!found) {
            for(int dz = -3; dz <= 3 && !found; dz++) {
                for(int dx = -3; dx <= 3 && !found; dx++) {
                    if(dx == 0 && dz == 0) continue;
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    if(!world.getChunkProvider().chunkExists(cx, cz)) continue;
                    found = chunkHasOil(world, cx, cz, true);
                }
            }
        }

        if(OIL_CACHE.size() > 40000) OIL_CACHE.clear();
        OIL_CACHE.put(key, found);
        return found;
    }

    /** full=true 时只抽 9 个点（邻区块），false 时扫全 16x16（当前区块）。 */
    private static boolean chunkHasOil(World world, int chunkX, int chunkZ, boolean sampled) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        if(sampled) {
            for(int i = 0; i < 9; i++) {
                int x = x0 + 2 + (i % 3) * 6;
                int z = z0 + 2 + (i / 3) * 6;
                if(columnHasOil(world, x, z)) return true;
            }
            return false;
        }

        for(int x = x0; x < x0 + 16; x++) {
            for(int z = z0; z < z0 + 16; z++) {
                if(columnHasOil(world, x, z)) return true;
            }
        }
        return false;
    }

    private static boolean columnHasOil(World world, int x, int z) {
        int y = Math.min(255, world.getHeightValue(x, z));
        for(int i = 0; i < 3 && y > 0; i++, y--) {
            if(isOilBlock(world.getBlock(x, y, z))) return true;
        }
        return false;
    }

    private static boolean isOilBlock(Block block) {
        return block == com.hbm.blocks.ModBlocks.sand_dirty
                || block == com.hbm.blocks.ModBlocks.sand_dirty_red
                || block == com.hbm.blocks.ModBlocks.dirt_oily
                || block == com.hbm.blocks.ModBlocks.ore_oil_sand;
    }

    /** 丘陵里随机挑少量地下点：裸露（邻接空气）的砾石按概率换成盐砾石，主要让峡谷壁可见。 */
    private void exposeGravelInCaves(World world, Random rng, int x0, int z0) {

        for(int i = 0; i < 120; i++) {

            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = 18 + rng.nextInt(46);          // 18~63

            if(world.getBlock(x, y, z) != Blocks.gravel) continue;
            if(!isExposed(world, x, y, z)) continue;
            if(rng.nextInt(100) >= ModConfig.caveGravelPercent) continue;

            world.setBlock(x, y, z, ModBlocks.Block_Salt_Contain_Gravel, 0, 2);
        }
    }

    private static boolean isExposed(World world, int x, int y, int z) {
        return world.getBlock(x + 1, y, z).getMaterial() == Material.air
                || world.getBlock(x - 1, y, z).getMaterial() == Material.air
                || world.getBlock(x, y + 1, z).getMaterial() == Material.air
                || world.getBlock(x, y - 1, z).getMaterial() == Material.air
                || world.getBlock(x, y, z + 1).getMaterial() == Material.air
                || world.getBlock(x, y, z - 1).getMaterial() == Material.air;
    }

    private static int surfaceY(World world, int x, int z) {
        int y = Math.min(255, world.getHeightValue(x, z) + 1);
        for(int i = 0; i < 8 && y > 0; i++, y--) {
            Block b = world.getBlock(x, y, z);
            if(b != Blocks.air && b.getMaterial() != Material.water) return y;
        }
        return -1;
    }

    /** 某些 1.8 回移模组（例如 Et Futurum 的某些版本/分支）可能注册独立的 red_sand 方块，这里做惰性兼容。 */
    private static boolean moddedRedSandChecked;
    private static Block moddedRedSand;

    private static Block getModdedRedSand() {
        if(!moddedRedSandChecked) {
            moddedRedSandChecked = true;
            moddedRedSand = Block.getBlockFromName("etfuturum:red_sand");
            if(moddedRedSand == null) moddedRedSand = Block.getBlockFromName("minecraft:red_sand");
        }
        return moddedRedSand;
    }

    private static int kindOf(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if(block == Blocks.sand) {
            return world.getBlockMetadata(x, y, z) == 1 ? KIND_RED_SAND : KIND_SAND;
        }
        Block redSand = getModdedRedSand();
        if(redSand != null && block == redSand) return KIND_RED_SAND;
        if(block == Blocks.gravel) return KIND_GRAVEL;
        return -1;
    }

    private static boolean touchesWater(World world, int x, int y, int z) {
        return world.getBlock(x + 1, y, z).getMaterial() == Material.water
                || world.getBlock(x - 1, y, z).getMaterial() == Material.water
                || world.getBlock(x, y, z + 1).getMaterial() == Material.water
                || world.getBlock(x, y, z - 1).getMaterial() == Material.water
                || world.getBlock(x, y + 1, z).getMaterial() == Material.water;
    }

    private static Block saltBlock(int kind) {
        if(kind == KIND_RED_SAND) return ModBlocks.Block_Salt_Contain_Red_Sand;
        if(kind == KIND_GRAVEL) return ModBlocks.Block_Salt_Contain_Gravel;
        return ModBlocks.Block_Salt_Contain_Sand;
    }

    private static boolean isWaterBiome(BiomeGenBase biome) {
        return BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.WATER)
                || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.BEACH)
                || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP);
    }

    /** 真正的海洋/河流：整块跳过，连地下矿脉都不给（水下沉积不该有蒸发盐层）。 */
    private static boolean isOceanBiome(BiomeGenBase biome) {
        return BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.WATER);
    }

    private static boolean isDryBiome(BiomeGenBase biome) {
        return biome.rainfall <= 0.05F
                || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.DRY)
                || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SANDY)
                || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.WASTELAND);
    }

    /** 原版只有丘陵（Extreme Hills 及其 M 变种）会铺砾石地表，森林丘陵/平原不算 */
    private static boolean isHillyBiome(BiomeGenBase biome) {
        return biome instanceof BiomeGenHills;
    }

    private static int floorDiv(int a, int b) {
        return a >= 0 ? a / b : (a - b + 1) / b;
    }
}

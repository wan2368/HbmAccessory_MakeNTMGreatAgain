package com.makentmgreatagain.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

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
 * <p>盐是干燥蒸发留下的，所以有水的地方一律不生成。判定分三层：群系层整区块跳过
 * WATER/BEACH/SWAMP；方块层要求上方是空气；邻接层只对真正要改的列查四向与上方。</p>
 *
 * <ul>
 *   <li><b>小片</b>：3x3 区块一格、20% 概率、半径 6~15，片内每列 25% 换掉地表 1 格。</li>
 *   <li><b>丘陵地表散点</b>：4x4 区块一格、60% 概率圈一块半径 10~20 的圆，圆内按列替换。
 *       草方块永不替换（换掉后草与花会浮空）。</li>
 *   <li><b>洞穴/峡谷裸露砾石</b>：同区域约束下把邻接空气的砾石换成盐砾石。</li>
 *   <li><b>盐砾石矿脉</b>：地下含盐沙砾的主来源，NxN 区块一格、每格 70% 概率出 1~3 处扁椭圆，
 *       只替换石头。参数全在 {@code ModConfig.saltVein*}。</li>
 * </ul>
 *
 * <p>原油规避靠 {@link #columnHasOil} 纵向扫描整列：HBM 的 {@code ore_oil} 泡中心在
 * y 15~39、纯地下，只看地表 3 格永远看不见它。基岩油（y 0~4）一并纳入。</p>
 *
 * <p>性能：原油检测降采样到每区块 16 点、邻区块 4 点、每列 28 次取值，并带区块级缓存；
 * 全部用 flag=2 放置，不触发邻接更新。</p>
 */
public class WorldGenSaltBlocks implements IWorldGenerator {

    /** 小片按 3x3 区块为一个“格子”，每格最多一个，保证间隔 */
    private static final int PATCH_CELL = 3;

    /** 散点按 4x4 区块为一格：格内先掷要不要有散点区，有的话再定圆心与半径，只有圆内参与。 */
    private static final int SCATTER_CELL = 4;

    /** 散点区被激活的概率（%）。没激活的格子里一块盐都不放。 */
    private static final int SCATTER_ZONE_CHANCE = 60;

    /** 丘陵地表散点区的半径（格）。4 区块 = 64 格，半径 10~20 约占格子的 8~30%。 */
    private static final int HILLS_ZONE_RADIUS_MIN = 10;
    private static final int HILLS_ZONE_RADIUS_MAX = 20;

    /** 洞穴散点区的半径（格）。比地表小，只覆盖一小段峡谷壁。 */
    private static final int CAVE_ZONE_RADIUS_MIN = 6;
    private static final int CAVE_ZONE_RADIUS_MAX = 14;

    /** 每区块为洞穴散点掷骰次数。区域只覆盖约 10% 面积，所以要比地表多掷几次。 */
    private static final int CAVE_TRIES = 24;

    /** 洞穴散点的取值高度：18~63，正好与 HBM 的 ore_oil（15~55）重叠，所以必须查油。 */
    private static final int CAVE_Y_MIN = 18;
    private static final int CAVE_Y_SPAN = 46;

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

        // 原油附近什么都不长：地表盐斑、丘陵散点、洞穴砾石、地下矿脉四条全部让路。
        boolean oilNearby = (isDryBiome(biome) || hilly) && hasOilNearby(world, chunkX, chunkZ);

        // 沙漠/平顶山：圆形盐斑；丘陵不用圆，改成散点（见 scatterHills）
        if(!wetBiome && !oilNearby && isDryBiome(biome) && !hilly) {
            applyPatches(world, seed, chunkX, chunkZ);
        }

        if(ModConfig.saltGravelVeinEnabled) {
            applyVeins(world, seed, chunkX, chunkZ);
        }

        if(hilly && !wetBiome && !oilNearby) {
            // 丘陵：不生成圆形盐斑，改为「区域内散点」
            scatterHills(world, seed, chunkX, chunkZ);

            // 峡谷/洞穴壁上裸露的砾石按概率换掉（同样限制在区域内）
            exposeGravelInCaves(world, seed, chunkX, chunkZ);
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
    //  丘陵地表：区域内散点（先圈一块圆，圆内再按列掷骰子）
    // =====================================================================================

    private void scatterHills(World world, long seed, int chunkX, int chunkZ) {

        // 配置成 0 就等于关掉这条，不必再去算 256 次高度图
        if(ModConfig.hillsScatterPercent <= 0) return;

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        ZoneSet zones = new ZoneSet(seed, floorDiv(chunkX, SCATTER_CELL), floorDiv(chunkZ, SCATTER_CELL),
                0x51A7L, SCATTER_ZONE_CHANCE, HILLS_ZONE_RADIUS_MIN, HILLS_ZONE_RADIUS_MAX);

        // 周围 3x3 个格子一个都没激活时直接返回，连 256 次高度图都省了
        if(zones.isEmpty()) return;

        Random rng = new Random(seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L));

        for(int x = 0; x < 16; x++) {
            for(int z = 0; z < 16; z++) {

                int wx = x0 + x;
                int wz = z0 + z;

                // 圆外一块都不放 —— 这一句就是「区域内散点」与「全图均匀撒点」的全部区别
                if(!zones.contains(wx, wz)) continue;

                int y = surfaceY(world, wx, wz);
                if(y < 0) continue;

                Block block = world.getBlock(wx, y, wz);
                boolean gravel = block == Blocks.gravel;

                // 草方块不替换：它说明这一列有植被覆盖，不是裸露的蒸发面，换掉还会让草与花浮空。
                if(!gravel && block != Blocks.stone && block != Blocks.dirt) continue;

                if(world.getBlock(wx, y + 1, wz).getMaterial() != Material.air) continue;
                if(touchesWater(world, wx, y, wz)) continue;

                // 砾石按 4 倍、石/土按 1 倍：区域只覆盖约 10% 面积，不补倍数就看不见了。
                int base = ModConfig.hillsScatterPercent;
                // base <= 0 时两块都必须是 0，否则配置填 0 也关不干净。
                int percent = base <= 0 ? 0
                        : (gravel ? Math.min(100, base * 4) : base);
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

        // 区域边长读配置：8 = 64 区块一格（默认），5 很密，11 很稀
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

                    // 只有真正会碰到矿脉时才做“原油附近”检查（带缓存）。
                    // 用 continue 不用 return，否则这一格剩下的矿脉会全部作废。
                    if(hasOilNearby(world, chunkX, chunkZ)) continue;

                    fillVeinPart(world, chunkX, chunkZ,
                            centerBlockX, centerBlockZ,
                            radiusX, radiusZ, thickness, centerY, veinSeed);
                }
            }
        }
    }

    /** 先掷「这一格有没有矿脉」，有的话再掷 1~3 处；概率由 {@code saltVeinChancePercent} 控制。 */
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

                    // 填充率读配置，默认 94%，矿体内还能看到零星石头。
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

    /** 按世界隔离的区块级油藏缓存；弱引用避免卸载存档后保留 World。 */
    private static final Map<World, Map<Long, Boolean>> OIL_CACHE = new WeakHashMap<World, Map<Long, Boolean>>();

    /** 纵扫上限。ore_oil 中心 15~39（半径 8~16）、ore_oil_sand 24~119、基岩油 0~4。 */
    private static final int OIL_SCAN_TOP = 72;

    /** 纵扫步长：3 格一个采样点，足以命中半径 8~16 的油泡。 */
    private static final int OIL_SCAN_STEP = 3;

    /** 本区块的采样格距：步长 4 → 每区块 16 点。 */
    private static final int OIL_SAMPLE_STEP = 4;

    /** 2 个区块内是否有 HBM 油藏。半径取 2 足够：油泡最大半径 16 格，再扫一圈只是白做纵扫。 */
    private static synchronized boolean hasOilNearby(World world, int chunkX, int chunkZ) {

        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        Map<Long, Boolean> worldCache = OIL_CACHE.get(world);
        if(worldCache == null) {
            worldCache = new HashMap<Long, Boolean>();
            OIL_CACHE.put(world, worldCache);
        }

        Boolean cached = worldCache.get(key);
        if(cached != null) return cached;

        boolean found = chunkHasOil(world, chunkX, chunkZ, false);

        if(!found) {
            for(int dz = -2; dz <= 2 && !found; dz++) {
                for(int dx = -2; dx <= 2 && !found; dx++) {
                    if(dx == 0 && dz == 0) continue;
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    if(!world.getChunkProvider().chunkExists(cx, cz)) continue;
                    found = chunkHasOil(world, cx, cz, true);
                }
            }
        }

        if(worldCache.size() > 40000) worldCache.clear();
        worldCache.put(key, found);
        return found;
    }

    /** sampled=true 时只取 4 个角点（邻区块用），false 时按 4 格步长取 16 点。 */
    private static boolean chunkHasOil(World world, int chunkX, int chunkZ, boolean sampled) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        if(sampled) {
            for(int i = 0; i < 4; i++) {
                int x = x0 + (i % 2 == 0 ? 3 : 12);
                int z = z0 + (i < 2 ? 3 : 12);
                if(columnHasOil(world, x, z)) return true;
            }
            return false;
        }

        for(int x = x0 + 2; x < x0 + 16; x += OIL_SAMPLE_STEP) {
            for(int z = z0 + 2; z < z0 + 16; z += OIL_SAMPLE_STEP) {
                if(columnHasOil(world, x, z)) return true;
            }
        }
        return false;
    }

    /**
     * 某一列有没有 HBM 油：先看地表 3 格（油沙、油污土常露头），再纵向扫到 y=72。
     * ore_oil 是纯地下的（y 15~39），只查地表永远看不见它。
     */
    private static boolean columnHasOil(World world, int x, int z) {

        int surface = Math.min(255, world.getHeightValue(x, z));

        // 第一段：地表 3 格
        for(int y = surface, i = 0; i < 3 && y > 0; i++, y--) {
            if(isOilBlock(world.getBlock(x, y, z))) return true;
        }

        // 第二段：地下纵扫（最坏 24 次取值）
        int top = Math.min(surface, OIL_SCAN_TOP);
        for(int y = top; y >= 1; y -= OIL_SCAN_STEP) {
            if(isOilBlock(world.getBlock(x, y, z))) return true;
        }

        // 基岩油只在 y 0~4，步长 3 可能正好跳过 1~2，补扫一遍（4 次取值）
        for(int y = 1; y <= 4; y++) {
            if(isOilBlock(world.getBlock(x, y, z))) return true;
        }

        return false;
    }

    /** 沾一点油就算：地下油泡（含抽空的）、基岩油、浅层油沙、油污土、地表油污。 */
    private static boolean isOilBlock(Block block) {
        return block == com.hbm.blocks.ModBlocks.ore_oil
                || block == com.hbm.blocks.ModBlocks.ore_oil_empty      // 被抽空的油矿，仍属油田
                || block == com.hbm.blocks.ModBlocks.ore_bedrock_oil
                || block == com.hbm.blocks.ModBlocks.ore_oil_sand
                || block == com.hbm.blocks.ModBlocks.sand_dirty
                || block == com.hbm.blocks.ModBlocks.sand_dirty_red
                || block == com.hbm.blocks.ModBlocks.dirt_oily
                || block == com.hbm.blocks.ModBlocks.oil_spill;         // 油泡地表溅出的原油渍
    }

    /**
     * 丘陵里挑少量地下点：邻接空气的砾石按概率换成盐砾石，让峡谷壁可见。
     * 只在散点区内取点，且每个候选点按列精确复查有没有油 ——
     * 区块级的 {@link #hasOilNearby} 是降采样的，会漏掉小油泡。
     */
    private void exposeGravelInCaves(World world, long seed, int chunkX, int chunkZ) {

        if(ModConfig.caveGravelPercent <= 0) return;

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        ZoneSet zones = new ZoneSet(seed, floorDiv(chunkX, SCATTER_CELL), floorDiv(chunkZ, SCATTER_CELL),
                0x0CA7EL, SCATTER_ZONE_CHANCE, CAVE_ZONE_RADIUS_MIN, CAVE_ZONE_RADIUS_MAX);

        if(zones.isEmpty()) return;

        Random rng = new Random(seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkZ * 0xC2B2AE3D27D4EB4FL));

        for(int i = 0; i < CAVE_TRIES; i++) {

            int x = x0 + rng.nextInt(16);
            int z = z0 + rng.nextInt(16);
            int y = CAVE_Y_MIN + rng.nextInt(CAVE_Y_SPAN);

            if(!zones.contains(x, z)) continue;

            if(world.getBlock(x, y, z) != Blocks.gravel) continue;
            if(!isExposed(world, x, y, z)) continue;
            if(rng.nextInt(100) >= ModConfig.caveGravelPercent) continue;

            // ★ 本列有油就跳过：盐层是更早的蒸发沉积，不可能长在油藏里面
            if(columnHasOil(world, x, z)) continue;

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

    /**
     * 一组「散点区域」：把世界切成 {@link #SCATTER_CELL} x {@link #SCATTER_CELL} 区块一格，
     * 每格独立掷「要不要有散点区」，有的话再定圆心与半径，只有圆内允许生成。
     *
     * <p>本区块只看周围 3x3 个格子，参数完全由种子决定，所以结果与区块生成顺序无关，
     * 边界也不会出现直线。</p>
     */
    private static final class ZoneSet {

        private final int[] centerX = new int[9];
        private final int[] centerZ = new int[9];
        private final int[] radiusSq = new int[9];
        private final boolean[] active = new boolean[9];

        /**
         * @param salt     用来把这个 ZoneSet 和同一格子里别的用途区分开（地表 / 洞穴各一个）
         * @param radiusMin 半径下限（格）
         * @param radiusMax 半径上限（格）
         */
        ZoneSet(long seed, int cellX, int cellZ, long salt,
                int chancePercent, int radiusMin, int radiusMax) {

            int cellSize = SCATTER_CELL << 4;

            for(int dz = -1; dz <= 1; dz++) {
                for(int dx = -1; dx <= 1; dx++) {

                    int index = (dz + 1) * 3 + (dx + 1);
                    int ccx = cellX + dx;
                    int ccz = cellZ + dz;

                    Random rng = new Random(seed
                            ^ (ccx * 341873128712L)
                            ^ (ccz * 132897987541L)
                            ^ salt);

                    if(rng.nextFloat() * 100F >= chancePercent) continue;

                    active[index] = true;
                    centerX[index] = ccx * cellSize + rng.nextInt(cellSize);
                    centerZ[index] = ccz * cellSize + rng.nextInt(cellSize);

                    int radius = radiusMin + rng.nextInt(radiusMax - radiusMin + 1);
                    radiusSq[index] = radius * radius;
                }
            }
        }

        /** 周围 3x3 个格子全没激活 —— 调用方可以整块跳过，省掉 256 次高度图。 */
        boolean isEmpty() {
            for(boolean b : active) if(b) return false;
            return true;
        }

        boolean contains(int x, int z) {
            for(int i = 0; i < 9; i++) {
                if(!active[i]) continue;
                int dx = x - centerX[i];
                int dz = z - centerZ[i];
                if(dx * dx + dz * dz <= radiusSq[i]) return true;
            }
            return false;
        }
    }
}

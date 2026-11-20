package com.makentmgreatagain.world;

import java.util.Random;

import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 铅锌矿的世界生成（主世界 + Duna）。
 *
 * <h3>密度是怎么堆出来的</h3>
 * <p>每区块掷一次 {@code leadzincChunkChancePercent}（默认 70%）；中了以后生成
 * {@code leadzincVeinsPerChunkMin~Max} 处（默认 1~2）球状矿囊，每处半径
 * {@code leadzincVeinRadius}（默认 2，跨 5x5x5）、撒
 * {@code leadzincClusterMin~Max} 次点（默认 16~26）。</p>
 *
 * <p>折算下来一个区块大约落下 <b>16 格</b>铅锌矿（按默认参数逐格模拟 2 万区块得出，
 * 旧参数只有 2.5 格）。作为对照：原版 1.7.10 的铁矿一个区块大约 60~90 格，
 * 所以这条线是「专门去找就挖得到、顺手挖不会满地都是」的密度，
 * 一条镉锭要 4 格矿，走一趟矿洞基本能凑齐一炉。</p>
 *
 * <p>调参在 config/MNGA/worldgen.cfg 的 {@code worldgen} 段，改完重启即对<b>新生成的区块</b>生效。
 * 调密度首选 {@code leadzincChunkChancePercent}，其次是 {@code leadzincVeinsPerChunkMax}；
 * 改半径是三次方放大，慎用。</p>
 *
 * <h3>Duna 分支</h3>
 * <p>维度识别走 {@link DunaHost#isDuna}：配置 ID 命中、或 provider 是 HBM 的
 * WorldProviderDuna 都算，整合包改过维度号也不会失效。
 * 生成变体方块 {@code ore_leadzinc_duna}，宿主岩换成 {@code hbm} 的 duna_rock
 * （查不到直接跳过，不会往别的岩层里塞矿）。
 * 触发概率和高度区间走 {@code dunaLeadzinc*} 独立配置；矿囊大小（几处/半径/撒点）
 * 沿用主世界参数，保持两种矿脉形态一致。</p>
 *
 * <p>每个扫过的 Duna 区块都会进 {@link DunaDiag} 计数，日志里能看到
 * 生成器到底有没有被调用、落了多少格矿。</p>
 */
public class WorldGenLeadZincOre implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        int dim = world.provider.dimensionId;

        if(dim == 0) {
            if(!ModConfig.leadzincWorldgenEnabled) return;
            if(random.nextInt(100) >= ModConfig.leadzincChunkChancePercent) return;
            scatter(world, random, chunkX, chunkZ, ModBlocks.ore_leadzinc,
                    ModConfig.leadzincMinY, ModConfig.leadzincMaxY, Blocks.stone);

        } else if(DunaHost.isDuna(world, ModConfig.dunaDimensionId)) {
            if(!ModConfig.dunaLeadzincEnabled) return;
            Block host = DunaHost.rock(world);
            int placed = 0;
            if(host != null && random.nextInt(100) < ModConfig.dunaLeadzincChunkChancePercent) {
                placed = scatter(world, random, chunkX, chunkZ, ModBlocks.ore_leadzinc_duna,
                        ModConfig.dunaLeadzincMinY, ModConfig.dunaLeadzincMaxY, host);
            }
            DunaDiag.tick("leadzinc", placed, host);
        }
    }

    /** 撒点矿囊：矿方块、高度区间、宿主岩由调用方给，其余参数共用主世界那套。 */
    private static int scatter(World world, Random random, int chunkX, int chunkZ,
                                Block ore, int minY, int maxY, Block host) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        // 高度区间两端都含。配错（max < min）时退化到 min 那一层，不抛异常。
        if(maxY < minY) maxY = minY;

        int veinsMin = ModConfig.leadzincVeinsPerChunkMin;
        int veinsMax = ModConfig.leadzincVeinsPerChunkMax;
        if(veinsMax < veinsMin) veinsMax = veinsMin;
        int veins = veinsMin + random.nextInt(veinsMax - veinsMin + 1);

        int attemptsMin = ModConfig.leadzincClusterMin;
        int attemptsMax = ModConfig.leadzincClusterMax;
        if(attemptsMax < attemptsMin) attemptsMax = attemptsMin;

        int radius = ModConfig.leadzincVeinRadius;
        int placed = 0;

        for(int i = 0; i < veins; i++) {

            int cx = x0 + random.nextInt(16);
            int cz = z0 + random.nextInt(16);
            int cy = minY + random.nextInt(maxY - minY + 1);

            int attempts = attemptsMin + random.nextInt(attemptsMax - attemptsMin + 1);

            // 越界的点由 placer 按 [minY, maxY] 丢弃，矿囊整体不会溢出配置区间
            placed += OreVeinPlacer.place(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY, host);
        }

        return placed;
    }
}

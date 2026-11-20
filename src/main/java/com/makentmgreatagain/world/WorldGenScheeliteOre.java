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
 * 白钨矿的世界生成（主世界 + Duna）。
 *
 * <h3>实心大矿囊</h3>
 * <p>白钨矿不做撒点，走 {@link OreVeinPlacer#placeFilled}：半径内的石头整片换成矿石，
 * 出来的是一大片连着的矿体。半径 6 的一条矿脉球内 925 格，挖进去就是一整块矿。</p>
 *
 * <p>密度仍然由「每区块触发概率」控制（默认 8%，平均 12~13 区块一条），
 * 所以整片矿虽然大，出现频率很低，不会把地下挖成筛子。</p>
 *
 * <h3>Duna 分支</h3>
 * <p>生成变体方块 {@code ore_scheelite_duna}，宿主岩为 {@code hbm} 的 duna_rock。
 * 概率与高度走 {@code dunaScheelite*} 独立配置（默认下限 14，避开 Duna 的玄武岩层），
 * 矿囊半径沿用主世界的 {@code scheeliteVeinRadius}。
 * 维度识别与诊断计数同 {@link WorldGenLeadZincOre}。</p>
 */
public class WorldGenScheeliteOre implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        int dim = world.provider.dimensionId;

        if(dim == 0) {
            if(!ModConfig.scheeliteWorldgenEnabled) return;
            if(random.nextInt(100) >= ModConfig.scheeliteChunkChancePercent) return;
            fill(world, random, chunkX, chunkZ, ModBlocks.ore_scheelite,
                    ModConfig.scheeliteMinY, ModConfig.scheeliteMaxY, Blocks.stone);

        } else if(DunaHost.isDuna(world, ModConfig.dunaDimensionId)) {
            if(!ModConfig.dunaScheeliteEnabled) return;
            Block host = DunaHost.rock(world);
            int placed = 0;
            if(host != null && random.nextInt(100) < ModConfig.dunaScheeliteChunkChancePercent) {
                placed = fill(world, random, chunkX, chunkZ, ModBlocks.ore_scheelite_duna,
                        ModConfig.dunaScheeliteMinY, ModConfig.dunaScheeliteMaxY, host);
            }
            DunaDiag.tick("scheelite", placed, host);
        }
    }

    private static int fill(World world, Random random, int chunkX, int chunkZ,
                             Block ore, int minY, int maxY, Block host) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        // 配错（max < min）时退化到 min 那一层，不抛异常。
        if(maxY < minY) maxY = minY;

        int radius = ModConfig.scheeliteVeinRadius;
        if(radius < 1) radius = 1;

        // 中心高度收进 [minY + radius, maxY - radius]，让球体完整落下。
        // 下半球会被 placer 整片丢弃，矿脉凭空缩水一半。
        int centerMinY = minY + radius;
        int centerMaxY = maxY - radius;
        if(centerMaxY < centerMinY) {
            // 区间比球还窄：退化成在区间内随便放，接受裁切
            centerMinY = minY;
            centerMaxY = maxY;
        }

        int veinsMin = ModConfig.scheeliteVeinsPerChunkMin;
        int veinsMax = ModConfig.scheeliteVeinsPerChunkMax;
        if(veinsMax < veinsMin) veinsMax = veinsMin;
        int veins = veinsMin + random.nextInt(veinsMax - veinsMin + 1);

        int placed = 0;

        for(int i = 0; i < veins; i++) {

            int cx = x0 + random.nextInt(16);
            int cz = z0 + random.nextInt(16);
            int cy = centerMinY + random.nextInt(centerMaxY - centerMinY + 1);

            // 越界的点由 placer 按 [minY, maxY] 丢弃
            placed += OreVeinPlacer.placeFilled(world, random, cx, cy, cz, radius, ore, minY, maxY, host);
        }

        return placed;
    }
}

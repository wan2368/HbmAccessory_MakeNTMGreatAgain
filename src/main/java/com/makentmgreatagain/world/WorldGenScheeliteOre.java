package com.makentmgreatagain.world;

import java.util.Random;

import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 白钨矿的世界生成（只主世界）。
 */
public class WorldGenScheeliteOre implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        if(!ModConfig.scheeliteWorldgenEnabled) return;
        if(world.provider.dimensionId != 0) return;                       // 只主世界
        if(random.nextInt(100) >= ModConfig.scheeliteChunkChancePercent) return;

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        // 配错（max < min）时退化到 min 那一层，不抛异常。
        int minY = ModConfig.scheeliteMinY;
        int maxY = ModConfig.scheeliteMaxY;
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

        int attemptsMin = ModConfig.scheeliteClusterMin;
        int attemptsMax = ModConfig.scheeliteClusterMax;
        if(attemptsMax < attemptsMin) attemptsMax = attemptsMin;

        Block ore = ModBlocks.ore_scheelite;

        for(int i = 0; i < veins; i++) {

            int cx = x0 + random.nextInt(16);
            int cz = z0 + random.nextInt(16);
            int cy = centerMinY + random.nextInt(centerMaxY - centerMinY + 1);

            int attempts = attemptsMin + random.nextInt(attemptsMax - attemptsMin + 1);

            // 越界的点由 placer 按 [minY, maxY] 丢弃
            OreVeinPlacer.place(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY);
        }
    }
}

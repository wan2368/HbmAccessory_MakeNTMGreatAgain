package com.makentmgreatagain.world;

import java.util.Random;

import com.makentmgreatagain.config.ModConfig;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 含砷金矿的世界生成（只主世界）。
 *
 * <p>定位和铅锌矿不同：铅锌矿是玩家专门去找的目标矿，含砷金矿是「挖矿顺手撞见、
 * 当垃圾扔掉、等湿法线建起来再回头翻」的料。所以它<b>生成得浅</b>（y 15~40，比铅锌矿低 5 层）、
 * 密度比铅锌矿<b>低三成左右</b>，但绝对数量仍然够建产线 ——
 * 一个区块约落 <b>9~11 格</b>，4 格矿出 4 锭金，也就是每区块约合 10 锭。</p>
 *
 * <p>调参在 config/MNGA/worldgen.cfg 的 {@code worldgen} 段，改完重启即对<b>新生成的区块</b>生效。</p>
 */
public class WorldGenRefractoryGoldOre implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        if(!ModConfig.refractoryGoldWorldgenEnabled) return;
        if(world.provider.dimensionId != 0) return;                       // 只主世界
        if(random.nextInt(100) >= ModConfig.refractoryGoldChunkChancePercent) return;

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        int minY = ModConfig.refractoryGoldMinY;
        int maxY = ModConfig.refractoryGoldMaxY;
        if(maxY < minY) maxY = minY;

        int veinsMin = ModConfig.refractoryGoldVeinsPerChunkMin;
        int veinsMax = ModConfig.refractoryGoldVeinsPerChunkMax;
        if(veinsMax < veinsMin) veinsMax = veinsMin;
        int veins = veinsMin + random.nextInt(veinsMax - veinsMin + 1);

        int attemptsMin = ModConfig.refractoryGoldClusterMin;
        int attemptsMax = ModConfig.refractoryGoldClusterMax;
        if(attemptsMax < attemptsMin) attemptsMax = attemptsMin;

        int radius = ModConfig.refractoryGoldVeinRadius;
        Block ore = ModBlocks.ore_refractory_gold;

        for(int i = 0; i < veins; i++) {

            int cx = x0 + random.nextInt(16);
            int cz = z0 + random.nextInt(16);
            int cy = minY + random.nextInt(maxY - minY + 1);

            int attempts = attemptsMin + random.nextInt(attemptsMax - attemptsMin + 1);

            OreVeinPlacer.place(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY);
        }
    }
}

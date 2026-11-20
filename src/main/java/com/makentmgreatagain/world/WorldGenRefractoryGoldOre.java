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
 * 含砷金矿的世界生成（主世界 + Duna）。
 *
 * <p>定位和铅锌矿不同：铅锌矿是玩家专门去找的目标矿，含砷金矿是「挖矿顺手撞见、
 * 当垃圾扔掉、等湿法线建起来再回头翻」的料。所以它<b>生成得浅</b>（y 15~40，比铅锌矿低 5 层）、
 * 密度比铅锌矿<b>低三成左右</b>，但绝对数量仍然够建产线 ——
 * 一个区块约落 <b>9~11 格</b>，4 格矿出 4 锭金，也就是每区块约合 10 锭。</p>
 *
 * <p>调参在 config/MNGA/worldgen.cfg 的 {@code worldgen} 段，改完重启即对<b>新生成的区块</b>生效。</p>
 *
 * <h3>Duna 分支</h3>
 * <p>生成变体方块 {@code ore_refractory_gold_duna}，宿主岩为 {@code hbm} 的 duna_rock；
 * 概率与高度走 {@code dunaRefractoryGold*} 独立配置，矿囊大小沿用主世界参数。
 * 维度识别与诊断计数同 {@link WorldGenLeadZincOre}。</p>
 */
public class WorldGenRefractoryGoldOre implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

        int dim = world.provider.dimensionId;

        if(dim == 0) {
            if(!ModConfig.refractoryGoldWorldgenEnabled) return;
            if(random.nextInt(100) >= ModConfig.refractoryGoldChunkChancePercent) return;
            scatter(world, random, chunkX, chunkZ, ModBlocks.ore_refractory_gold,
                    ModConfig.refractoryGoldMinY, ModConfig.refractoryGoldMaxY, Blocks.stone);

        } else if(DunaHost.isDuna(world, ModConfig.dunaDimensionId)) {
            if(!ModConfig.dunaRefractoryGoldEnabled) return;
            Block host = DunaHost.rock(world);
            int placed = 0;
            if(host != null && random.nextInt(100) < ModConfig.dunaRefractoryGoldChunkChancePercent) {
                placed = scatter(world, random, chunkX, chunkZ, ModBlocks.ore_refractory_gold_duna,
                        ModConfig.dunaRefractoryGoldMinY, ModConfig.dunaRefractoryGoldMaxY, host);
            }
            DunaDiag.tick("refractoryGold", placed, host);
        }
    }

    private static int scatter(World world, Random random, int chunkX, int chunkZ,
                                Block ore, int minY, int maxY, Block host) {

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;

        if(maxY < minY) maxY = minY;

        int veinsMin = ModConfig.refractoryGoldVeinsPerChunkMin;
        int veinsMax = ModConfig.refractoryGoldVeinsPerChunkMax;
        if(veinsMax < veinsMin) veinsMax = veinsMin;
        int veins = veinsMin + random.nextInt(veinsMax - veinsMin + 1);

        int attemptsMin = ModConfig.refractoryGoldClusterMin;
        int attemptsMax = ModConfig.refractoryGoldClusterMax;
        if(attemptsMax < attemptsMin) attemptsMax = attemptsMin;

        int radius = ModConfig.refractoryGoldVeinRadius;
        int placed = 0;

        for(int i = 0; i < veins; i++) {

            int cx = x0 + random.nextInt(16);
            int cz = z0 + random.nextInt(16);
            int cy = minY + random.nextInt(maxY - minY + 1);

            int attempts = attemptsMin + random.nextInt(attemptsMax - attemptsMin + 1);

            placed += OreVeinPlacer.place(world, random, cx, cy, cz, radius, attempts, ore, minY, maxY, host);
        }

        return placed;
    }
}

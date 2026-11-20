package com.makentmgreatagain.world;

import java.util.Random;

import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 地核熔岩裂隙的世界生成（仅下界，平均 100 区块一片）。
 *
 * <h3>结构</h3>
 * <ul>
 *   <li><b>裂隙本体</b>：y=0 的 3×3，替换下界底床岩（可替换性判定与本体基岩矿一致）；</li>
 *   <li><b>盖层</b>：裂隙正上方 3 层深岩（{@code stone_depth}）。下界 y=1~3 多是地狱岩，
 *       走本体那套可替换判定换不掉，盖层会残缺，所以这 3×3×3 直接强制铺；</li>
 *   <li><b>岩丘</b>：与本体基岩矿同效果 —— 7×7、y=1~6 范围内把可替换的石头/基岩换成深岩，
 *       形成基岩层上鼓起的深岩堆，挖到深处一眼能认出来。</li>
 * </ul>
 *
 * <p>生成位置在区块内留 3 格边距，保证 7×7 岩丘不跨区块（跨区块写未加载区块会丢方块）。</p>
 */
public class WorldGenCoreFissure implements IWorldGenerator {

	/** 触发概率的分母：每区块 1/100 */
	public static final int CHUNK_CHANCE = 100;

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

		if(world.provider.dimensionId != -1) return;
		if(random.nextInt(CHUNK_CHANCE) != 0) return;

		int x = chunkX * 16 + 3 + random.nextInt(10);
		int z = chunkZ * 16 + 3 + random.nextInt(10);
		generateAt(world, x, z);
	}

	public static void generateAt(World world, int x, int z) {

		// 裂隙本体：y=0 的 3×3 床岩
		for(int ix = x - 1; ix <= x + 1; ix++) {
			for(int iz = z - 1; iz <= z + 1; iz++) {
				if(world.getBlock(ix, 0, iz).isReplaceableOreGen(world, ix, 0, iz, Blocks.bedrock))
					world.setBlock(ix, 0, iz, ModBlocks.core_fissure);
			}
		}

		// 岩丘：照本体 BedrockOre.generate 的覆岩逻辑
		for(int ix = x - 3; ix <= x + 3; ix++) {
			for(int iz = z - 3; iz <= z + 3; iz++) {
				for(int iy = 1; iy < 7; iy++) {
					if(iy >= 3 && world.getBlock(ix, iy, iz) != Blocks.bedrock) continue;

					Block b = world.getBlock(ix, iy, iz);
					if(b.isReplaceableOreGen(world, ix, iy, iz, Blocks.stone)
							|| b.isReplaceableOreGen(world, ix, iy, iz, Blocks.bedrock))
						world.setBlock(ix, iy, iz, com.hbm.blocks.ModBlocks.stone_depth);
				}
			}
		}

		// 盖层：裂隙正上方 3 层深岩，强制铺（地狱岩不在可替换名单里）
		for(int ix = x - 1; ix <= x + 1; ix++) {
			for(int iz = z - 1; iz <= z + 1; iz++) {
				for(int iy = 1; iy <= 3; iy++) {
					world.setBlock(ix, iy, iz, com.hbm.blocks.ModBlocks.stone_depth);
				}
			}
		}
	}
}

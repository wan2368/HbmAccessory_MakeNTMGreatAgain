package com.makentmgreatagain.world;

import java.util.Random;

import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;

/**
 * 稳定X晶体簇的末地生成。
 *
 * <h3>生成规则</h3>
 * <ul>
 *   <li>只在末地（{@code dimensionId == 1} 且 {@link WorldProviderEnd}）。</li>
 *   <li><b>全世界只有 {@link #LIMIT} 个</b>，不是每区块几个：计数存在末地维度的
 *       {@link MapStorage} 里，重启后继续累加，删档才会归零。</li>
 *   <li>每个区块先按 {@link #CHANCE} 掷一次，中了才找位置；概率的作用是让这 8 个
 *       散得开一点，而不是挤在出生岛的头几个区块里。</li>
 *   <li>根部必须接触<b>末地石</b>：从 y=254 往下找当前列最高的非空气方块，它必须是
 *       {@code end_stone}，否则这一列作废（黑曜石柱顶、紫珀结构都自动排除）。</li>
 *   <li>只放 1 格高，要求该格与上方一格都是空气，避免卡在方块里。</li>
 * </ul>
 *
 * <h3>为什么用独立 Random</h3>
 * <p>FML 给每个区块的 {@code Random} 是所有 {@link IWorldGenerator} 串着用的，
 * 谁在前面多抽几次，后面的结果就全变。这里用「世界种子 + 区块坐标」自建 Random，
 * 保证同一个存档里这 8 个点的位置是确定的，也不受本模组其他矿脉生成的影响。</p>
 *
 * <h3>为什么写在区块内</h3>
 * <p>坐标严格限制在 {@code chunkX*16 ~ +15}、{@code chunkZ*16 ~ +15}，不跨区块写方块，
 * 也就不会在世界生成阶段触发连锁加载。</p>
 */
public class WorldGenXenCrystal implements IWorldGenerator {

	/** 整个末地的总量上限。 */
	public static final int LIMIT = 8;
	/** 每个区块尝试生成的概率：压低到 6%，让 8 个点铺开到外岛，而不是全挤在出生岛。 */
	public static final float CHANCE = 0.06F;
	/** 扫描顶端时的起始高度。 */
	private static final int SCAN_TOP = 254;
	/** 末地最低有效高度，再往下是虚空。 */
	private static final int SCAN_BOTTOM = 2;

	/** 计数在 MapStorage 里的键。 */
	private static final String DATA_KEY = "makentmgreatagain_xen_crystal";

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {

		if(world.isRemote) return;
		if(world.provider.dimensionId != 1) return;
		if(!(world.provider instanceof WorldProviderEnd)) return;

		XenCrystalCounter counter = getCounter(world);
		if(counter.placed >= LIMIT) return;

		// 自建 Random：同种子同坐标的结果固定，且不吃别的 generator 的随机数
		Random rnd = new Random(world.getSeed()
			+ chunkX * 341873128712L + chunkZ * 132897987541L);

		if(rnd.nextFloat() >= CHANCE) return;

		int x = chunkX * 16 + rnd.nextInt(16);
		int z = chunkZ * 16 + rnd.nextInt(16);

		int y = findTopEndStoneAir(world, x, z);
		if(y < 0) return;

		if(!world.isAirBlock(x, y, z)) return;
		if(!world.isAirBlock(x, y + 1, z)) return;

		world.setBlock(x, y, z, ModBlocks.xen_crystal_cluster, 0, 2);

		counter.placed++;
		counter.markDirty();
	}

	/**
	 * 从顶端往下找当前列最高的非空气方块。
	 *
	 * @return 该方块是末地石时返回它上方一格的 y，否则 -1
	 */
	private static int findTopEndStoneAir(World world, int x, int z) {
		for(int y = SCAN_TOP; y >= SCAN_BOTTOM; y--) {
			Block block = world.getBlock(x, y, z);
			if(block.isAir(world, x, y, z)) continue;
			return block == Blocks.end_stone ? y + 1 : -1;
		}
		return -1;
	}

	private static XenCrystalCounter getCounter(World world) {
		MapStorage storage = world.mapStorage;

		XenCrystalCounter data = (XenCrystalCounter) storage.loadData(
			XenCrystalCounter.class, DATA_KEY);

		if(data == null) {
			data = new XenCrystalCounter(DATA_KEY);
			storage.setData(DATA_KEY, data);
		}
		return data;
	}

	/**
	 * 末地的晶体簇计数。存在末地维度的 MapStorage 里，随存档一起落盘。
	 */
	public static class XenCrystalCounter extends WorldSavedData {

		public int placed;

		public XenCrystalCounter(String name) {
			super(name);
		}

		@Override
		public void readFromNBT(NBTTagCompound nbt) {
			this.placed = nbt.getInteger("placed");
		}

		@Override
		public void writeToNBT(NBTTagCompound nbt) {
			nbt.setInteger("placed", this.placed);
		}
	}
}

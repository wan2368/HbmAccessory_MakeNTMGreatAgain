package com.makentmgreatagain.blocks;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;

/**
 * 地核熔岩裂隙：下界基岩层 3×3 成片生成，地核熔岩开采机的开采对象。
 *
 * <p>性质照基岩矿：生存不可破坏（硬度 -1）、无掉落；
 * 熔岩裂纹自身发光（亮度 0.5），挖穿深岩盖层时能看见微光。</p>
 */
public class BlockCoreFissure extends Block {

	public BlockCoreFissure() {
		super(Material.rock);
		this.setBlockUnbreakable();
		this.setResistance(6000000.0F);
		this.setLightLevel(0.5F);
		this.setStepSound(Block.soundTypeStone);
	}

	@Override
	public Item getItemDropped(int meta, Random rand, int fortune) {
		return null;
	}

	@Override
	public int quantityDropped(Random rand) {
		return 0;
	}
}

package com.makentmgreatagain.blocks;

import com.makentmgreatagain.compat.HBMVersionCompat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * 金属储存块（9 锭 = 1 块）。
 * 仅是装饰性储存方块，不带 TileEntity，音效沿用 HBM 的金属板音效。
 */
public class BlockStorageMetal extends Block {

	public BlockStorageMetal() {
		super(Material.iron);
		setStepSound(HBMVersionCompat.plateMetalSound());
		setHardness(5.0F);
		setResistance(10.0F);
	}
}

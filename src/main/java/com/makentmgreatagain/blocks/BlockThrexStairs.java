package com.makentmgreatagain.blocks;

import com.makentmgreatagain.compat.HBMVersionCompat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;

/**
 * Threx 楼梯（复用 Threx 屏蔽砖材质与高抗爆属性）。
 */
public class BlockThrexStairs extends BlockStairs {

	public BlockThrexStairs(Block base) {
		super(base, 0);
		setStepSound(HBMVersionCompat.plateMetalSound());
		setHardness(50.0F);
		setResistance(100000.0F);
		this.useNeighborBrightness = true;
	}
}

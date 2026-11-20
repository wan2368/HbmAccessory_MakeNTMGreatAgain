package com.makentmgreatagain.blocks;

import com.makentmgreatagain.compat.HBMVersionCompat;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Threx 屏蔽砖：以 Threx 块为芯材压制成型的高产量防护建材。
 * 抗爆与辐射阻力低于实心 Threx，高于普通加固 CMB 建材。
 */
public class BlockThrexShieldBrick extends Block {

	public BlockThrexShieldBrick() {
		super(Material.rock);
		setStepSound(HBMVersionCompat.plateMetalSound());
		setHardness(50.0F);
		setResistance(100000.0F);
	}
}

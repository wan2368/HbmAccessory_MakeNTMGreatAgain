package com.makentmgreatagain.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;

/**
 * 含盐红沙：与原版红沙同属性——受重力下落、沙子音效。
 */
public class BlockSaltContainRedSand extends BlockFalling {

	public BlockSaltContainRedSand() {
		super(Material.sand);
		((Block) this).stepSound = Block.soundTypeSand;      // 原版红沙同样是沙子声。显式转 Block：改名工具不追多层继承
	}
}

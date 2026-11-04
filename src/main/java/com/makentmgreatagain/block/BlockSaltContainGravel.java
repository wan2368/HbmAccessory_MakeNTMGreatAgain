package com.makentmgreatagain.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;

/**
 * 含盐沙砾：与原版砾石同属性——受重力下落、砾石音效。
 */
public class BlockSaltContainGravel extends BlockFalling {

	public BlockSaltContainGravel() {
		super(Material.sand);
		((Block) this).stepSound = Block.soundTypeGravel;    // 原版砾石/沙子都是 Material.sand，靠 stepSound 区分。显式转 Block：改名工具不追多层继承
	}
}

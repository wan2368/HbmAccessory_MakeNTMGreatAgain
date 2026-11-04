package com.makentmgreatagain.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;

/**
 * 含盐沙子：与原版沙子同属性——受重力（悬空自动下落）、沙子的踩踏/放置音效。
 *
 * 继承 BlockFalling 即可拿到下落逻辑（原版 BlockSand 就是这么做的）；
 * 音效由 setStepSound 显式指定，和原版 Block 构造里按 Material.sand 选的一致。
 */
public class BlockSaltContainSand extends BlockFalling {

	public BlockSaltContainSand() {
		super(Material.sand);
		((Block) this).stepSound = Block.soundTypeSand;      // 走路/放置 = 沙子声（与原版沙子一致）。显式转 Block：改名工具不追多层继承
	}
}

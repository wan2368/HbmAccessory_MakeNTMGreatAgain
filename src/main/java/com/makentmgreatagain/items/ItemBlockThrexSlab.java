package com.makentmgreatagain.items;

import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.item.ItemSlab;

/**
 * Threx 台阶物品（绑定单层与双层 {@link com.makentmgreatagain.blocks.BlockThrexSlab}）。
 */
public class ItemBlockThrexSlab extends ItemSlab {

	public ItemBlockThrexSlab(Block block) {
		super(block,
				(BlockSlab) ModBlocks.threx_slab,
				(BlockSlab) ModBlocks.threx_double_slab,
				block == ModBlocks.threx_double_slab);
	}
}

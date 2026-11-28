package com.makentmgreatagain.blocks;

import java.util.Random;

import com.makentmgreatagain.compat.HBMVersionCompat;
import com.makentmgreatagain.init.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Threx 台阶（单层 / 双层共用，复用 Threx 屏蔽砖材质与高抗爆属性）。
 */
public class BlockThrexSlab extends BlockSlab {

	public BlockThrexSlab(boolean isDouble) {
		super(isDouble, Material.rock);
		setStepSound(HBMVersionCompat.plateMetalSound());
		setHardness(50.0F);
		setResistance(100000.0F);
		this.useNeighborBrightness = !isDouble;
	}

	@Override
	public Item getItemDropped(int meta, Random rand, int fortune) {
		return Item.getItemFromBlock(ModBlocks.threx_slab);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Item getItem(World world, int x, int y, int z) {
		return Item.getItemFromBlock(ModBlocks.threx_slab);
	}

	@Override
	protected ItemStack createStackedBlock(int meta) {
		return new ItemStack(Item.getItemFromBlock(ModBlocks.threx_slab), 2, 0);
	}

	@Override
	public String func_150002_b(int meta) {
		return getUnlocalizedName();
	}
}

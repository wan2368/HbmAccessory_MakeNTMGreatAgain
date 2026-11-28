package com.makentmgreatagain.blocks;

import com.hbm.blocks.ITooltipProvider;
import com.makentmgreatagain.tileentity.TileEntityDeliveryRelay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

/**
 * 投送中继节点方块：支持 6 方向贴面摆放，自动组网接力激光，单跳 32 格、多跳叠加无上限距离。
 */
public class BlockDeliveryRelay extends BlockContainer implements ITooltipProvider {

	public BlockDeliveryRelay() {
		super(Material.iron);
		this.setHardness(3.0F);
		this.setResistance(8.0F);
		this.setStepSound(Block.soundTypeMetal);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityDeliveryRelay();
	}

	@Override public int getRenderType() { return -1; }
	@Override public boolean isOpaqueCube() { return false; }
	@Override public boolean renderAsNormalBlock() { return false; }

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
		return side % 6;
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		ForgeDirection f = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z) % 6);
		float lo = 0.25F, hi = 0.75F, h = 0.96F;
		switch(f) {
			case UP:    this.setBlockBounds(lo, 0.0F, lo, hi, h, hi); break;
			case DOWN:  this.setBlockBounds(lo, 1.0F - h, lo, hi, 1.0F, hi); break;
			case SOUTH: this.setBlockBounds(lo, lo, 0.0F, hi, hi, h); break;
			case NORTH: this.setBlockBounds(lo, lo, 1.0F - h, hi, hi, 1.0F); break;
			case EAST:  this.setBlockBounds(0.0F, lo, lo, h, hi, hi); break;
			case WEST:  this.setBlockBounds(1.0F - h, lo, lo, 1.0F, hi, hi); break;
			default:    this.setBlockBounds(lo, 0.0F, lo, hi, h, hi); break;
		}
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(StatCollector.translateToLocal("mnga.relay.tooltip.1"));
		list.add(StatCollector.translateToLocal("mnga.relay.tooltip.2"));
	}
}

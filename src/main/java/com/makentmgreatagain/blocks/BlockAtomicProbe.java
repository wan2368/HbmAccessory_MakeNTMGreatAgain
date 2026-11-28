package com.makentmgreatagain.blocks;

import com.hbm.blocks.ITooltipProvider;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.items.ItemAtomicLinker;
import com.makentmgreatagain.tileentity.TileEntityAtomicProbe;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

/**
 * 原子投送探头方块：6 方向贴面挂载在箱子或机器接口上，无内部物品缓存。
 */
public class BlockAtomicProbe extends BlockContainer implements ITooltipProvider {

	public BlockAtomicProbe() {
		super(Material.iron);
		this.setHardness(3.0F);
		this.setResistance(8.0F);
		this.setStepSound(Block.soundTypeMetal);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityAtomicProbe();
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
		float lo = 0.18F, hi = 0.82F, h = 0.68F;
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
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		ItemStack held = player.getHeldItem();
		if(held != null && held.getItem() instanceof ItemAtomicLinker) {
			return false;
		}
		if(world.isRemote) return true;

		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityAtomicProbe)) return false;
		TileEntityAtomicProbe probe = (TileEntityAtomicProbe) te;

		// 潜行空手右键：快捷切换 发送端 / 接收端
		if(player.isSneaking() && held == null) {
			probe.isReceiver = !probe.isReceiver;
			probe.hasActiveSender = false;
			probe.hasActiveTarget = false;
			probe.lockHoldTicks = 0;
			probe.fireTicks = 0;
			probe.state = probe.isReceiver ? TileEntityAtomicProbe.STATE_IDLE
					: (probe.targets.isEmpty() ? TileEntityAtomicProbe.STATE_UNBOUND : TileEntityAtomicProbe.STATE_IDLE);
			probe.markDirty();
			probe.syncState();
			player.addChatMessage(new ChatComponentTranslation(
					probe.isReceiver ? "mnga.probe.chat.mode_recv" : "mnga.probe.chat.mode_send"));
			return true;
		}

		player.openGui(MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_ATOMIC_PROBE, world, x, y, z);
		return true;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(StatCollector.translateToLocal("mnga.probe.tooltip.1"));
		list.add(StatCollector.translateToLocal("mnga.probe.tooltip.2"));
		list.add(StatCollector.translateToLocal("mnga.probe.tooltip.3"));
	}
}

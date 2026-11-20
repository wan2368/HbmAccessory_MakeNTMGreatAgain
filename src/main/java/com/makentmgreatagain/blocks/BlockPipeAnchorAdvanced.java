package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.network.FluidPipeAnchor;
import com.makentmgreatagain.tileentity.TileEntityPipeAnchorAdvanced;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * 高级管锚（25m）。
 *
 * <p>除了贴图和连接距离之外，所有行为都直接继承本体的 {@link FluidPipeAnchor}：
 * 同样的碰撞箱、朝向、流体识别码交互、扳手连线逻辑、单管连接类型。</p>
 *
 * <p>连接距离由 {@link TileEntityPipeAnchorAdvanced#getMaxPipeLength()} 提供。
 * 本体的 {@code TileEntityPipelineBase.canConnect} 取两端距离的较小值，
 * 所以高级锚和普通锚混连时仍然是 10m，两个高级锚相连才是 25m。</p>
 */
public class BlockPipeAnchorAdvanced extends FluidPipeAnchor {

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityPipeAnchorAdvanced();
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(EnumChatFormatting.GOLD + "Connection Type: " + EnumChatFormatting.YELLOW + "Single");
		list.add(EnumChatFormatting.GOLD + "Connection Range: " + EnumChatFormatting.YELLOW + "25m");
	}
}

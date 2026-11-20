package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 电池组摆放阶段的外壳 / 端口零件方块（普通方块，无 TE）。
 *
 * <p>成型时被替换成 {@link BlockBatteryArrayShell}，原方块记在壳的 TE 里供拆解还原。
 * 端口与外壳的区别只在贴图与成型后的 meta（端口=1，外壳=0）。</p>
 */
public class BlockBatteryArrayPart extends Block implements ITooltipProvider {

	public final boolean isPort;

	public BlockBatteryArrayPart(boolean isPort) {
		super(Material.iron);
		this.isPort = isPort;
		this.setHardness(5.0F);
		this.setResistance(10.0F);
		this.setCreativeTab(com.makentmgreatagain.ModCreativeTabs.TAB_MACHINES);
		this.setBlockTextureName("makentmgreatagain:" + (isPort ? "battery_array_port" : "battery_array_casing"));
	}

	/**
	 * 端口必须写在提示里：<b>不能放在棱或角上</b>。
	 *
	 * <p>棱/角同时属于两三个面，朝外方向不唯一，线缆会接错面导致错位；
	 * 成型时 {@link BatteryArrayShape} 也会硬拦这种情况。</p>
	 */
	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		if(isPort) {
			list.add(EnumChatFormatting.GOLD + "电力接口：成型后接电缆充放电。");
			list.add(EnumChatFormatting.RED + "不能放在棱或角上！");
			list.add(EnumChatFormatting.GRAY + "请嵌在某个面的中央区域，");
			list.add(EnumChatFormatting.GRAY + "否则成型会失败、线缆也会接错面。");
		}
	}
}
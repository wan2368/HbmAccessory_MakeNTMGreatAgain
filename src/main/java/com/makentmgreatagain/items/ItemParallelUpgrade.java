package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.machine.TileEntityMachineCompressorBase;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;
import com.hbm.tileentity.machine.TileEntityMachineElectricFurnace;
import com.hbm.tileentity.machine.TileEntityMachineMixer;
import com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor;
import com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier;
import com.hbm.util.i18n.I18nUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;

/**
 * 一级 / 二级 / 三级并行芯片（分别提供 +1x / +2x / +3x 并行，同机可叠加至最高 4x）。
 */
public class ItemParallelUpgrade extends ItemMachineUpgrade {

	public final int parallelTier;

	public ItemParallelUpgrade(int tier) {
		super(UpgradeType.SPECIAL, tier);
		this.parallelTier = tier;
		this.setMaxStackSize(1);
	}

	private static boolean isSupportedMachine(Object inv) {
		return inv instanceof TileEntityMachineCrystallizer
				|| inv instanceof TileEntityMachineMixer
				|| inv instanceof TileEntityMachineCompressorBase
				|| inv instanceof TileEntityMachineLiquefactor
				|| inv instanceof TileEntityMachineSolidifier
				|| inv instanceof TileEntityMachineElectricFurnace;
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean bool) {
		GuiScreen open = Minecraft.getMinecraft().currentScreen;
		if(!(open instanceof GuiContainer)) return;

		Container container = ((GuiContainer) open).inventorySlots;
		if(container == null || container.inventorySlots.isEmpty()) return;

		Slot first = container.getSlot(0);
		IInventory inv = first != null ? first.inventory : null;
		if(!(inv instanceof TileEntity) || !isSupportedMachine(inv)) return;

		int maxParallel = 1 + this.parallelTier;
		int consPct = (int) Math.round((maxParallel * 1.25D - 1.0D) * 100.0D);
		TileEntity te = (TileEntity) inv;
		if(te.getBlockType() != null) {
			list.add(IUpgradeInfoProvider.getStandardLabel(te.getBlockType()));
		}
		list.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("mnga.upgrade.parallel.info", maxParallel));
		list.add(EnumChatFormatting.RED + I18nUtil.resolveKey(IUpgradeInfoProvider.KEY_CONSUMPTION, "+" + consPct + "%"));
	}
}

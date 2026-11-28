package com.makentmgreatagain.inventory;

import com.makentmgreatagain.tileentity.TileEntityAtomicProbe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 投送探头容器（严格对齐 176×196 gui_atomic_probe.png）。
 */
public class ContainerAtomicProbe extends Container {

	private final TileEntityAtomicProbe probe;

	public ContainerAtomicProbe(InventoryPlayer invPlayer, TileEntityAtomicProbe probe) {
		this.probe = probe;

		// 0..26: 玩家主背包
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 9; col++) {
				this.addSlotToContainer(new Slot(invPlayer, col + row * 9 + 9, 8 + col * 18, 113 + row * 18));
			}
		}

		// 27..35: 玩家快捷栏
		for(int col = 0; col < 9; col++) {
			this.addSlotToContainer(new Slot(invPlayer, col, 8 + col * 18, 171));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return probe.getWorldObj().getTileEntity(probe.xCoord, probe.yCoord, probe.zCoord) == probe
				&& player.getDistanceSq(probe.xCoord + 0.5D, probe.yCoord + 0.5D, probe.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		return null;
	}
}

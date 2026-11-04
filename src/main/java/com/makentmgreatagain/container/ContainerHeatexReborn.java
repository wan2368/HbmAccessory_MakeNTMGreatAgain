package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 集成式换热加热器 GUI 容器。
 * 只有 0 号槽（流体识别码），用来切换换热回路的热流体，位置与原版换热器一致。
 */
public class ContainerHeatexReborn extends Container {

	private TileEntityHeatexReborn heatex;

	public ContainerHeatexReborn(InventoryPlayer invPlayer, TileEntityHeatexReborn te) {
		this.heatex = te;

		this.addSlotToContainer(new Slot(te, 0, 80, 72));

		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 122 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 180));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index == 0) {
				if(!this.mergeItemStack(stack, 1, this.inventorySlots.size(), true)) return null;
			} else {
				if(!this.mergeItemStack(stack, 0, 1, false)) return null;
			}

			if(stack.stackSize == 0) {
				slot.putStack(null);
			} else {
				slot.onSlotChanged();
			}
		}

		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return heatex.isUseableByPlayer(player);
	}
}

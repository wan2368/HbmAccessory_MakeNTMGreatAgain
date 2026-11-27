package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 流体传送器容器：6 个流体识别码槽（稳态启用 0~1，超频启用 0~5）+ 玩家背包。
 */
public class ContainerXenFluidTransceiver extends Container {

	public static final int PANEL_W = 248;
	public static final int PANEL_H = 242;

	public static final int COL_START_X = 11;
	public static final int COL_STEP_X = 38;
	public static final int ID_SLOT_Y = 66;

	public static final int INV_X = 44;
	public static final int INV_Y = 160;
	public static final int HOTBAR_Y = 218;

	private final TileEntityXenFluidTransceiver te;

	public ContainerXenFluidTransceiver(InventoryPlayer invPlayer, TileEntityXenFluidTransceiver te) {
		this.te = te;

		for(int i = 0; i < TileEntityXenFluidTransceiver.MAX_CHANNELS; i++) {
			int slotX = COL_START_X + i * COL_STEP_X + 10;
			this.addSlotToContainer(new SlotNonRetarded(te, i, slotX, ID_SLOT_Y));
		}

		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 9; c++) {
				this.addSlotToContainer(new Slot(invPlayer, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
			}
		}

		for(int c = 0; c < 9; c++) {
			this.addSlotToContainer(new Slot(invPlayer, c, INV_X + c * 18, HOTBAR_Y));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);
		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index < TileEntityXenFluidTransceiver.MAX_CHANNELS) {
				if(!this.mergeItemStack(stack, TileEntityXenFluidTransceiver.MAX_CHANNELS, this.inventorySlots.size(), true)) {
					return null;
				}
			} else if(stack.getItem() instanceof IItemFluidIdentifier) {
				int active = this.te.getActiveChannelCount();
				if(!this.mergeItemStack(stack, 0, active, false)) {
					return null;
				}
			} else {
				return null;
			}

			if(stack.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();
		}
		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return this.te.isUseableByPlayer(player);
	}
}

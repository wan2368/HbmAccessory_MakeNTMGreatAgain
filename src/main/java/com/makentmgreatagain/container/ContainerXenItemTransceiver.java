package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 物品传送器容器：左侧 3×3 发送缓冲（0~8）、右侧 3×3 接收缓冲（9~17）、下方玩家背包。
 */
public class ContainerXenItemTransceiver extends Container {

	public static final int PANEL_W = 216;
	public static final int PANEL_H = 236;

	public static final int SEND_X = 14;
	public static final int RECV_X = 148;
	public static final int GRID_Y = 74;

	public static final int INV_X = 28;
	public static final int INV_Y = 154;
	public static final int HOTBAR_Y = 212;

	private final TileEntityXenItemTransceiver te;

	public ContainerXenItemTransceiver(InventoryPlayer invPlayer, TileEntityXenItemTransceiver te) {
		this.te = te;

		// 0..8 发送缓冲
		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 3; c++) {
				this.addSlotToContainer(new SlotNonRetarded(te, r * 3 + c, SEND_X + c * 18, GRID_Y + r * 18));
			}
		}

		// 9..17 接收缓冲
		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 3; c++) {
				this.addSlotToContainer(new SlotNonRetarded(te, 9 + r * 3 + c, RECV_X + c * 18, GRID_Y + r * 18));
			}
		}

		// 玩家背包
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

			if(index < TileEntityXenItemTransceiver.TOTAL_SLOTS) {
				if(!this.mergeItemStack(stack, TileEntityXenItemTransceiver.TOTAL_SLOTS, this.inventorySlots.size(), true)) {
					return null;
				}
			} else {
				boolean moved = false;
				for(int s = 0; s < TileEntityXenItemTransceiver.SEND_SLOTS; s++) {
					if(!this.te.isItemValidForSlot(s, stack)) continue;
					if(this.mergeItemStack(stack, s, s + 1, false)) {
						moved = true;
						if(stack.stackSize <= 0) break;
					}
				}
				if(!moved) return null;
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

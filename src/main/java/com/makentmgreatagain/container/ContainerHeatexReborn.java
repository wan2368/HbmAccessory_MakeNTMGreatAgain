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

	/**
	 * 原版 mergeItemStack 不看 Slot#isItemValid，Shift 点击垃圾物品会被硬塞进识别码槽，
	 * 机器随后又拒收，物品就凭空消失了。补上校验：只有槽位允许的物品才放进去。
	 */
	@Override
	protected boolean mergeItemStack(ItemStack stack, int start, int end, boolean reverse) {

		boolean moved = false;
		int index = reverse ? end - 1 : start;

		// 先并入已有的同类堆
		if(stack.isStackable()) {
			while(stack.stackSize > 0 && (!reverse && index < end || reverse && index >= start)) {
				Slot slot = (Slot) this.inventorySlots.get(index);
				ItemStack existing = slot.getStack();

				if(existing != null && slot.isItemValid(stack)
						&& existing.getItem() == stack.getItem()
						&& (!stack.getHasSubtypes() || stack.getItemDamage() == existing.getItemDamage())
						&& ItemStack.areItemStackTagsEqual(stack, existing)) {

					int limit = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
					int total = existing.stackSize + stack.stackSize;

					if(total <= limit) {
						stack.stackSize = 0;
						existing.stackSize = total;
						slot.onSlotChanged();
						moved = true;
					} else if(existing.stackSize < limit) {
						stack.stackSize -= limit - existing.stackSize;
						existing.stackSize = limit;
						slot.onSlotChanged();
						moved = true;
					}
				}

				if(reverse) index--; else index++;
			}
		}

		// 剩下的放进允许它的空槽
		if(stack.stackSize > 0) {
			index = reverse ? end - 1 : start;

			while(!reverse && index < end || reverse && index >= start) {
				Slot slot = (Slot) this.inventorySlots.get(index);

				if(slot.getStack() == null && slot.isItemValid(stack)) {
					int limit = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());

					if(stack.stackSize > limit) {
						slot.putStack(stack.splitStack(limit));
					} else {
						slot.putStack(stack.copy());
						stack.stackSize = 0;
					}

					slot.onSlotChanged();
					moved = true;
					break;
				}

				if(reverse) index--; else index++;
			}
		}

		return moved;
	}
}

package com.makentmgreatagain.container;

import com.hbm.inventory.SlotTakeOnly;
import com.makentmgreatagain.tileentity.TileEntityElectricBoiler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 电锅炉容器（完整移植原版 ContainerMachineBoilerElectric 的槽位布局与 shift-click 路由）。
 * 0 输入装卸(8,17) 1 只取(8,53) 2 装罐(44,17) 3 只取(44,53)
 * 4 电池(98,53) 5 输出卸罐(152,17) 6 只取(152,53)；背包 y84、快捷栏 y142。
 */
public class ContainerElectricBoiler extends Container {

	private final TileEntityElectricBoiler boiler;

	public ContainerElectricBoiler(InventoryPlayer invPlayer, TileEntityElectricBoiler te) {
		this.boiler = te;

		this.addSlotToContainer(new Slot(te, 0, 8, 17));
		this.addSlotToContainer(new SlotTakeOnly(te, 1, 8, 53));
		this.addSlotToContainer(new Slot(te, 2, 44, 17));
		this.addSlotToContainer(new SlotTakeOnly(te, 3, 44, 53));
		this.addSlotToContainer(new Slot(te, 4, 98, 53));
		this.addSlotToContainer(new Slot(te, 5, 152, 17));
		this.addSlotToContainer(new SlotTakeOnly(te, 6, 152, 53));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));

		for(int i = 0; i < 9; i++)
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 142));
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack result = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();

			if(index <= 6) {
				if(!this.mergeItemStack(stack, 7, this.inventorySlots.size(), true)) return null;
			} else if(!this.mergeItemStack(stack, 4, 5, false)) {
				if(!this.mergeItemStack(stack, 2, 3, false)
						&& !this.mergeItemStack(stack, 5, 6, false)
						&& !this.mergeItemStack(stack, 0, 1, false)) return null;
			}

			if(stack.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();
		}

		return result;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return this.boiler.isUseableByPlayer(player);
	}

	/** 原版 mergeItemStack 不校验 isItemValid：shift 会把任意物品硬塞进电池/流体槽。
	 *  覆写补上槽位准入（与 TE.isItemValidForSlot / 容器专用槽同源），照地核开采机范式。 */
	@Override
	protected boolean mergeItemStack(ItemStack stack, int start, int end, boolean reverse) {
		boolean merged = false;
		int i = reverse ? end - 1 : start;

		// 先叠同类
		if(stack.isStackable()) {
			while(stack.stackSize > 0 && (reverse ? i >= start : i < end)) {
				Slot slot = (Slot) this.inventorySlots.get(i);
				ItemStack in = slot.getStack();
				if(in != null && in.getItem() == stack.getItem()
						&& (!stack.getHasSubtypes() || stack.getItemDamage() == in.getItemDamage())
						&& ItemStack.areItemStackTagsEqual(stack, in)
						&& slot.isItemValid(stack)) {
					int total = in.stackSize + stack.stackSize;
					int max = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
					if(total <= max) {
						stack.stackSize = 0;
						in.stackSize = total;
						slot.onSlotChanged();
						merged = true;
					} else if(in.stackSize < max) {
						stack.stackSize -= max - in.stackSize;
						in.stackSize = max;
						slot.onSlotChanged();
						merged = true;
					}
				}
				i += reverse ? -1 : 1;
			}
		}

		// 再找空格
		if(stack.stackSize > 0) {
			i = reverse ? end - 1 : start;
			while(reverse ? i >= start : i < end) {
				Slot slot = (Slot) this.inventorySlots.get(i);
				if(slot.getStack() == null && slot.isItemValid(stack)) {
					int put = Math.min(stack.stackSize, slot.getSlotStackLimit());
					ItemStack copy = stack.copy();
					copy.stackSize = put;
					slot.putStack(copy);
					slot.onSlotChanged();
					stack.stackSize -= put;
					merged = true;
					if(stack.stackSize <= 0) break;
				}
				i += reverse ? -1 : 1;
			}
		}

		return merged;
	}
}
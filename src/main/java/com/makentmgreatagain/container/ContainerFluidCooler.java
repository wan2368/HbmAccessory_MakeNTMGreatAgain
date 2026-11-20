package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityFluidCooler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 流体冷却机 GUI 容器。
 *
 * <p>两个识别码槽（0 = 冷却剂，1 = 被冷却流体），坐标与底图
 * {@code gui_advanced_thermostat.png} 上两个方格一致：(24,80)、(55,80)。
 * 下方是标准玩家物品栏（3×9 + 快捷栏），位置与 176×204 的底图对齐。</p>
 */
public class ContainerFluidCooler extends Container {

	private TileEntityFluidCooler cooler;

	public ContainerFluidCooler(InventoryPlayer invPlayer, TileEntityFluidCooler te) {
		this.cooler = te;

		this.addSlotToContainer(new Slot(te, 0, 23, 79));
		this.addSlotToContainer(new Slot(te, 1, 54, 79));

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

			// 识别码槽 ↔ 玩家背包
			if(index < 2) {
				if(!this.mergeItemStack(stack, 2, this.inventorySlots.size(), true)) return null;
			} else {
				if(!this.mergeItemStack(stack, 0, 2, false)) return null;
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
		return cooler.isUseableByPlayer(player);
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

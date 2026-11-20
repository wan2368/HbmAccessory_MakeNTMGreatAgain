package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityBatteryArray;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 电池组控制器容器。
 *
 * <p><b>已按需求删掉电池槽</b>：整组容量完全由结构内的电池/电容元件决定，
 * 界面不再放电池物品，所以这里只保留玩家背包，Shift 点击在
 * 「背包 ↔ 快捷栏」之间互转。</p>
 *
 * <p>控制器的槽 0 仍然存在于 TE（保持与电池座基类兼容），但不再暴露到界面，
 * 也不参与容量计算。</p>
 */
public class ContainerBatteryArray extends Container {

	protected TileEntityBatteryArray array;

	public ContainerBatteryArray(InventoryPlayer invPlayer, TileEntityBatteryArray te) {
		this.array = te;

		// 背包 27 格
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 99 + i * 18));
			}
		}
		// 快捷栏 9 格
		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 157));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return array.isUseableByPlayer(player);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			// 0..26 = 背包 → 快捷栏；27..35 = 快捷栏 → 背包
			if(index < 27) {
				if(!this.mergeItemStack(stack, 27, 36, false)) return null;
			} else {
				if(!this.mergeItemStack(stack, 0, 27, false)) return null;
			}

			if(stack.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();
		}
		return copy;
	}
}
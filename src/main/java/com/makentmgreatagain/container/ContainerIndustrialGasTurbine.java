package com.makentmgreatagain.container;

import com.hbm.items.machine.ItemMachineUpgrade;
import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 工业燃气轮机容器。槽坐标与底图 {@code gui/industial_gas_turbine.png} 对齐：
 * 识别码0(8,66) 识别码1(60,66) 桶入(42,12) 桶出(42,48) 电池(152,66) 升级(96,66)/(116,66)。
 * 玩家背包 3×9 从 (8,111)、快捷栏 (8,169)。
 */
public class ContainerIndustrialGasTurbine extends Container {

	private TileEntityIndustrialGasTurbine te;

	public ContainerIndustrialGasTurbine(InventoryPlayer invPlayer, TileEntityIndustrialGasTurbine te) {
		this.te = te;

		this.addSlotToContainer(new SlotId(te, TileEntityIndustrialGasTurbine.SLOT_ID_LUBE, 8, 66));
		this.addSlotToContainer(new SlotId(te, TileEntityIndustrialGasTurbine.SLOT_ID_FUEL, 60, 66));
		this.addSlotToContainer(new SlotBucketIn(te, TileEntityIndustrialGasTurbine.SLOT_BUCKET_IN, 42, 12));
		this.addSlotToContainer(new SlotOut(te, TileEntityIndustrialGasTurbine.SLOT_BUCKET_OUT, 42, 48));
		this.addSlotToContainer(new SlotBattery(te, TileEntityIndustrialGasTurbine.SLOT_BATTERY, 152, 66));
		this.addSlotToContainer(new SlotUpgrade(te, TileEntityIndustrialGasTurbine.SLOT_UPGRADE_1, 96, 66));
		this.addSlotToContainer(new SlotUpgrade(te, TileEntityIndustrialGasTurbine.SLOT_UPGRADE_2, 116, 66));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 111 + i * 18));

		for(int i = 0; i < 9; i++)
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 169));
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index < TileEntityIndustrialGasTurbine.SLOT_COUNT) {
				if(!this.mergeItemStack(stack, TileEntityIndustrialGasTurbine.SLOT_COUNT, this.inventorySlots.size(), true)) return null;
			} else {
				if(!this.mergeItemStack(stack, 0, TileEntityIndustrialGasTurbine.SLOT_COUNT, false)) return null;
			}

			if(stack.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();
		}
		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return te.isUseableByPlayer(player);
	}

	/** Shift 点击补 isItemValid 校验，避免垃圾塞进机器槽消失。 */
	@Override
	protected boolean mergeItemStack(ItemStack stack, int start, int end, boolean reverse) {

		boolean moved = false;
		int index = reverse ? end - 1 : start;

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
						stack.stackSize = 0; existing.stackSize = total; slot.onSlotChanged(); moved = true;
					} else if(existing.stackSize < limit) {
						stack.stackSize -= limit - existing.stackSize; existing.stackSize = limit; slot.onSlotChanged(); moved = true;
					}
				}
				if(reverse) index--; else index++;
			}
		}

		if(stack.stackSize > 0) {
			index = reverse ? end - 1 : start;
			while(!reverse && index < end || reverse && index >= start) {
				Slot slot = (Slot) this.inventorySlots.get(index);
				if(slot.getStack() == null && slot.isItemValid(stack)) {
					int limit = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
					if(stack.stackSize > limit) slot.putStack(stack.splitStack(limit));
					else { slot.putStack(stack.copy()); stack.stackSize = 0; }
					slot.onSlotChanged(); moved = true; break;
				}
				if(reverse) index--; else index++;
			}
		}
		return moved;
	}

	/** 流体识别码槽 */
	private static class SlotId extends Slot {
		public SlotId(IInventory inv, int id, int x, int y) { super(inv, id, x, y); }
		@Override public boolean isItemValid(ItemStack s) { return s != null && s.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier; }
		@Override public int getSlotStackLimit() { return 1; }
	}

	/** 燃料桶入槽 */
	private class SlotBucketIn extends Slot {
		public SlotBucketIn(IInventory inv, int id, int x, int y) { super(inv, id, x, y); }
		@Override public boolean isItemValid(ItemStack s) { return te.isItemValidForSlot(TileEntityIndustrialGasTurbine.SLOT_BUCKET_IN, s); }
	}

	/** 只出不进 */
	private static class SlotOut extends Slot {
		public SlotOut(IInventory inv, int id, int x, int y) { super(inv, id, x, y); }
		@Override public boolean isItemValid(ItemStack s) { return false; }
	}

	/** 电池槽 */
	private static class SlotBattery extends Slot {
		public SlotBattery(IInventory inv, int id, int x, int y) { super(inv, id, x, y); }
		@Override public boolean isItemValid(ItemStack s) {
			return s != null && (s.getItem() instanceof IBatteryItem || s.getItem() == com.hbm.items.ModItems.battery_creative);
		}
	}

	/** 升级槽：仅效率/加力 */
	private static class SlotUpgrade extends Slot {
		public SlotUpgrade(IInventory inv, int id, int x, int y) { super(inv, id, x, y); }
		@Override public boolean isItemValid(ItemStack s) {
			if(s != null && s.getItem() instanceof ItemMachineUpgrade) {
				ItemMachineUpgrade.UpgradeType t = ((ItemMachineUpgrade) s.getItem()).type;
				return t == ItemMachineUpgrade.UpgradeType.EFFECT || t == ItemMachineUpgrade.UpgradeType.AFTERBURN;
			}
			return false;
		}
		@Override public int getSlotStackLimit() { return 1; }
	}
}

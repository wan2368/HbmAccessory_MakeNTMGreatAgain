package com.makentmgreatagain.container;

import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** 大型离心机的物品容器。 */
public class ContainerLargeCentrifuge extends Container {

	private final TileEntityLargeCentrifuge tile;

	/** 按核心的槽位规则校验，手动放置和 Shift 点击都不会把物品塞错槽 */
	private static class ValidatedSlot extends Slot {

		ValidatedSlot(TileEntityLargeCentrifuge tile, int index, int x, int y) {
			super(tile, index, x, y);
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return this.inventory.isItemValidForSlot(this.getSlotIndex(), stack);
		}
	}

	public ContainerLargeCentrifuge(InventoryPlayer inventory, TileEntityLargeCentrifuge tile) {

		this.tile = tile;

		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_CAN_IN, 7, 68));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_CAN_OUT, 7, 86));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_IN_A, 32, 13));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_IN_B, 32, 33));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_FLUID_ID, 33, 86));

		for(int i = 0; i < 4; i++) {
			this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_OUT + i, 56 + i * 20, 68));
		}
		for(int i = 0; i < 4; i++) {
			this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_OUT + 4 + i, 56 + i * 20, 86));
		}
		for(int i = 0; i < 4; i++) {
			this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_FLUID_CAN + i, 137 + i * 18, 68));
		}
		for(int i = 0; i < 4; i++) {
			this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_FILLED + i, 137 + i * 18, 86));
		}

		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_BATTERY, 229, 86));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_UPGRADE, 211, 118));
		this.addSlotToContainer(new ValidatedSlot(tile, TileEntityLargeCentrifuge.SLOT_UPGRADE + 1, 211, 136));

		// 玩家背包
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 9; col++) {
				this.addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 35 + col * 18, 130 + row * 18));
			}
		}

		// 快捷栏
		for(int col = 0; col < 9; col++) {
			this.addSlotToContainer(new Slot(inventory, col, 35 + col * 18, 188));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return player.getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D, tile.zCoord + 0.5D) <= 64D;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack result = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot == null || !slot.getHasStack()) return null;

		ItemStack stack = slot.getStack();
		result = stack.copy();

		if(index < TileEntityLargeCentrifuge.INV_SIZE) {
			if(!this.mergeItemStack(stack, TileEntityLargeCentrifuge.INV_SIZE, this.inventorySlots.size(), true)) return null;
		} else {
			if(!this.mergeIntoMachine(stack)) return null;
		}

		if(stack.stackSize <= 0) slot.putStack(null);
		else slot.onSlotChanged();

		return result;
	}

	/** Shift 点击时按物品类型挑目标槽，避免识别码/电池/升级/流体罐被塞进输入槽 */
	private boolean mergeIntoMachine(ItemStack stack) {

		for(int target : getTargetSlots(stack)) {
			Slot slot = (Slot) this.inventorySlots.get(target);
			if(!slot.isItemValid(stack)) continue;
			if(this.mergeItemStack(stack, target, target + 1, false)) return true;
		}

		return false;
	}

	private static int[] getTargetSlots(ItemStack stack) {

		Item item = stack.getItem();

		if(item instanceof IItemFluidIdentifier) {
			return new int[] {TileEntityLargeCentrifuge.SLOT_FLUID_ID};
		}

		if(item instanceof IBatteryItem) {
			return new int[] {TileEntityLargeCentrifuge.SLOT_BATTERY};
		}

		if(item == ModItems.upgrade_speed_1 || item == ModItems.upgrade_speed_2 || item == ModItems.upgrade_speed_3
				|| item == ModItems.upgrade_overdrive_1 || item == ModItems.upgrade_overdrive_2 || item == ModItems.upgrade_overdrive_3) {
			return new int[] {TileEntityLargeCentrifuge.SLOT_UPGRADE, TileEntityLargeCentrifuge.SLOT_UPGRADE + 1};
		}

		if(item == ModItems.fluid_tank_lead_empty || item == ModItems.fluid_tank_empty
				|| item == ModItems.fluid_barrel_empty) {
			return new int[] {
					TileEntityLargeCentrifuge.SLOT_FLUID_CAN, TileEntityLargeCentrifuge.SLOT_FLUID_CAN + 1,
					TileEntityLargeCentrifuge.SLOT_FLUID_CAN + 2, TileEntityLargeCentrifuge.SLOT_FLUID_CAN + 3 };
		}

		if(item == ModItems.fluid_tank_lead_full || item == ModItems.fluid_tank_full
				|| item == ModItems.fluid_barrel_full || item instanceof ItemInfiniteFluid) {
			return new int[] {TileEntityLargeCentrifuge.SLOT_CAN_IN};
		}

		return new int[] {TileEntityLargeCentrifuge.SLOT_IN_A, TileEntityLargeCentrifuge.SLOT_IN_B};
	}
}

package com.makentmgreatagain.container;

import com.hbm.inventory.SlotUpgrade;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.makentmgreatagain.tileentity.TileEntityIndustrialCultivationStation;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 工业种植站容器（1:1 像素对齐用户重绘的 gui_industrial_cultivation_station.png 210×187 布局）：
 * - 电池槽 (8, 62)、左侧升级槽 (8, 95)
 * - 流体识别码槽 (44, 63)
 * - 3×2 作物槽 (65 + c*18, 18 / 40)，每格限堆叠 1 个且严格排除树苗
 * - 肥料槽 (65, 63)
 * - 3×3 输出槽 (149 + c*18, 18 + r*18)
 * - 玩家背包 (42, 106) + 快捷栏 (42, 164)
 */
public class ContainerIndustrialCultivationStation extends Container {

	private final TileEntityIndustrialCultivationStation te;

	public ContainerIndustrialCultivationStation(InventoryPlayer invPlayer, TileEntityIndustrialCultivationStation te) {
		this.te = te;

		// 0: 电池槽
		this.addSlotToContainer(new Slot(te, TileEntityIndustrialCultivationStation.SLOT_BATTERY, 8, 62) {
			@Override
			public boolean isItemValid(ItemStack stack) {
				return stack != null && stack.getItem() instanceof IBatteryItem;
			}
		});

		// 1: 流体识别码槽
		this.addSlotToContainer(new Slot(te, TileEntityIndustrialCultivationStation.SLOT_FLUID_ID, 44, 63) {
			@Override
			public boolean isItemValid(ItemStack stack) {
				return stack != null && stack.getItem() instanceof IItemFluidIdentifier;
			}
		});

		// 2..7: 3×2 作物槽（每格限堆叠 1 个，行 0 在 y=18，行 1 在 y=40，中间留给小进度条）
		int[] cropYs = { 18, 40 };
		for(int r = 0; r < 2; r++) {
			for(int c = 0; c < 3; c++) {
				int idx = TileEntityIndustrialCultivationStation.SLOT_CROP_START + r * 3 + c;
				this.addSlotToContainer(new SlotCrop(te, idx, 65 + c * 18, cropYs[r]));
			}
		}

		// 8: 肥料槽
		this.addSlotToContainer(new Slot(te, TileEntityIndustrialCultivationStation.SLOT_FERTILIZER, 65, 63) {
			@Override
			public boolean isItemValid(ItemStack stack) {
				return TileEntityIndustrialCultivationStation.isValidFertilizer(stack);
			}
		});

		// 9: 左侧独立 HBM 升级槽（支持节能 POWER / 速度 SPEED / 效能 EFFECT 三类芯片）
		this.addSlotToContainer(new SlotUpgrade(te, TileEntityIndustrialCultivationStation.SLOT_UPGRADE, 8, 93) {
			@Override
			public boolean isItemValid(ItemStack stack) {
				return TileEntityIndustrialCultivationStation.isValidUpgrade(stack);
			}
		});

		// 10..18: 保持旧索引的 3×3 输出槽（旧存档无需迁移）
		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 3; c++) {
				int idx = TileEntityIndustrialCultivationStation.SLOT_OUTPUT_START + r * 3 + c;
				this.addSlotToContainer(new SlotOutput(te, idx, 149 + c * 18, 18 + r * 18));
			}
		}

		// 玩家背包 (9×3)
		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 9; c++) {
				this.addSlotToContainer(new Slot(invPlayer, c + r * 9 + 9, 42 + c * 18, 106 + r * 18));
			}
		}

		// 玩家快捷栏 (9)
		for(int c = 0; c < 9; c++) {
			this.addSlotToContainer(new Slot(invPlayer, c, 42 + c * 18, 164));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return te.isUseableByPlayer(player);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);
		if(slot == null || !slot.getHasStack()) return null;

		ItemStack stack = slot.getStack();
		copy = stack.copy();

		int teSlots = TileEntityIndustrialCultivationStation.TOTAL_SLOTS;
		if(index < teSlots) {
			if(!this.mergeItemStack(stack, teSlots, this.inventorySlots.size(), true)) {
				return null;
			}
		} else {
			if(stack.getItem() instanceof IBatteryItem) {
				if(!this.mergeItemStack(stack, 0, 1, false)) {
					return null;
				}
			} else if(stack.getItem() instanceof IItemFluidIdentifier) {
				if(!this.mergeItemStack(stack, 1, 2, false)) {
					return null;
				}
			} else if(TileEntityIndustrialCultivationStation.isValidUpgrade(stack)) {
				if(!this.mergeItemStack(stack, TileEntityIndustrialCultivationStation.SLOT_UPGRADE,
						TileEntityIndustrialCultivationStation.SLOT_UPGRADE + 1, false)) {
					return null;
				}
			} else if(TileEntityIndustrialCultivationStation.isValidFertilizer(stack)) {
				if(!this.mergeItemStack(stack, 8, 9, false)) {
					return null;
				}
			} else if(TileEntityIndustrialCultivationStation.isValidCrop(stack)) {
				boolean placed = false;
				for(int i = 0; i < TileEntityIndustrialCultivationStation.CROP_COUNT && stack.stackSize > 0; i++) {
					Slot cs = (Slot) this.inventorySlots.get(TileEntityIndustrialCultivationStation.SLOT_CROP_START + i);
					if(!cs.getHasStack()) {
						ItemStack one = stack.splitStack(1);
						cs.putStack(one);
						placed = true;
						break;
					}
				}
				if(!placed) return null;
			} else {
				return null;
			}
		}

		if(stack.stackSize == 0) {
			slot.putStack(null);
		} else {
			slot.onSlotChanged();
		}

		if(stack.stackSize == copy.stackSize) return null;
		slot.onPickupFromSlot(player, stack);
		return copy;
	}

	private static class SlotCrop extends Slot {
		SlotCrop(IInventory inv, int idx, int x, int y) {
			super(inv, idx, x, y);
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return TileEntityIndustrialCultivationStation.isValidCrop(stack);
		}

		@Override
		public int getSlotStackLimit() {
			return 1;
		}
	}

	private static class SlotOutput extends Slot {
		SlotOutput(IInventory inv, int idx, int x, int y) {
			super(inv, idx, x, y);
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return false;
		}
	}
}

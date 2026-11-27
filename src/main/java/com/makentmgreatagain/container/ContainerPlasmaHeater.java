package com.makentmgreatagain.container;

import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.SlotTakeOnly;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.makentmgreatagain.tileentity.TileEntityPlasmaHeater;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 等离子加热器容器。槽位与底图 {@code gui/plasma_heater.png} 一一对齐：
 *
 * <ul>
 *   <li>电池槽 (10,77)；</li>
 *   <li>左桶入 (39,17)、左桶出 (39,53)；</li>
 *   <li>右桶入 (147,17)、右桶出 (147,53)；</li>
 *   <li>蓝图槽 (62,77)、升级插件槽 (84,77)；</li>
 *   <li>玩家背包 3×9 从 (8,126)、快捷栏 (8,184)，GUI 整体 176×208。</li>
 * </ul>
 *
 * <p>配方选择区 (42,76) 不是槽位：GUI 层点击后直接打开配方选择器，
 * 蓝图槽 (62,77) 决定选择器里可解锁的配方池。</p>
 */
public class ContainerPlasmaHeater extends Container {

	/** 背包首列 x、首行 y、快捷栏 y */
	public static final int INV_X = 8;
	public static final int INV_Y = 126;
	public static final int HOTBAR_Y = 184;

	private final TileEntityPlasmaHeater te;

	public ContainerPlasmaHeater(InventoryPlayer invPlayer, TileEntityPlasmaHeater te) {
		this.te = te;

		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityPlasmaHeater.SLOT_BATTERY, 10, 77));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityPlasmaHeater.SLOT_LEFT_IN, 39, 17));
		this.addSlotToContainer(new SlotTakeOnly(te, TileEntityPlasmaHeater.SLOT_LEFT_OUT, 39, 53));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityPlasmaHeater.SLOT_RIGHT_IN, 147, 17));
		this.addSlotToContainer(new SlotTakeOnly(te, TileEntityPlasmaHeater.SLOT_RIGHT_OUT, 147, 53));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityPlasmaHeater.SLOT_BLUEPRINT, 93, 77));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityPlasmaHeater.SLOT_UPGRADE, 129, 77));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, INV_X + j * 18, INV_Y + i * 18));

		for(int i = 0; i < 9; i++)
			this.addSlotToContainer(new Slot(invPlayer, i, INV_X + i * 18, HOTBAR_Y));
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index < TileEntityPlasmaHeater.SLOT_COUNT) {
				// 机器槽 → 背包（桶出槽 / 电池 / 蓝图都能被掏走）
				if(!this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_COUNT, this.inventorySlots.size(), true)) return null;
			} else {
				// 背包 → 机器：电池进槽 0，蓝图进槽 5，流体容器（满/空）先进左桶入再进右桶入
				if(copy.getItem() instanceof IBatteryItem || copy.getItem() == ModItems.battery_creative) {
					if(!this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_BATTERY,
							TileEntityPlasmaHeater.SLOT_BATTERY + 1, false)) return null;
				} else if(copy.getItem() == ModItems.blueprints) {
					if(!this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_BLUEPRINT,
							TileEntityPlasmaHeater.SLOT_BLUEPRINT + 1, false)) return null;
				} else if(this.te.isItemValidForSlot(TileEntityPlasmaHeater.SLOT_UPGRADE, copy)) {
					if(!this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_UPGRADE,
							TileEntityPlasmaHeater.SLOT_UPGRADE + 1, false)) return null;
				} else if(!(copy.getItem() instanceof ItemInfiniteFluid) && isFluidContainer(copy)) {
					if(!this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_LEFT_IN,
							TileEntityPlasmaHeater.SLOT_LEFT_IN + 1, false)
							&& !this.mergeItemStack(stack, TileEntityPlasmaHeater.SLOT_RIGHT_IN,
									TileEntityPlasmaHeater.SLOT_RIGHT_IN + 1, false)) return null;
				} else {
					return null;
				}
			}

			if(stack.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();
		}

		return copy;
	}

	/** 满桶（任意流体）或空桶 */
	private static boolean isFluidContainer(ItemStack stack) {
		return FluidContainerRegistry.getFluidType(stack) != com.hbm.inventory.fluid.Fluids.NONE
				|| FluidContainerRegistry.getEmptyContainer(stack) != null;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return this.te.isUseableByPlayer(player);
	}
}

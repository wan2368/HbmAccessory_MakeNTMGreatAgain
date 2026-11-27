package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.SlotTakeOnly;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.makentmgreatagain.tileentity.TileEntityStellarator;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 仿星器容器。槽位与底图 {@code gui/gui_stellarator.png} 一一对齐：
 *
 * <ul>
 *   <li>电力条 (8,18) 16×52；电池槽 (8,72)；</li>
 *   <li>等离子体罐 (44,18) 16×52；</li>
 *   <li>增值流体罐 (72,18)、流体识别码槽 (72,72)；</li>
 *   <li>超浓密蒸汽罐 (106,18)；输出流体罐 (134,18)；增值后流体罐 (152,18)；水罐 (188,18)；</li>
 *   <li>等离子配方物品产物槽 (116,36)；增值物品槽 (54,72)；增值物品产物槽 (116,54)；</li>
 *   <li>玩家背包 3×9 从 (28,115)、快捷栏 (28,173)，GUI 整体 230×227。</li>
 * </ul>
 */
public class ContainerStellarator extends Container {

	/** 背包首列 x、首行 y、快捷栏 y */
	public static final int INV_X = 28;
	public static final int INV_Y = 115;
	public static final int HOTBAR_Y = 173;

	private final TileEntityStellarator te;

	public ContainerStellarator(InventoryPlayer invPlayer, TileEntityStellarator te) {
		this.te = te;

		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityStellarator.SLOT_BATTERY, 8, 72));
		this.addSlotToContainer(new SlotTakeOnly(te, TileEntityStellarator.SLOT_PLASMA_OUT, 116, 36));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityStellarator.SLOT_BREED_IN, 54, 72));
		this.addSlotToContainer(new SlotTakeOnly(te, TileEntityStellarator.SLOT_BREED_OUT, 116, 54));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityStellarator.SLOT_BREED_ID, 72, 72));

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

			if(index < TileEntityStellarator.SLOT_COUNT) {
				if(!this.mergeItemStack(stack, TileEntityStellarator.SLOT_COUNT, this.inventorySlots.size(), true)) return null;
			} else {
				// 背包 → 机器：电池进槽 0、识别码进槽 4，其余（可增殖物品）进槽 2
				if(copy.getItem() instanceof IBatteryItem || copy.getItem() == ModItems.battery_creative) {
					if(!this.mergeItemStack(stack, TileEntityStellarator.SLOT_BATTERY,
							TileEntityStellarator.SLOT_BATTERY + 1, false)) return null;
				} else if(copy.getItem() instanceof IItemFluidIdentifier) {
					if(!this.mergeItemStack(stack, TileEntityStellarator.SLOT_BREED_ID,
							TileEntityStellarator.SLOT_BREED_ID + 1, false)) return null;
				} else if(this.te.isItemValidForSlot(TileEntityStellarator.SLOT_BREED_IN, copy)) {
					if(!this.mergeItemStack(stack, TileEntityStellarator.SLOT_BREED_IN,
							TileEntityStellarator.SLOT_BREED_IN + 1, false)) return null;
				} else {
					return null;
				}
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

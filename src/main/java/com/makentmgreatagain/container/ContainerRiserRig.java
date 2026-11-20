package com.makentmgreatagain.container;

import com.hbm.inventory.SlotTakeOnly;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 海面立管钻机的容器，坐标对应 gui_riser_rig.png（242×204）。
 *
 * <p>布局（全部按贴图像素量得）：</p>
 * <ul>
 *   <li>电池 220,72；流体识别码 202,72（最右两列，与大型采矿钻机相同）</li>
 *   <li>3 个输出罐底部的流体罐槽 135/153/171, 72</li>
 *   <li>3×3 物品输出槽 68/86/104 × 5/23/41（只出不进）</li>
 *   <li>两个升级插槽 68/86, 75</li>
 *   <li>左侧两个拉杆是 GUI 按钮，不是槽位</li>
 *   <li>玩家背包 41+j*18, 122+i*18；快捷栏 y=180</li>
 * </ul>
 */
public class ContainerRiserRig extends Container {

	private final TileEntityRiserRig rig;

	/** 机器槽位总数（0..15），之后是玩家背包 */
	public static final int MACHINE_SLOTS = TileEntityRiserRig.SLOT_COUNT;

	public ContainerRiserRig(InventoryPlayer invPlayer, TileEntityRiserRig te) {
		this.rig = te;

		// 电池 0
		this.addSlotToContainer(new Slot(te, TileEntityRiserRig.SLOT_BATTERY, 220, 72));
		// 流体识别码 1
		this.addSlotToContainer(new Slot(te, TileEntityRiserRig.SLOT_FLUID_ID, 202, 72));

		// 三个输出罐底部的流体罐槽 2..4
		for(int i = 0; i < 3; i++) {
			this.addSlotToContainer(new Slot(te, TileEntityRiserRig.SLOT_CAN_START + i, 135 + i * 18, 72));
		}

		// 3×3 物品输出槽 5..13
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 3; col++) {
				this.addSlotToContainer(new SlotTakeOnly(te,
						TileEntityRiserRig.SLOT_OUTPUT_START + row * 3 + col,
						68 + col * 18, 5 + row * 18));
			}
		}

		// 两个升级插槽 14..15
		for(int i = 0; i < 2; i++) {
			this.addSlotToContainer(new Slot(te, TileEntityRiserRig.SLOT_UPGRADE_START + i, 68 + i * 18, 75));
		}

		// 玩家背包
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 9; col++) {
				this.addSlotToContainer(new Slot(invPlayer, col + row * 9 + 9, 41 + col * 18, 122 + row * 18));
			}
		}
		for(int col = 0; col < 9; col++) {
			this.addSlotToContainer(new Slot(invPlayer, col, 41 + col * 18, 180));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack rStack = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			rStack = stack.copy();

			if(index < MACHINE_SLOTS) {
				if(!this.mergeItemStack(stack, MACHINE_SLOTS, this.inventorySlots.size(), true)) {
					return null;
				}
			} else {
				if(stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative) {
					if(!this.mergeItemStack(stack, TileEntityRiserRig.SLOT_BATTERY, TileEntityRiserRig.SLOT_BATTERY + 1, false)) return null;
				} else if(stack.getItem() instanceof IItemFluidIdentifier) {
					if(!this.mergeItemStack(stack, TileEntityRiserRig.SLOT_FLUID_ID, TileEntityRiserRig.SLOT_FLUID_ID + 1, false)) return null;
				} else if(stack.getItem() instanceof ItemMachineUpgrade) {
					if(!this.mergeItemStack(stack, TileEntityRiserRig.SLOT_UPGRADE_START, TileEntityRiserRig.SLOT_COUNT, false)) return null;
				} else if(stack.getItem() == ModItems.canister_empty || stack.getItem() == ModItems.canister_full) {
					if(!this.mergeItemStack(stack, TileEntityRiserRig.SLOT_CAN_START, TileEntityRiserRig.SLOT_OUTPUT_START, false)) return null;
				} else {
					return null;
				}
			}

			if(stack.stackSize == 0) {
				slot.putStack((ItemStack) null);
			} else {
				slot.onSlotChanged();
			}
		}

		return rStack;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return rig.isUseableByPlayer(player);
	}
}

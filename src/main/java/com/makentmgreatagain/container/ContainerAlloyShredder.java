package com.makentmgreatagain.container;

import com.hbm.inventory.SlotTakeOnly;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBlades;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 高级粉碎机的容器。
 *
 * 槽位坐标对应 GUI 贴图 gui_alloy_shredder.png（320×256）的大致布局：
 *   输入 3×5：x 45/63/81，y 25~97 五行
 *   刀片 4 格：y 155 一行，x 44/62/80/98
 *   输出 4×6：x 153/171/189/207，y 25~115 六行
 *   电池：左侧 x=8,y=10
 *   升级：左侧 x=8, y=110/128
 * 玩家可自行微调坐标。
 */
public class ContainerAlloyShredder extends Container {

	private final TileEntityAlloyShredder shredder;

	public ContainerAlloyShredder(InventoryPlayer invPlayer, TileEntityAlloyShredder te) {
		this.shredder = te;

		// ---- 输入 15 格（3×5）----
		int[] inX = {46, 64, 82, 100, 118};
		int[] inY = {39, 57, 75};
		int idx = 0;
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 5; col++) {
				this.addSlotToContainer(new Slot(te, idx++, inX[col], inY[row]));
			}
		}

		// ---- 刀片 4 格 ----
		int[] bladeX = {46, 64, 100, 118};
		for(int i = 0; i < 4; i++) {
			this.addSlotToContainer(new Slot(te, TileEntityAlloyShredder.SLOT_BLADE_START + i, bladeX[i], 129));
		}

		// ---- 输出 24 格（4×6）——SlotOutput 物理禁入 ----
		int[] outX = {154, 172, 190, 208};
		int[] outY = {39, 57, 75, 93, 111, 129};
		for(int row = 0; row < 6; row++) {
			for(int col = 0; col < 4; col++) {
				this.addSlotToContainer(new SlotTakeOnly(te, TileEntityAlloyShredder.SLOT_OUTPUT_START + row * 4 + col, outX[col], outY[row]));
			}
		}

		// ---- 能量槽 ----
		this.addSlotToContainer(new Slot(te, TileEntityAlloyShredder.SLOT_BATTERY, 10, 101));

		// ---- 升级槽 1 格（仅红色速度插件）----
		this.addSlotToContainer(new Slot(te, TileEntityAlloyShredder.SLOT_UPGRADE, 10, 132));

		// ---- 玩家物品栏（背景图下方白框区域）----
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 9; col++) {
				this.addSlotToContainer(new Slot(invPlayer, col + row * 9 + 9, 44 + col * 18, 172 + row * 18));
			}
		}
		for(int col = 0; col < 9; col++) {
			this.addSlotToContainer(new Slot(invPlayer, col, 44 + col * 18, 230));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack result = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();

			// 机器槽 -> 玩家栏（机器共 45 槽：0~44）
			if(index < 45) {
				if(!this.mergeItemStack(stack, 45, this.inventorySlots.size(), true)) {
					return null;
				}
			} else {
				// 玩家栏 -> 机器槽：电池、刀片、其余进输入
				if(stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative) {
					if(!this.mergeItemStack(stack, TileEntityAlloyShredder.SLOT_BATTERY, TileEntityAlloyShredder.SLOT_BATTERY + 1, false)) return null;
				} else if(stack.getItem() instanceof ItemBlades) {
					if(!this.mergeItemStack(stack, TileEntityAlloyShredder.SLOT_BLADE_START, TileEntityAlloyShredder.SLOT_BLADE_END + 1, false)) return null;
				} else {
					if(!this.mergeItemStack(stack, TileEntityAlloyShredder.SLOT_INPUT_START, TileEntityAlloyShredder.SLOT_INPUT_END + 1, false)) return null;
				}
			}

			if(stack.stackSize == 0) {
				slot.putStack(null);
			} else {
				slot.onSlotChanged();
			}
		}

		return result;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return shredder.isUseableByPlayer(player);
	}
}

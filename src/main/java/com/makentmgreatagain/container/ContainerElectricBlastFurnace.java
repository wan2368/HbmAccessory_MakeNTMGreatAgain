package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.container.ContainerBase;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

/**
 * 电力高炉容器。
 *
 * <p>槽位坐标与底图 {@code gui_electric_blast_furnace.png} 对齐：
 * 5 种原料 × 4 格从 (82,21) 起、行距 18 列距 18；电池 (44,93)、
 * 铸造模板 (266,12)、升级插件 (258,129)/(276,129)。
 * 输出 6 格暂不上界面（等铸造阶段），放在面板外，物流仍可抽取。</p>
 */
public class ContainerElectricBlastFurnace extends ContainerBase {

	public ContainerElectricBlastFurnace(InventoryPlayer invPlayer, TileEntityElectricBlastFurnace te) {
		super(invPlayer, te);

		// 5 种原料 × 4 格
		for(int type = 0; type < TileEntityElectricBlastFurnace.INPUT_TYPES; type++) {
			this.addSlots(te, TileEntityElectricBlastFurnace.SLOT_INPUT + type * TileEntityElectricBlastFurnace.INPUT_PER_TYPE,
					82, 21 + type * 18, 1, 4, 18);
		}

		// 输出 8 格：两列四行 (258,39) 起，行距 18 列距 18
		this.addTakeOnlySlots(te, TileEntityElectricBlastFurnace.SLOT_OUTPUT, 258, 39, 4, 2, 18);

		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityElectricBlastFurnace.SLOT_BATTERY, 44, 93));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityElectricBlastFurnace.SLOT_TEMPLATE, 266, 12));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityElectricBlastFurnace.SLOT_UPGRADE_1, 258, 129));
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityElectricBlastFurnace.SLOT_UPGRADE_2, 276, 129));

		this.playerInv(invPlayer, 80, 139);
	}

	@Override
	protected boolean mergeItemStack(net.minecraft.item.ItemStack stack, int start, int end, boolean reverse) {

		boolean moved = false;
		int index = reverse ? end - 1 : start;

		while((reverse ? index >= start : index < end)) {
			Slot slot = (Slot) this.inventorySlots.get(index);

			if(slot != null && slot.isItemValid(stack)) {
				net.minecraft.item.ItemStack in = slot.getStack();

				if(in == null) {
					slot.putStack(stack.copy());
					slot.onSlotChanged();
					stack.stackSize = 0;
					return true;
				}

				if(in.isItemEqual(stack) && net.minecraft.item.ItemStack.areItemStackTagsEqual(in, stack)) {
					int space = Math.min(slot.getSlotStackLimit(), in.getMaxStackSize()) - in.stackSize;
					int add = Math.min(space, stack.stackSize);
					if(add > 0) {
						in.stackSize += add;
						stack.stackSize -= add;
						slot.onSlotChanged();
						moved = true;
						if(stack.stackSize == 0) return true;
					}
				}
			}

			index += reverse ? -1 : 1;
		}

		return moved;
	}
}
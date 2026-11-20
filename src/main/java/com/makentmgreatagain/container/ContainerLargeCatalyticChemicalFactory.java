package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.SlotUpgrade;
import com.hbm.inventory.container.ContainerBase;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.makentmgreatagain.recipes.HybridFractionRecipes;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 大型催化化工厂容器。
 *
 * <p>槽位坐标与 GUI 贴图一一对应，物品栏编号见
 * {@link TileEntityLargeCatalyticChemicalFactory} 的类注释。</p>
 */
public class ContainerLargeCatalyticChemicalFactory extends ContainerBase {

	private final TileEntityLargeCatalyticChemicalFactory chemfac;

	public ContainerLargeCatalyticChemicalFactory(InventoryPlayer invPlayer, TileEntityLargeCatalyticChemicalFactory tile) {
		super(invPlayer, tile);
		this.chemfac = tile;

		// 电池
		this.addSlotToContainer(new SlotNonRetarded(tile, TileEntityLargeCatalyticChemicalFactory.SLOT_BATTERY, 253, 88));

		// 三个升级（右侧竖排插座）
		for(int i = 0; i < 3; i++) {
			this.addSlotToContainer(new SlotUpgrade(tile, TileEntityLargeCatalyticChemicalFactory.SLOT_UPGRADE + i, 235, 125 + i * 18));
		}

		// 4 个输入类型 × 每个 4 个槽
		for(int type = 0; type < TileEntityLargeCatalyticChemicalFactory.INPUT_TYPES; type++) {
			this.addSlots(tile,
					TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT + type * TileEntityLargeCatalyticChemicalFactory.INPUT_SLOTS,
					41, 15 + type * 22, 1, 4, 16);
		}

		// 4 个输出类型 × 每个 2 个槽
		for(int type = 0; type < TileEntityLargeCatalyticChemicalFactory.OUTPUT_TYPES; type++) {
			this.addOutputSlots(invPlayer.player,
					tile,
					TileEntityLargeCatalyticChemicalFactory.SLOT_OUTPUT + type * TileEntityLargeCatalyticChemicalFactory.OUTPUT_SLOTS,
					178, 15 + type * 22, 1, 2, 16);
		}

		// 催化剂
		this.addSlotToContainer(new SlotNonRetarded(tile, TileEntityLargeCatalyticChemicalFactory.SLOT_CATALYST, 142, 23));
		// 蓝图
		this.addSlotToContainer(new SlotNonRetarded(tile, TileEntityLargeCatalyticChemicalFactory.SLOT_BLUEPRINT, 151, 81));

		// 两个电极槽
		for(int i = 0; i < 2; i++) {
			this.addSlotToContainer(new SlotNonRetarded(tile, TileEntityLargeCatalyticChemicalFactory.SLOT_ELECTRODE + i, 5 + i * 18, 121));
		}

		this.playerInv(invPlayer, 55, 134);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack slotOriginal = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack slotStack = slot.getStack();
			slotOriginal = slotStack.copy();

			if(index <= tile.getSizeInventory() - 1) {
				if(!this.mergeItemStack(slotStack, tile.getSizeInventory(), this.inventorySlots.size(), true)) return null;
			} else {

				if(slotOriginal.getItem() instanceof IBatteryItem || slotOriginal.getItem() == ModItems.battery_creative) {
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_BATTERY,
							TileEntityLargeCatalyticChemicalFactory.SLOT_BATTERY + 1, false)) return null;
				} else if(slotOriginal.getItem() instanceof ItemMachineUpgrade) {
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_UPGRADE,
							TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT, false)) return null;
				} else if(slotOriginal.getItem() instanceof ItemBlueprints) {
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_BLUEPRINT,
							TileEntityLargeCatalyticChemicalFactory.SLOT_BLUEPRINT + 1, false)) return null;
				} else if(TileEntityLargeCatalyticChemicalFactory.isElectrodeItem(slotOriginal)) {
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_ELECTRODE,
							TileEntityLargeCatalyticChemicalFactory.SLOT_ELECTRODE + 2, false)) return null;
				} else if(HybridFractionRecipes.catalystTier(slotOriginal) > 0) {
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_CATALYST,
							TileEntityLargeCatalyticChemicalFactory.SLOT_CATALYST + 1, false)) return null;
				} else {
					int end = TileEntityLargeCatalyticChemicalFactory.SLOT_OUTPUT;
					if(!this.mergeItemStack(slotStack, TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT, end, false)) return null;
				}
			}

			if(slotStack.stackSize == 0) {
				slot.putStack(null);
			} else {
				slot.onSlotChanged();
			}

			slot.onPickupFromSlot(player, slotStack);
		}

		return slotOriginal;
	}

	/**
	 * 原版 mergeItemStack 不看 Slot#isItemValid，会把物品硬塞进空槽。
	 * 输入槽随后又会被机器拒收，Shift 点击时物品就凭空消失了。
	 * 这里补上校验：只有槽位允许的物品才会被放进去。
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

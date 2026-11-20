package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 地核熔岩开采机 GUI 容器。
 *
 * <p>机器槽坐标与底图 {@code gui/core_lava_extractor.png} 对齐：
 * 晶体 (52,59)、电池 (186,68)、空桶 (133,49)/(151,49)、满桶 (133,67)/(151,67)。
 * 玩家背包 3×9 从 (24,112) 起、快捷栏 (24,170)，与 210×196 的底图对齐。</p>
 */
public class ContainerCoreLavaExtractor extends Container {

	private TileEntityCoreLavaExtractor te;

	public ContainerCoreLavaExtractor(InventoryPlayer invPlayer, TileEntityCoreLavaExtractor te) {
		this.te = te;

		this.addSlotToContainer(new SlotCrystal(te, TileEntityCoreLavaExtractor.SLOT_CRYSTAL, 52, 59));
		this.addSlotToContainer(new SlotBattery(te, TileEntityCoreLavaExtractor.SLOT_BATTERY, 186, 68));
		this.addSlotToContainer(new SlotContainerIn(te, TileEntityCoreLavaExtractor.SLOT_EMPTY_1, 133, 49));
		this.addSlotToContainer(new SlotContainerIn(te, TileEntityCoreLavaExtractor.SLOT_EMPTY_2, 151, 49));
		this.addSlotToContainer(new SlotContainerOut(te, TileEntityCoreLavaExtractor.SLOT_FULL_1, 133, 67));
		this.addSlotToContainer(new SlotContainerOut(te, TileEntityCoreLavaExtractor.SLOT_FULL_2, 151, 67));

		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 25 + j * 18, 114 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(invPlayer, i, 25 + i * 18, 172));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index < 6) {
				if(!this.mergeItemStack(stack, 6, this.inventorySlots.size(), true)) return null;
			} else {
				if(!this.mergeItemStack(stack, 0, 6, false)) return null;
			}

			if(stack.stackSize == 0) {
				slot.putStack(null);
			} else {
				slot.onSlotChanged();
			}
		}

		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return te.isUseableByPlayer(player);
	}

	/** 原版 merge 不看 isItemValid，Shift 点击会把垃圾塞进机器槽后凭空消失，补校验。 */
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

	/** 人造X晶体槽：只收 crystal_xen，单件 */
	private static class SlotCrystal extends Slot {
		public SlotCrystal(net.minecraft.inventory.IInventory inv, int id, int x, int y) {
			super(inv, id, x, y);
		}
		@Override
		public boolean isItemValid(ItemStack stack) {
			return stack != null && stack.getItem() == com.hbm.items.ModItems.crystal_xen;
		}
		@Override
		public int getSlotStackLimit() {
			return 1;
		}
	}

	/** 电池槽：只收可充放电池 */
	private static class SlotBattery extends Slot {
		public SlotBattery(net.minecraft.inventory.IInventory inv, int id, int x, int y) {
			super(inv, id, x, y);
		}
		@Override
		public boolean isItemValid(ItemStack stack) {
			return stack != null && (stack.getItem() instanceof IBatteryItem || stack.getItem() == com.hbm.items.ModItems.battery_creative);
		}
	}

	/**
	 * 空容器槽：收一切「能被灌装」的容器。
	 *
	 * <p>判定必须与机器实际灌装逻辑（{@code FluidTank.unloadTank} 的 handler 链）一致，
	 * 否则会出现「放得进却灌不满」或「能灌却放不进」。三条放行路径：</p>
	 * <ul>
	 *   <li>{@code IItemFluidIdentifier} —— 流体识别码，用来指定流体类型；</li>
	 *   <li>{@code IFillableItem} —— 自带灌装接口的物品（{@code FluidLoaderFillableItem}）；</li>
	 *   <li>注册表里登记过的空容器 —— 流体单元 cell_empty、空流体罐、空桶等
	 *       （{@code FluidLoaderStandard} 走 {@code FluidContainerRegistry.getFullContainer}）。</li>
	 * </ul>
	 *
	 * <p>原写法只认前者的接口和原版桶，而 HBM 的流体单元是普通 {@code Item}，
	 * 既没实现识别码接口也不是原版桶，于是被挡在槽外 —— 这就是放不进去的原因。</p>
	 */
	private class SlotContainerIn extends Slot {
		public SlotContainerIn(net.minecraft.inventory.IInventory inv, int id, int x, int y) {
			super(inv, id, x, y);
		}
		@Override
		public boolean isItemValid(ItemStack stack) {

			if(stack == null) return false;

			if(stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) return true;
			if(stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem) return true;
			if(stack.getItem() == net.minecraft.init.Items.bucket) return true;

			// 注册表路径：本机两个输出罐的流体，只要能把这个空容器灌满就收
			for(com.hbm.inventory.fluid.tank.FluidTank tank : te.tanks) {
				if(tank == null || tank.getTankType() == null) continue;
				if(com.hbm.inventory.FluidContainerRegistry.getFullContainer(stack, tank.getTankType()) != null) return true;
			}

			return false;
		}
	}

	/** 满容器槽：只出不进 */
	private static class SlotContainerOut extends Slot {
		public SlotContainerOut(net.minecraft.inventory.IInventory inv, int id, int x, int y) {
			super(inv, id, x, y);
		}
		@Override
		public boolean isItemValid(ItemStack stack) {
			return false;
		}
	}
}

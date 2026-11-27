package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerLargeThermalPowerStation extends Container {
    private final TileEntityLargeThermalPowerStation te;

    public ContainerLargeThermalPowerStation(InventoryPlayer player, TileEntityLargeThermalPowerStation te) {
        this.te = te;
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_FUEL, 9, 17, true));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_ASH, 9, 71, false));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_BUCKET_IN, 50, 17, true));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_BUCKET_OUT, 50, 53, false));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_FLUID_ID, 68, 71, true));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_UPGRADE_1, 92, 71, true));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_UPGRADE_2, 116, 71, true));
        addSlotToContainer(new MachineSlot(te, TileEntityLargeThermalPowerStation.SLOT_BATTERY, 152, 71, true));
        for(int row = 0; row < 3; row++)
            for(int col = 0; col < 9; col++)
                addSlotToContainer(new Slot(player, col + row * 9 + 9, 8 + col * 18, 121 + row * 18));
        for(int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 8 + col * 18, 179));
    }

    @Override public boolean canInteractWith(EntityPlayer player) { return te.isUseableByPlayer(player); }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if(index < 0 || index >= inventorySlots.size()) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if(slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        boolean moved;
        if(index < TileEntityLargeThermalPowerStation.SLOT_COUNT) {
            // 机器槽 → 玩家背包
            moved = mergeItemStack(stack, TileEntityLargeThermalPowerStation.SLOT_COUNT, inventorySlots.size(), true);
        } else {
            // 玩家背包 → 按物品类型路由到唯一对应槽；输出槽(灰烬/桶出)永远进不去
            int target = targetSlotFor(stack);
            moved = target >= 0 && mergeItemStack(stack, target, target + 1, false);
        }
        if(!moved) return null;
        if(stack.stackSize <= 0) slot.putStack(null); else slot.onSlotChanged();
        return original;
    }

    /** shift 路由表：燃料→燃料槽，升级→空升级槽，识别码/桶/电池各归其槽 */
    private int targetSlotFor(ItemStack stack) {
        if(stack == null) return -1;
        if(TileEntityLargeThermalPowerStation.getHeatValue(stack) > 0)
            return TileEntityLargeThermalPowerStation.SLOT_FUEL;
        if(stack.getItem() instanceof com.hbm.items.machine.ItemMachineUpgrade) {
            com.hbm.items.machine.ItemMachineUpgrade.UpgradeType type =
                    ((com.hbm.items.machine.ItemMachineUpgrade) stack.getItem()).type;
            if(type != com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.SPEED
                    && type != com.hbm.items.machine.ItemMachineUpgrade.UpgradeType.EFFECT) return -1;
            if(te.getStackInSlot(TileEntityLargeThermalPowerStation.SLOT_UPGRADE_1) == null)
                return TileEntityLargeThermalPowerStation.SLOT_UPGRADE_1;
            if(te.getStackInSlot(TileEntityLargeThermalPowerStation.SLOT_UPGRADE_2) == null)
                return TileEntityLargeThermalPowerStation.SLOT_UPGRADE_2;
            return -1;
        }
        if(stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier)
            return TileEntityLargeThermalPowerStation.SLOT_FLUID_ID;
        if(stack.getItem() == com.hbm.items.ModItems.fluid_barrel_infinite
                || stack.getItem() instanceof api.hbm.fluidmk2.IFillableItem
                || com.hbm.inventory.FluidContainerRegistry.getEmptyContainer(stack) != null)
            return TileEntityLargeThermalPowerStation.SLOT_BUCKET_IN;
        if(stack.getItem() instanceof api.hbm.energymk2.IBatteryItem
                || stack.getItem() == com.hbm.items.ModItems.battery_creative)
            return TileEntityLargeThermalPowerStation.SLOT_BATTERY;
        return -1;
    }

    @Override protected boolean mergeItemStack(ItemStack stack, int start, int end, boolean reverse) {
        boolean moved = false;
        int index = reverse ? end - 1 : start;
        while(stack.stackSize > 0 && (reverse ? index >= start : index < end)) {
            Slot slot = (Slot) inventorySlots.get(index);
            ItemStack existing = slot.getStack();
            if(existing != null && slot.isItemValid(stack) && existing.getItem() == stack.getItem()
                    && (!stack.getHasSubtypes() || existing.getItemDamage() == stack.getItemDamage())
                    && ItemStack.areItemStackTagsEqual(existing, stack)) {
                int room = Math.min(slot.getSlotStackLimit(), existing.getMaxStackSize()) - existing.stackSize;
                if(room > 0) {
                    int amount = Math.min(room, stack.stackSize);
                    existing.stackSize += amount; stack.stackSize -= amount; slot.onSlotChanged(); moved = true;
                }
            }
            index += reverse ? -1 : 1;
        }
        index = reverse ? end - 1 : start;
        while(stack.stackSize > 0 && (reverse ? index >= start : index < end)) {
            Slot slot = (Slot) inventorySlots.get(index);
            if(slot.getStack() == null && slot.isItemValid(stack)) {
                int amount = Math.min(stack.stackSize, slot.getSlotStackLimit());
                slot.putStack(stack.splitStack(amount)); slot.onSlotChanged(); moved = true; break;
            }
            index += reverse ? -1 : 1;
        }
        return moved;
    }

    private static class MachineSlot extends Slot {
        private final boolean input;
        MachineSlot(TileEntityLargeThermalPowerStation te, int index, int x, int y, boolean input) {
            super(te, index, x, y); this.input = input;
        }
        @Override public boolean isItemValid(ItemStack stack) {
            return input && this.inventory.isItemValidForSlot(this.slotNumber, stack);
        }
        @Override public int getSlotStackLimit() {
            if(slotNumber == TileEntityLargeThermalPowerStation.SLOT_FLUID_ID
                    || slotNumber == TileEntityLargeThermalPowerStation.SLOT_UPGRADE_1
                    || slotNumber == TileEntityLargeThermalPowerStation.SLOT_UPGRADE_2
                    || slotNumber == TileEntityLargeThermalPowerStation.SLOT_BUCKET_IN
                    || slotNumber == TileEntityLargeThermalPowerStation.SLOT_BUCKET_OUT
                    || slotNumber == TileEntityLargeThermalPowerStation.SLOT_BATTERY) return 1;
            return super.getSlotStackLimit();
        }
    }
}

package com.makentmgreatagain.container;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.SlotTakeOnly;
import com.makentmgreatagain.init.ModCoatings;
import com.makentmgreatagain.init.ModNukeCores;
import com.makentmgreatagain.recipes.NukeGeneratorData;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 可控核爆炸发生器界面容器。
 *
 * <p>坐标全部照 gui_nuclear_explosion.png 里的凹槽位置摆：
 * 电池 (7,94)、覆盖层 (79,16)、被增殖物品 (39,52)、增殖产物 (119,52)、核心 (79,52)，
 * 玩家背包从 (17,137) 开始，快捷栏在 (17,195)。</p>
 *
 * <p>机器格一律用 {@link SlotNonRetarded}，这样 {@code isItemValidForSlot} 的准入判定
 * （覆层只收覆层、核心只收核心……）在界面上也生效，而不是只能靠管道那一侧拦。</p>
 */
public class ContainerNuclearExplosionGenerator extends Container {

	/** 机器自身槽位数量，也是玩家背包在 inventorySlots 里的起点 */
	private static final int MACHINE_SLOTS = 5;

	private final TileEntityNuclearExplosionGenerator te;

	public ContainerNuclearExplosionGenerator(InventoryPlayer inv, TileEntityNuclearExplosionGenerator te) {

		this.te = te;

		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityNuclearExplosionGenerator.SLOT_BATTERY, 7, 94));
		// 覆层运行中可以取下，但取下的那一刻直接核爆（服务端判定，和拆机器同一套）
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityNuclearExplosionGenerator.SLOT_COATING, 79, 16) {
			@Override
			public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
				super.onPickupFromSlot(player, stack);
				if(!ContainerNuclearExplosionGenerator.this.te.getWorldObj().isRemote
						&& ContainerNuclearExplosionGenerator.this.te.running) {
					ContainerNuclearExplosionGenerator.this.te.onMachineBroken();
				}
			}
		});
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityNuclearExplosionGenerator.SLOT_INPUT, 39, 52));
		this.addSlotToContainer(new SlotTakeOnly(te, TileEntityNuclearExplosionGenerator.SLOT_OUTPUT, 119, 52));
		// 核心只在机器空闲时能拿走：按下运行键后到爆炸那一 tick 之间锁住
		this.addSlotToContainer(new SlotNonRetarded(te, TileEntityNuclearExplosionGenerator.SLOT_CORE, 79, 52) {
			@Override
			public boolean canTakeStack(EntityPlayer player) {
				return !ContainerNuclearExplosionGenerator.this.te.running;
			}
		});

		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(inv, j + i * 9 + 9, 17 + j * 18, 137 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(inv, i, 17 + i * 18, 195));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return this.te.isUseableByPlayer(player);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack result = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot == null || !slot.getHasStack()) return null;

		ItemStack stack = slot.getStack();
		result = stack.copy();

		if(index < MACHINE_SLOTS) {
			// 机器 → 玩家背包；运行中核心槽不许 shift 走
			if(index == TileEntityNuclearExplosionGenerator.SLOT_CORE && this.te.running) return null;
			if(!this.mergeItemStack(stack, MACHINE_SLOTS, this.inventorySlots.size(), true)) return null;
		} else {
			// 玩家背包 → 机器：按物品类型各归各位，不认识的物品不收
			int target = getTargetSlot(stack);
			if(target < 0) return null;
			if(!this.mergeItemStack(stack, target, target + 1, false)) return null;
		}

		if(stack.stackSize == 0) {
			slot.putStack(null);
		} else {
			slot.onSlotChanged();
		}

		if(stack.stackSize == result.stackSize) return null;

		slot.onPickupFromSlot(player, stack);

		return result;
	}

	/** shift+左键的落点：电池 / 覆层 / 核心 / 可增殖物品各归各位，其余不接收 */
	private static int getTargetSlot(ItemStack stack) {

		if(stack.getItem() instanceof IBatteryItem) return TileEntityNuclearExplosionGenerator.SLOT_BATTERY;
		if(ModCoatings.isCoating(stack)) return TileEntityNuclearExplosionGenerator.SLOT_COATING;
		if(ModNukeCores.isCore(stack)) return TileEntityNuclearExplosionGenerator.SLOT_CORE;
		if(NukeGeneratorData.findRecipe(stack) != null) return TileEntityNuclearExplosionGenerator.SLOT_INPUT;

		return -1;
	}
}

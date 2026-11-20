package com.makentmgreatagain.container;

import com.hbm.inventory.SlotTakeOnly;
import com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 混合催化分馏塔 GUI 容器。
 *
 * <p>槽位坐标以界面底图左上角为 0,0。机器区整体比底图凹槽右移、下移 1 px
 * （物品图标相对凹槽左偏上偏 1 px 的实测补偿）；识别码槽和两个进料桶槽再回退 1 px。</p>
 * <pre>
 *  0  识别码 1      (  5, 70)      1  识别码 2      ( 23, 70)
 *  2  进料桶 1      (  5, 86)      3  空桶出 1      (  5,106)
 *  4  进料桶 2      ( 23, 86)      5  空桶出 2      ( 23,106)
 *  6  催化剂        ( 59, 30)      7  物品输出      ( 77,106)
 *  8  出料桶进 1    ( 95, 88)      9  满桶出 1      ( 95,106)
 * 10  出料桶进 2    (113, 88)     11  满桶出 2      (113,106)
 * 12  出料桶进 3    (131, 88)     13  满桶出 3      (131,106)
 * 14  出料桶进 4    (149, 88)     15  满桶出 4      (149,106)
 * </pre>
 * <p>玩家背包三行 154/172/190，快捷栏 212（底图比标准位置高 2 px）。</p>
 */
public class ContainerHybridCatalyticFractionTower extends Container {

	private TileEntityHybridCatalyticFractionTower tower;

	public ContainerHybridCatalyticFractionTower(InventoryPlayer invPlayer, TileEntityHybridCatalyticFractionTower te) {

		this.tower = te;

		// 左侧：两路进料的识别码 + 进料桶 / 空桶
		this.addSlotToContainer(new Slot(te, 0, 5, 70));
		this.addSlotToContainer(new Slot(te, 1, 23, 70));
		this.addSlotToContainer(new Slot(te, 2, 5, 88));
		this.addSlotToContainer(new SlotTakeOnly(te, 3, 5, 106));
		this.addSlotToContainer(new Slot(te, 4, 23, 88));
		this.addSlotToContainer(new SlotTakeOnly(te, 5, 23, 106));

		// 中间：催化剂（唯一不需要补偿的槽）+ 物品输出
		this.addSlotToContainer(new Slot(te, 6, 59, 30));
		this.addSlotToContainer(new SlotTakeOnly(te, 7, 77, 106));

		// 右侧：四路出料的空桶 / 满桶
		for(int i = 0; i < 4; i++) {
			int x = 95 + i * 18;
			this.addSlotToContainer(new Slot(te, 8 + i * 2, x, 88));
			this.addSlotToContainer(new SlotTakeOnly(te, 9 + i * 2, x, 106));
		}

		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 154 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 212));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {

		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);

		if(slot != null && slot.getHasStack()) {

			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if(index < 16) {
				if(!this.mergeItemStack(stack, 16, this.inventorySlots.size(), true)) return null;
			} else {
				// 识别码优先塞进识别码槽，其余按进料桶 → 出料桶 → 催化剂槽的顺序
				if(stack.getItem() instanceof com.hbm.items.machine.IItemFluidIdentifier) {
					if(!this.mergeItemStack(stack, 0, 2, false)) return null;
				} else if(!this.mergeItemStack(stack, 2, 3, false)) {
					if(!this.mergeItemStack(stack, 4, 5, false)) {
						if(!this.mergeItemStack(stack, 8, 9, false)) {
							if(!this.mergeItemStack(stack, 10, 11, false)) {
								if(!this.mergeItemStack(stack, 12, 13, false)) {
									if(!this.mergeItemStack(stack, 14, 15, false)) {
										if(!this.mergeItemStack(stack, 6, 7, false)) return null;
									}
								}
							}
						}
					}
				}
			}

			if(stack.stackSize == 0) {
				slot.putStack((ItemStack) null);
			} else {
				slot.onSlotChanged();
			}
		}

		return copy;
	}

	@Override
	public void addCraftingToCrafters(ICrafting crafting) {
		super.addCraftingToCrafters(crafting);
	}

	/** 上一次看到的催化剂槽内容，用来判断有没有换过催化剂 */
	private ItemStack lastCatalyst;
	private boolean catalystSeen;

	/**
	 * 每 tick 由服务端调用。除了走父类的常规槽位同步，还额外盯着 6 号催化剂槽：
	 * 一旦换了催化剂就立刻让 TE 重算并广播，客户端下一 tick 就能换界面底图。
	 */
	@Override
	public void detectAndSendChanges() {

		super.detectAndSendChanges();

		ItemStack current = tower.getStackInSlot(6);

		if(!catalystSeen) {
			catalystSeen = true;
			lastCatalyst = current == null ? null : current.copy();
			return;
		}

		if(sameCatalyst(lastCatalyst, current)) return;

		lastCatalyst = current == null ? null : current.copy();
		tower.syncCatalystType();
	}

	private static boolean sameCatalyst(ItemStack a, ItemStack b) {

		if(a == null) return b == null;
		if(b == null) return false;

		return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return tower.isUseableByPlayer(player);
	}
}

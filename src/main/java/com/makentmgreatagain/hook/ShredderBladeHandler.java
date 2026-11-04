package com.makentmgreatagain.hook;

import com.hbm.tileentity.machine.TileEntityMachineShredder;
import com.makentmgreatagain.init.ModItems;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 高级合金刀片磨损耗尽后自动变为"损坏刀片"（scrap_blades_alloy_reborn）。
 *
 * <p>方案 A：不改 HBM 任何代码，用服务端 tick 轮询。</p>
 *
 * <p>HBM 粉碎机原生只做 setItemDamage(+1)，磨完仍留在槽里显示为损坏状态，
 * 没有"自动变成另一个物品"的钩子。这里每服务器 tick 扫一遍所有粉碎机：
 * 若刀片槽(27/28)里是高级合金刀片且耐久已归零（damage >= maxDamage），
 * 就用 setInventorySlotContents 把它替换成 scrap_blades_alloy_reborn，
 * 该物品没有耐久、是普通物品（粉碎机不再把它当刀片），玩家取出后可直接熔炼回锭。</p>
 *
 * <p>性能：每个 tick 遍历世界已加载的 TileEntity 并 instanceof 判断，
 * 命中才做物品比对；绝大多数 tick 不满足条件，开销可忽略。</p>
 */
public class ShredderBladeHandler {

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != Phase.END) return;
		if (ModItems.blades_alloy_reborn == null || ModItems.scrap_blades_alloy_reborn == null) return;

		MinecraftServer server = MinecraftServer.getServer();
		if (server == null) return;

		for (World world : server.worldServers) {
			if (world == null) continue;
			checkWorld(world);
		}
	}

	private void checkWorld(World world) {
		// 复制一份列表，避免遍历中被替换/卸载元素
		for (Object o : world.loadedTileEntityList.toArray()) {
			if (!(o instanceof TileEntity)) continue;
			TileEntity te = (TileEntity) o;

			// 原版 HBM 粉碎机：槽 27~28
			if (te instanceof TileEntityMachineShredder) {
				TileEntityMachineShredder shredder = (TileEntityMachineShredder) te;
				for (int slot = 27; slot <= 28; slot++) {
					convertIfBroken(shredder, slot);
				}
			}
			// 附属高级粉碎机：槽 15~18
			else if (te instanceof TileEntityAlloyShredder) {
				TileEntityAlloyShredder shredder = (TileEntityAlloyShredder) te;
				for (int slot = TileEntityAlloyShredder.SLOT_BLADE_START; slot <= TileEntityAlloyShredder.SLOT_BLADE_END; slot++) {
					convertIfBroken(shredder, slot);
				}
			}
		}
	}

	/** 槽里是完好高级合金刀片且耐久耗尽 -> 原地替换为损坏刀片 */
	private void convertIfBroken(net.minecraft.inventory.IInventory inv, int slot) {
		ItemStack stack = inv.getStackInSlot(slot);
		if (stack == null) return;
		if (stack.getItem() != ModItems.blades_alloy_reborn) return;
		if (stack.getItemDamage() >= stack.getMaxDamage()) {
			inv.setInventorySlotContents(slot, new ItemStack(ModItems.scrap_blades_alloy_reborn, 1));
			if (inv instanceof TileEntity) ((TileEntity) inv).markDirty();
		}
	}
}

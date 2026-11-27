package com.makentmgreatagain.container;

import com.makentmgreatagain.tileentity.TileEntityMachineDrain;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 可调节排液管空容器：无物品槽位，仅用于挂载 GUI 与距离校验。
 */
public class ContainerMachineDrain extends Container {

	private final TileEntityMachineDrain te;

	public ContainerMachineDrain(TileEntityMachineDrain te) {
		this.te = te;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return this.te != null && this.te.hasPermission(player);
	}
}

package com.makentmgreatagain.hook;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

public class CigaretteDeathHandler {

	@SubscribeEvent
	public void onDeath(LivingDeathEvent event) {
		if (event.entity == null || event.entity.worldObj.isRemote) return;
		if (!(event.entity instanceof EntityPlayer)) return;
		event.entity.getEntityData().setInteger("mntga.cigCount", 0);
	}
}

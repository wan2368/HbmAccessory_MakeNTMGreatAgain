package com.makentmgreatagain.client;

import com.hbm.extprop.HbmPlayerProps;
import com.hbm.handler.ArmorModHandler;
import com.makentmgreatagain.init.ModItems;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;

/** Calls from EntityRenderer only; never changes an entity's potion state. */
@SideOnly(Side.CLIENT)
public final class NeutrinoNightVision {

	// The local renderer uses a cached lightmap. Remember what its last update actually rendered.
	private static boolean gogglesInLightmap;
	// Snapshot the most recent renderer check so a HUD packet between it and the
	// brightness calculation cannot make that calculation try to read a missing potion.
	private static boolean gogglesForBrightness;

	private NeutrinoNightVision() { }

	static boolean isActive(EntityPlayer player) {
		if(player == null) return false;
		ItemStack helmet = player.getCurrentArmor(3);
		if(helmet == null || !HbmPlayerProps.getData(player).enableHUD) return false;
		ItemStack headMod = ArmorModHandler.pryMod(helmet, ArmorModHandler.helmet_only);
		return headMod != null && headMod.getItem() == ModItems.neutrino_goggles;
	}

	public static boolean lightmapNightVision(boolean vanilla) {
		gogglesInLightmap = isActive(Minecraft.getMinecraft().thePlayer);
		gogglesForBrightness = gogglesInLightmap;
		return vanilla || gogglesInLightmap;
	}

	public static boolean fogNightVision(boolean vanilla) {
		gogglesForBrightness = isActive(Minecraft.getMinecraft().thePlayer);
		return vanilla || gogglesForBrightness;
	}

	public static boolean refreshLightmap(boolean vanillaDirty) {
		return vanillaDirty || gogglesInLightmap != isActive(Minecraft.getMinecraft().thePlayer);
	}

	public static int nightVisionDuration(EntityLivingBase entity, Potion potion) {
		// Vanilla returns a stable 1.0 above 200 ticks. No effect object is needed for goggles.
		if(gogglesForBrightness) return 201;
		return entity.getActivePotionEffect(potion).getDuration();
	}
}

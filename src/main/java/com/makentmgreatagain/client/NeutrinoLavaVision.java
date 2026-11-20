package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.event.EntityViewRenderEvent.FogColors;
import net.minecraftforge.client.event.EntityViewRenderEvent.FogDensity;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;

@SideOnly(Side.CLIENT)
public final class NeutrinoLavaVision {

	private static boolean isActive(EntityLivingBase view) {
		Minecraft mc = Minecraft.getMinecraft();
		return mc.thePlayer != null && view == mc.thePlayer && view == mc.renderViewEntity
				&& mc.gameSettings.thirdPersonView == 0 && NeutrinoNightVision.isActive(mc.thePlayer);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void onFogDensity(FogDensity event) {
		if(event.block.getMaterial() != Material.lava || !isActive(event.entity)
				|| event.entity.isPotionActive(Potion.blindness)) return;

		// Forge skips vanilla's density branch when canceled. Set the mode explicitly
		// so the result does not depend on the previous frame or HBM's soot fog.
		GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
		GL11.glFogf(GL11.GL_FOG_START, 12F);
		GL11.glFogf(GL11.GL_FOG_END, 48F);
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onFogColors(FogColors event) {
		if(event.block.getMaterial() != Material.lava || !isActive(event.entity)
				|| event.entity.isPotionActive(Potion.blindness)) return;

		event.red = 0.65F;
		event.green = 0.32F;
		event.blue = 0.16F;
	}

	@SubscribeEvent
	public void onFireOverlay(RenderBlockOverlayEvent event) {
		if(event.overlayType != RenderBlockOverlayEvent.OverlayType.FIRE || !isActive(event.player)) return;

		// Use the same camera/flowing liquid check as vanilla fog. Only remove the
		// first-person overlay while submerged; the player still burns and takes damage.
		if(ActiveRenderInfo.getBlockAtEntityViewpoint(event.player.worldObj, event.player,
				event.renderPartialTicks).getMaterial() == Material.lava) {
			event.setCanceled(true);
		}
	}
}

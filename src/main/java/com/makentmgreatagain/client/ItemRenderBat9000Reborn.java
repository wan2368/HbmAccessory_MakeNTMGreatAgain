package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.item.ItemRenderBase;

import net.minecraft.client.Minecraft;

/**
 * 「巨尻-9000」储罐（重生版）的物品渲染：
 */
public class ItemRenderBat9000Reborn extends ItemRenderBase {

	@Override
	public void renderInventory() {
		GL11.glTranslated(0, -3, 0);
		GL11.glScaled(2, 2, 2);
	}

	@Override
	public void renderCommon() {
		GL11.glShadeModel(GL11.GL_SMOOTH);
		Minecraft.getMinecraft().renderEngine.bindTexture(AddonModels.bat9000_tex);
		AddonModels.bat9000Model().renderAll();
		GL11.glShadeModel(GL11.GL_FLAT);
	}
}

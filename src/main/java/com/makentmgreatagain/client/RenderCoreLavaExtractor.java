package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.client.AddonModels;
import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 地核熔岩开采机渲染器（模型照搬旧版 NTM 1.0.27「AMS发射极」的 ams_emitter.obj）。
 *
 * <p>模型原点在核心格底面中心，绕 Y 转 180°（旧版如此）。
 * 旧版的熔毁换模与向下光束是开发期效果，本机改为 GUI 机器后不再渲染。</p>
 */
public class RenderCoreLavaExtractor extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);

		renderModel();

		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glPopMatrix();
	}

	private void renderModel() {
		bindTexture(AddonModels.core_lava_extractor_tex);
		AddonModels.coreLavaExtractorModel().renderAll();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.core_lava_extractor);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 5×5×6 结构，与混合分馏塔同体积，沿用同一缩放
				GL11.glTranslated(0, -1.8, 0);
				GL11.glScaled(1.58, 1.58, 1.58);
			}
			public void renderCommon() {
				GL11.glDisable(GL11.GL_CULL_FACE);
				GL11.glShadeModel(GL11.GL_SMOOTH);
				renderModel();
				GL11.glShadeModel(GL11.GL_FLAT);
				GL11.glEnable(GL11.GL_CULL_FACE);
			}
		};
	}
}

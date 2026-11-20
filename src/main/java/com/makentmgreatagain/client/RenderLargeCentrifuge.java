package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 大型离心机渲染器：直接按旧版回旋加速器的分组（Body / B1~B4）和贴图渲染。
 *
 * <p>5×5 占地是正方形、模型居中，所以不需要按朝向旋转。
 * 四个 B 部件只是装饰，没有交互，因此始终使用空槽贴图。</p>
 */
public class RenderLargeCentrifuge extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);

		renderCommon();

		GL11.glPopMatrix();
	}

	private void renderCommon() {

		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		bindTexture(AddonModels.centrifuge_tex);
		AddonModels.centrifugeModel().renderPart("Body");

		bindTexture(AddonModels.centrifuge_ashes);
		AddonModels.centrifugeModel().renderPart("B1");

		bindTexture(AddonModels.centrifuge_book);
		AddonModels.centrifugeModel().renderPart("B2");

		bindTexture(AddonModels.centrifuge_gavel);
		AddonModels.centrifugeModel().renderPart("B3");

		bindTexture(AddonModels.centrifuge_coin);
		AddonModels.centrifugeModel().renderPart("B4");

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.large_centrifuge);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 5×5×3.75 的模型，按方块图标尺度缩到槽内
				GL11.glTranslated(0, -1.6, 0);
				GL11.glScaled(2.0, 2.0, 2.0);
			}
			public void renderCommon() {
				RenderLargeCentrifuge.this.renderCommon();
			}
		};
	}
}

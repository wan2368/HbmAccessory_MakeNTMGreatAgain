package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 仿星器渲染器（31×29×9）。
 *
 * <p>模型 Y 轴向上、1 单位 = 1 格，原点在核心格底面中心：X ±14.89、Z ±13.94、高 8.806。
 * 旋转表沿用本体机器：南 0° / 北 180° / 东 90° / 西 270°。</p>
 */
public class RenderStellarator extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 2: GL11.glRotatef(180F, 0F, 1F, 0F); break;	// 北
		case 3: GL11.glRotatef(0F, 0F, 1F, 0F); break;		// 南
		case 4: GL11.glRotatef(270F, 0F, 1F, 0F); break;	// 西
		case 5: GL11.glRotatef(90F, 0F, 1F, 0F); break;		// 东
		}

		renderModel();

		GL11.glPopMatrix();
	}

	private void renderModel() {
		bindTexture(AddonModels.stellarator_tex);
		AddonModels.stellaratorModel().renderAll();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.stellarator);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 模型 29.78 格宽，0.48 倍约 14.3 px，保证图标不超过 15×15
				GL11.glScaled(0.48, 0.48, 0.48);
			}
			@Override
			public void renderNonInv() {
				// 外层已乘 0.25，净缩放 0.05 → 手持 / 掉落物都是原来的 2 倍
				GL11.glScaled(0.2, 0.2, 0.2);
				GL11.glTranslated(0, -4.4, 0);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel();
			}
		};
	}
}

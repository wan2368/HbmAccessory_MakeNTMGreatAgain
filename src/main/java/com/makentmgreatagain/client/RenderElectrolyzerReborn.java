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
 * 小型电解机渲染器。
 *
 * <p>模型以 2×2 足迹中心为原点，而多方块核心在其中一个角上，
 * 所以除了旋转之外还要把模型平移 (-0.5, 0, +0.5) 个方块，让模型中心和足迹中心对齐。</p>
 *
 * <p>旋转表与换热器、环境地下泵一致：SOUTH=90°、EAST=180°、NORTH=270°、WEST=0°。</p>
 */
public class RenderElectrolyzerReborn extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 2: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}

		// 先旋转再平移，偏移量才会跟着机器朝向一起转
		// 模型 +X = -dir、+Z = -rot，足迹中心相对核心格是 (-0.5 dir, -0.5 rot) = 模型 (+0.5, 0, -0.5)
		GL11.glTranslated(0.5D, 0D, -0.5D);

		renderCommon();

		GL11.glPopMatrix();
	}

	private void renderCommon() {
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		bindTexture(AddonModels.electrolyzer_tex);
		AddonModels.electrolyzerModel().renderAll();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.electrolyzer_reborn);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				GL11.glTranslated(0, -3.25, 0);
				GL11.glScaled(3.5, 3.5, 3.5);
			}
			public void renderCommon() {
				RenderElectrolyzerReborn.this.renderCommon();
			}
		};
	}
}

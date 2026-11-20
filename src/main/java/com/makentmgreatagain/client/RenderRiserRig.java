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
 * 海面立管钻机渲染器。
 *
 * <p>模型原点就是 7×7 足迹的中心、结构底面，所以平移到核心格中心后
 * 不需要额外偏移；旋转表沿用原版井架（{@code RenderDerrick}）：
 * 基础 90°，再按 metadata 2/4/3/5 分别叠加 90/180/270/0 度。</p>
 *
 * <p>泵送落下的管道是世界里真实存在的方块，由 {@link RenderRiserPipe} 单独渲染。</p>
 */
public class RenderRiserRig extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);

		GL11.glRotatef(90, 0F, 1F, 0F);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 2: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 3: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}

		renderCommon();

		GL11.glPopMatrix();
	}

	private void renderCommon() {
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		bindTexture(AddonModels.riser_rig_tex);
		AddonModels.riserRigModel().renderAll();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.riser_rig);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 模型 7 宽 × 16 高：比上一版放大 15%，下移量再减少 33%（整体上移）
				GL11.glTranslated(0, -3.6, 0);
				GL11.glScaled(0.8, 0.8, 0.8);
			}
			public void renderCommon() {
				RenderRiserRig.this.renderCommon();
			}
		};
	}
}

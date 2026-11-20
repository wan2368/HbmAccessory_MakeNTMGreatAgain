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
 * 电弧加热器渲染器。
 *
 * <p>模型原点在 4×3 占地的几何中心，而核心格位于模型的（-1, -1, 下层）——
 * 也就是模型原点相对核心格是（+1, 0, -1），所以旋转后要按这个偏移平移。</p>
 *
 * <p>旋转表与换热器、小型电解机一致：SOUTH=90°、EAST=180°、NORTH=270°、WEST=0°。</p>
 */
public class RenderArcHeater extends TileEntitySpecialRenderer implements IItemRendererProvider {

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

		// 先旋转再平移；核心格就在接线箱那一格，模型原点在它 -X 方向 2 格处
		GL11.glTranslated(-2.0D, 0D, 0D);

		renderCommon();

		GL11.glPopMatrix();
	}

	private void renderCommon() {
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		// 顶盖、接线箱、机体用原版电加热器贴图；窗口里的铜管用原版电弧炉贴图
		bindTexture(AddonModels.arc_heater_top_tex);
		AddonModels.arcHeaterBodyModel().renderAll();

		bindTexture(AddonModels.arc_heater_coil_tex);
		AddonModels.arcHeaterCoilModel().renderAll();

		bindTexture(AddonModels.arc_heater_top_tex);
		AddonModels.arcHeaterTopModel().renderAll();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.arc_heater);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 模型 4 宽 × 2 高：缩放 3.5 后约 12 格高，再下移 2.1 格让图标在槽内居中
				GL11.glTranslated(0, -2.1, 0);
				GL11.glScaled(3.5, 3.5, 3.5);
			}
			public void renderCommon() {
				RenderArcHeater.this.renderCommon();
			}
		};
	}
}

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
 * 混合催化分馏塔渲染器。
 *
 * <p>模型原点在核心格底面的中心（和原版分馏塔一致），所以只平移到格子中心，不需要按朝向旋转：
 * 塔身是八棱柱，四面结构对称，8 个检修口绕轴转 90° 后仍是同一组位置。</p>
 */
public class RenderHybridCatalyticFractionTower extends TileEntitySpecialRenderer implements IItemRendererProvider {

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

		bindTexture(AddonModels.hybrid_catalytic_fraction_tower_tex);
		AddonModels.hybridCatalyticFractionTowerModel().renderAll();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_hybrid_catalytic_fraction_tower);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 原版分馏塔是 3×3×3、缩放 3.25；这里是 5×5×6，按体积比例缩到 1.58
				GL11.glTranslated(0, -1.8, 0);
				GL11.glScaled(1.58, 1.58, 1.58);
			}
			public void renderCommon() {
				RenderHybridCatalyticFractionTower.this.renderCommon();
			}
		};
	}
}

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
 * 工业燃气轮机渲染器（纯外观，4×13×4）。
 *
 * <p>模型来自 {@code industial_gas_turbine.blend}：Y 轴向上、1 单位 = 1 格，
 * 底面在 y=0，长度沿 Z 轴 ±6.5，宽度沿 X 轴 [-1.5, 2.5]。
 * 因此渲染锚点就是核心格底面中心，不需要额外平移；只有朝向旋转。</p>
 *
 * <p>旋转表沿用本体 {@code RenderIndustrialTurbine}（同样是以「朝南」为模型的默认朝向）：
 * 南 0° / 北 180° / 东 90° / 西 270°。</p>
 */
public class RenderIndustrialGasTurbine extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		// 模型已改为封闭实体、法线全部朝外，开启背面剔除；关掉剔除会看到内壁，产生“透见内部”和闪烁
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
		bindTexture(AddonModels.industrial_gas_turbine_tex);
		AddonModels.industrialGasTurbineModel().renderAll();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.industrial_gas_turbine);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 13 格长、4 格宽 4 格高；X 方向偏了半格，先挪回槽位中心
				GL11.glTranslated(-0.5, 0, 0);
				GL11.glScaled(1.15, 1.15, 1.15);
			}
			@Override
			public void renderNonInv() {
				// 基类已经缩到 0.25，13 格长还是会撑满视野，再缩一次
				GL11.glTranslated(-0.5, 0, 0);
				GL11.glScaled(0.28, 0.28, 0.28);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel();
			}
		};
	}
}

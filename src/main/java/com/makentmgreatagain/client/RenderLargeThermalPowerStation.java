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
 * 大型火力发电站渲染器（6×4×5）。
 *
 * <p>模型 Y 轴向上、1 单位 = 1 格，原点在核心格底面中心：
 * 长度沿 Z（±3）、宽度沿 X（[-1.5, 2.5]）、高度 0~5。</p>
 *
 * <p>旋转表沿用本体机器：南 0° / 北 180° / 东 90° / 西 270°。</p>
 */
public class RenderLargeThermalPowerStation extends TileEntitySpecialRenderer implements IItemRendererProvider {

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
		bindTexture(AddonModels.large_thermal_power_station_tex);
		AddonModels.largeThermalPowerStationModel().renderAll();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.large_thermal_power_station);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 4 格宽相对核心偏了半格，先挪回槽位中心
				GL11.glTranslated(-0.5, 0, 0);
				GL11.glScaled(1.6, 1.6, 1.6);
			}
			@Override
			public void renderNonInv() {
				GL11.glTranslated(-0.5, 0, 0);
				GL11.glScaled(0.4, 0.4, 0.4);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel();
			}
		};
	}
}

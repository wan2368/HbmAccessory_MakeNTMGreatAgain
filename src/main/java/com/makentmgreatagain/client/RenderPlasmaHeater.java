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
 * 等离子加热器渲染器（21×7×8 长条机器）。
 *
 * <p>模型原点在核心格底面中心、长轴在 OBJ +X、桶管朝 -X、1 单位 = 1 格。
 * 旋转表与电弧加热器一致（SOUTH=90°、EAST=180°、NORTH=270°、WEST=0°）——
 * 这张表必须与 {@link com.hbm.blocks.BlockDummyable} 的 {@code bounding} 碰撞盒旋转
 * （{@code getAABBRotationOffset}）严格同相位，碰撞才会贴着模型走。</p>
 */
public class RenderPlasmaHeater extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(90F, 0F, 1F, 0F); break;		// 南
		case 5: GL11.glRotatef(180F, 0F, 1F, 0F); break;	// 东
		case 2: GL11.glRotatef(270F, 0F, 1F, 0F); break;	// 北
		case 4: GL11.glRotatef(0F, 0F, 1F, 0F); break;		// 西
		}

		renderModel();

		GL11.glPopMatrix();
	}

	private void renderModel() {
		bindTexture(AddonModels.plasma_heater_tex);
		AddonModels.plasmaHeaterModel().renderAll();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.plasma_heater);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 模型 20.26 格长，0.69 倍约 14 px，保证图标不超过 15×15
				GL11.glScaled(0.69, 0.69, 0.69);
			}
			@Override
			public void renderNonInv() {
				// 外层已乘 0.25，净缩放 0.05 → 手持 / 掉落物都是原来的 2 倍
				GL11.glScaled(0.2, 0.2, 0.2);
				GL11.glTranslated(0, -3.5, 0);
			}
			@Override
			public void renderCommon() {
				GL11.glEnable(GL11.GL_CULL_FACE);
				renderModel();
			}
		};
	}
}

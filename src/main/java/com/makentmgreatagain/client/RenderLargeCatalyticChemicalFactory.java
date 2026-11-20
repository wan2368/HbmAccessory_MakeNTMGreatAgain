package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;

/**
 * 大型催化化工厂渲染器。
 *
 * <p>渲染逻辑照搬旧版 {@code RenderChemfac}：模型原点在 8×8 足迹的中心，
 * 相对核心方块还要平移 {@code (0.5, 0, -0.5)}；朝向换算表也与旧版一致
 * （NORTH=90°、WEST=180°、SOUTH=270°、EAST=0°）。</p>
 *
 * <p>两个顶置风扇绕 {@code y} 轴旋转，轴心在模型 {@code x = ±1} 处；
 * 角度来自 {@link TileEntityLargeCatalyticChemicalFactory#rot}，用 partialTicks 在
 * {@code prevRot} 与 {@code rot} 之间插值。</p>
 */
public class RenderLargeCatalyticChemicalFactory extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 5: GL11.glRotatef(180F, 0F, 1F, 0F); break;
		case 2: GL11.glRotatef(270F, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(0F, 0F, 1F, 0F); break;
		case 3: GL11.glRotatef(90F, 0F, 1F, 0F); break;
		}

		// 核心方块在足迹中心偏 (-0.5, +0.5)
		GL11.glTranslated(0.5D, 0.0D, -0.5D);

		float rot = 0F;
		if(tile instanceof TileEntityLargeCatalyticChemicalFactory) {
			TileEntityLargeCatalyticChemicalFactory chemfac = (TileEntityLargeCatalyticChemicalFactory) tile;
			rot = chemfac.prevRot + (chemfac.rot - chemfac.prevRot) * interp;
		}

		renderModel(rot);

		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glPopMatrix();
	}

	private void renderModel(float rot) {

		GL11.glShadeModel(GL11.GL_SMOOTH);
		bindTexture(AddonModels.large_catalytic_chemical_factory_tex);

		AddonModels.largeCatalyticChemicalFactoryModel().renderPart("Main");

		GL11.glPushMatrix();
		GL11.glTranslated(1.0D, 0.0D, 0.0D);
		GL11.glRotated(rot, 0.0D, -1.0D, 0.0D);
		GL11.glTranslated(-1.0D, 0.0D, 0.0D);
		AddonModels.largeCatalyticChemicalFactoryModel().renderPart("Fan1");
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslated(-1.0D, 0.0D, 0.0D);
		GL11.glRotated(rot, 0.0D, -1.0D, 0.0D);
		GL11.glTranslated(1.0D, 0.0D, 0.0D);
		AddonModels.largeCatalyticChemicalFactoryModel().renderPart("Fan2");
		GL11.glPopMatrix();

		GL11.glShadeModel(GL11.GL_FLAT);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_large_catalytic_chemical_factory);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 8×8×4 按原版图标尺度缩到 16 像素见方
				GL11.glTranslated(0, -1.75, 0);
				GL11.glScaled(1.9, 1.9, 1.9);
			}

			@Override
			public void renderCommon() {
				GL11.glDisable(GL11.GL_CULL_FACE);
				renderModel(0F);
				GL11.glEnable(GL11.GL_CULL_FACE);
			}
		};
	}
}

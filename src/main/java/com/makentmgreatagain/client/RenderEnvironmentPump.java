package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

/**
 * 环境地下泵渲染器。
 */
public class RenderEnvironmentPump extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		TileEntityEnvironmentPump pump = (TileEntityEnvironmentPump) tile;

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 2: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}

		float angle = pump.lastRotor + (pump.rotor - pump.lastRotor) * interp;
		renderCommon(angle, AddonModels.pumpTexture(pump.outputTanks[0].getTankType()));

		GL11.glPopMatrix();
	}

	private void renderCommon(double rot, ResourceLocation texture) {
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		bindTexture(texture);
		AddonModels.pumpModel().renderPart("Base");

		GL11.glPushMatrix();
		GL11.glTranslated(0, 2.25, 0);
		GL11.glRotated(rot - 90, 0, 0, 1);
		GL11.glTranslated(0, -2.25, 0);
		AddonModels.pumpModel().renderPart("Rotor");
		GL11.glPopMatrix();

		double sin = Math.sin(rot * Math.PI / 180D) * 0.5D - 0.5D;
		double cos = Math.cos(rot * Math.PI / 180D) * 0.5D;
		double ang = Math.acos(cos / 2D);
		double cath = Math.sqrt(1 + (cos * cos) / 2);

		GL11.glPushMatrix();
		GL11.glTranslated(0, 1 - cath + sin, 0);
		GL11.glTranslated(0, 4.75, 0);
		GL11.glRotated(ang * 180D / Math.PI - 90D, 0, 0, -1);
		GL11.glTranslated(0, -4.75, 0);
		AddonModels.pumpModel().renderPart("Arms");
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslated(0, 1 - cath + sin, 0);
		AddonModels.pumpModel().renderPart("Piston");
		GL11.glPopMatrix();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.environment_pump);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				GL11.glTranslated(0, -3, 0);
				GL11.glScaled(2.5, 2.5, 2.5);
			}
			public void renderCommonWithStack(ItemStack item) {
				RenderEnvironmentPump.this.renderCommon(System.currentTimeMillis() % 3600 * 0.1F, AddonModels.pump_default_tex);
			}
		};
	}
}

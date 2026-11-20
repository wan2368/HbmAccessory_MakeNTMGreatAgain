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
 * 电力高炉渲染器。
 *
 * <p>模型原点在 5×5 足迹的底面中心，相对核心方块只需要平移到格子中心。</p>
 *
 * <p>模型正面（控制面板 / 炉门 / 铭牌）在模型 +Z 方向，而基类的朝向表里
 * {@code dir} 与玩家视线相反，所以正面朝玩家的角度是：
 * NORTH=0°、WEST=90°、SOUTH=180°、EAST=270°。</p>
 */
public class RenderElectricBlastFurnace extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);

		switch(tile.getBlockMetadata() - BlockDummyable.offset) {
		case 3: GL11.glRotatef(180F, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(90F, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(270F, 0F, 1F, 0F); break;
		default: break;
		}

		renderCommon();

		GL11.glPopMatrix();
	}

	private void renderCommon() {
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		bindTexture(AddonModels.electric_blast_furnace_tex);
		AddonModels.electricBlastFurnaceModel().renderAll();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_electric_blast_furnace);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 5×5×7，物品栏显示再放大 15%（1.35 × 1.15）
				GL11.glTranslated(0, -1.5, 0);
				GL11.glScaled(1.5525, 1.5525, 1.5525);
			}
			public void renderCommon() {
				RenderElectricBlastFurnace.this.renderCommon();
			}
		};
	}
}

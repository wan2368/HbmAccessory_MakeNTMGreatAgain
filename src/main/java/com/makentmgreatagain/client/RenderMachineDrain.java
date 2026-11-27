package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

/**
 * 排液管渲染器（钢制 / 钨制共用）。
 *
 * <p>模型是本体排液管砍掉伸出两格后的 1×1×1 底座（底部内凹 2px 的圆孔），
 * 平移与原版 {@code RenderDrain} 一致，都在 {@code (x + 0.5, y, z + 0.5)}，
 * 即模型坐标原点就在方块底面中心。贴图按方块类型区分：钢制用原版 {@code drain.png}，
 * 钨制用按钨色加深过的 {@code drain_tungsten.png}。</p>
 */
public class RenderMachineDrain extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);

		bindTexture(textureFor(te));
		AddonModels.drainModel().renderAll();

		GL11.glPopMatrix();
	}

	private ResourceLocation textureFor(TileEntity te) {
		if(te.getBlockType() == ModBlocks.machine_drain_adjustable_tungsten) return AddonModels.drain_adjustable_tungsten_tex;
		if(te.getBlockType() == ModBlocks.machine_drain_adjustable_steel) return AddonModels.drain_adjustable_tex;
		if(te.getBlockType() == ModBlocks.machine_drain_tungsten) return AddonModels.drain_tungsten_tex;
		return AddonModels.drain_tex;
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_drain);
	}

	@Override
	public IItemRenderer getRenderer() {
		return itemRenderer(AddonModels.drain_tex);
	}

	public IItemRenderer itemRenderer(final ResourceLocation tex) {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 模型是 1×1×1、以方块底面中心为原点（中心在 y=0.5）。
				// 原版 RenderDrain 的 (-1,-1) 是为 3 格长管身留的，这里按本体尺寸重新算，
				// 让立方体中心正好落在 16×16 槽位的 (8,8)。
				GL11.glTranslated(0, -0.19, 0);
				GL11.glScaled(5, 5, 5);
			}

			@Override
			public void renderCommon() {
				GL11.glRotatef(180, 0F, 1F, 0F);
				bindTexture(tex);
				AddonModels.drainModel().renderAll();
			}
		};
	}
}

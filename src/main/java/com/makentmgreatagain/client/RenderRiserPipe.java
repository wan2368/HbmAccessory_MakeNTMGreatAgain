package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * 输送管道方块的渲染器：在方块中心绘制 1 格高的管段 OBJ。
 *
 * <p>管段模型以方块中心为原点（x/z 为 0），所以平移到 {@code +0.5} 后再画。</p>
 */
public class RenderRiserPipe extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		GL11.glDisable(GL11.GL_CULL_FACE);
		bindTexture(AddonModels.riser_pipe_tex);
		AddonModels.riserPipeModel().renderAll();
		GL11.glEnable(GL11.GL_CULL_FACE);

		GL11.glPopMatrix();
	}
}

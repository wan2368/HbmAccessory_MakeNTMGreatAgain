package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.blocks.BlockWirelessStation;
import com.makentmgreatagain.tileentity.TileEntityWirelessStation;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 无线充电站的方块渲染器：外壳走标准立方体，灯条单独画一遍。
 *
 * <h3>为什么分两遍</h3>
 * <p>用户要的是「只有灯条发光」。如果整块走一遍标准渲染，灯条受方块光照影响，
 * 夜里就是一团暗斑。所以第一遍 {@code renderStandardBlock} 画受光照的六面外壳，
 * 第二遍把<b>正面</b>用灯条贴图重画一次，逐顶点颜色顶满（{@code setColorOpaque}
 * 带 fullBright 亮度）——这一遍与光照无关，等于自发光。</p>
 *
 * <h3>为什么第二遍不能 start/draw</h3>
 * <p>{@code renderWorldBlock} 被调用时世界渲染器<b>已经在 tessellating</b>，
 * 两遍的面都加进同一个批次、由世界渲染器统一 draw。这里若自己
 * {@code startDrawingQuads()} 会抛 {@code IllegalStateException: Already tesselating!}
 * （放置瞬间崩溃的根因）。</p>
 *
 * <h3>为什么开混合</h3>
 * <p>灯条贴图除中间那条窄灯外全是透明像素。方块渲染默认不混合，透明像素会被画成黑框，
 * 所以第二遍必须 {@code GL_BLEND}，让透明部分彻底不落笔。</p>
 *
 * <h3>为什么沿朝向偏移 0.002</h3>
 * <p>灯条面与外壳正面共面，同深度会 z-fighting（隔帧闪）。沿正面法线挪出千分之二格，
 * 既能稳定盖在外壳上，肉眼也看不出错位。</p>
 */
@SideOnly(Side.CLIENT)
public class RenderWirelessStation implements ISimpleBlockRenderingHandler {

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
		Tessellator tess = Tessellator.instance;

		GL11.glPushMatrix();
		GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

		tess.startDrawingQuads();

		tess.setNormal(0F, -1F, 0F);
		renderer.renderFaceYNeg(block, 0D, 0D, 0D, block.getIcon(0, 2));
		tess.setNormal(0F, 1F, 0F);
		renderer.renderFaceYPos(block, 0D, 0D, 0D, block.getIcon(1, 2));
		tess.setNormal(0F, 0F, -1F);
		renderer.renderFaceZNeg(block, 0D, 0D, 0D, block.getIcon(2, 2));
		tess.setNormal(0F, 0F, 1F);
		renderer.renderFaceZPos(block, 0D, 0D, 0D, block.getIcon(3, 2));
		tess.setNormal(-1F, 0F, 0F);
		renderer.renderFaceXNeg(block, 0D, 0D, 0D, block.getIcon(4, 2));
		tess.setNormal(1F, 0F, 0F);
		renderer.renderFaceXPos(block, 0D, 0D, 0D, block.getIcon(5, 2));

		tess.draw();

		GL11.glPopMatrix();
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block,
			int modelId, RenderBlocks renderer) {

		renderer.renderStandardBlock(block, x, y, z);

		int state = 1;
		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityWirelessStation) {
			state = ((TileEntityWirelessStation) te).getLampState();
		}

		IIcon lamp = BlockWirelessStation.lampIcons[MathHelper.clamp_int(state, 0, 2)];
		if(lamp == null) return true;

		int meta = world.getBlockMetadata(x, y, z);
		if(meta < 2 || meta > 5) meta = 2;

		ForgeDirection front = ForgeDirection.getOrientation(meta);
		float push = 0.002F;
		float ox = front.offsetX * push;
		float oy = front.offsetY * push;
		float oz = front.offsetZ * push;

		Tessellator tess = Tessellator.instance;

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		// 世界渲染器此刻正在 tessellating（renderStandardBlock 的面也加在同一批次里），
		// 绝不能 startDrawingQuads/draw 另开批次，否则 IllegalStateException: Already tesselating!
		// 灯条面直接加进当前批次；自发光用逐顶点颜色顶满（setColorOpaque 会带上 fullBright 亮度），
		// 不依赖批次级 setBrightness（那会被外壳面的光照覆盖）。
		tess.setColorOpaque(255, 255, 255);
		tess.addTranslation(ox, oy, oz);

		switch(meta) {
		case 2: renderer.renderFaceZNeg(block, x, y, z, lamp); break;
		case 3: renderer.renderFaceZPos(block, x, y, z, lamp); break;
		case 4: renderer.renderFaceXNeg(block, x, y, z, lamp); break;
		case 5: renderer.renderFaceXPos(block, x, y, z, lamp); break;
		default: break;
		}

		tess.addTranslation(-ox, -oy, -oz);

		GL11.glDisable(GL11.GL_BLEND);

		return true;
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return true;
	}

	@Override
	public int getRenderId() {
		return BlockWirelessStation.RENDER_ID;
	}
}

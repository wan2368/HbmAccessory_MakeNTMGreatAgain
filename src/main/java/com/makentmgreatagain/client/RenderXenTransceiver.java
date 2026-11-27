package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityXenFluidTransceiver;
import com.makentmgreatagain.tileentity.TileEntityXenItemTransceiver;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 流体传送器 / 物品传送器 3D 渲染器。
 *
 * <p>外层深色金属骨架与内凹发光接口环（{@code Frame}）+ 中心悬浮旋转棱面 X 晶体（{@code Core}）；
 * 流体传送器检测到相邻面接入 HBM 流体管道时，会在对应内陷接口自动伸出对齐管道截面的对接套管与法兰。</p>
 */
public class RenderXenTransceiver extends TileEntitySpecialRenderer implements IItemRendererProvider {

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float interp) {
		boolean isFluid = (te instanceof TileEntityXenFluidTransceiver);
		boolean overclocked = false;
		boolean active = false;
		byte connMask = 0;
		if(isFluid) {
			TileEntityXenFluidTransceiver fte = (TileEntityXenFluidTransceiver) te;
			overclocked = fte.overclocked;
			active = fte.lastThroughput > 0 || (fte.peerSenders > 0 && fte.peerReceivers > 0);
			connMask = fte.connectedSidesMask;
		} else if(te instanceof TileEntityXenItemTransceiver) {
			TileEntityXenItemTransceiver ite = (TileEntityXenItemTransceiver) te;
			overclocked = ite.overclocked;
			active = ite.lastItemsMoved > 0 || (ite.peerSenders > 0 && ite.peerReceivers > 0);
		}

		ResourceLocation tex = isFluid
			? (overclocked ? AddonModels.xen_fluid_transceiver_overclock_tex : AddonModels.xen_fluid_transceiver_tex)
			: (overclocked ? AddonModels.xen_item_transceiver_overclock_tex : AddonModels.xen_item_transceiver_tex);

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		renderTransceiver(tex, true, overclocked, active, isFluid ? connMask : 0);
		GL11.glPopMatrix();
	}

	private void renderTransceiver(ResourceLocation tex, boolean animate, boolean overclocked, boolean active, byte connMask) {
		IModelCustom model = AddonModels.xenTransceiverModel();
		bindTexture(tex);

		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_FLAT);

		// 1. 主体框架与内凹接口环
		model.renderPart("Frame");

		// 2. 流体传送器内陷接口 → 外表面管道对接套管与金属法兰（对齐 HBM FluidDuctStandard 0.3125~0.6875 截面）
		if(connMask != 0) {
			for(int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++) {
				ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
				if((connMask & (1 << dir.ordinal())) != 0) {
					renderPipeConnectorSleeve(dir, overclocked);
				}
			}
		}

		// 3. 中心悬浮旋转晶体（稳态慢/中速，超频高速亮橙红辐射辉光）
		double time = animate ? ((System.currentTimeMillis() % 360000L) / 1000.0D) : 0.0D;
		double speed = overclocked ? 220.0D : (active ? 95.0D : 42.0D);
		double bob = animate ? Math.sin(time * (overclocked ? 6.0D : 2.2D)) * (overclocked ? 0.042D : 0.032D) : 0.0D;
		float angle = animate ? (float) ((time * speed) % 360.0D) : 35.0F;

		float prevLX = OpenGlHelper.lastBrightnessX;
		float prevLY = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 0.5D + bob, 0.0D);
		GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(overclocked ? 20.0F : 14.0F, 1.0F, 0.0F, 0.0F);
		if(overclocked) {
			GL11.glColor4f(1.0F, 0.65F, 0.45F, 1.0F);
		}
		model.renderPart("Core");
		if(overclocked) {
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}
		GL11.glPopMatrix();

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevLX, prevLY);
		GL11.glShadeModel(GL11.GL_SMOOTH);
	}

	/**
	 * 在已连接的朝向渲染从内陷接口环伸出到方块外边界（0.5）的密封管道套管与金属法兰。
	 * 基准轴向为 +Y，先渲染窄导管颈部（±0.125, y∈[0.3125,0.4375]），再渲染与 HBM 管道同宽的法兰（±0.1875, y∈[0.4375,0.5000]）。
	 */
	private void renderPipeConnectorSleeve(ForgeDirection dir, boolean overclocked) {
		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 0.5D, 0.0D);
		switch(dir) {
			case DOWN:  GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F); break;
			case UP:    break;
			case NORTH: GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F); break;
			case SOUTH: GL11.glRotatef(90.0F,  1.0F, 0.0F, 0.0F); break;
			case WEST:  GL11.glRotatef(90.0F,  0.0F, 0.0F, 1.0F); break;
			case EAST:  GL11.glRotatef(-90.0F, 0.0F, 0.0F, 1.0F); break;
			default: break;
		}

		Tessellator t = Tessellator.instance;
		// 内层导管颈（与接口内环色调对齐）
		drawTexturedBox(t, -0.125D, 0.3125D, -0.125D, 0.125D, 0.4375D, 0.125D,
				0.6875D, 0.6875D, 0.8125D, 0.8125D);
		// 外层管道法兰（与 HBM 管道 0.375 截面严丝合缝，使用框架深钢材质）
		drawTexturedBox(t, -0.1875D, 0.4375D, -0.1875D, 0.1875D, 0.5000D, 0.1875D,
				0.0000D, 0.9063D, 0.0938D, 1.0000D);
		if(overclocked) {
			// 超频热环提示线
			GL11.glColor4f(1.0F, 0.55F, 0.30F, 1.0F);
			drawTexturedBox(t, -0.15625D, 0.40625D, -0.15625D, 0.15625D, 0.4375D, 0.15625D,
					0.6875D, 0.6875D, 0.8125D, 0.8125D);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}
		GL11.glPopMatrix();
	}

	private static void drawTexturedBox(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1,
			double u0, double v0, double u1, double v1) {
		t.startDrawingQuads();
		// +Y
		t.setNormal(0F, 1F, 0F);
		t.addVertexWithUV(x0, y1, z1, u0, v0);
		t.addVertexWithUV(x1, y1, z1, u1, v0);
		t.addVertexWithUV(x1, y1, z0, u1, v1);
		t.addVertexWithUV(x0, y1, z0, u0, v1);
		// -Y
		t.setNormal(0F, -1F, 0F);
		t.addVertexWithUV(x0, y0, z0, u0, v0);
		t.addVertexWithUV(x1, y0, z0, u1, v0);
		t.addVertexWithUV(x1, y0, z1, u1, v1);
		t.addVertexWithUV(x0, y0, z1, u0, v1);
		// +Z
		t.setNormal(0F, 0F, 1F);
		t.addVertexWithUV(x0, y0, z1, u0, v1);
		t.addVertexWithUV(x1, y0, z1, u1, v1);
		t.addVertexWithUV(x1, y1, z1, u1, v0);
		t.addVertexWithUV(x0, y1, z1, u0, v0);
		// -Z
		t.setNormal(0F, 0F, -1F);
		t.addVertexWithUV(x1, y0, z0, u0, v1);
		t.addVertexWithUV(x0, y0, z0, u1, v1);
		t.addVertexWithUV(x0, y1, z0, u1, v0);
		t.addVertexWithUV(x1, y1, z0, u0, v0);
		// +X
		t.setNormal(1F, 0F, 0F);
		t.addVertexWithUV(x1, y0, z1, u0, v1);
		t.addVertexWithUV(x1, y0, z0, u1, v1);
		t.addVertexWithUV(x1, y1, z0, u1, v0);
		t.addVertexWithUV(x1, y1, z1, u0, v0);
		// -X
		t.setNormal(-1F, 0F, 0F);
		t.addVertexWithUV(x0, y0, z0, u0, v1);
		t.addVertexWithUV(x0, y0, z1, u1, v1);
		t.addVertexWithUV(x0, y1, z1, u1, v0);
		t.addVertexWithUV(x0, y1, z0, u0, v0);
		t.draw();
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.xen_fluid_transceiver);
	}

	@Override
	public IItemRenderer getRenderer() {
		return itemRenderer(AddonModels.xen_fluid_transceiver_tex);
	}

	public IItemRenderer itemRenderer(final ResourceLocation tex) {
		return new ItemRenderBase() {
			@Override
			public void renderInventory() {
				// 物品栏预览放大 75%（5.0 -> 8.75）并向下居中补偿，适配 16×16 槽位
				GL11.glScaled(8.75D, 8.75D, 8.75D);
				GL11.glTranslated(0.0D, -0.27D, 0.0D);
			}

			@Override
			public void renderCommon() {
				GL11.glRotatef(180, 0F, 1F, 0F);
				renderTransceiver(tex, false, false, false, (byte) 0);
			}
		};
	}
}

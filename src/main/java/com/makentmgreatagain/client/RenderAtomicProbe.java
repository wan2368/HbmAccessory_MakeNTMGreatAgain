package com.makentmgreatagain.client;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityAtomicProbe;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

/**
 * 原子投送探头 3D 渲染器：
 * - 三段式暗钢云台（贴面法兰基座 + 偏航 U 形支架 + 俯仰原子镜头筒）
 * - 镜头发光环区分模式（琥珀橙=发送端 / 青蓝=接收端），顶部状态灯区分运行态
 * - 传输瞬间发射双层短脉冲细光束 + 高速原子能量包
 */
public class RenderAtomicProbe extends TileEntitySpecialRenderer {

	private static final ResourceLocation TEX = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/models/atomic_probe.png");

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
		if(!(te instanceof TileEntityAtomicProbe)) return;
		TileEntityAtomicProbe probe = (TileEntityAtomicProbe) te;

		int pass = net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
		if(pass == 0) {
			float interpYaw = lerpAngle(probe.prevYaw, probe.yaw, partialTicks);
			float interpPitch = probe.prevPitch + (probe.pitch - probe.prevPitch) * partialTicks;

			GL11.glPushMatrix();
			GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
			applyFacingRotation(probe.getFacing());
			GL11.glTranslated(0.0D, -0.5D, 0.0D);

			renderProbeModel(probe.isReceiver, probe.state, interpYaw, interpPitch);
			GL11.glPopMatrix();
		}

		// 在半透明通道 (pass == 1) 渲染全向接收能量球与激光
		if(pass == 1 && probe.isReceiver && probe.omniReceiveMode) {
			renderOmniReceptionOrb(probe, x, y, z, partialTicks);
		}
		if(pass == 1 && !probe.isReceiver && probe.hasActiveTarget && probe.clientAligned
				&& (probe.state == TileEntityAtomicProbe.STATE_FIRING || probe.beamHoldTicks > 0)) {
			renderAtomicBeam(probe, x, y, z, partialTicks);
		}
	}

	private void renderProbeModel(boolean isReceiver, byte state, float yaw, float pitch) {
		bindTexture(AddonModels.atomic_probe_tex);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_CULL_FACE);

		net.minecraftforge.client.model.IModelCustom model = AddonModels.atomicProbeModel();

		// 1. 贴面暗钢基座 (OBJ: Base)
		model.renderPart("Base");

		// 2. 偏航云台（绕局部 +Y 旋转，OBJ: Yoke）
		GL11.glPushMatrix();
		GL11.glRotatef(yaw, 0F, 1F, 0F);
		model.renderPart("Yoke");

		// 3. 俯仰镜头筒（绕局部 +X 旋转，OBJ: Head，枢轴高度 Y=0.375 与 TileEntity.getLensWorldPos 严格一致）
		GL11.glTranslated(0.0D, 0.375D, 0.0D);
		GL11.glRotatef(pitch, 1F, 0F, 0F);
		model.renderPart("Head");

		// 发光元素（全亮，不受环境暗度影响）
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

		// 镜头前发光晶面（常亮不闪烁）：发送端=琥珀橙，接收端=青蓝
		if(isReceiver) {
			GL11.glColor4f(0.18F, 0.85F, 1.0F, 1.0F);
		} else {
			GL11.glColor4f(1.0F, 0.62F, 0.15F, 1.0F);
		}
		drawColoredBox(-0.0625, 0.250, -0.0625, 0.0625, 0.262, 0.0625);

		// 顶部状态 LED 灯
		setLedColor(isReceiver, state);
		drawColoredBox(-0.03125, 0.03125, 0.125, 0.03125, 0.09375, 0.136);

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);

		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	private void setLedColor(boolean isReceiver, byte state) {
		if(isReceiver) {
			if(state == TileEntityAtomicProbe.STATE_TARGET_FULL) {
				GL11.glColor4f(1.0F, 0.82F, 0.20F, 1.0F); // 黄：入库满
			} else {
				GL11.glColor4f(0.25F, 1.0F, 0.45F, 1.0F); // 绿：就绪
			}
			return;
		}
		switch(state) {
			case TileEntityAtomicProbe.STATE_IDLE:
			case TileEntityAtomicProbe.STATE_FIRING:
				GL11.glColor4f(0.25F, 1.0F, 0.45F, 1.0F); // 绿
				break;
			case TileEntityAtomicProbe.STATE_ALIGNING:
			case TileEntityAtomicProbe.STATE_TARGET_FULL:
				GL11.glColor4f(1.0F, 0.82F, 0.20F, 1.0F); // 黄
				break;
			default:
				GL11.glColor4f(1.0F, 0.22F, 0.22F, 1.0F); // 红（遮挡/死角/超距/未绑定）
				break;
		}
	}

	/** 多源并发时在接收端青色镜头正上方（局部 Y=0.78）绘制旋转青蓝全向汇聚球 + 能量茎。 */
	private void renderOmniReceptionOrb(TileEntityAtomicProbe probe, double x, double y, double z, float partialTicks) {
		float time = (probe.getWorldObj() != null ? probe.getWorldObj().getTotalWorldTime() : 0L) + partialTicks;
		float spin1 = (time * 4.5F) % 360.0F;
		float spin2 = (-time * 3.0F) % 360.0F;

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
		applyFacingRotation(probe.getFacing());
		GL11.glTranslated(0.0D, -0.5D, 0.0D);

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDepthMask(false);

		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

		// 1. 垂直细能量茎（连接镜头顶面 Y=0.63 到汇聚球心 Y=0.78）
		GL11.glColor4f(0.25F, 0.85F, 1.0F, 0.55F);
		drawColoredBox(-0.025, 0.630, -0.025, 0.025, 0.780, 0.025);

		// 2. 内核：高亮白青旋转晶核
		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 0.78D, 0.0D);
		GL11.glRotatef(spin1, 0F, 1F, 0F);
		GL11.glRotatef(35.0F, 1F, 0F, 1F);
		GL11.glColor4f(0.75F, 0.96F, 1.0F, 0.90F);
		drawColoredBox(-0.055, -0.055, -0.055, 0.055, 0.055, 0.055);
		GL11.glPopMatrix();

		// 3. 外壳：半透明青蓝反向旋转全向汇聚光球
		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 0.78D, 0.0D);
		GL11.glRotatef(spin2, 0F, 1F, 0F);
		GL11.glRotatef(45.0F, 1F, 0F, 0F);
		GL11.glColor4f(0.18F, 0.78F, 1.0F, 0.42F);
		drawColoredBox(-0.105, -0.105, -0.105, 0.105, 0.105, 0.105);
		GL11.glPopMatrix();

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glPopMatrix();
	}

	/** 沿完整链路（发送端 -> 节点1 -> 节点2 -> ... -> 接收端）连续绘制 3px 半透明蓝色激光。 */
	private void renderAtomicBeam(TileEntityAtomicProbe sender, double rx, double ry, double rz, float partialTicks) {
		java.util.List<Vec3> pts = new java.util.ArrayList<Vec3>();
		pts.add(sender.getLensWorldPos());

		for(int[] rc : sender.activeRouteRelays) {
			TileEntity rte = sender.getWorldObj().getTileEntity(rc[0], rc[1], rc[2]);
			if(rte instanceof com.makentmgreatagain.tileentity.TileEntityDeliveryRelay) {
				pts.add(((com.makentmgreatagain.tileentity.TileEntityDeliveryRelay) rte).getCoreWorldPos());
			} else {
				pts.add(Vec3.createVectorHelper(rc[0] + 0.5D, rc[1] + 0.5D, rc[2] + 0.5D));
			}
		}

		TileEntity targetTe = sender.getWorldObj().getTileEntity(sender.activeTargetX, sender.activeTargetY, sender.activeTargetZ);
		boolean targetOmni = false;
		if(targetTe instanceof TileEntityAtomicProbe) {
			TileEntityAtomicProbe recv = (TileEntityAtomicProbe) targetTe;
			targetOmni = recv.isReceiver && recv.omniReceiveMode;
			pts.add(recv.getBeamEndpointWorldPos());
		} else {
			pts.add(Vec3.createVectorHelper(sender.activeTargetX + 0.5D, sender.activeTargetY + 0.5D, sender.activeTargetZ + 0.5D));
		}

		if(pts.size() < 2) return;

		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDepthMask(false);
		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

		double beamR = 0.0925D;
		int segCount = pts.size() - 1;
		for(int i = 0; i < segCount; i++) {
			Vec3 a = pts.get(i);
			Vec3 b = pts.get(i + 1);
			double sx = rx + (a.xCoord - sender.xCoord);
			double sy = ry + (a.yCoord - sender.yCoord);
			double sz = rz + (a.zCoord - sender.zCoord);
			double ex = rx + (b.xCoord - sender.xCoord);
			double ey = ry + (b.yCoord - sender.yCoord);
			double ez = rz + (b.zCoord - sender.zCoord);

			double dx = ex - sx, dy = ey - sy, dz = ez - sz;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if(len < 0.05D) continue;
			double ux = dx / len, uy = dy / len, uz = dz / len;

			// 首段起点从发送端镜头前沿出发；末段非全向模式落入镜头前沿，全向模式直接接入悬浮球心（与中继节点一致）
			if(i == 0 && len > 0.35D) {
				sx += ux * 0.25D; sy += uy * 0.25D; sz += uz * 0.25D;
			}
			if(i == segCount - 1 && !targetOmni && len > 0.35D) {
				ex -= ux * 0.25D; ey -= uy * 0.25D; ez -= uz * 0.25D;
			}

			double refX = Math.abs(uy) < 0.9D ? 0.0D : 1.0D;
			double refY = Math.abs(uy) < 0.9D ? 1.0D : 0.0D;
			double refZ = 0.0D;
			double px = uy * refZ - uz * refY;
			double py = uz * refX - ux * refZ;
			double pz = ux * refY - uy * refX;
			double plen = Math.sqrt(px * px + py * py + pz * pz);
			px /= plen; py /= plen; pz /= plen;
			double qx = uy * pz - uz * py;
			double qy = uz * px - ux * pz;
			double qz = ux * py - uy * px;

			GL11.glDisable(GL11.GL_DEPTH_TEST);
			drawBeamCross(sx, sy, sz, ex, ey, ez, px * beamR, py * beamR, pz * beamR, qx * beamR, qy * beamR, qz * beamR,
					0.25F, 0.68F, 1.0F, 0.24F);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			drawBeamCross(sx, sy, sz, ex, ey, ez, px * beamR, py * beamR, pz * beamR, qx * beamR, qy * beamR, qz * beamR,
					0.25F, 0.68F, 1.0F, 0.48F);
		}

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glPopMatrix();
	}

	private static void drawBeamCross(double sx, double sy, double sz, double ex, double ey, double ez,
	                                  double px, double py, double pz, double qx, double qy, double qz,
	                                  float r, float g, float b, float a) {
		Tessellator t = Tessellator.instance;
		GL11.glColor4f(r, g, b, a);
		t.startDrawingQuads();
		t.addVertex(sx - px, sy - py, sz - pz);
		t.addVertex(sx + px, sy + py, sz + pz);
		t.addVertex(ex + px, ey + py, ez + pz);
		t.addVertex(ex - px, ey - py, ez - pz);

		t.addVertex(sx - qx, sy - qy, sz - qz);
		t.addVertex(sx + qx, sy + qy, sz + qz);
		t.addVertex(ex + qx, ey + qy, ez + qz);
		t.addVertex(ex - qx, ey - qy, ez - qz);
		t.draw();
	}

	private static void applyFacingRotation(ForgeDirection facing) {
		switch(facing) {
			case UP:    break;
			case DOWN:  GL11.glRotatef(180F, 1F, 0F, 0F); break;
			case SOUTH: GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case NORTH: GL11.glRotatef(180F, 0F, 1F, 0F); GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case EAST:  GL11.glRotatef(90F, 0F, 1F, 0F);  GL11.glRotatef(90F, 1F, 0F, 0F); break;
			case WEST:  GL11.glRotatef(-90F, 0F, 1F, 0F); GL11.glRotatef(90F, 1F, 0F, 0F); break;
			default:    break;
		}
	}

	private static float lerpAngle(float a, float b, float t) {
		float d = (b - a) % 360F;
		if(d >= 180F) d -= 360F;
		if(d < -180F) d += 360F;
		return a + d * t;
	}

	private static void drawTexturedBox(double x0, double y0, double z0, double x1, double y1, double z1,
	                                    double u0, double v0, double u1, double v1) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		// +Y
		t.setNormal(0, 1, 0);
		t.addVertexWithUV(x0, y1, z1, u0, v1); t.addVertexWithUV(x1, y1, z1, u1, v1);
		t.addVertexWithUV(x1, y1, z0, u1, v0); t.addVertexWithUV(x0, y1, z0, u0, v0);
		// -Y
		t.setNormal(0, -1, 0);
		t.addVertexWithUV(x0, y0, z0, u0, v0); t.addVertexWithUV(x1, y0, z0, u1, v0);
		t.addVertexWithUV(x1, y0, z1, u1, v1); t.addVertexWithUV(x0, y0, z1, u0, v1);
		// +Z
		t.setNormal(0, 0, 1);
		t.addVertexWithUV(x0, y0, z1, u0, v1); t.addVertexWithUV(x1, y0, z1, u1, v1);
		t.addVertexWithUV(x1, y1, z1, u1, v0); t.addVertexWithUV(x0, y1, z1, u0, v0);
		// -Z
		t.setNormal(0, 0, -1);
		t.addVertexWithUV(x1, y0, z0, u0, v1); t.addVertexWithUV(x0, y0, z0, u1, v1);
		t.addVertexWithUV(x0, y1, z0, u1, v0); t.addVertexWithUV(x1, y1, z0, u0, v0);
		// +X
		t.setNormal(1, 0, 0);
		t.addVertexWithUV(x1, y0, z1, u0, v1); t.addVertexWithUV(x1, y0, z0, u1, v1);
		t.addVertexWithUV(x1, y1, z0, u1, v0); t.addVertexWithUV(x1, y1, z1, u0, v0);
		// -X
		t.setNormal(-1, 0, 0);
		t.addVertexWithUV(x0, y0, z0, u0, v1); t.addVertexWithUV(x0, y0, z1, u1, v1);
		t.addVertexWithUV(x0, y1, z1, u1, v0); t.addVertexWithUV(x0, y1, z0, u0, v0);
		t.draw();
	}

	private static void drawColoredBox(double x0, double y0, double z0, double x1, double y1, double z1) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertex(x0, y1, z1); t.addVertex(x1, y1, z1); t.addVertex(x1, y1, z0); t.addVertex(x0, y1, z0);
		t.addVertex(x0, y0, z0); t.addVertex(x1, y0, z0); t.addVertex(x1, y0, z1); t.addVertex(x0, y0, z1);
		t.addVertex(x0, y0, z1); t.addVertex(x1, y0, z1); t.addVertex(x1, y1, z1); t.addVertex(x0, y1, z1);
		t.addVertex(x1, y0, z0); t.addVertex(x0, y0, z0); t.addVertex(x0, y1, z0); t.addVertex(x1, y1, z0);
		t.addVertex(x1, y0, z1); t.addVertex(x1, y0, z0); t.addVertex(x1, y1, z0); t.addVertex(x1, y1, z1);
		t.addVertex(x0, y0, z0); t.addVertex(x0, y0, z1); t.addVertex(x0, y1, z1); t.addVertex(x0, y1, z0);
		t.draw();
	}

	/** 物品栏/手持 3D 渲染器。 */
	public IItemRenderer getItemRenderer() {
		return new IItemRenderer() {
			@Override public boolean handleRenderType(ItemStack item, ItemRenderType type) { return true; }
			@Override public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) { return true; }
			@Override
			public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
				GL11.glPushMatrix();
				if(type == ItemRenderType.INVENTORY) {
					GL11.glTranslated(0.0D, -0.32D, 0.0D);
					GL11.glScalef(1.35F, 1.35F, 1.35F);
				} else if(type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
					GL11.glTranslated(0.5D, 0.25D, 0.5D);
					GL11.glScalef(1.15F, 1.15F, 1.15F);
				} else {
					GL11.glTranslated(0.0D, -0.25D, 0.0D);
				}
				renderProbeModel(false, TileEntityAtomicProbe.STATE_IDLE, 35F, 32F);
				GL11.glPopMatrix();
			}
		};
	}
}

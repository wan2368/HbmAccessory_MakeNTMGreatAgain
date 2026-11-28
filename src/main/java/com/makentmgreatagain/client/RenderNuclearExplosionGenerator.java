package com.makentmgreatagain.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.tileentity.IItemRendererProvider;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;

/**
 * 次临界核爆发生器渲染器（15×15×19）。
 *
 * <p>模型原点在核心格底面中心（1 单位 = 1 格、Y 轴朝上），正面默认朝 -Z（NORTH）。
 * 腔体中心（0, 9.5, 0）的球体 {@code fuelAtlas} 根据 GUI 核心槽中装入的核心实时显示对应材质配色，
 * 空槽时不渲染；观察窗玻璃在最后开启半透明混合绘制，使玩家能透过玻璃清晰看到腔内核心。</p>
 */
public class RenderNuclearExplosionGenerator extends TileEntitySpecialRenderer implements IItemRendererProvider {

	private static final Map<String, float[]> CORE_COLOR_CACHE = new HashMap<String, float[]>();

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interp) {

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5D, y, z + 0.5D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glRotatef(facingAngle(tile.getBlockMetadata()), 0F, 1F, 0F);

		TileEntityNuclearExplosionGenerator te =
				tile instanceof TileEntityNuclearExplosionGenerator ? (TileEntityNuclearExplosionGenerator) tile : null;
		renderCommon(te, interp);

		GL11.glPopMatrix();
	}

	/** 核心 meta = 10 + 朝向序号（NORTH=2、SOUTH=3、WEST=4、EAST=5）；模型正面默认朝 -Z */
	private static float facingAngle(int meta) {
		switch(meta - BlockDummyable.offset) {
		case 2: return 0F;      // NORTH：-Z，模型本来就朝这边
		case 3: return 180F;    // SOUTH
		case 4: return 90F;     // WEST：-Z 绕 Y 转 +90° 得到 -X
		case 5: return -90F;    // EAST：得到 +X
		default: return 0F;
		}
	}

	private void renderCommon(TileEntityNuclearExplosionGenerator te, float interp) {

		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glShadeModel(GL11.GL_FLAT);

		IModelCustom model = AddonModels.negModel();

		for(AddonModels.NegPart part : AddonModels.neg_parts) {

			// 腔内核心球：世界里只有核心槽装入核心时才显示，并根据对应核心贴图实时着色 + 全亮防漆黑
			if("fuelAtlas".equals(part.group)) {
				if(te != null) {
					String coreId = te.syncedCoreId;
					if(coreId == null || coreId.isEmpty()) continue;

					float[] rgb = getCoreColor(coreId);
					float boost = 1.0F;
					if(te.running) {
						float p = Math.min(1.0F, (te.progress + interp) / (float) TileEntityNuclearExplosionGenerator.CYCLE);
						boost = 1.15F + 0.55F * p;
					} else if(te.cooling) {
						float c = Math.max(0.0F, 1.0F - (te.cool + interp) / (float) TileEntityNuclearExplosionGenerator.CYCLE);
						boost = 1.0F + 0.35F * c;
					}

					float prevX = OpenGlHelper.lastBrightnessX;
					float prevY = OpenGlHelper.lastBrightnessY;
					OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
					GL11.glDisable(GL11.GL_LIGHTING);
					bindTexture(part.texture);
					GL11.glColor4f(
							Math.min(1.0F, rgb[0] * boost),
							Math.min(1.0F, rgb[1] * boost),
							Math.min(1.0F, rgb[2] * boost),
							1.0F);
					model.renderPart(part.group);
					GL11.glEnable(GL11.GL_LIGHTING);
					OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
					continue;
				}
			}

			if(part.translucent) {
				// 先绘制腔内充能切伦科夫辉光 / 核爆强闪光，再盖上半透明观察窗玻璃与窗面溢光
				if(te != null && (te.running || te.cooling)) {
					renderChamberFlash(te, interp);
				}

				bindTexture(part.texture);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
				GL11.glEnable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GL11.glDepthMask(false);
				GL11.glColor4f(part.r, part.g, part.b, 0.45F);
				model.renderPart(part.group);

				// 观察窗爆闪溢光：从外部远距离也能看到 8 扇观察窗同步迸发强光
				if(te != null && (te.running || te.cooling)) {
					renderWindowGlare(model, part.group, te, interp);
				}

				GL11.glDepthMask(true);
				GL11.glDisable(GL11.GL_BLEND);
			} else {
				bindTexture(part.texture);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
				GL11.glColor4f(part.r, part.g, part.b, 1.0F);
				model.renderPart(part.group);
			}
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	/**
	 * 腔体中心 (0, 9.5, 0) 的充能辉光与核爆闪光：
	 * - 充能期（running）：切伦科夫蓝白光晕逐渐收缩增亮，最后 2 秒高频临界闪烁；
	 * - 爆轰瞬间（cooling 0..12t）：炽白强闪光瞬间充满整个内腔（半径达 4.85 格，不穿透外壳）；
	 * - 消散期（cooling 12..200t）：三层嵌套等离子火球由炽白 → 橙金 → 暗红余晖平滑衰减。
	 */
	private static void renderChamberFlash(TileEntityNuclearExplosionGenerator te, float interp) {

		float prevX = OpenGlHelper.lastBrightnessX;
		float prevY = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

		GL11.glPushMatrix();
		GL11.glTranslated(0.0D, 9.5D, 0.0D);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);

		if(te.running) {
			float t = Math.min(1.0F, (te.progress + interp) / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			float flicker = t > 0.78F ? (0.75F + 0.25F * (float) Math.sin((te.progress + interp) * 2.8D)) : 1.0F;
			float intensity = t * t * flicker;

			// 内层炽白压缩核
			drawAdditiveSphere(0.75F + 0.65F * t, 0.75F, 0.92F, 1.0F, 0.35F * intensity);
			// 外层切伦科夫蓝晕
			drawAdditiveSphere(1.25F + 1.45F * t, 0.25F, 0.68F, 1.0F, 0.28F * intensity);
		} else if(te.cooling) {
			float ticks = Math.max(0.0F, te.cool + interp);
			float norm = Math.min(1.0F, ticks / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			float flicker = 0.90F + 0.10F * (float) Math.sin(ticks * 1.9D);

			if(ticks < 14.0F) {
				// 0..0.7s：核爆瞬时炽白强闪，火球急速膨胀充满内腔
				float f = ticks / 14.0F;
				float expand = (float) Math.sin(Math.min(1.0F, f * 1.6F) * Math.PI * 0.5D);
				float flashAlpha = Math.max(0.0F, 1.0F - f * 0.35F);

				drawAdditiveSphere(1.6F + 3.25F * expand, 1.0F, 0.98F, 0.92F, 0.95F * flashAlpha);
				drawAdditiveSphere(1.0F + 2.50F * expand, 1.0F, 0.85F, 0.45F, 0.85F * flashAlpha);
				drawAdditiveSphere(0.8F + 1.80F * expand, 1.0F, 1.0F, 1.0F, 0.95F);
			} else {
				// 0.7s..10s：多层次高温等离子体与余晖指数衰减
				float decay = (float) Math.pow(Math.max(0.0F, 1.0F - (norm - 0.07F) / 0.93F), 1.65D) * flicker;
				float shrink = 0.55F + 0.45F * decay;

				// 外层暗橙红热浪
				drawAdditiveSphere(4.75F * shrink, 1.0F, 0.32F * decay + 0.12F, 0.05F, 0.55F * decay);
				// 中层金橙火球
				drawAdditiveSphere(3.40F * shrink, 1.0F, 0.62F * decay + 0.18F, 0.12F * decay, 0.65F * decay);
				// 内层炽白核心（前 4 秒可见）
				if(decay > 0.25F) {
					float coreA = (decay - 0.25F) / 0.75F;
					drawAdditiveSphere(1.90F * shrink, 1.0F, 0.96F, 0.80F, 0.80F * coreA);
				}
			}
		}

		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
	}

	/** 8 扇观察窗的叠加溢光：爆轰瞬间白炽、消散期暖橙渐暗、充能末期淡蓝闪烁 */
	private static void renderWindowGlare(IModelCustom model, String glassGroup, TileEntityNuclearExplosionGenerator te, float interp) {

		float r, g, b, a;
		if(te.cooling) {
			float ticks = Math.max(0.0F, te.cool + interp);
			float norm = Math.min(1.0F, ticks / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			if(ticks < 16.0F) {
				float f = ticks / 16.0F;
				r = 1.0F;
				g = 0.98F - 0.15F * f;
				b = 0.92F - 0.50F * f;
				a = 0.95F - 0.20F * f;
			} else {
				float decay = (float) Math.pow(Math.max(0.0F, 1.0F - norm), 1.8D);
				r = 1.0F;
				g = 0.55F * decay + 0.12F;
				b = 0.12F * decay;
				a = 0.75F * decay;
			}
		} else {
			float t = Math.min(1.0F, (te.progress + interp) / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			if(t < 0.45F) return;
			float p = (t - 0.45F) / 0.55F;
			float flicker = t > 0.80F ? (0.75F + 0.25F * (float) Math.sin((te.progress + interp) * 2.8D)) : 1.0F;
			r = 0.45F;
			g = 0.80F;
			b = 1.0F;
			a = 0.55F * p * p * flicker;
		}

		if(a <= 0.01F) return;

		float prevX = OpenGlHelper.lastBrightnessX;
		float prevY = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glColor4f(r, g, b, a);
		model.renderPart(glassGroup);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
	}

	/** 绘制纯色叠加发光球（12×8 网格，轻量零分配） */
	private static void drawAdditiveSphere(float radius, float r, float g, float b, float a) {
		if(a <= 0.01F || radius <= 0.01F) return;
		GL11.glColor4f(r, g, b, Math.min(1.0F, a));
		Tessellator tess = Tessellator.instance;
		int stacks = 8;
		int slices = 12;
		tess.startDrawingQuads();
		for(int i = 0; i < stacks; i++) {
			double phi0 = Math.PI * i / stacks - Math.PI * 0.5D;
			double phi1 = Math.PI * (i + 1) / stacks - Math.PI * 0.5D;
			double y0 = Math.sin(phi0) * radius;
			double r0 = Math.cos(phi0) * radius;
			double y1 = Math.sin(phi1) * radius;
			double r1 = Math.cos(phi1) * radius;
			for(int j = 0; j < slices; j++) {
				double th0 = 2.0D * Math.PI * j / slices;
				double th1 = 2.0D * Math.PI * (j + 1) / slices;
				double c0 = Math.cos(th0), s0 = Math.sin(th0);
				double c1 = Math.cos(th1), s1 = Math.sin(th1);
				tess.addVertex(c0 * r0, y0, s0 * r0);
				tess.addVertex(c1 * r0, y0, s1 * r0);
				tess.addVertex(c1 * r1, y1, s1 * r1);
				tess.addVertex(c0 * r1, y1, s0 * r1);
			}
		}
		tess.draw();
	}

	/** 从对应核心物品的 16×16 贴图中心球体区域采样主色调（跳过透明像素与左下角角标），缓存复用 */
	private static float[] getCoreColor(String coreId) {
		float[] cached = CORE_COLOR_CACHE.get(coreId);
		if(cached != null) return cached;

		float[] fallback = new float[] {0.85F, 0.85F, 0.85F};
		ResourceLocation loc = new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/items/core_" + coreId + ".png");
		InputStream in = null;
		try {
			in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
			BufferedImage img = ImageIO.read(in);
			if(img != null) {
				int w = img.getWidth();
				int h = img.getHeight();
				double rSum = 0, gSum = 0, bSum = 0;
				int count = 0;
				// 采样球体中上核心区（避开左下角 F/I 洋红角标与外圈阴影）
				int x0 = w / 4, x1 = (w * 3) / 4;
				int y0 = h / 5, y1 = (h * 5) / 8;
				for(int py = y0; py < y1; py++) {
					for(int px = x0; px < x1; px++) {
						int argb = img.getRGB(px, py);
						int a = (argb >>> 24) & 0xFF;
						if(a < 128) continue;
						int r = (argb >> 16) & 0xFF;
						int g = (argb >> 8) & 0xFF;
						int b = argb & 0xFF;
						rSum += r / 255.0;
						gSum += g / 255.0;
						bSum += b / 255.0;
						count++;
					}
				}
				if(count > 0) {
					// 略微提亮以补偿 fuel_surface.png 本身的灰底色，让腔内色泽还原物品本色
					float r = Math.min(1.0F, (float) (rSum / count) * 1.25F);
					float g = Math.min(1.0F, (float) (gSum / count) * 1.25F);
					float b = Math.min(1.0F, (float) (bSum / count) * 1.25F);
					fallback = new float[] {r, g, b};
				}
			}
		} catch(Exception ignored) {
		} finally {
			if(in != null) {
				try { in.close(); } catch(Exception ignored) { }
			}
		}

		CORE_COLOR_CACHE.put(coreId, fallback);
		return fallback;
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_nuclear_explosion_generator);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			public void renderInventory() {
				// 15×15×19 的巨型机器：适当缩放以完整塞进物品格
				GL11.glTranslated(0, -2.0, 0);
				GL11.glScaled(0.46, 0.46, 0.46);
			}
			public void renderNonInv() {
				GL11.glTranslated(0, -0.45, 0);
				GL11.glScaled(0.18, 0.18, 0.18);
			}
			public void renderCommon() {
				RenderNuclearExplosionGenerator.this.renderCommon(null, 0F);
			}
		};
	}
}

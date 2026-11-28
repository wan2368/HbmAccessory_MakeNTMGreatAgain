package com.makentmgreatagain.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.items.ItemCigaretteDrx;
import com.makentmgreatagain.packet.CigaretteScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

/**
 * DRX「中华牌」香烟全屏精神演出管理器（梦核 / 中式教育压迫感，共 4 档）。
 *
 * <p>全档位统一使用原版 {@link FontRenderer} 像素字体绘制，彻底移除 Java AWT /
 * {@code DynamicTexture} 动态建图，零卡顿且与 {@code "Am I still alive？"} 质感完全一致。</p>
 */
@SideOnly(Side.CLIENT)
public class ScreenEffectManager {

	/** 各档位持续时长（毫秒）：复用 ItemCigaretteDrx.STAGE_DURATIONS_MS，与服务端冷却/处决严格对齐 */
	private static final long[] STAGE_DURATIONS_MS = ItemCigaretteDrx.STAGE_DURATIONS_MS;

	private static final long FADE_IN_MS = 300L;
	private static final long FADE_OUT_MS = 340L;

	/** 1~4 档中央主句 */
	private static final String[][] STAGE_LINES = {
			{ "你看看人家。", "为什么别人都能做到，就你不行？" },        // 1
			{ "我这都是为你好。", "你真让人失望。" },                    // 2
			{ "你的价值是什么？", "你这样下去，以后没人要你。" },        // 3
			{ "Am I still alive？" }                                     // 4
	};

	/** 1~3 档周围陆续浮现的低语（随档位逐渐变多、变密） */
	private static final String[][] STAGE_WHISPERS = {
			{
				"人家考了第一",
				"你怎么这么笨",
				"别人在学你在干嘛",
				"这点事都做不好"
			},
			{
				"考这点分，对得起谁？",
				"别人在学你在干嘛",
				"供你吃供你穿",
				"怎么这么不懂事",
				"不要让我再说第二遍",
				"你能不能听点话？"
			},
			{
				"不要找借口",
				"怎么这么不争气",
				"这道题讲过多少遍了",
				"你什么时候能懂事",
				"考这点分有什么用",
				"白养你这么大",
				"别人都走远了",
				"你还想混到什么时候"
			},
			null
	};

	/** 第 4 档四周低语池（避开屏幕正中央，给 "Am I still alive？" 留出绝对主角空间） */
	private static final String[] STAGE4_SCATTER = {
			"你看看人家",
			"我这都是为你好",
			"你真让人失望",
			"你的价值是什么？",
			"考这点分有什么用",
			"别人在学你在干嘛",
			"你能不能听点话？",
			"以后没人要你",
			"怎么这么不争气",
			"这道题讲过多少遍了",
			"不要让我再说第二遍",
			"你什么时候能懂事",
			"白养你这么大",
			"不要找借口",
			"人家都结婚了，孩子都有了"
	};

	/* ---- 运行状态 ---- */
	private boolean active;
	private int stage;
	private long startMs;
	private List<WhisperEntry> stage4Entries;
	private List<WhisperEntry> sideWhispers;

	private final Random rand = new Random();

	private static class WhisperEntry {
		final String text;
		final float x;
		final float y;
		final float scale;
		final float angleDeg;
		final long spawnMs;
		final float maxAlpha;
		final boolean crimson;

		WhisperEntry(String text, float x, float y, float scale, float angleDeg,
				long spawnMs, float maxAlpha, boolean crimson) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.scale = scale;
			this.angleDeg = angleDeg;
			this.spawnMs = spawnMs;
			this.maxAlpha = maxAlpha;
			this.crimson = crimson;
		}
	}

	/* ================= 生命周期 ================= */

	private void clearActive() {
		this.active = false;
		CigaretteScreenEvent.clientEffectActive = false;
	}

	private void applyPending() {
		int pending = CigaretteScreenEvent.pendingStage;
		if (pending <= 0) return;
		CigaretteScreenEvent.pendingStage = 0;
		this.stage = Math.max(1, Math.min(4, pending));
		this.active = true;
		CigaretteScreenEvent.clientEffectActive = true;
		this.startMs = Minecraft.getSystemTime();
		this.stage4Entries = null;
		this.sideWhispers = null;
	}

	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
		if (!active) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0.0F) {
			clearActive();
			return;
		}
		if (event.type == ElementType.ALL || event.type == ElementType.TEXT) return;
		event.setCanceled(true);
	}

	@SubscribeEvent
	public void onOverlayPost(RenderGameOverlayEvent.Post event) {
		if (event.type != ElementType.ALL) return;

		applyPending();
		if (!active) return;

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0.0F) {
			clearActive();
			CigaretteScreenEvent.pendingStage = 0;
			return;
		}

		long now = Minecraft.getSystemTime();
		long elapsed = now - startMs;
		long duration = STAGE_DURATIONS_MS[stage - 1];
		if (elapsed > duration) {
			clearActive();
			return;
		}

		FontRenderer fr = mc.fontRenderer;
		if (fr == null) return;

		ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		int W = res.getScaledWidth();
		int H = res.getScaledHeight();

		float fade = fadeCurve(elapsed, duration);

		// 1. 梦核底幕：深黑 + 随档位加深的暗红呼吸暗角 + 极细 CRT 扫描线
		drawAtmosphere(W, H, stage, fade, elapsed, duration);

		// 2. 文字演出
		if (stage == 4) {
			if (stage4Entries == null) {
				stage4Entries = buildStage4Whispers(W, H, duration);
			}
			drawStage4(fr, W, H, fade, elapsed, duration);
		} else {
			if (sideWhispers == null) {
				sideWhispers = buildSideWhispers(stage, W, H, duration);
			}
			drawStage1To3(fr, W, H, fade, elapsed, duration);
		}

		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	/* ================= 梦核氛围底幕 ================= */

	private void drawAtmosphere(int W, int H, int st, float fade, long elapsed, long duration) {
		float baseDark = (st == 1 ? 0.93F : st == 2 ? 0.96F : st == 3 ? 0.98F : 1.0F) * fade;
		float pulse = 0.5F + 0.5F * (float) Math.sin(elapsed * 0.0045D);
		float redStrength = (st == 1 ? 0.15F : st == 2 ? 0.24F : st == 3 ? 0.34F : 0.46F)
				* (0.75F + 0.25F * pulse) * fade;

		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glShadeModel(GL11.GL_SMOOTH);

		// 深黑主体
		drawSolidQuad(0, 0, W, H, 0.02F, 0.018F, 0.024F, baseDark);

		// 四周暗红梦核暗角
		float edgeW = W * 0.22F;
		float edgeH = H * 0.24F;
		float cr = 0.48F, cg = 0.04F, cb = 0.06F;
		drawGradQuad(0, 0, W, edgeH, cr, cg, cb, redStrength, cr, cg, cb, 0F, true);
		drawGradQuad(0, H - edgeH, W, edgeH, cr, cg, cb, 0F, cr, cg, cb, redStrength, true);
		drawGradQuad(0, 0, edgeW, H, cr, cg, cb, redStrength * 0.85F, cr, cg, cb, 0F, false);
		drawGradQuad(W - edgeW, 0, edgeW, H, cr, cg, cb, 0F, cr, cg, cb, redStrength * 0.85F, false);

		// 极淡 CRT 扫描线
		float scanA = (0.055F + 0.015F * st) * fade;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setColorRGBA_F(0.65F, 0.72F, 0.80F, scanA);
		for (int y = 0; y < H; y += 3) {
			tess.addVertex(0, y + 1, 0);
			tess.addVertex(W, y + 1, 0);
			tess.addVertex(W, y, 0);
			tess.addVertex(0, y, 0);
		}
		tess.draw();

		// 2~4 档偶发极短横向信号撕裂条
		if (st >= 2) {
			long bucket = elapsed / 110L;
			long hash = (bucket * 1103515245L + 12345L) & 0x7FFFFFFFL;
			if ((hash % (st == 4 ? 4 : 7)) == 0) {
				float gy = (hash % Math.max(1, H - 10));
				float gh = 1.5F + (hash % 3);
				drawSolidQuad(0, gy, W, gh, 0.75F, 0.14F, 0.16F, 0.12F * fade);
			}
		}

		// 第 4 档处决前 0.35 秒：短暂刺目血白闪屏
		if (st == 4 && elapsed > duration - 350L) {
			float f = (float) (elapsed - (duration - 350L)) / 350L;
			float flashA = Math.min(0.85F, f * f * 0.90F);
			drawSolidQuad(0, 0, W, H, 1.0F, 0.82F, 0.82F, flashA);
		}

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
	}

	/* ================= 1~3 档：逐字梦核字幕 + 四周多点低语 ================= */

	private void drawStage1To3(FontRenderer fr, int W, int H, float fade, long elapsed, long duration) {
		String[] lines = STAGE_LINES[stage - 1];
		if (lines == null) return;

		// 周围陆续浮现的低语（避开中央主句区，随档位加密）
		if (sideWhispers != null) {
			for (WhisperEntry w : sideWhispers) {
				if (elapsed < w.spawnMs) continue;
				float wf = Math.min(1.0F, (elapsed - w.spawnMs) / 360.0F) * fade;
				float a = w.maxAlpha * wf;
				if (a <= 0.02F) continue;
				int rgb = w.crimson ? 0xA23238 : 0x9098A4;
				drawPixelText(fr, w.text, w.x, w.y, w.scale, w.angleDeg, rgb, a, false, 0F);
			}
		}

		// 中央主句：极缓呼吸放大 + 微微漂浮 + 逐字显现
		float progress = Math.min(1.0F, (float) elapsed / duration);
		float breathe = 1.55F + 0.16F * progress;
		float floatY = (float) Math.sin(elapsed * 0.0022D) * 1.8F;
		float jitter = (stage >= 3) ? ((float) Math.sin(elapsed * 0.045D) * 0.95F) : 0F;

		// 第 1 句：180ms 开始逐字（每字 60ms）
		String line1 = revealText(lines[0], elapsed - 180L, 60L);
		if (!line1.isEmpty()) {
			float y1 = H * 0.45F + floatY;
			float chromatic = 0.65F + 0.35F * stage;
			drawPixelText(fr, line1, W * 0.5F + jitter, y1, breathe, 0F, 0xECE8E1, fade, true, chromatic);
		}

		// 第 2 句：停顿后落下
		if (lines.length > 1) {
			long l2Start = 180L + lines[0].length() * 60L + 460L;
			String line2 = revealText(lines[1], elapsed - l2Start, 55L);
			if (!line2.isEmpty()) {
				float l2Fade = Math.min(1.0F, Math.max(0.0F, (elapsed - l2Start) / 240.0F)) * fade;
				float y2 = H * 0.56F + floatY * 0.8F;
				int color2 = (stage >= 2) ? 0xE6B8B8 : 0xD8D4CE;
				float chromatic2 = (stage >= 2) ? 1.25F : 0.65F;
				drawPixelText(fr, line2, W * 0.5F - jitter, y2, breathe * 0.84F, 0F, color2, l2Fade, true, chromatic2);
			}
		}
	}

	private List<WhisperEntry> buildSideWhispers(int st, int W, int H, long duration) {
		List<WhisperEntry> list = new ArrayList<WhisperEntry>();
		String[] pool = STAGE_WHISPERS[st - 1];
		if (pool == null || pool.length == 0) return list;

		// 分布于屏幕四周的 8 个候选题位（全部避开中央 0.34~0.66 的主句带）
		float[][] slots = {
				{ 0.16F, 0.18F }, { 0.82F, 0.20F },
				{ 0.18F, 0.80F }, { 0.80F, 0.78F },
				{ 0.48F, 0.16F }, { 0.52F, 0.84F },
				{ 0.14F, 0.48F }, { 0.86F, 0.52F }
		};
		long span = Math.max(800L, duration - 1250L);
		for (int i = 0; i < pool.length; i++) {
			float[] base = slots[i % slots.length];
			float ax = W * base[0] + (rand.nextFloat() * 26F - 13F);
			float ay = H * base[1] + (rand.nextFloat() * 16F - 8F);
			float scale = 0.76F + rand.nextFloat() * 0.24F;
			float ang = (rand.nextFloat() * 14F - 7F);
			long spawn = 450L + (long) ((float) i / Math.max(1, pool.length) * span);
			float maxA = 0.24F + 0.05F * st + rand.nextFloat() * 0.08F;
			list.add(new WhisperEntry(pool[i], ax, ay, scale, ang, spawn, maxA, i % 2 == 1));
		}
		return list;
	}

	/* ================= 第 4 档：四周密集低语 + 中央 "Am I still alive？" ================= */

	private List<WhisperEntry> buildStage4Whispers(int W, int H, long duration) {
		List<WhisperEntry> list = new ArrayList<WhisperEntry>();
		int count = 34;
		long usable = Math.max(1200L, duration - 1450L);
		for (int i = 0; i < count; i++) {
			String text = STAGE4_SCATTER[i % STAGE4_SCATTER.length];
			float x, y;
			int tries = 0;
			do {
				x = W * (0.07F + rand.nextFloat() * 0.86F);
				y = H * (0.07F + rand.nextFloat() * 0.86F);
				tries++;
			} while (tries < 12 && Math.abs(x - W * 0.5F) < W * 0.26F && Math.abs(y - H * 0.5F) < H * 0.17F);

			float scale = 0.68F + rand.nextFloat() * 0.44F;
			float angle = (rand.nextFloat() * 22F - 11F);
			float frac = (float) i / Math.max(1, count - 1);
			long spawn = 650L + (long) (Math.pow(frac, 0.82D) * usable);
			float maxA = 0.22F + rand.nextFloat() * 0.24F;
			boolean crimson = (i % 3 == 0);
			list.add(new WhisperEntry(text, x, y, scale, angle, spawn, maxA, crimson));
		}
		return list;
	}

	private void drawStage4(FontRenderer fr, int W, int H, float fade, long elapsed, long duration) {
		if (stage4Entries != null) {
			for (WhisperEntry e : stage4Entries) {
				if (elapsed < e.spawnMs) continue;
				float sf = Math.min(1.0F, (elapsed - e.spawnMs) / 300.0F);
				float a = e.maxAlpha * sf * fade;
				if (a <= 0.02F) continue;
				int rgb = e.crimson ? 0xA82A30 : 0x9CA3AD;
				drawPixelText(fr, e.text, e.x, e.y, e.scale, e.angleDeg, rgb, a, false, 0F);
			}
		}

		String hero = STAGE_LINES[3][0];
		int baseW = Math.max(1, fr.getStringWidth(hero));
		float progress = Math.min(1.0F, (float) elapsed / duration);
		float eased = progress * progress;
		float maxScale = Math.min(6.0F, (W * 0.86F) / baseW);
		float scale = 1.25F + (maxScale - 1.25F) * eased;

		float tremor = progress > 0.62F ? (progress - 0.62F) / 0.38F : 0F;
		float jx = tremor > 0F ? ((float) Math.sin(elapsed * 0.09D) * 2.2F * tremor) : 0F;
		float jy = tremor > 0F ? ((float) Math.cos(elapsed * 0.11D) * 1.6F * tremor) : 0F;
		float chroma = 0.7F + 2.2F * tremor;

		drawPixelText(fr, hero, W * 0.5F + jx, H * 0.5F + jy, scale, 0F, 0xFFFFFF, fade, true, chroma);
	}

	/* ================= 像素字体渲染（含双层梦核色差残影） ================= */

	private void drawPixelText(FontRenderer fr, String text, float cx, float cy,
			float scale, float angleDeg, int rgb, float alpha, boolean withChroma, float chromaOffset) {
		if (alpha <= 0.02F || text == null || text.isEmpty()) return;

		int aMain = Math.max(4, Math.min(255, (int) (alpha * 255.0F)));
		int w = fr.getStringWidth(text);
		int h = fr.FONT_HEIGHT;
		int ox = Math.round(-w / 2.0F);
		int oy = Math.round(-h / 2.0F);

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glTranslatef(cx, cy, 0F);
		if (angleDeg != 0F) {
			GL11.glRotatef(angleDeg, 0F, 0F, 1F);
		}
		GL11.glScalef(scale, scale, 1F);

		if (withChroma && chromaOffset > 0.05F) {
			int aGhost = Math.max(4, Math.min(255, (int) (alpha * 0.42F * 255.0F)));
			GL11.glPushMatrix();
			GL11.glTranslatef(-chromaOffset * 0.75F, 0F, 0F);
			fr.drawString(text, ox, oy, (aGhost << 24) | 0x3A688A, false);
			GL11.glPopMatrix();
			GL11.glPushMatrix();
			GL11.glTranslatef(chromaOffset, 0.35F, 0F);
			fr.drawString(text, ox, oy, (aGhost << 24) | 0xB8222A, false);
			GL11.glPopMatrix();
		}

		fr.drawString(text, ox, oy, (aMain << 24) | (rgb & 0x00FFFFFF), false);

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
	}

	private static String revealText(String full, long elapsedSinceStart, long msPerChar) {
		if (elapsedSinceStart <= 0L) return "";
		int count = (int) (elapsedSinceStart / Math.max(1L, msPerChar)) + 1;
		if (count >= full.length()) return full;
		return full.substring(0, count);
	}

	/* ================= 基础几何绘制 ================= */

	private void drawSolidQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
		if (w <= 0 || h <= 0 || a <= 0.001F) return;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setColorRGBA_F(r, g, b, a);
		tess.addVertex(x, y + h, 0);
		tess.addVertex(x + w, y + h, 0);
		tess.addVertex(x + w, y, 0);
		tess.addVertex(x, y, 0);
		tess.draw();
	}

	private void drawGradQuad(float x, float y, float w, float h,
			float r0, float g0, float b0, float a0,
			float r1, float g1, float b1, float a1, boolean vertical) {
		if (w <= 0 || h <= 0) return;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		if (vertical) {
			tess.setColorRGBA_F(r1, g1, b1, a1);
			tess.addVertex(x, y + h, 0);
			tess.addVertex(x + w, y + h, 0);
			tess.setColorRGBA_F(r0, g0, b0, a0);
			tess.addVertex(x + w, y, 0);
			tess.addVertex(x, y, 0);
		} else {
			tess.setColorRGBA_F(r0, g0, b0, a0);
			tess.addVertex(x, y + h, 0);
			tess.setColorRGBA_F(r1, g1, b1, a1);
			tess.addVertex(x + w, y + h, 0);
			tess.addVertex(x + w, y, 0);
			tess.setColorRGBA_F(r0, g0, b0, a0);
			tess.addVertex(x, y, 0);
		}
		tess.draw();
	}

	private float fadeCurve(long elapsed, long duration) {
		float in = Math.min((float) elapsed / FADE_IN_MS, 1F);
		float out = Math.min((float) (duration - elapsed) / FADE_OUT_MS, 1F);
		return Math.max(0F, Math.min(in, out));
	}
}

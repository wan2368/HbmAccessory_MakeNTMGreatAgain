package com.makentmgreatagain.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.packet.CigaretteScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;

@SideOnly(Side.CLIENT)
public class ScreenEffectManager {

	/** 档位 1~4 黑幕持续时长（毫秒） = 1.5 秒 */
	public static final long STAGE1_4_DURATION_MS = 1500L;
	/** 档位 5 黑幕持续时长（毫秒）= 5 秒：中央句放大占满 + 散句延迟出现，随后击杀（含创造）。 */
	public static final long STAGE5_DURATION_MS = 5000L;
	/** 淡入/淡出时长（毫秒） */
	private static final long FADE_MS = 250L;

	/** 文字字号（像素，GUI 缩放坐标系）。改小更低调，不抢戏 */
	private static final int FONT_SIZE = 22;
	/** 多行文字行距（像素） */
	private static final int LINE_SPACING = 26;
	/** 档位 1~4 黑幕透明度（0~1，接近全黑） */
	private static final float BLACKOUT_ALPHA = 0.96F;
	/** 洞口宽度/高度（像素，"脚底一两格"） */
	private static final int HOLE_W = 88;
	private static final int HOLE_H = 60;
	/** 洞口中心纵向位置（占屏幕高比例，偏下 = 看脚下） */
	private static final float HOLE_CENTER_Y_RATIO = 0.78F;
	/** 文字相对准星的随机偏移上限（像素） */
	private static final int OFFSET_RANGE = 12;
	/** 文字摇摆幅度（像素）与频率（Hz） */
	private static final float SWAY_AMPLITUDE = 2.5F;
	private static final float SWAY_FREQ_HZ = 1.5F;

	/** 各档位浮现的文字。档位 1 为 null（无文字）。 */
	private static final String[][] STAGE_LINES = {
			null,                                                        // 1
			{ "你看看人家......" },                                       // 2
			{ "都是为你好", "你真让人失望" },                             // 3
			{ "你的价值是什么？", "你这样下去没人要你" },                   // 4
			{ "Am I still alive？" }                                    // 5
	};

	/** 第五档：中央主句（用原版像素字体绘制） */
	private static final String[] STAGE5_CENTER = { "Am I still alive？" };
	/** 第五档：满屏铺开的 8 句中文，全部要出现 */
	private static final String[] STAGE5_SCATTER = {
			"你看看人家",
			"都是为你好",
			"你真让人失望",
			"你的价值是什么？",
			"你这样下去没人要你",
			"人家都结婚了，孩子都有了",
			"你看这P的像你吗？",
			"你能不能听点话？"
	};
	/** 满屏文字缩放区间：最小 = 中央句的一半，最大 = 中央句本身（中央句按默认字号 1.0 绘制） */
	private static final float MIN_SCATTER_SCALE = 0.5F;
	private static final float MAX_SCATTER_SCALE = 1.0F;
	/** 满屏铺字密度：网格间距（像素），越小铺得越密 */
	private static final int SCATTER_SPACING = 32;
	/** 满屏文字随机旋转范围（度），±25° = 轻微倾斜、可阅读、不倒置 */
	private static final float SCATTER_ANGLE_RANGE = 50F;
	/** 散句延迟出现：中央句先展示并放大，过了这个延迟后散句才开始淡入（毫秒） */
	private static final long SCATTER_START_DELAY_MS = 800L;
	/** 同一区域相邻两句的间隔（毫秒）= 0.3 秒 */
	private static final long SCATTER_INTERVAL_MS = 300L;
	/** 单句出现时的淡入时长（毫秒） */
	private static final long SCATTER_FADE_MS = 250L;
	/** 屏幕随机分成几部分的下限 / 上限 */
	private static final int SCATTER_PARTS_MIN = 3;
	private static final int SCATTER_PARTS_MAX = 5;
	/** 横向铺字列距（像素）：决定每块区域横向铺多密 */
	private static final int SCATTER_COL_SPACING = 30;

	/* ---- 运行状态 ---- */
	private boolean active;
	private int stage;
	private long startMs;
	private int offsetX;
	private int offsetY;

	/** 第五档：满屏文字的一次性随机摆放（位置/角度/大小），效果开始时生成，避免每帧跳变 */
	private List<ScatterEntry> scatterEntries;

	private final Random rand = new Random();

	/** 满屏文字的一次摆放描述：含出现时刻（相对效果开始，毫秒） */
	private static class ScatterEntry {
		final String text;
		final int x;
		final int y;
		final float scale;
		final float angleDeg;
		final long spawnTime;
		ScatterEntry(String text, int x, int y, float scale, float angleDeg, long spawnTime) {
			this.text = text; this.x = x; this.y = y; this.scale = scale; this.angleDeg = angleDeg;
			this.spawnTime = spawnTime;
		}
	}

	/* ---- 文字纹理缓存 ---- */
	private static final Map<String, TextAtlas> ATLAS_CACHE = new HashMap<>();
	private static final Random TEX_RAND = new Random();

	private static class TextAtlas {
		final ResourceLocation res;
		final int width;
		final int height;
		TextAtlas(ResourceLocation res, int width, int height) {
			this.res = res;
			this.width = width;
			this.height = height;
		}
	}

	/* ================= 生命周期 ================= */

	/**
	 * 主线程每帧调用：消费网络包写入的待处理档位，真正开始一次效果。
	 */
	private void applyPending() {
		int pending = CigaretteScreenEvent.pendingStage;
		if (pending <= 0) return;
		CigaretteScreenEvent.pendingStage = 0;
		this.stage = Math.max(1, Math.min(5, pending));
		this.active = true;
		this.startMs = Minecraft.getSystemTime();
		this.offsetX = rand.nextInt(OFFSET_RANGE * 2 + 1) - OFFSET_RANGE;
		this.offsetY = rand.nextInt(OFFSET_RANGE * 2 + 1) - OFFSET_RANGE;
		this.scatterEntries = null;   // 第五档满屏摆位重置，重新随机生成
	}

	/**
	 * 效果激活期间取消全部原版 HUD 元素（物品栏/准星/血条等）。
	 * 文字（聊天、F3）保留，反正会被黑幕盖住；关键是物品栏不能露进"脚底洞口"。
	 */
	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
		if (!active) return;
		if (event.type == ElementType.ALL || event.type == ElementType.TEXT) return;
		event.setCanceled(true);
	}

	/**
	 * 绘制黑幕与文字。Post(ALL) 是最晚的 HUD 阶段，保证盖住一切既有界面。
	 */
	@SubscribeEvent
	public void onOverlayPost(RenderGameOverlayEvent.Post event) {
		if (event.type != ElementType.ALL) return;

		// 网络线程写进来的档位在这里（主线程）消费
		applyPending();

		if (!active) return;

		long now = Minecraft.getSystemTime();
		long elapsed = now - startMs;
		long duration = stage == 5 ? STAGE5_DURATION_MS : STAGE1_4_DURATION_MS;
		if (elapsed > duration) {
			active = false;
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		int W = res.getScaledWidth();
		int H = res.getScaledHeight();

		float fade = fadeCurve(elapsed, duration);
		float blackAlpha = (stage == 5 ? 1.0F : BLACKOUT_ALPHA) * fade;

		drawBlackout(W, H, blackAlpha, false);

		if (stage == 5) {
			// 第五档：首次绘制帧生成满屏文字的固定随机摆放，之后保持不变
			if (scatterEntries == null) {
				scatterEntries = generateScatter(W, H);
			}
			drawStage5(mc, W, H, fade, elapsed);
		} else {
			// STAGE_LINES 是 0 基数组：档位 1~4 对应索引 0~3
			String[] lines = STAGE_LINES[stage - 1];
			if (lines != null) {
				drawLines(mc, lines, W, H, fade, elapsed);
			}
		}
	}

	/* ================= 黑幕 ================= */

	private void drawBlackout(int W, int H, float alpha, boolean withHole) {
		if (alpha <= 0.001F) return;

		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		if (!withHole) {
			drawQuad(0, 0, W, H, 0F, 0F, 0F, alpha);
		} else {
			// 屏幕底部中央留一个矩形洞 -> 模拟"只有脚底一两格能看见"
			int cx = W / 2;
			int cy = (int) (H * HOLE_CENTER_Y_RATIO);
			int x1 = cx - HOLE_W / 2;
			int y1 = cy - HOLE_H / 2;
			int x2 = cx + HOLE_W / 2;
			int y2 = cy + HOLE_H / 2;

			drawQuad(0, 0, W, y1, 0F, 0F, 0F, alpha);                       // 上
			drawQuad(0, y2, W, H - y2, 0F, 0F, 0F, alpha);                  // 下
			drawQuad(0, y1, x1, y2 - y1, 0F, 0F, 0F, alpha);                // 左
			drawQuad(x2, y1, W - x2, y2 - y1, 0F, 0F, 0F, alpha);           // 右
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	private void drawQuad(int x, int y, int w, int h, float r, float g, float b, float a) {
		if (w <= 0 || h <= 0) return;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setColorRGBA_F(r, g, b, a);
		tess.addVertex(x, y + h, 0);
		tess.addVertex(x + w, y + h, 0);
		tess.addVertex(x + w, y, 0);
		tess.addVertex(x, y, 0);
		tess.draw();
	}

	/* ================= 文字 ================= */

	private void drawLines(Minecraft mc, String[] lines, int W, int H, float alpha, long elapsed) {
		int n = lines.length;
		// 不晃动：文字固定，随黑幕一起淡入淡出（alpha 即 fade 曲线）
		float sway = 0F;

		for (int i = 0; i < n; i++) {
			String text = lines[i];
			TextAtlas atlas = getAtlas(text);
			if (atlas == null) continue;

			int cx = W / 2 + offsetX;
			float fBaseY = H / 2F + offsetY + (i - (n - 1) / 2F) * LINE_SPACING - atlas.height / 2F;
			int x = Math.round(cx - atlas.width / 2 + sway);
			int y = Math.round(fBaseY);

			drawTextured(mc, atlas, x, y, alpha);
		}
	}

	/** 绑定文字纹理并绘制（白色文字，透明度用 glColor 控制） */
	private void drawTextured(Minecraft mc, TextAtlas atlas, int x, int y, float alpha) {
		if (alpha <= 0.001F) return;

		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		mc.renderEngine.bindTexture(atlas.res);
		GL11.glColor4f(1F, 1F, 1F, alpha);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertexWithUV(x, y + atlas.height, 0, 0, 1);
		tess.addVertexWithUV(x + atlas.width, y + atlas.height, 0, 1, 1);
		tess.addVertexWithUV(x + atlas.width, y, 0, 1, 0);
		tess.addVertexWithUV(x, y, 0, 0, 0);
		tess.draw();

		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	/* ================= 第五档：满屏文字（原版像素字体） ================= */

	/**
	 * 生成第五档散句：屏幕随机切成 3~5 条横带区域，8 句轮流分配到各区域；
	 * 每个区域内部按 0.3 秒间隔逐条出现，下一条落在上一条旁边随机位置。
	 * 每条记录一个相对效果开始的出现时刻（含 SCATTER_START_DELAY_MS 起步延迟）。
	 * 每次效果触发只生成一次（applyPending 重置），保证画面不跳变。
	 */
	private List<ScatterEntry> generateScatter(int W, int H) {
		List<ScatterEntry> list = new ArrayList<>();
		int parts = SCATTER_PARTS_MIN + rand.nextInt(SCATTER_PARTS_MAX - SCATTER_PARTS_MIN + 1);

		float bandH = (float) H / parts;

		for (int p = 0; p < parts; p++) {
			int bandTop = Math.round(p * bandH);
			int bandBottom = Math.round((p + 1) * bandH) - 1;
			if (bandBottom - bandTop < 20) continue;

			// 每块区域横向铺满：从屏幕左缘到右缘，按列距排布；句子从 8 句循环取
			int cols = Math.max(2, W / SCATTER_COL_SPACING);
			// 动态间隔：保证最后一列在击杀(5s)前出现；列少时接近 0.3s，列多时自动压缩
			long usable = STAGE5_DURATION_MS - SCATTER_START_DELAY_MS - 200L;
			long interval = Math.max(80L, usable / (cols - 1));
			for (int c = 0; c < cols; c++) {
				String text = STAGE5_SCATTER[(p * cols + c) % STAGE5_SCATTER.length];
				float scale = MIN_SCATTER_SCALE + rand.nextFloat() * (MAX_SCATTER_SCALE - MIN_SCATTER_SCALE);
				float angle = (rand.nextFloat() * 2F - 1F) * SCATTER_ANGLE_RANGE;

				int x = c * SCATTER_COL_SPACING + rand.nextInt(16);
				int y = bandTop + 6 + rand.nextInt(Math.max(1, bandBottom - bandTop - 12));
				// 各区域第一列同时开始；同区域内按动态间隔逐列出现
				long spawn = SCATTER_START_DELAY_MS + c * interval;

				list.add(new ScatterEntry(text, x, y, scale, angle, spawn));
			}
		}
		return list;
	}

	/** 渲染第五档：中央句先出现并逐步放大占满全屏，随后散句延迟淡入 */
	private void drawStage5(Minecraft mc, int W, int H, float alpha, long elapsed) {
		FontRenderer fr = mc.fontRenderer;
		if (fr == null) return;

		// 1) 散句：每条按自己的出现时刻逐条出现，独立淡入
		if (scatterEntries != null) {
			GL11.glPushMatrix();
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

			for (ScatterEntry e : scatterEntries) {
				// 没到出现时刻的句子不画
				if (elapsed < e.spawnTime) continue;
				long sinceSpawn = elapsed - e.spawnTime;
				float spawnFade = Math.min(1.0F, (float) sinceSpawn / SCATTER_FADE_MS);
				int argb = (int) (alpha * spawnFade * 255.0F) << 24 | 0x00FFFFFF;
				drawRotatedScaled(fr, e.text, e.x, e.y, e.scale, e.angleDeg, argb);
			}

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glPopMatrix();
		}

		// 2) 中央主句：先出现并持续放大，逐步占满全屏
		for (String line : STAGE5_CENTER) {
			// 缓慢放大：前期慢、5 秒结束时刚好占满全屏（easeIn 平方缓动）
			int baseW = fr.getStringWidth(line);
			float progress = (float) elapsed / STAGE5_DURATION_MS;
			float eased = progress * progress;                      // 前期变化极缓
			float maxScale = (float) W / baseW;                     // 最终占满宽度
			float scale = 1.0F + (maxScale - 1.0F) * Math.min(1.0F, eased);

			GL11.glPushMatrix();
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			int argb = (int) (alpha * 255.0F) << 24 | 0x00FFFFFF;

			// 以屏幕中心为锚点放大
			GL11.glTranslatef(W / 2F, H / 2F, 0F);
			GL11.glScalef(scale, scale, 1F);
			fr.drawString(line, Math.round(-baseW / 2F), Math.round(-fr.FONT_HEIGHT / 2F), argb, false);

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glPopMatrix();
		}
	}

	/** 用原版字体绘制一句，支持缩放与旋转（旋转/缩放围绕该句中心） */
	private void drawRotatedScaled(FontRenderer fr, String text, int x, int y, float scale, float angleDeg, int argbColor) {
		int w = fr.getStringWidth(text);
		int h = fr.FONT_HEIGHT;

		GL11.glPushMatrix();
		// 以句子中心为原点：先平移到中心，再旋转缩放，最后以中心为锚点绘制
		float cx = x + w / 2F;
		float cy = y + h / 2F;
		GL11.glTranslatef(cx, cy, 0F);
		GL11.glRotatef(angleDeg, 0F, 0F, 1F);
		GL11.glScalef(scale, scale, 1F);

		fr.drawString(text, Math.round(-w / 2F), Math.round(-h / 2F), argbColor, false);

		GL11.glPopMatrix();
	}

	/* ================= 纹理生成与缓存 ================= */

	private TextAtlas getAtlas(String text) {
		TextAtlas atlas = ATLAS_CACHE.get(text);
		if (atlas != null) return atlas;
		try {
			BufferedImage img = renderTextImage(text);
			ResourceLocation res = new ResourceLocation("makentmgreatagain",
					"textures/drx/font_" + Integer.toHexString(text.hashCode()) + "_" + TEX_RAND.nextInt(100000));
			Minecraft.getMinecraft().renderEngine.loadTexture(res, new DynamicTexture(img));
			atlas = new TextAtlas(res, img.getWidth(), img.getHeight());
			ATLAS_CACHE.put(text, atlas);
			return atlas;
		} catch (Throwable t) {
			// 字体/纹理生成失败时静默降级：只保留黑幕效果，不打断游戏
			ATLAS_CACHE.put(text, null);
			return null;
		}
	}

	/**
	 * 把文字画进 BufferedImage：黑色四角阴影 + 白色主字，抗锯齿。
	 */
	private BufferedImage renderTextImage(String text) {
		Font font = cjkFont();

		// 第一遍：量尺寸
		BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g0 = probe.createGraphics();
		g0.setFont(font);
		FontMetrics fm = g0.getFontMetrics();
		int tw = fm.stringWidth(text) + 8;
		int th = fm.getHeight() + 8;
		g0.dispose();

		BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(font);
		g.setColor(Color.WHITE);

		int bx = 4;
		int by = 4 + fm.getAscent();

		g.setColor(new Color(0, 0, 0, 200));
		g.drawString(text, bx - 2, by - 2);
		g.drawString(text, bx + 2, by - 2);
		g.drawString(text, bx - 2, by + 2);
		g.drawString(text, bx + 2, by + 2);

		g.setColor(Color.WHITE);
		g.drawString(text, bx, by);
		g.dispose();
		return img;
	}

	/** 优先使用系统的微软雅黑等中文字体；都没有就退回无衬线（可能缺中文位图） */
	private static Font cjkFont() {
		String[] candidates = {
				"Microsoft YaHei", "微软雅黑", "SimHei", "黑体",
				"SimSun", "宋体", "Noto Sans CJK SC", "Source Han Sans CN"
		};
		for (String name : candidates) {
			try {
				Font f = new Font(name, Font.PLAIN, 1);
				if (f.canDisplay('你')) {
					return new Font(name, Font.BOLD, FONT_SIZE);
				}
			} catch (Exception ignored) {
			}
		}
		return new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
	}

	/* ================= 工具 ================= */

	private float fadeCurve(long elapsed, long duration) {
		float in = Math.min((float) elapsed / FADE_MS, 1F);
		float out = Math.min((float) (duration - elapsed) / FADE_MS, 1F);
		return Math.min(in, out);
	}
}

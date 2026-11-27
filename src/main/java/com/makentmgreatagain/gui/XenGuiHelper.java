package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

/**
 * 流体/物品传送器 GUI 共享绘制与文本工具（中英文适配、15 字自动换行、纯字母数字上移 1px、频率响应示波器）。
 */
public final class XenGuiHelper {

	private static final float MAX_VISUAL_UNITS_PER_LINE = 15.0F;

	private XenGuiHelper() {
	}

	public static String tr(String key, Object... args) {
		String raw = I18n.format(key, args);
		// 确保任意括号展示均为英文括号
		return raw.replace('（', '(').replace('）', ')');
	}

	/**
	 * 严格在矩形 (x, y, w, h) 内水平+垂直居中绘制文字；
	 * 纯数字/ASCII 字符整体上移 1px 避免视觉偏下，过宽或过高时自动等比缩放。
	 */
	public static void drawCenteredFit(FontRenderer fr, String text, int x, int y, int w, int h, int color) {
		if(text == null || text.isEmpty()) return;
		int sw = fr.getStringWidth(text);
		if(sw <= 0) return;

		boolean asciiOnly = true;
		for(int i = 0; i < text.length(); i++) {
			if(text.charAt(i) > 0x7F) {
				asciiOnly = false;
				break;
			}
		}

		float inkedH = asciiOnly ? 7.0F : 8.0F;
		int maxW = Math.max(4, w - 5);
		int maxH = Math.max(4, h - 4);
		float scaleW = (sw > maxW) ? ((float) maxW / (float) sw) : 1.0F;
		float scaleH = (inkedH > maxH) ? ((float) maxH / inkedH) : 1.0F;
		float scale = Math.min(scaleW, scaleH);
		float cx = x + w * 0.5F;
		float cy = y + h * 0.5F;
		float yShift = asciiOnly ? -1.0F : 0.0F;

		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy - inkedH * 0.5F * scale + yShift, 0.0F);
		if(scale < 1.0F) {
			GL11.glScalef(scale, scale, 1.0F);
		}
		fr.drawString(text, Math.round(-sw * 0.5F), 0, color);
		GL11.glPopMatrix();
	}

	/**
	 * 将提示文本按每行不超过 15 个全角字符（英文约 30 字符，优先在空格/标点处断行）自动换行，
	 * 保留前导颜色控制符，防止宽提示框遮挡界面视野。
	 */
	public static List<String> wrapTooltipLines(List<String> input) {
		List<String> out = new ArrayList<String>(input.size() * 2);
		for(int i = 0; i < input.size(); i++) {
			String raw = input.get(i);
			if(raw == null) continue;
			raw = raw.replace('（', '(').replace('）', ')');
			String[] hardLines = raw.split("\\\\n|\\n");
			for(String part : hardLines) {
				wrapSingleLine(part, out);
			}
		}
		return out;
	}

	private static void wrapSingleLine(String line, List<String> out) {
		if(line.isEmpty()) {
			out.add("");
			return;
		}
		String activeColor = "";
		int len = line.length();
		int idx = 0;

		while(idx < len) {
			StringBuilder cur = new StringBuilder();
			if(!activeColor.isEmpty() && !(idx + 1 < len && line.charAt(idx) == '\u00a7')) {
				cur.append(activeColor);
			}

			float units = 0.0F;
			int lastSpaceOutIdx = -1;
			int lastSpaceSrcIdx = -1;

			while(idx < len) {
				char c = line.charAt(idx);
				if(c == '\u00a7' && idx + 1 < len) {
					String code = line.substring(idx, idx + 2);
					activeColor = code;
					cur.append(code);
					idx += 2;
					continue;
				}
				float cw = (c > 0x7F) ? 1.0F : 0.5F;
				if(units > 0.0F && units + cw > MAX_VISUAL_UNITS_PER_LINE) {
					if(lastSpaceOutIdx > 0 && c <= 0x7F && lastSpaceSrcIdx > 0) {
						// 英文词尽量在空格处换行
						cur.setLength(lastSpaceOutIdx);
						idx = lastSpaceSrcIdx + 1;
					}
					break;
				}
				if(c == ' ') {
					lastSpaceOutIdx = cur.length();
					lastSpaceSrcIdx = idx;
				}
				cur.append(c);
				units += cw;
				idx++;
			}
			out.add(cur.toString());
		}
	}

	/**
	 * 绘制随当前共振频率（0~9999）实时变化特征的 CRT 示波器波形。
	 */
	public static void drawOscilloscope(int sx, int sy, int sw, int sh, boolean oc, boolean linked, int frequency) {
		Gui.drawRect(sx, sy, sx + sw, sy + sh, 0xFF141414);
		Gui.drawRect(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, oc ? 0xFF230909 : 0xFF0A171E);

		int gridColor = oc ? 0xFF3E1414 : 0xFF122B36;
		Gui.drawRect(sx + 2, sy + sh / 2, sx + sw - 2, sy + sh / 2 + 1, gridColor);
		for(int gx = sx + 22; gx < sx + sw - 2; gx += 22) {
			Gui.drawRect(gx, sy + 2, gx + 1, sy + sh - 2, gridColor);
		}

		int fWhole = frequency / 10;
		int fHundreds = (fWhole / 100) % 10;
		int fTens = (fWhole / 10) % 10;
		int fOnes = fWhole % 10;
		int fDec = frequency % 10;

		// 频率特征参数：个位与十分位控制载波疏密与谐波比，百十位控制包络与扫速
		double baseK = 0.065D + (fOnes * 10 + fDec) * 0.0022D;
		double envK = 0.025D + (fHundreds * 10 + fTens) * 0.0012D;
		double harmRatio = 1.45D + fDec * 0.32D;
		double speedMul = 0.70D + ((frequency % 200) / 200.0D) * 0.95D;

		double t = (System.currentTimeMillis() / (oc ? 80.0D : 175.0D)) * speedMul;
		int waveColor = oc ? 0xFFFF6622 : (linked ? 0xFF38E8FF : 0xFF2B6F7D);
		int midY = sy + 22;

		for(int px = 2; px < sw - 2; px++) {
			double env = 0.58D + 0.42D * Math.sin(px * envK - t * 0.45D);
			double carrier = Math.sin(px * baseK + t) * 0.72D
					+ Math.sin(px * baseK * harmRatio - t * 1.35D) * 0.28D;
			if(oc) {
				double val = carrier * env * 8.5D;
				int wy = Math.max(sy + 12, Math.min(sy + sh - 3, midY + (int) Math.round(val)));
				Gui.drawRect(sx + px, wy, sx + px + 1, wy + 2, waveColor);
			} else if(linked) {
				double val = carrier * env * 6.5D;
				int wy = Math.max(sy + 12, Math.min(sy + sh - 3, midY + (int) Math.round(val)));
				Gui.drawRect(sx + px, wy, sx + px + 1, wy + 1, waveColor);
			} else {
				// 待机时也随当前调谐频率绘制微弱载波，便于直观感受频率变化
				double val = Math.sin(px * baseK + t * 0.45D) * 2.2D;
				int wy = Math.max(sy + 14, Math.min(sy + sh - 4, midY + (int) Math.round(val)));
				Gui.drawRect(sx + px, wy, sx + px + 1, wy + 1, waveColor);
			}
		}
	}

	public static void drawBevelPanel(int x, int y, int w, int h, int border, int light, int dark, int fill) {
		Gui.drawRect(x + 2, y, x + w - 2, y + h, border);
		Gui.drawRect(x, y + 2, x + w, y + h - 2, border);
		Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, border);
		Gui.drawRect(x + 2, y + 1, x + w - 3, y + h - 1, light);
		Gui.drawRect(x + 1, y + 2, x + w - 2, y + h - 3, light);
		Gui.drawRect(x + 3, y + 2, x + w - 1, y + h - 2, dark);
		Gui.drawRect(x + 2, y + 3, x + w - 2, y + h - 1, dark);
		Gui.drawRect(x + 3, y + 3, x + w - 3, y + h - 3, fill);
	}

	public static void drawSmallButton(int x, int y, int w, int h, boolean hover, boolean oc) {
		if(oc) {
			drawHbmButton(x, y, w, h, 0xFF1A0404, hover ? 0xFFE8664A : 0xFFC24836, 0xFF42120E, 0xFF7C261E, hover ? 0xFF843228 : 0xFF682620);
		} else {
			drawHbmButton(x, y, w, h, 0xFF141414, hover ? 0xFFB8B8B8 : 0xFF9C9C9C, 0xFF424242, 0xFF686868, hover ? 0xFF7A7A7A : 0xFF646464);
		}
	}

	public static void drawToggleButton(int x, int y, int w, int h, boolean on, boolean hover, boolean oc) {
		if(on) {
			if(oc) {
				drawHbmButton(x, y, w, h, 0xFF1A0404, hover ? 0xFF56C478 : 0xFF42A862, 0xFF164225, 0xFF287242, hover ? 0xFF36804D : 0xFF29663D);
			} else {
				drawHbmButton(x, y, w, h, 0xFF141414, hover ? 0xFF56C478 : 0xFF44AA64, 0xFF164225, 0xFF287242, hover ? 0xFF347C4A : 0xFF27623A);
			}
		} else {
			drawSmallButton(x, y, w, h, hover, oc);
		}
	}

	/** 参照 HBM 标准立体斜角按钮：1px 黑框 + 1px 高光/暗影斜角 + 对角过渡像素。 */
	public static void drawHbmButton(int x, int y, int w, int h, int outer, int hi, int lo, int corner, int fill) {
		Gui.drawRect(x, y, x + w, y + h, outer);
		Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		Gui.drawRect(x + 1, y + 1, x + w - 2, y + 2, hi);
		Gui.drawRect(x + 1, y + 1, x + 2, y + h - 2, hi);
		Gui.drawRect(x + 2, y + h - 2, x + w - 1, y + h - 1, lo);
		Gui.drawRect(x + w - 2, y + 2, x + w - 1, y + h - 1, lo);
		Gui.drawRect(x + w - 2, y + 1, x + w - 1, y + 2, corner);
		Gui.drawRect(x + 1, y + h - 2, x + 2, y + h - 1, corner);
	}

	public static void drawHazardBar(int x, int y, int w, int h, int shift) {
		for(int px = 0; px < w; px++) {
			int stripe = Math.floorMod(px + shift, 12);
			int col = stripe < 6 ? 0xFFFF8818 : 0xFF1A0808;
			Gui.drawRect(x + px, y, x + px + 1, y + h, col);
		}
	}
}

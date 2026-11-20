package com.makentmgreatagain.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hbm.qmaw.GuiQMAW;
import com.hbm.qmaw.ManualElement;
import com.hbm.qmaw.QuickManualAndWiki;
import com.hbm.qmaw.components.QComponentLink;
import com.hbm.qmaw.components.QComponentText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.LanguageManager;

/** Reuses the base QMAW screen and link behavior, but lays out MNGA manual pages itself. */
final class GuiMNGAManual extends GuiQMAW {

	private static final Pattern MARKUP = Pattern.compile("<br>|\\[\\[([^\\]]*+(?:\\](?!\\])[^\\]]*+)*+)\\]\\]|\\s+");

	GuiMNGAManual(QuickManualAndWiki page, GuiQMAW original) {
		super(page);
		back.addAll(original.back);
		forward.addAll(original.forward);
	}

	@Override
	protected void parseQMAW(QuickManualAndWiki page) {
		// Called by the superclass constructor: only use initialized superclass state.
		Minecraft mc = Minecraft.getMinecraft();
		LanguageManager language = mc.getLanguageManager();
		title = page.getTitle(language);
		icon = page.icon;
		String text = page.contents.get(language.getCurrentLanguage().getLanguageCode());
		if (text == null) text = page.contents.get("en_US");
		if (text == null) text = "Missing Localization!";
		text = compose(text);

		Layout layout = new Layout(lines, mc.fontRenderer, xSize - 29);
		Matcher markup = MARKUP.matcher(text);
		int start = 0;
		while (markup.find()) {
			if (start < markup.start()) layout.append(text.substring(start, markup.start()), null);
			String link = markup.group(1);
			if (link != null) {
				int pipe = link.indexOf('|');
				layout.append(pipe < 0 ? link : link.substring(0, pipe), pipe < 0 ? link : link.substring(pipe + 1));
			} else if ("<br>".equals(markup.group())) {
				layout.lineBreak();
			} else {
				layout.pendingSpace = true;
			}
			start = markup.end();
		}
		if (start < text.length()) layout.append(text.substring(start), null);
		layout.finish();
	}

	private static final class Layout {
		private final List<List<ManualElement>> lines;
		private final FontRenderer font;
		private final int maxWidth;
		private List<ManualElement> line = new ArrayList<>();
		private int width;
		private int breaks;
		private boolean pendingSpace;

		Layout(List<List<ManualElement>> lines, FontRenderer font, int maxWidth) {
			this.lines = lines;
			this.font = font;
			this.maxWidth = maxWidth;
		}

		void lineBreak() {
			flushLine();
			breaks++;
		}

		void finish() {
			flushLine();
			if (lines.isEmpty()) lines.add(line);
		}

		void append(String text, String target) {
			LinkFragment link = target == null ? null : new LinkFragment(target, true);
			int iconWidth = link == null ? 0 : link.getWidth();
			if (text.isEmpty() && iconWidth == 0) return;
			if (breaks > 1 && !lines.isEmpty()) {
				// A single <br> ends a row. Multiple <br>s leave one compact paragraph gap.
				lines.add(Collections.<ManualElement>singletonList(new ParagraphGap(font.FONT_HEIGHT / 2)));
			}
			breaks = 0;
			int space = pendingSpace && !line.isEmpty() ? font.getStringWidth(" ") : 0;
			pendingSpace = false;
			if (width + space + iconWidth + font.getStringWidth(text) > maxWidth) {
				// Keep normal words/labels intact. Oversized runs instead use the remaining room
				// if there is space for a visible fragment (Chinese text, a URL, a long label).
				if (iconWidth + font.getStringWidth(text) <= maxWidth
						|| fittingEnd(text, "", maxWidth - width - space - iconWidth) == 0) {
					flushLine();
					space = 0;
				}
			}
			if (space > 0) add(new QComponentText(" ", font));

			String format = "";
			do {
				int room = maxWidth - width - iconWidth;
				int end = fittingEnd(text, format, room);
				int next = end;
				if (end < text.length()) {
					int spaceAt = text.lastIndexOf(' ', end);
					if (spaceAt > 0) {
						end = spaceAt;
						next = spaceAt;
					}
					while (next < text.length() && text.charAt(next) == ' ') next++;
				}
				String part = format + text.substring(0, end);
				add(link == null ? new QComponentText(part, font) : link.withText(part));
				if (next == text.length()) return;

				format = formattingAtEnd(part);
				text = text.substring(next);
				flushLine();
				if (target != null) link = new LinkFragment(target, false);
				iconWidth = 0;
			} while (!text.isEmpty());
		}

		private int fittingEnd(String text, String format, int room) {
			int end = 0;
			while (end < text.length()) {
				int next = end + Character.charCount(text.codePointAt(end));
				if (text.charAt(end) == '\u00a7' && next < text.length()) next++;
				if (font.getStringWidth(format + text.substring(0, next)) > room) break;
				end = next;
			}
			return end;
		}

		private static String formattingAtEnd(String text) {
			String active = "";
			for (int i = 0; i + 1 < text.length(); i++) {
				if (text.charAt(i) != '\u00a7') continue;
				char code = Character.toLowerCase(text.charAt(++i));
				if (code == 'r') active = "";
				else if ("0123456789abcdef".indexOf(code) >= 0) active = "\u00a7" + code;
				else if ("klmno".indexOf(code) >= 0) active += "\u00a7" + code;
			}
			return active;
		}

		private void add(ManualElement element) {
			line.add(element);
			width += element.getWidth();
		}

		private void flushLine() {
			if (!line.isEmpty()) {
				lines.add(line);
				line = new ArrayList<>();
			}
			width = 0;
			pendingSpace = false;
		}
	}

	private static final class LinkFragment extends QComponentLink {
		LinkFragment(String target, boolean first) {
			super(target, "");
			if (!first) icon = null;
		}

		LinkFragment withText(String label) {
			text = label;
			return this;
		}
	}

	/** Explicit height lets the inherited screen render and scroll gaps consistently. */
	private static final class ParagraphGap extends ManualElement {
		private final int height;

		ParagraphGap(int height) { this.height = height; }
		@Override public int getHeight() { return height; }
		@Override public int getWidth() { return 0; }
		@Override public void render(boolean hovered, int x, int y, int mouseX, int mouseY, GuiQMAW parent) { }
		@Override public void onClick(GuiQMAW parent) { }
	}
}

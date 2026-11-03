package dev.langlearn.client;

import dev.langlearn.client.speech.SpeechEngine;
import dev.langlearn.client.speech.Tts;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** Draws the "SAY IT IN ..." card at the top middle of the screen, and the result popup. */
public final class HudOverlay {
	private static final int BG = 0xD0141820;
	private static final int ACCENT = 0xFFF2C744;
	private static final int GREY = 0xFFA0A4AC;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int WORD = 0xFFFFE27A;
	private static final int ROMAN = 0xFF7FE3FF;
	private static final int TIP = 0xFFE8C77A;
	private static final int GREEN_BG = 0xD0123A22;
	private static final int GREEN = 0xFF6CF08C;
	private static final int RED_BG = 0xD0401418;
	private static final int RED = 0xFFFF6B6B;
	private static final float WORD_SCALE = 1.5f;

	public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;
		Font font = mc.font;
		int y = 4;
		PromptManager.Prompt p = PromptManager.active();
		if (p != null) y = drawPrompt(g, font, p, y) + 4;
		PromptManager.Result r = PromptManager.result();
		if (r != null) drawResult(g, font, r, y);
	}

	private static int drawPrompt(GuiGraphicsExtractor g, Font font, PromptManager.Prompt p, int y) {
		String header = "SAY IT IN " + p.langName.toUpperCase(Locale.ROOT) + (p.test ? "  (TEST)" : "");
		String secs = (p.ticksLeft + 19) / 20 + "s";
		String hear = hearLabel();
		String status = statusLine(p);

		int wordW = (int) (font.width(p.word) * WORD_SCALE);
		int w = Math.max(190, 34 + wordW + 10);
		w = Math.max(w, font.width(header) + font.width(secs) + font.width(hear) + 34);
		if (p.roman != null) w = Math.max(w, 34 + font.width(p.roman) + 10);
		if (p.own != null) w = Math.max(w, 34 + font.width("= " + p.own) + 10);
		w = Math.max(w, font.width(status) + 20 + (p.voice ? 36 : 0));
		w = Math.min(w, g.guiWidth() - 8);

		// rotating pronunciation tip (a new one every 4 seconds) and the last mistake, wrapped to the card
		String tip = p.tips.isEmpty() ? null : "tip: " + p.tips.get(((p.totalTicks - p.ticksLeft) / 80) % p.tips.size());
		java.util.List<String> tipLines = tip == null ? java.util.List.of() : wrap(font, tip, w - 16);
		java.util.List<String> mistakeLines = p.mistake == null ? java.util.List.of() : wrap(font, p.mistake, w - 16);
		int extra = (tipLines.size() + mistakeLines.size()) * 10;

		int lines = 1 + (p.roman != null ? 1 : 0) + (p.own != null ? 1 : 0);
		int h = 18 + 16 + (lines - 1) * 11 + 10 + 12 + extra + (extra > 0 ? 2 : 0);
		int x = (g.guiWidth() - w) / 2;

		g.fill(x, y, x + w, y + h, BG);
		g.fill(x, y, x + 2, y + h, ACCENT);

		// header row
		g.text(font, header, x + 8, y + 5, GREY);
		g.text(font, secs, x + w - 8 - font.width(secs), y + 5, p.ticksLeft < 60 ? RED : WHITE);
		if (!hear.isEmpty()) g.text(font, hear, x + w - 16 - font.width(secs) - font.width(hear), y + 5, ROMAN);

		// item icon + big word
		int rowY = y + 17;
		g.item(p.stack, x + 8, rowY);
		g.pose().pushMatrix();
		g.pose().translate(x + 30, rowY + 2);
		g.pose().scale(WORD_SCALE);
		if (p.roman == null && p.colors != null) {
			drawColored(g, font, p.word, p.colors, 0, 0, WORD); // Latin script: colour the word itself
		} else {
			g.text(font, p.word, 0, 0, WORD);
		}
		g.pose().popMatrix();

		int ty = rowY + 17;
		if (p.roman != null) {
			if (p.colors != null) drawColored(g, font, p.roman, p.colors, x + 30, ty, ROMAN);
			else g.text(font, p.roman, x + 30, ty, ROMAN);
			ty += 11;
		}
		if (p.own != null) {
			g.text(font, "= " + p.own, x + 30, ty, GREY);
			ty += 11;
		}

		// timer bar
		int barX = x + 8, barW = w - 16, barY = ty + 2;
		g.fill(barX, barY, barX + barW, barY + 3, 0x60FFFFFF);
		int filled = (int) (barW * (p.ticksLeft / (float) p.totalTicks));
		g.fill(barX, barY, barX + filled, barY + 3, p.ticksLeft < 60 ? RED : ACCENT);

		g.text(font, status, x + 8, barY + 6, GREY);
		int ly = barY + 18;
		for (String l : mistakeLines) {
			g.text(font, l, x + 8, ly, RED);
			ly += 10;
		}
		for (String l : tipLines) {
			g.text(font, l, x + 8, ly, TIP);
			ly += 10;
		}
		if (p.voice) {
			// microphone level: if this never moves while you talk, the wrong mic is selected or it's muted
			int lvlW = 30, lvlX = x + w - 8 - lvlW, lvlY = barY + 8;
			g.fill(lvlX, lvlY, lvlX + lvlW, lvlY + 4, 0x60FFFFFF);
			g.fill(lvlX, lvlY, lvlX + (int) (lvlW * SpeechEngine.micLevel()), lvlY + 4, GREEN);
		}
		return y + h;
	}

	/** Splits text into lines that fit {@code maxW} pixels. */
	private static java.util.List<String> wrap(Font font, String text, int maxW) {
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (font.width(next) > maxW && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	/** Draws text letter by letter: green = you said it right, red = wrong or missing. */
	private static void drawColored(GuiGraphicsExtractor g, Font font, String text, int[] colors, int x, int y, int neutral) {
		int cx = x;
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			int c = i < colors.length ? colors[i] : 0;
			g.text(font, ch, cx, y, c > 0 ? GREEN : c < 0 ? RED : neutral);
			cx += font.width(ch);
		}
	}

	/** "[H] hear it" or the voice download progress, shown in the header. */
	private static String hearLabel() {
		String lang = Config.get().language;
		if (!Tts.available(lang)) return "";
		String st = Tts.status();
		if (!st.isEmpty()) return st;
		return Tts.busy() ? "speaking..." : "[" + LangLearnClient.HEAR.getTranslatedKeyMessage().getString() + "] hear it";
	}

	private static String statusLine(PromptManager.Prompt p) {
		String heard = p.lastHeard.isEmpty() ? "" : "  heard: " + p.lastHeard + "  (best " + Math.max(0, p.bestShown) + "%)";
		if (p.voice && p.typing) return "listening... or type it in chat" + heard;
		if (p.voice) return "listening..." + heard;
		String why = "";
		SpeechEngine.State st = SpeechEngine.state();
		if (Config.get().mode.equals("auto") && st != SpeechEngine.State.READY) why = " (" + SpeechEngine.status() + ")";
		else if (SpeechEngine.micBroken()) why = " (no microphone found)";
		return "type it in chat" + why + heard;
	}

	private static void drawResult(GuiGraphicsExtractor g, Font font, PromptManager.Result r, int y) {
		String line1 = r.word + (r.roman != null ? "  " + r.roman : "");
		String line2;
		if (r.success) {
			line2 = "UNLOCKED " + r.score + "%";
		} else {
			String lost = r.test ? "(test, nothing lost)" : "LOST x" + r.count;
			String learned = r.successes > 0 ? "" : "  NEVER LEARNED (0/" + r.attempts + ")";
			line2 = lost + learned;
		}
		int w = Math.max(font.width(line1), font.width(line2)) + 36;
		int h = 28;
		int x = (g.guiWidth() - w) / 2;
		// fade out during the last half second
		int alpha = Math.min(255, r.ticksLeft * 255 / 10);
		int bg = ((((r.success ? GREEN_BG : RED_BG) >>> 24) * alpha / 255) << 24) | ((r.success ? GREEN_BG : RED_BG) & 0xFFFFFF);
		int fg = (Math.max(alpha, 5) << 24) | ((r.success ? GREEN : RED) & 0xFFFFFF);
		int white = (Math.max(alpha, 5) << 24) | 0xFFFFFF;
		g.fill(x, y, x + w, y + h, bg);
		g.fill(x, y, x + 2, y + h, fg);
		g.item(r.stack, x + 8, y + 6);
		g.text(font, line1, x + 28, y + 5, white);
		g.text(font, line2, x + 28, y + 16, fg);
	}
}

package dev.langlearn.client.text;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import dev.langlearn.LangLearn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Japanese text -> romaji. Kuromoji gives the readings of kanji, then {@link Kana} converts to romaji. */
public final class Japanese {
	private static volatile Tokenizer tokenizer;
	private static volatile boolean failed;

	/** Loading the dictionary takes a second or two, so do it in the background ahead of time. */
	public static void warmUp() {
		if (tokenizer == null && !failed) CompletableFuture.runAsync(Japanese::tokenizer);
	}

	private static synchronized Tokenizer tokenizer() {
		if (tokenizer == null && !failed) {
			try {
				tokenizer = new Tokenizer();
			} catch (Throwable t) {
				failed = true;
				LangLearn.LOGGER.warn("Japanese tokenizer failed to load, kanji will not be romanized", t);
			}
		}
		return tokenizer;
	}

	/** Reading in katakana, e.g. "ダイヤモンドの剣" -> "ダイヤモンド ノ ケン" (space between words). */
	public static String reading(String text) {
		Tokenizer tk = tokenizer();
		if (tk == null) return text;
		List<String> words = new ArrayList<>();
		for (Token t : tk.tokenize(text)) {
			String surface = t.getSurface();
			if (surface.isBlank()) continue;
			String reading = t.getReading();
			words.add(reading == null || reading.equals("*") ? surface : reading);
		}
		return String.join(" ", words);
	}

	/** The words of the text separated by spaces, e.g. "ダイヤモンドの剣" -> "ダイヤモンド の 剣". */
	public static String surfaces(String text) {
		Tokenizer tk = tokenizer();
		if (tk == null) return text;
		List<String> words = new ArrayList<>();
		for (Token t : tk.tokenize(text)) {
			if (!t.getSurface().isBlank()) words.add(t.getSurface());
		}
		return String.join(" ", words);
	}

	public static String toRomaji(String text) {
		return Kana.toRomaji(reading(text)).replaceAll("\\s+", " ").trim();
	}
}

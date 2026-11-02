package dev.langlearn.client.speech;

import com.google.gson.JsonArray;
import dev.langlearn.client.Translations;
import dev.langlearn.client.text.Japanese;
import dev.langlearn.client.text.Romanizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Builds the list of phrases the "constrained" recognizer may answer with: the correct item name
 * plus a few dozen other item names. Instead of guessing random words, the recognizer then picks
 * whichever Minecraft item sounds closest to what you said (or nothing).
 */
public final class Grammar {
	private static final int DISTRACTORS = 40;
	private static final Random RANDOM = new Random();

	/** Grammar JSON for this target word, or null if the model can't spell it with words it knows. */
	public static String build(String lang, String target) {
		Set<String> vocab = SpeechEngine.vocabulary();
		String phrase = phrase(lang, target, vocab);
		if (phrase == null) return null;
		Set<String> phrases = new LinkedHashSet<>();
		phrases.add(phrase);

		List<String> names = new ArrayList<>(Translations.itemNames());
		Collections.shuffle(names, RANDOM);
		for (String name : names) {
			if (phrases.size() > DISTRACTORS) break;
			if (name.equals(target)) continue;
			String p = phrase(lang, name, vocab);
			if (p != null && !p.isEmpty()) phrases.add(p);
		}
		JsonArray arr = new JsonArray();
		for (String p : phrases) arr.add(p);
		arr.add("[unk]");
		return arr.toString();
	}

	/** Turns a name into space-separated words from the model's vocabulary. */
	static String phrase(String lang, String text, Set<String> vocab) {
		boolean cjk = VoskModels.isCjk(lang);
		String t = text;
		if (lang.startsWith("zh") && !lang.equals("zh_cn") || lang.equals("lzh")) {
			t = Romanizer.icuOrSame("Traditional-Simplified", t); // the Chinese model writes simplified
		}
		if (lang.startsWith("ja")) t = katakanaFriendly(t);
		t = t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
		if (t.isEmpty()) return null;

		List<String> out = new ArrayList<>();
		for (String token : t.split(" ")) {
			if (vocab == null) {
				if (cjk) {
					// no vocabulary list: best guess is one character per word
					token.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp))));
				} else {
					out.add(token);
				}
				continue;
			}
			if (vocab.contains(token)) {
				out.add(token);
				continue;
			}
			if (lang.startsWith("ru") && vocab.contains(token.replace('ё', 'е'))) {
				out.add(token.replace('ё', 'е'));
				continue;
			}
			// split into the longest pieces the model knows (needed for Chinese/Japanese, also helps compounds)
			List<String> pieces = segment(token, vocab);
			if (pieces == null) return null;
			out.addAll(pieces);
		}
		return String.join(" ", out);
	}

	private static String katakanaFriendly(String t) {
		// help segmentation: put spaces between the words Kuromoji finds
		try {
			return Japanese.surfaces(t);
		} catch (Throwable e) {
			return t;
		}
	}

	private static List<String> segment(String token, Set<String> vocab) {
		int n = token.length();
		// dynamic programming: fewest pieces
		int[] best = new int[n + 1];
		int[] from = new int[n + 1];
		java.util.Arrays.fill(best, Integer.MAX_VALUE);
		best[0] = 0;
		for (int i = 0; i < n; i++) {
			if (best[i] == Integer.MAX_VALUE) continue;
			for (int j = i + 1; j <= Math.min(n, i + 12); j++) {
				if (vocab.contains(token.substring(i, j)) && best[i] + 1 < best[j]) {
					best[j] = best[i] + 1;
					from[j] = i;
				}
			}
		}
		if (best[n] == Integer.MAX_VALUE) return null;
		List<String> pieces = new ArrayList<>();
		for (int j = n; j > 0; j = from[j]) pieces.add(0, token.substring(from[j], j));
		return pieces;
	}
}

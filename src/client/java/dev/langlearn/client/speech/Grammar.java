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
	private static final Random RANDOM = new Random();

	/** Grammar JSON for this target word, or null if the model can't spell it with words it knows. */
	public static String build(String lang, String target, Strictness strictness) {
		List<String> names = new ArrayList<>(Translations.itemNames());
		Collections.shuffle(names, RANDOM);
		return assemble(lang, target, names, SpeechEngine.vocabulary(), strictness);
	}

	/** How picky the recognizer is. */
	public enum Strictness {
		EASY(0, 40, 0), NORMAL(6, 25, 0.5), HARD(12, 25, 0.6);

		/** near-miss words offered per word of the item name */
		public final int confusablesPerWord;
		/** other random item names offered */
		public final int randomNames;
		/** minimum recognizer confidence (0-1) for a correct answer to count */
		public final double minConfidence;

		Strictness(int c, int r, double conf) {
			confusablesPerWord = c;
			randomNames = r;
			minConfidence = conf;
		}

		public static Strictness of(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return NORMAL;
			}
		}
	}

	/** The phrase the recognizer will answer with when you say the target right (for comparing). */
	public static String targetPhrase(String lang, String target) {
		return phrase(lang, target, SpeechEngine.vocabulary());
	}

	/** Pure version of {@link #build}, testable without Minecraft. */
	public static String assemble(String lang, String target, List<String> otherNames, Set<String> vocab, Strictness st) {
		List<List<String>> groups = groups(lang, target, vocab);
		if (groups == null) return null;
		String[] words = new String[groups.size()];
		for (int i = 0; i < words.length; i++) words[i] = String.join(" ", groups.get(i));
		String phrase = String.join(" ", words);
		Set<String> phrases = new LinkedHashSet<>();
		phrases.add(phrase);

		// near misses: replace one real word at a time with a word that sounds almost the same
		// (not for words the model had to build from pieces: swapping a piece makes nonsense)
		for (int i = 0; i < words.length && st.confusablesPerWord > 0; i++) {
			if (groups.get(i).size() != 1) continue;
			for (String c : Confusables.forWord(lang, words[i], vocab, st.confusablesPerWord)) {
				String[] copy = words.clone();
				copy[i] = c;
				phrases.add(String.join(" ", copy));
			}
		}

		// parts of the name on their own, so saying only half of it can't match the whole name
		for (int len = words.length - 1; len >= 1; len--) {
			for (int start = 0; start + len <= words.length; start++) {
				phrases.add(String.join(" ", java.util.Arrays.copyOfRange(words, start, start + len)));
			}
		}

		// other items that share a word with this one ("незеритовый меч" for "незеритовый нагрудник")
		if (words.length > 1) {
			Set<String> mine = new java.util.HashSet<>(java.util.Arrays.asList(phrase.split(" ")));
			int shared = 0;
			for (String name : otherNames) {
				if (shared >= 15) break;
				if (name.equals(target)) continue;
				String p = phrase(lang, name, vocab);
				if (p == null || p.equals(phrase)) continue;
				boolean overlaps = false;
				for (String w : p.split(" ")) overlaps |= w.length() > 2 && mine.contains(w);
				if (overlaps && phrases.add(p)) shared++;
			}
		}

		int added = 0;
		for (String name : otherNames) {
			if (added >= st.randomNames) break;
			if (name.equals(target)) continue;
			String p = phrase(lang, name, vocab);
			if (p != null && !p.isEmpty() && phrases.add(p)) added++;
		}
		JsonArray arr = new JsonArray();
		for (String p : phrases) arr.add(p);
		arr.add("[unk]");
		return arr.toString();
	}

	/** Turns a name into space-separated words from the model's vocabulary. */
	public static String phrase(String lang, String text, Set<String> vocab) {
		List<List<String>> g = groups(lang, text, vocab);
		if (g == null) return null;
		List<String> out = new ArrayList<>();
		for (List<String> grp : g) out.addAll(grp);
		return String.join(" ", out);
	}

	/**
	 * The name as groups of recognizer words: one group per real word of the name. A word the model
	 * doesn't know is split into pieces it does know, and those pieces stay together in one group.
	 * For Chinese/Japanese every piece is its own group (they are real words there).
	 */
	static List<List<String>> groups(String lang, String text, Set<String> vocab) {
		boolean cjk = VoskModels.isCjk(lang);
		String t = text;
		if (lang.startsWith("zh") && !lang.equals("zh_cn") || lang.equals("lzh")) {
			t = Romanizer.icuOrSame("Traditional-Simplified", t); // the Chinese model writes simplified
		}
		if (lang.startsWith("ja")) t = katakanaFriendly(t);
		t = t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
		if (t.isEmpty()) return null;

		List<List<String>> out = new ArrayList<>();
		for (String token : t.split(" ")) {
			List<String> pieces;
			if (vocab == null) {
				if (cjk) {
					// no vocabulary list: best guess is one character per word
					pieces = new ArrayList<>();
					List<String> p = pieces;
					token.codePoints().forEach(cp -> p.add(new String(Character.toChars(cp))));
				} else {
					pieces = List.of(token);
				}
			} else if (vocab.contains(token)) {
				pieces = List.of(token);
			} else if (lang.startsWith("ru") && vocab.contains(token.replace('ё', 'е'))) {
				pieces = List.of(token.replace('ё', 'е'));
			} else {
				// split into the longest pieces the model knows (needed for Chinese/Japanese, also helps compounds)
				pieces = segment(token, vocab);
				if (pieces == null) return null;
			}
			if (cjk) {
				for (String piece : pieces) out.add(List.of(piece));
			} else {
				out.add(pieces);
			}
		}
		return out;
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

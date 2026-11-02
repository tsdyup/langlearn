package dev.langlearn.client.text;

import com.ibm.icu.text.Transliterator;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns words in any script into Latin letters, both for showing the pronunciation
 * and for comparing what you said with the correct word.
 */
public final class Romanizer {
	private static final Map<String, Transliterator> ICU = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> ICU_MISSING = new ConcurrentHashMap<>();

	/** True if the text uses a non-Latin script (Cyrillic, kanji, kana, hangul, Greek, Arabic...). */
	public static boolean needsRomanization(String s) {
		return s.codePoints().anyMatch(cp -> {
			if (!Character.isLetter(cp)) return false;
			Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
			return sc != Character.UnicodeScript.LATIN && sc != Character.UnicodeScript.COMMON
					&& sc != Character.UnicodeScript.INHERITED;
		});
	}

	/** Pronunciation line to show under the word, or null for Latin-script languages. */
	public static String display(String lang, String text) {
		if (!needsRomanization(text)) return null;
		String r = romanize(lang, text);
		return r == null || r.isBlank() ? null : r.replaceAll("\\s+", " ").trim();
	}

	private static String romanize(String lang, String text) {
		if (lang.startsWith("ja")) return Japanese.toRomaji(text);
		if (lang.startsWith("ru")) return Cyrillic.translit(text);
		if (lang.startsWith("zh") || lang.equals("lzh")) {
			String r = icu("Han-Latin", text);
			if (r != null) return r;
		}
		String r = icu("Any-Latin", text);
		if (r != null) return r;
		if (Cyrillic.contains(text)) return Cyrillic.translit(text);
		String kana = Kana.toRomaji(text);
		return kana.equals(text) ? null : kana;
	}

	/** Runs an ICU transliterator if available, otherwise returns the text unchanged. */
	public static String icuOrSame(String id, String text) {
		String r = icu(id, text);
		return r == null ? text : r;
	}

	private static String icu(String id, String text) {
		if (ICU_MISSING.containsKey(id)) return null;
		try {
			Transliterator t = ICU.computeIfAbsent(id, Transliterator::getInstance);
			synchronized (t) {
				return t.transliterate(text);
			}
		} catch (Throwable e) {
			ICU_MISSING.put(id, true);
			return null;
		}
	}

	/**
	 * Comparison key: romanized, lowercase, no accents/tones, no spaces or punctuation.
	 * "Алмазный меч" -> "almaznyymech", "钻石剑" -> "zuanshijian", "Espada de diamante" -> "espadadediamante".
	 */
	public static String key(String lang, String text) {
		String r = needsRomanization(text) ? romanize(lang, text) : text;
		if (r == null) r = text;
		return normalize(r);
	}

	/** Lowercase, strip accents/tones, keep only letters and digits. */
	public static String normalize(String r) {
		r = Normalizer.normalize(r.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.replace("'", "")
				.replace("ß", "ss")
				.replace("ł", "l")
				.replace("ø", "o")
				.replace("æ", "ae");
		return r.replaceAll("[^\\p{L}\\p{N}]", "");
	}
}

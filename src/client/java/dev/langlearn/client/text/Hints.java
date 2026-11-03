package dev.langlearn.client.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Short pronunciation tips for the tricky sounds in a word, and what went wrong in an attempt. */
public final class Hints {
	private static final Map<String, String> RUSSIAN = new LinkedHashMap<>();
	private static final Map<String, String> JAPANESE = new LinkedHashMap<>();
	private static final Map<String, String> CHINESE = new LinkedHashMap<>();

	static {
		RUSSIAN.put("ы", "ы (y): say \"i\" with your tongue pulled back, between \"i\" and \"u\"");
		RUSSIAN.put("щ", "щ (shch): a long, soft \"sh\", like \"sheep\" held longer");
		RUSSIAN.put("ж", "ж (zh): like the \"s\" in \"measure\", but harder");
		RUSSIAN.put("х", "х (kh): like \"ch\" in Scottish \"loch\", air from the throat");
		RUSSIAN.put("ц", "ц (ts): like \"ts\" in \"cats\"");
		RUSSIAN.put("ь", "ь ('): no sound itself, it makes the letter before it soft (add a hint of \"y\")");
		RUSSIAN.put("ъ", "ъ: no sound, a tiny pause before the next vowel");
		RUSSIAN.put("й", "й (y): a short \"y\" like in \"boy\"");
		RUSSIAN.put("ё", "ё (yo): always stressed, say it clearly");
		RUSSIAN.put("р", "р (r): a tapped or rolled r, like the Spanish r");
		RUSSIAN.put("ч", "ч (ch): a soft \"ch\" like in \"cheese\"");
		RUSSIAN.put("о", "о: only sounds like \"o\" when stressed, otherwise closer to \"a\" (молоко = ma-la-KO)");
		RUSSIAN.put("я", "я (ya): \"ya\" like in \"yard\"");
		RUSSIAN.put("е", "е (e): usually \"ye\" like in \"yes\", and it softens the consonant before it");
		RUSSIAN.put("л", "л: a \"dark\" l with the tongue low, unless soft (ль, ли, ле)");

		JAPANESE.put("ー", "ー: hold the vowel before it twice as long");
		JAPANESE.put("ッ", "small ッ/っ: a short stop before the next consonant (double it: \"tt\", \"kk\")");
		JAPANESE.put("っ", "small ッ/っ: a short stop before the next consonant (double it: \"tt\", \"kk\")");
		JAPANESE.put("r", "r: a light tap between r, l and d, never a rolled English r");
		JAPANESE.put("tsu", "tsu: like \"ts\" in \"cats\" plus a short \"u\"");
		JAPANESE.put("fu", "fu: blow softly through both lips, it's not an English \"f\"");
		JAPANESE.put("ou", "ou / oo: a long \"o\", hold it");
		JAPANESE.put("ei", "ei: usually said as a long \"e\"");
		JAPANESE.put("ン", "ん/ン (n): its own beat, hold it a moment");
		JAPANESE.put("ん", "ん/ン (n): its own beat, hold it a moment");
		JAPANESE.put("u", "u: unrounded and short, often almost silent after s, k, t (desu = \"des\")");

		CHINESE.put("1", "tone 1 (ā): high and flat, like singing one note");
		CHINESE.put("2", "tone 2 (á): rising, like asking \"what?\"");
		CHINESE.put("3", "tone 3 (ǎ): low, dipping down then up a little");
		CHINESE.put("4", "tone 4 (à): short and falling, like a firm \"no!\"");
		CHINESE.put("zh", "zh: like \"j\" in \"jump\" with the tongue curled back");
		CHINESE.put("ch", "ch: like \"ch\" in \"church\" with the tongue curled back");
		CHINESE.put("sh", "sh: like \"sh\" with the tongue curled back");
		CHINESE.put("x", "x: like \"sh\" but with a smile, tongue behind the lower teeth");
		CHINESE.put("q", "q: like \"ch\" but with a smile, tongue behind the lower teeth");
		CHINESE.put("j", "j: like \"j\" but with a smile, tongue behind the lower teeth");
		CHINESE.put("c", "c: like \"ts\" in \"cats\", with a puff of air");
		CHINESE.put("z", "z: like \"ds\" in \"kids\"");
		CHINESE.put("r", "r: like the \"s\" in \"pleasure\"");
		CHINESE.put("ü", "ü: say \"ee\" with rounded lips");
		CHINESE.put("e", "e: an \"uh\" from the back of the throat, not English \"e\"");
	}

	/** Up to {@code max} tips for this word (most specific first). */
	public static List<String> forWord(String lang, String word, String roman, int max) {
		List<String> out = new ArrayList<>();
		if (lang.startsWith("ru")) {
			String w = word.toLowerCase();
			for (Map.Entry<String, String> e : RUSSIAN.entrySet()) {
				if (out.size() >= max) break;
				if (w.contains(e.getKey())) out.add(e.getValue());
			}
		} else if (lang.startsWith("ja")) {
			String r = roman == null ? "" : roman.toLowerCase();
			for (Map.Entry<String, String> e : JAPANESE.entrySet()) {
				if (out.size() >= max) break;
				String k = e.getKey();
				boolean has = k.charAt(0) > 0x3000 ? word.contains(k) : r.contains(k);
				if (has && !out.contains(e.getValue())) out.add(e.getValue());
			}
		} else if (lang.startsWith("zh") || lang.equals("lzh")) {
			String r = roman == null ? "" : roman;
			for (int tone : toneNumbers(r)) {
				if (out.size() >= max) break;
				String t = CHINESE.get(String.valueOf(tone));
				if (!out.contains(t)) out.add(t);
			}
			String plain = Romanizer.normalize(r);
			for (Map.Entry<String, String> e : CHINESE.entrySet()) {
				if (out.size() >= max) break;
				String k = e.getKey();
				if (Character.isDigit(k.charAt(0))) continue;
				if ((k.equals("ü") ? r.contains("ü") : plain.contains(k)) && !out.contains(e.getValue())) out.add(e.getValue());
			}
		}
		return out;
	}

	/** Tone numbers (1-4) of the syllables in tone-marked pinyin, in order. */
	static List<Integer> toneNumbers(String pinyin) {
		List<Integer> tones = new ArrayList<>();
		for (char c : pinyin.toCharArray()) {
			int t = "āēīōūǖĀĒĪŌŪǕ".indexOf(c) >= 0 ? 1
					: "áéíóúǘÁÉÍÓÚǗ".indexOf(c) >= 0 ? 2
					: "ǎěǐǒǔǚǍĚǏǑǓǙ".indexOf(c) >= 0 ? 3
					: "àèìòùǜÀÈÌÒÙǛ".indexOf(c) >= 0 ? 4 : 0;
			if (t > 0) tones.add(t);
		}
		return tones;
	}

	/**
	 * One line about the first mistake in an attempt, e.g. "you said \"o\" where it should be \"a\"".
	 * Compares the romanized forms.
	 */
	public static String mistake(String lang, String target, String heard) {
		// Cyrillic is compared letter by letter in Cyrillic (with the Latin sound in brackets),
		// everything else on the romanized form
		boolean cyr = Cyrillic.contains(target);
		String a = cyr ? cyrLetters(target) : Romanizer.key(lang, target);
		String b = cyr ? cyrLetters(heard) : Romanizer.key(lang, heard);
		if (a.isEmpty() || b.isEmpty() || a.equals(b)) return null;
		// align the two and find the first difference
		int n = a.length(), m = b.length();
		int[][] dp = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++) dp[i][0] = i;
		for (int j = 0; j <= m; j++) dp[0][j] = j;
		for (int i = 1; i <= n; i++)
			for (int j = 1; j <= m; j++)
				dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
						dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
		String msg = null;
		int i = n, j = m;
		while (i > 0 || j > 0) {
			if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)) {
				if (a.charAt(i - 1) != b.charAt(j - 1)) {
					msg = "you said " + show(b.charAt(j - 1), cyr) + " where it should be " + show(a.charAt(i - 1), cyr);
				}
				i--;
				j--;
			} else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
				msg = "you missed the " + show(a.charAt(i - 1), cyr);
				i--;
			} else {
				msg = "there's an extra " + show(b.charAt(j - 1), cyr);
				j--;
			}
		}
		return msg; // the last message set while walking backwards = the first mistake in the word
	}

	private static String cyrLetters(String s) {
		return s.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}]", "");
	}

	private static String show(char c, boolean cyr) {
		if (!cyr) return "\"" + c + "\"";
		String lat = Cyrillic.translit(String.valueOf(c));
		return "\"" + c + "\"" + (lat.isEmpty() || lat.equals(String.valueOf(c)) ? "" : " (" + lat + ")");
	}
}

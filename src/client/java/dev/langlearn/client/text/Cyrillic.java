package dev.langlearn.client.text;

import java.util.HashMap;
import java.util.Map;

/** Simple, readable Cyrillic transliteration (Russian-style, plus Ukrainian/Belarusian letters). */
public final class Cyrillic {
	private static final Map<Character, String> MAP = new HashMap<>();

	static {
		String[][] t = {
				{"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"}, {"е", "e"}, {"ё", "yo"},
				{"ж", "zh"}, {"з", "z"}, {"и", "i"}, {"й", "y"}, {"к", "k"}, {"л", "l"}, {"м", "m"},
				{"н", "n"}, {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"}, {"у", "u"},
				{"ф", "f"}, {"х", "kh"}, {"ц", "ts"}, {"ч", "ch"}, {"ш", "sh"}, {"щ", "shch"},
				{"ъ", ""}, {"ы", "y"}, {"ь", "'"}, {"э", "e"}, {"ю", "yu"}, {"я", "ya"},
				{"і", "i"}, {"ї", "yi"}, {"є", "ye"}, {"ґ", "g"}, {"ў", "w"},
		};
		for (String[] e : t) MAP.put(e[0].charAt(0), e[1]);
	}

	public static boolean contains(String s) {
		return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC);
	}

	public static String translit(String s) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			char lower = Character.toLowerCase(c);
			String r = MAP.get(lower);
			if (r == null) {
				out.append(c);
			} else if (c != lower && !r.isEmpty()) {
				out.append(Character.toUpperCase(r.charAt(0))).append(r, 1, r.length());
			} else {
				out.append(r);
			}
		}
		return out.toString();
	}
}

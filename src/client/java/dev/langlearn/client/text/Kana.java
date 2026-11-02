package dev.langlearn.client.text;

import java.util.HashMap;
import java.util.Map;

/** Hiragana/katakana to Hepburn romaji. Pure Java, no dependencies. */
public final class Kana {
	private static final Map<Character, String> BASE = new HashMap<>();
	private static final Map<Character, String> SMALL = new HashMap<>();

	static {
		String[][] table = {
				{"ア", "a"}, {"イ", "i"}, {"ウ", "u"}, {"エ", "e"}, {"オ", "o"},
				{"カ", "ka"}, {"キ", "ki"}, {"ク", "ku"}, {"ケ", "ke"}, {"コ", "ko"},
				{"ガ", "ga"}, {"ギ", "gi"}, {"グ", "gu"}, {"ゲ", "ge"}, {"ゴ", "go"},
				{"サ", "sa"}, {"シ", "shi"}, {"ス", "su"}, {"セ", "se"}, {"ソ", "so"},
				{"ザ", "za"}, {"ジ", "ji"}, {"ズ", "zu"}, {"ゼ", "ze"}, {"ゾ", "zo"},
				{"タ", "ta"}, {"チ", "chi"}, {"ツ", "tsu"}, {"テ", "te"}, {"ト", "to"},
				{"ダ", "da"}, {"ヂ", "ji"}, {"ヅ", "zu"}, {"デ", "de"}, {"ド", "do"},
				{"ナ", "na"}, {"ニ", "ni"}, {"ヌ", "nu"}, {"ネ", "ne"}, {"ノ", "no"},
				{"ハ", "ha"}, {"ヒ", "hi"}, {"フ", "fu"}, {"ヘ", "he"}, {"ホ", "ho"},
				{"バ", "ba"}, {"ビ", "bi"}, {"ブ", "bu"}, {"ベ", "be"}, {"ボ", "bo"},
				{"パ", "pa"}, {"ピ", "pi"}, {"プ", "pu"}, {"ペ", "pe"}, {"ポ", "po"},
				{"マ", "ma"}, {"ミ", "mi"}, {"ム", "mu"}, {"メ", "me"}, {"モ", "mo"},
				{"ヤ", "ya"}, {"ユ", "yu"}, {"ヨ", "yo"},
				{"ラ", "ra"}, {"リ", "ri"}, {"ル", "ru"}, {"レ", "re"}, {"ロ", "ro"},
				{"ワ", "wa"}, {"ヰ", "wi"}, {"ヱ", "we"}, {"ヲ", "o"}, {"ン", "n"}, {"ヴ", "vu"},
		};
		for (String[] e : table) BASE.put(e[0].charAt(0), e[1]);
		String[][] small = {
				{"ァ", "a"}, {"ィ", "i"}, {"ゥ", "u"}, {"ェ", "e"}, {"ォ", "o"},
				{"ャ", "ya"}, {"ュ", "yu"}, {"ョ", "yo"}, {"ヮ", "wa"},
		};
		for (String[] e : small) SMALL.put(e[0].charAt(0), e[1]);
	}

	private static char toKatakana(char c) {
		// hiragana block 3041-3096 maps to katakana 30A1-30F6
		return (c >= 'ぁ' && c <= 'ゖ') ? (char) (c + 0x60) : c;
	}

	private static boolean isVowel(char c) {
		return "aeiou".indexOf(c) >= 0;
	}

	public static String toRomaji(String text) {
		StringBuilder out = new StringBuilder();
		boolean doubleNext = false;
		for (int i = 0; i < text.length(); i++) {
			char c = toKatakana(text.charAt(i));
			if (c == '・') {
				out.append(' ');
				continue;
			}
			if (c == 'ッ') {
				doubleNext = true;
				continue;
			}
			if (c == 'ー') {
				// long vowel mark: repeat the previous vowel
				for (int j = out.length() - 1; j >= 0; j--) {
					if (isVowel(out.charAt(j))) {
						out.append(out.charAt(j));
						break;
					}
				}
				continue;
			}
			String r = BASE.get(c);
			if (r == null) {
				r = SMALL.get(c); // stray small kana
			} else if (i + 1 < text.length()) {
				String sm = SMALL.get(toKatakana(text.charAt(i + 1)));
				if (sm != null) {
					String stem;
					if (c == 'ウ') stem = "w";
					else stem = r.substring(0, r.length() - 1); // drop the vowel: ki -> k, fu -> f, te -> t
					if (sm.startsWith("y") && (r.equals("shi") || r.equals("chi") || r.equals("ji"))) {
						sm = sm.substring(1); // shi+ya -> sha, not shya
					}
					r = stem + sm;
					i++;
				}
			}
			if (r == null) {
				out.append(c); // not kana (kanji, Latin, punctuation...)
				doubleNext = false;
				continue;
			}
			if (doubleNext) {
				if (r.startsWith("ch")) out.append('t');
				else if (!r.isEmpty() && !isVowel(r.charAt(0))) out.append(r.charAt(0));
				doubleNext = false;
			}
			out.append(r);
		}
		return out.toString();
	}
}

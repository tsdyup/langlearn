package dev.langlearn.client.speech;

import dev.langlearn.client.text.Romanizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds words that sound almost like the target ("палка" -> "полка", "балка", "палки").
 * They are offered to the recognizer as wrong answers, so a sloppy pronunciation gets
 * matched to the near miss instead of being accepted.
 */
public final class Confusables {
	/** romanized key -> words with that key, built once per model for Chinese/Japanese */
	private static volatile Map<String, List<String>> cjkIndex;
	private static volatile String cjkIndexLang;

	/** Builds the sound index for Chinese/Japanese in the background (needs romanizing every word). */
	public static void prepare(String lang, Set<String> vocab) {
		cjkIndex = null;
		cjkIndexLang = lang;
		if (vocab == null || !VoskModels.isCjk(lang)) return;
		Thread t = new Thread(() -> {
			Map<String, List<String>> idx = new HashMap<>();
			for (String w : vocab) {
				if (w.length() > 4 || !lang.equals(cjkIndexLang)) continue;
				if (!Romanizer.needsRomanization(w)) continue;
				String k = Romanizer.key(lang, w);
				if (!k.isEmpty()) idx.computeIfAbsent(k, x -> new ArrayList<>(2)).add(w);
			}
			if (lang.equals(cjkIndexLang)) cjkIndex = idx;
		}, "LangLearn-SoundIndex");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	/**
	 * Up to {@code max} vocabulary words that sound close to {@code word} but not the same.
	 * Words that sound identical (same romanization) are left out, since nobody could tell them apart.
	 */
	public static List<String> forWord(String lang, String word, Set<String> vocab, int max) {
		List<String> out = new ArrayList<>();
		if (vocab == null || max <= 0) return out;
		String key = Romanizer.key(lang, word);
		if (key.isEmpty()) return out;
		int maxDist = key.length() <= 3 ? 1 : 2;
		record Cand(String w, int d) {}
		List<Cand> cands = new ArrayList<>();

		if (VoskModels.isCjk(lang)) {
			Map<String, List<String>> idx = cjkIndex;
			if (idx == null || !lang.equals(cjkIndexLang)) return out; // still building
			for (Map.Entry<String, List<String>> e : idx.entrySet()) {
				String k = e.getKey();
				if (Math.abs(k.length() - key.length()) > maxDist || k.equals(key)) continue;
				int d = distance(key, k, maxDist);
				if (d <= maxDist) for (String w : e.getValue()) cands.add(new Cand(w, d));
			}
		} else {
			int len = word.length();
			for (String w : vocab) {
				if (Math.abs(w.length() - len) > 1 || w.equals(word)) continue;
				int d = distance(word, w, maxDist);
				if (d > maxDist) continue;
				if (Romanizer.key(lang, w).equals(key)) continue; // sounds the same (e.g. ё/е)
				cands.add(new Cand(w, d));
			}
		}
		cands.sort(Comparator.comparingInt(Cand::d).thenComparing(Cand::w));
		for (Cand c : cands) {
			if (out.size() >= max) break;
			out.add(c.w());
		}
		return out;
	}

	/** Levenshtein distance, giving up early (returns max+1) once it exceeds {@code max}. */
	static int distance(String a, String b, int max) {
		int n = a.length(), m = b.length();
		if (Math.abs(n - m) > max) return max + 1;
		int[] prev = new int[m + 1], cur = new int[m + 1];
		for (int j = 0; j <= m; j++) prev[j] = j;
		for (int i = 1; i <= n; i++) {
			cur[0] = i;
			int rowMin = cur[0];
			for (int j = 1; j <= m; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
				rowMin = Math.min(rowMin, cur[j]);
			}
			if (rowMin > max) return max + 1;
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[m];
	}
}

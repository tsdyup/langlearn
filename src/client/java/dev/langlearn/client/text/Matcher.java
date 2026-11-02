package dev.langlearn.client.text;

/** Scores how close what you said is to the correct word, 0-100. */
public final class Matcher {
	public static int score(String lang, String target, String heard) {
		if (heard == null || heard.isBlank()) return 0;
		String a = Romanizer.key(lang, target);
		String b = Romanizer.key(lang, heard);
		if (a.isEmpty() || b.isEmpty()) return 0;
		int d = bestSubstringDistance(a, b);
		double s = 1.0 - (double) d / a.length();
		return (int) Math.round(Math.max(0, Math.min(1, s)) * 100);
	}

	/**
	 * Edit distance between {@code a} and the best-matching part of {@code b}, so extra words
	 * around the answer ("uh... brot") don't count against you.
	 */
	static int bestSubstringDistance(String a, String b) {
		int n = a.length(), m = b.length();
		int[] prev = new int[m + 1];
		int[] cur = new int[m + 1];
		// row 0: starting anywhere in b is free
		for (int i = 1; i <= n; i++) {
			cur[0] = i;
			for (int j = 1; j <= m; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		int best = Integer.MAX_VALUE;
		for (int j = 0; j <= m; j++) best = Math.min(best, prev[j]); // ending anywhere is free
		return best;
	}
}

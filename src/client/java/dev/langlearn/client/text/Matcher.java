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
	 * Which characters of the target were pronounced right in {@code heard}.
	 * Returns one flag per character of {@code Romanizer.key(lang, target)}.
	 */
	public static boolean[] matchedLetters(String targetKey, String heardKey) {
		int n = targetKey.length(), m = heardKey.length();
		boolean[] matched = new boolean[n];
		if (n == 0 || m == 0) return matched;
		int[][] dp = new int[n + 1][m + 1];
		for (int i = 1; i <= n; i++) dp[i][0] = i; // row 0 stays 0: starting anywhere in heard is free
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				int cost = targetKey.charAt(i - 1) == heardKey.charAt(j - 1) ? 0 : 1;
				dp[i][j] = Math.min(Math.min(dp[i][j - 1] + 1, dp[i - 1][j] + 1), dp[i - 1][j - 1] + cost);
			}
		}
		int j = 0;
		for (int k = 1; k <= m; k++) if (dp[n][k] < dp[n][j]) j = k; // ending anywhere is free
		int i = n;
		while (i > 0 && j > 0) {
			int cost = targetKey.charAt(i - 1) == heardKey.charAt(j - 1) ? 0 : 1;
			if (dp[i][j] == dp[i - 1][j - 1] + cost) {
				matched[i - 1] = cost == 0;
				i--;
				j--;
			} else if (dp[i][j] == dp[i - 1][j] + 1) {
				i--; // letter missing from what you said
			} else {
				j--; // extra sound
			}
		}
		return matched;
	}

	/**
	 * Colour per character of {@code display} (the romanized or Latin word shown on screen):
	 * 1 = said right, -1 = wrong/missing, 0 = not a letter (space etc).
	 */
	public static int[] letterColors(String lang, String display, String heard) {
		String targetKey = Romanizer.normalize(display);
		boolean[] ok = matchedLetters(targetKey, Romanizer.key(lang, heard));
		int[] colors = new int[display.length()];
		int k = 0;
		for (int c = 0; c < display.length(); c++) {
			int len = Romanizer.normalize(String.valueOf(display.charAt(c))).length();
			if (len == 0) continue;
			boolean all = true;
			for (int x = 0; x < len && k + x < ok.length; x++) all &= ok[k + x];
			colors[c] = all ? 1 : -1;
			k += len;
		}
		return colors;
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

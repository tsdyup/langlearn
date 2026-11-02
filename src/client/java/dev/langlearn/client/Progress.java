package dev.langlearn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.langlearn.LangLearn;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Which words you've unlocked, per language. Saved to config/langlearn/progress_<lang>.json. */
public final class Progress {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<HashMap<String, Entry>>() {}.getType();

	public static final class Entry {
		public boolean unlocked;
		public int bestScore;
		public int attempts;
		public int fails;
	}

	private static String loadedLang = null;
	private static Map<String, Entry> entries = new HashMap<>();

	private static Path file(String lang) {
		return Config.dir().resolve("progress_" + lang + ".json");
	}

	public static void load(String lang) {
		loadedLang = lang;
		entries = new HashMap<>();
		Path f = file(lang);
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			Map<String, Entry> m = GSON.fromJson(r, TYPE);
			if (m != null) entries = m;
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not read progress for {}", lang, e);
		}
	}

	private static void ensure() {
		String lang = Config.get().language;
		if (!lang.equals(loadedLang)) load(lang);
	}

	public static Entry get(String key) {
		ensure();
		return entries.computeIfAbsent(key, k -> new Entry());
	}

	public static boolean isUnlocked(String key) {
		ensure();
		Entry e = entries.get(key);
		return e != null && e.unlocked;
	}

	public static void recordSuccess(String key, int score) {
		Entry e = get(key);
		e.attempts++;
		e.unlocked = true;
		e.bestScore = Math.max(e.bestScore, score);
		save();
	}

	public static void recordFail(String key, int score) {
		Entry e = get(key);
		e.attempts++;
		e.fails++;
		e.bestScore = Math.max(e.bestScore, score);
		save();
	}

	public static int unlockedCount() {
		ensure();
		return (int) entries.values().stream().filter(e -> e.unlocked).count();
	}

	public static int seenCount() {
		ensure();
		return entries.size();
	}

	public static void reset() {
		ensure();
		entries.clear();
		save();
	}

	private static void save() {
		if (loadedLang == null || loadedLang.isEmpty()) return;
		try {
			Files.createDirectories(Config.dir());
			try (Writer w = Files.newBufferedWriter(file(loadedLang), StandardCharsets.UTF_8)) {
				GSON.toJson(entries, TYPE, w);
			}
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not save progress", e);
		}
	}
}

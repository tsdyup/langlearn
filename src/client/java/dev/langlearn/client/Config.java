package dev.langlearn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langlearn.LangLearn;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, saved to .minecraft/config/langlearn/config.json. Change them in game with /langlearn. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance;

	/** Minecraft language code to learn, e.g. ru_ru, zh_cn, ja_jp, es_es. Empty = not chosen yet. */
	public String language = "";
	public boolean enabled = true;
	/** Seconds you get to say the word for items picked up from the ground. */
	public int timerSeconds = 20;
	/** Seconds for items that came from chests, crafting, furnaces, trades, etc. */
	public int timerOtherSeconds = 30;
	/** easy / normal / hard: how close your pronunciation must be (see Grammar.Strictness). */
	public String strictness = "normal";
	/** Show pronunciation tips on the card. */
	public boolean hints = true;
	/** Bumped when defaults change, so old config files get the new defaults once. */
	public int configVersion = 0;
	/** How close (0-100%) your pronunciation must be to count. */
	public int threshold = 80;
	/** auto = voice when the speech model is ready, typing otherwise. voice / typing force one. */
	public String mode = "auto";
	/**
	 * Also run a recognizer that may only answer with Minecraft item names. Much more accurate than
	 * free recognition for single words, and what you said shows up as a real word instead of gibberish.
	 */
	public boolean itemGrammar = true;
	/** Also show the item's name in your own game language under the word. */
	public boolean showOwnLanguage = true;

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(LangLearn.MOD_ID);
	}

	public static Config get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		Path file = dir().resolve("config.json");
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					instance = GSON.fromJson(r, Config.class);
				}
			}
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not read config, using defaults", e);
		}
		if (instance == null) {
			instance = new Config();
			instance.configVersion = 2;
		}
		instance.timerSeconds = Math.clamp(instance.timerSeconds, 3, 60);
		if (instance.configVersion < 2) {
			// v0.5: longer default timers (stricter checking means more tries)
			if (instance.timerSeconds <= 10) instance.timerSeconds = 20;
			if (instance.timerOtherSeconds <= 20) instance.timerOtherSeconds = 30;
			if (instance.strictness == null) instance.strictness = "normal";
			instance.configVersion = 2;
		}
		if (instance.strictness == null) instance.strictness = "normal";
		if (instance.timerOtherSeconds <= 0) instance.timerOtherSeconds = 30;
		instance.timerOtherSeconds = Math.clamp(instance.timerOtherSeconds, 3, 60);
		instance.threshold = Math.clamp(instance.threshold, 10, 100);
		if (instance.mode == null) instance.mode = "auto";
		if (instance.language == null) instance.language = "";
	}

	public static void save() {
		try {
			Files.createDirectories(dir());
			try (Writer w = Files.newBufferedWriter(dir().resolve("config.json"), StandardCharsets.UTF_8)) {
				GSON.toJson(get(), w);
			}
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not save config", e);
		}
	}
}

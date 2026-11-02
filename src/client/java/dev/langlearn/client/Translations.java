package dev.langlearn.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.langlearn.LangLearn;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Reads Minecraft's own translation files. The game ships every item name in ~130 languages,
 * so the mod gets a full dictionary for free (including items from other mods).
 */
public final class Translations {
	private static String loadedCode = null;
	private static Map<String, String> strings = Map.of();
	private static List<String> codes = null;
	private static CompletableFuture<Map<String, String>> namesFuture = null;

	/** Loads the language file(s) for a code like "ru_ru". Returns false if the language doesn't exist. */
	public static synchronized boolean load(String code) {
		if (code == null || code.isEmpty()) return false;
		if (code.equals(loadedCode) && !strings.isEmpty()) return true;
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
		String path = "lang/" + code + ".json";
		Set<Identifier> ids = new LinkedHashSet<>();
		ids.add(Identifier.withDefaultNamespace(path));
		try {
			ids.addAll(rm.listResources("lang", id -> id.getPath().equals(path)).keySet());
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not list language files", e);
		}
		Map<String, String> out = new HashMap<>();
		for (Identifier id : ids) {
			for (Resource res : rm.getResourceStack(id)) {
				try (Reader r = res.openAsReader()) {
					JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
						if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
					}
				} catch (Exception e) {
					LangLearn.LOGGER.warn("Could not read {}", id, e);
				}
			}
		}
		if (out.isEmpty()) return false;
		strings = out;
		loadedCode = code;
		return true;
	}

	public static synchronized String get(String key) {
		return strings.get(key);
	}

	/** Native name of the loaded language, e.g. "Русский" or "日本語". */
	public static synchronized String languageName() {
		String name = strings.get("language.name");
		return name != null ? name : (loadedCode == null ? "?" : loadedCode);
	}

	/** All language codes the game has, e.g. [af_za, ar_sa, ..., zh_tw]. */
	public static synchronized List<String> availableCodes() {
		if (codes != null) return codes;
		Set<String> set = new java.util.TreeSet<>();
		try {
			ResourceManager rm = Minecraft.getInstance().getResourceManager();
			for (Identifier id : rm.listResources("lang", id -> id.getPath().endsWith(".json")).keySet()) {
				String p = id.getPath();
				String c = p.substring(p.lastIndexOf('/') + 1, p.length() - ".json".length());
				if (c.matches("[a-z]{2,3}_[a-z]{2,3}") || c.equals("lzh")) set.add(c);
			}
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not list languages", e);
		}
		codes = new ArrayList<>(set);
		return codes;
	}

	/** code -> "Name (Region)", computed in the background because it reads every language file. */
	public static synchronized CompletableFuture<Map<String, String>> languageNames() {
		if (namesFuture != null) return namesFuture;
		List<String> all = availableCodes();
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
		namesFuture = CompletableFuture.supplyAsync(() -> {
			Map<String, String> out = new TreeMap<>();
			for (String c : all) {
				String[] nr = readName(rm, c);
				out.put(c, nr[0] == null ? c : nr[0] + (nr[1] == null ? "" : " (" + nr[1] + ")"));
			}
			return out;
		});
		return namesFuture;
	}

	private static String[] readName(ResourceManager rm, String code) {
		String[] result = new String[2];
		for (Resource res : rm.getResourceStack(Identifier.withDefaultNamespace("lang/" + code + ".json"))) {
			try (JsonReader jr = new JsonReader(res.openAsReader())) {
				jr.beginObject();
				while (jr.hasNext()) {
					String k = jr.nextName();
					if (jr.peek() != JsonToken.STRING) {
						jr.skipValue();
						continue;
					}
					String v = jr.nextString();
					if (k.equals("language.name")) result[0] = v;
					else if (k.equals("language.region")) result[1] = v;
					if (result[0] != null && result[1] != null) return result;
				}
			} catch (Exception ignored) {
			}
		}
		return result;
	}
}

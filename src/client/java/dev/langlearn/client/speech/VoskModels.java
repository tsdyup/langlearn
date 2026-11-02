package dev.langlearn.client.speech;

import java.util.HashMap;
import java.util.Map;

/**
 * Which free offline Vosk speech model to use for each Minecraft language.
 * Languages not listed here still work, but you type the word in chat instead of saying it.
 * Models: https://alphacephei.com/vosk/models
 */
public final class VoskModels {
	private static final Map<String, String> MODELS = new HashMap<>();

	private static void put(String model, String... mcCodes) {
		for (String c : mcCodes) MODELS.put(c, model);
	}

	static {
		// the three priority languages
		put("vosk-model-small-ru-0.22", "ru_ru");
		put("vosk-model-small-cn-0.22", "zh_cn", "zh_tw", "zh_hk", "lzh");
		put("vosk-model-small-ja-0.22", "ja_jp");
		// everything else Vosk has a small model for
		put("vosk-model-small-en-us-0.15", "en_us", "en_gb", "en_au", "en_ca", "en_nz", "en_pt", "en_ud");
		put("vosk-model-small-es-0.42", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve");
		put("vosk-model-small-de-0.15", "de_de", "de_at", "de_ch");
		put("vosk-model-small-fr-0.22", "fr_fr", "fr_ca");
		put("vosk-model-small-it-0.22", "it_it");
		put("vosk-model-small-pt-0.3", "pt_br", "pt_pt");
		put("vosk-model-small-nl-0.22", "nl_nl", "nl_be");
		put("vosk-model-small-pl-0.22", "pl_pl");
		put("vosk-model-small-tr-0.3", "tr_tr");
		put("vosk-model-small-uk-v3-small", "uk_ua");
		put("vosk-model-small-ko-0.22", "ko_kr");
		put("vosk-model-small-vn-0.4", "vi_vn");
		put("vosk-model-small-ca-0.4", "ca_es");
		put("vosk-model-small-cs-0.4-rhasspy", "cs_cz");
		put("vosk-model-small-sv-rhasspy-0.15", "sv_se");
		put("vosk-model-small-fa-0.42", "fa_ir");
		put("vosk-model-small-hi-0.22", "hi_in");
		put("vosk-model-small-eo-0.42", "eo_uy");
		put("vosk-model-small-kz-0.42", "kk_kz");
		put("vosk-model-small-ka-0.42", "ka_ge");
		put("vosk-model-small-ky-0.42", "ky_kg");
		put("vosk-model-small-uz-0.22", "uz_uz");
		put("vosk-model-small-tg-0.22", "tg_tj");
		put("vosk-model-br-0.8", "br_fr");
	}

	/** Model name for a Minecraft language code, or null if voice isn't available for it. */
	public static String forLanguage(String mcCode) {
		return MODELS.get(mcCode);
	}

	public static String url(String model) {
		return "https://alphacephei.com/vosk/models/" + model + ".zip";
	}

	/** CJK models get free recognition only (their vocabularies aren't space separated words). */
	public static boolean isCjk(String mcCode) {
		return mcCode.startsWith("zh") || mcCode.equals("lzh") || mcCode.startsWith("ja");
	}
}

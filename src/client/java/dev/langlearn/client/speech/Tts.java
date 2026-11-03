package dev.langlearn.client.speech;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads words out loud with Piper, a free offline neural voice (https://github.com/rhasspy/piper).
 * The first time, it downloads the Piper program (~22 MB) and one voice per language (~60-75 MB)
 * into config/langlearn/tts. Generated audio is cached per word, so replays are instant.
 * Nothing is installed in Windows; delete the folder to remove it.
 */
public final class Tts {
	private static final String PIPER_ZIP =
			"https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip";
	private static final String VOICES = "https://huggingface.co/rhasspy/piper-voices/resolve/main/";

	/** Minecraft language -> Piper voice (path inside the piper-voices repository, without .onnx). */
	private static final Map<String, String> VOICE = Map.ofEntries(
			Map.entry("ru_ru", "ru/ru_RU/irina/medium/ru_RU-irina-medium"),
			Map.entry("zh_cn", "zh/zh_CN/huayan/medium/zh_CN-huayan-medium"),
			Map.entry("zh_tw", "zh/zh_CN/huayan/medium/zh_CN-huayan-medium"),
			Map.entry("zh_hk", "zh/zh_CN/huayan/medium/zh_CN-huayan-medium"),
			Map.entry("lzh", "zh/zh_CN/huayan/medium/zh_CN-huayan-medium"),
			Map.entry("en_us", "en/en_US/amy/medium/en_US-amy-medium"),
			Map.entry("en_gb", "en/en_GB/alan/medium/en_GB-alan-medium"),
			Map.entry("es_es", "es/es_ES/davefx/medium/es_ES-davefx-medium"),
			Map.entry("es_mx", "es/es_MX/ald/medium/es_MX-ald-medium"),
			Map.entry("de_de", "de/de_DE/mls/medium/de_DE-mls-medium"),
			Map.entry("fr_fr", "fr/fr_FR/mls/medium/fr_FR-mls-medium"),
			Map.entry("it_it", "it/it_IT/paola/medium/it_IT-paola-medium"),
			Map.entry("pt_br", "pt/pt_BR/cadu/medium/pt_BR-cadu-medium"),
			Map.entry("pl_pl", "pl/pl_PL/darkman/medium/pl_PL-darkman-medium"),
			Map.entry("nl_nl", "nl/nl_NL/alex/medium/nl_NL-alex-medium"),
			Map.entry("tr_tr", "tr/tr_TR/dfki/medium/tr_TR-dfki-medium"),
			Map.entry("cs_cz", "cs/cs_CZ/jirka/medium/cs_CZ-jirka-medium"),
			Map.entry("hu_hu", "hu/hu_HU/anna/medium/hu_HU-anna-medium"),
			Map.entry("fi_fi", "fi/fi_FI/harri/medium/fi_FI-harri-medium"),
			Map.entry("el_gr", "el/el_GR/joy/medium/el_GR-joy-medium"),
			Map.entry("ro_ro", "ro/ro_RO/mihai/medium/ro_RO-mihai-medium"),
			Map.entry("bg_bg", "bg/bg_BG/dimitar/medium/bg_BG-dimitar-medium"),
			Map.entry("ar_sa", "ar/ar_JO/kareem/medium/ar_JO-kareem-medium"),
			Map.entry("hi_in", "hi/hi_IN/pratham/medium/hi_IN-pratham-medium"),
			Map.entry("ka_ge", "ka/ka_GE/natia/medium/ka_GE-natia-medium"),
			Map.entry("fa_ir", "fa/fa_IR/amir/medium/fa_IR-amir-medium"),
			Map.entry("da_dk", "da/da_DK/talesyntese/medium/da_DK-talesyntese-medium"),
			Map.entry("sk_sk", "sk/sk_SK/lili/medium/sk_SK-lili-medium"),
			Map.entry("ca_es", "ca/ca_ES/upc_ona/medium/ca_ES-upc_ona-medium"),
			Map.entry("id_id", "id/id_ID/news_tts/medium/id_ID-news_tts-medium"));

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "LangLearn-Voice");
		t.setDaemon(true);
		return t;
	});

	private static Path base;
	private static volatile String status = "";
	private static volatile boolean busy = false;

	public static void init(Path configDir) {
		base = configDir.resolve("tts").toAbsolutePath();
	}

	public static boolean available(String lang) {
		return VOICE.containsKey(lang) && isWindows();
	}

	public static String status() {
		return status;
	}

	public static boolean busy() {
		return busy;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	/**
	 * Says {@code text} in the background. {@code onPlay} gets the audio length in milliseconds right before
	 * playback starts (used to pause the microphone so it doesn't hear the voice).
	 */
	public static void speak(String lang, String text, Consumer<Long> onPlay) {
		if (busy) return;
		String voice = VOICE.get(lang);
		if (voice == null) {
			status = "No voice for this language yet";
			return;
		}
		if (!isWindows()) {
			status = "Voices currently only work on Windows";
			return;
		}
		busy = true;
		WORKER.submit(() -> {
			try {
				Path wav = synthesize(voice, text);
				status = "";
				play(wav, onPlay);
			} catch (Throwable t) {
				status = "Voice error: " + t.getMessage();
				org.slf4j.LoggerFactory.getLogger("langlearn").error("Text to speech failed", t);
			} finally {
				busy = false;
			}
		});
	}

	/** Creates (or reuses) the WAV file for this text. Public for testing. */
	public static Path synthesize(String voice, String text) throws Exception {
		Path piper = ensurePiper();
		Path model = ensureVoice(voice);
		String name = voice.substring(voice.lastIndexOf('/') + 1);
		Path cacheDir = base.resolve("cache").resolve(name);
		Files.createDirectories(cacheDir);
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
				.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
		Path wav = cacheDir.resolve(hash + ".wav");
		if (Files.isRegularFile(wav) && Files.size(wav) > 1000) return wav;

		status = "Generating voice...";
		Process p = new ProcessBuilder(piper.toString(), "--model", model.toString(), "--output_file", wav.toString())
				.directory(piper.getParent().toFile())
				.redirectErrorStream(true)
				.start();
		try (OutputStream in = p.getOutputStream()) {
			in.write((text + "\n").getBytes(StandardCharsets.UTF_8));
		}
		byte[] log = p.getInputStream().readAllBytes();
		if (!p.waitFor(30, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			throw new IOException("Piper took too long");
		}
		if (!Files.isRegularFile(wav) || Files.size(wav) <= 1000) {
			throw new IOException("Piper made no audio: " + new String(log, StandardCharsets.UTF_8).trim());
		}
		return wav;
	}

	private static void play(Path wav, Consumer<Long> onPlay) throws Exception {
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(wav.toFile())) {
			Clip clip = AudioSystem.getClip();
			clip.open(ais);
			long ms = clip.getMicrosecondLength() / 1000;
			if (onPlay != null) onPlay.accept(ms);
			clip.start();
			Thread.sleep(ms + 100);
			clip.close();
		}
	}

	private static Path ensurePiper() throws Exception {
		Path dir = base.resolve("piper");
		Path exe = dir.resolve("piper").resolve("piper.exe");
		if (Files.isRegularFile(exe)) return exe;
		status = "Downloading voice program (22 MB)...";
		Path zip = base.resolve("piper.zip.part");
		download(PIPER_ZIP, zip, "Downloading voice program");
		Path tmp = base.resolve("piper.tmp");
		unzip(zip, tmp);
		if (Files.exists(dir)) deleteRecursively(dir);
		Files.move(tmp, dir);
		Files.deleteIfExists(zip);
		if (!Files.isRegularFile(exe)) throw new IOException("piper.exe missing after download");
		return exe;
	}

	private static Path ensureVoice(String voice) throws Exception {
		String name = voice.substring(voice.lastIndexOf('/') + 1);
		Path dir = base.resolve("voices");
		Path onnx = dir.resolve(name + ".onnx");
		Path json = dir.resolve(name + ".onnx.json");
		if (Files.isRegularFile(onnx) && Files.isRegularFile(json)) return onnx;
		Files.createDirectories(dir);
		download(VOICES + voice + ".onnx.json", dir.resolve(name + ".onnx.json.part"), "Downloading voice");
		download(VOICES + voice + ".onnx", dir.resolve(name + ".onnx.part"), "Downloading voice");
		Files.move(dir.resolve(name + ".onnx.json.part"), json, StandardCopyOption.REPLACE_EXISTING);
		Files.move(dir.resolve(name + ".onnx.part"), onnx, StandardCopyOption.REPLACE_EXISTING);
		return onnx;
	}

	private static void download(String url, Path target, String what) throws Exception {
		Files.createDirectories(target.getParent());
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
				.connectTimeout(Duration.ofSeconds(20)).build();
		HttpResponse<InputStream> resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
		long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(target)) {
			byte[] buf = new byte[1 << 16];
			long done = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				done += n;
				if (total > 0) status = what + "... " + (done * 100 / total) + "%";
			}
		}
	}

	private static void unzip(Path zip, Path dest) throws IOException {
		if (Files.exists(dest)) deleteRecursively(dest);
		Files.createDirectories(dest);
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				Path p = dest.resolve(e.getName()).normalize();
				if (!p.startsWith(dest)) continue;
				if (e.isDirectory()) Files.createDirectories(p);
				else {
					Files.createDirectories(p.getParent());
					Files.copy(zin, p, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteRecursively(Path p) throws IOException {
		try (var s = Files.walk(p)) {
			s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
				try {
					Files.delete(x);
				} catch (IOException ignored) {
				}
			});
		}
	}
}

package dev.langlearn.client.speech;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langlearn.LangLearn;
import dev.langlearn.client.Config;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline speech recognition with Vosk.
 * - downloads the language's model the first time (40-130 MB, once)
 * - while a prompt is active, records the microphone and feeds it to the recognizer
 * - everything it hears is pushed to {@link #heard} for the prompt logic to score
 */
public final class SpeechEngine {
	public enum State { NO_MODEL, DOWNLOADING, LOADING, READY, ERROR }

	private static final float SAMPLE_RATE = 16000f;

	private static volatile State state = State.NO_MODEL;
	private static volatile String status = "";
	private static volatile int downloadPercent = 0;
	private static volatile String modelLang = null;
	private static volatile Model model = null;

	private static volatile int session = 0;
	private static volatile boolean listening = false;
	private static volatile String grammar = null;
	private static volatile boolean micBroken = false;
	/** Text recognized during the current session (partial and final results). */
	public static final ConcurrentLinkedQueue<String> heard = new ConcurrentLinkedQueue<>();

	private static Thread micThread;
	private static Thread prepareThread;

	public static State state() {
		return state;
	}

	public static String status() {
		return state == State.DOWNLOADING ? "Downloading speech model... " + downloadPercent + "%" : status;
	}

	public static boolean isReadyFor(String lang) {
		return state == State.READY && lang.equals(modelLang) && !micBroken;
	}

	public static boolean micBroken() {
		return micBroken;
	}

	/** Make sure the model for this language is downloaded and loaded (runs in the background). */
	public static synchronized void prepare(String lang) {
		if (lang.equals(modelLang) && (state == State.READY || state == State.DOWNLOADING || state == State.LOADING)) return;
		String modelName = VoskModels.forLanguage(lang);
		modelLang = lang;
		Model old = model;
		model = null;
		if (old != null) {
			try {
				old.close();
			} catch (Throwable ignored) {
			}
		}
		if (modelName == null) {
			state = State.NO_MODEL;
			status = "No voice model for this language, type the word in chat instead";
			return;
		}
		state = State.LOADING;
		status = "Loading speech model...";
		prepareThread = new Thread(() -> {
			try {
				Path dir = ensureDownloaded(modelName);
				if (!lang.equals(modelLang)) return; // language changed meanwhile
				state = State.LOADING;
				status = "Loading speech model...";
				LibVosk.setLogLevel(LogLevel.WARNINGS);
				Model m = new Model(dir.toString());
				if (!lang.equals(modelLang)) {
					m.close();
					return;
				}
				model = m;
				state = State.READY;
				status = "Speech ready";
				ensureMicThread();
			} catch (Throwable t) {
				LangLearn.LOGGER.error("Could not prepare speech model {}", modelName, t);
				state = State.ERROR;
				status = "Speech model error: " + t.getMessage();
			}
		}, "LangLearn-ModelLoader");
		prepareThread.setDaemon(true);
		prepareThread.start();
	}

	private static Path ensureDownloaded(String modelName) throws IOException, InterruptedException {
		Path models = Config.dir().resolve("models");
		Files.createDirectories(models);
		Path existing = findModelRoot(models.resolve(modelName));
		if (existing != null) return existing;

		state = State.DOWNLOADING;
		downloadPercent = 0;
		Path zip = models.resolve(modelName + ".zip.part");
		HttpClient http = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpRequest req = HttpRequest.newBuilder(URI.create(VoskModels.url(modelName))).GET().build();
		HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " for " + VoskModels.url(modelName));
		long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(zip)) {
			byte[] buf = new byte[1 << 16];
			long done = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				done += n;
				if (total > 0) downloadPercent = (int) (done * 100 / total);
			}
		}
		status = "Unpacking speech model...";
		Path target = models.resolve(modelName);
		Path tmp = models.resolve(modelName + ".tmp");
		deleteRecursively(tmp);
		Files.createDirectories(tmp);
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				Path p = tmp.resolve(e.getName()).normalize();
				if (!p.startsWith(tmp)) continue; // zip-slip protection
				if (e.isDirectory()) {
					Files.createDirectories(p);
				} else {
					Files.createDirectories(p.getParent());
					Files.copy(zin, p, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		deleteRecursively(target);
		Files.move(tmp, target);
		Files.deleteIfExists(zip);
		Path root = findModelRoot(target);
		if (root == null) throw new IOException("Downloaded model has an unexpected layout");
		return root;
	}

	/** A Vosk model folder contains an "am" folder; zips usually wrap it in one extra folder. */
	private static Path findModelRoot(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) return null;
		try (Stream<Path> s = Files.walk(dir, 3)) {
			return s.filter(p -> Files.isDirectory(p.resolve("am")) || Files.isRegularFile(p.resolve("conf/model.conf")))
					.findFirst().orElse(null);
		}
	}

	private static void deleteRecursively(Path p) throws IOException {
		if (!Files.exists(p)) return;
		try (Stream<Path> s = Files.walk(p)) {
			s.sorted(Comparator.reverseOrder()).forEach(x -> {
				try {
					Files.delete(x);
				} catch (IOException ignored) {
				}
			});
		}
	}

	/** Start listening for a new prompt. {@code expected} (optional) enables grammar assist. */
	public static void startListening(String expectedPhrase) {
		heard.clear();
		last = "";
		grammar = expectedPhrase == null ? null
				: "[\"" + expectedPhrase.replace("\\", "").replace("\"", "") + "\", \"[unk]\"]";
		session++;
		listening = true;
		ensureMicThread();
	}

	public static void stopListening() {
		listening = false;
		session++;
	}

	private static synchronized void ensureMicThread() {
		if (micThread != null && micThread.isAlive()) return;
		micThread = new Thread(SpeechEngine::micLoop, "LangLearn-Microphone");
		micThread.setDaemon(true);
		micThread.start();
	}

	private static void micLoop() {
		AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
		TargetDataLine line = null;
		Recognizer rec = null;
		int recSession = -1;
		byte[] buf = new byte[3200]; // 0.1 s of audio
		while (true) {
			try {
				Model m = model;
				if (!listening || m == null) {
					if (line != null) {
						line.stop();
						line.close();
						line = null;
					}
					if (rec != null) {
						rec.close();
						rec = null;
					}
					Thread.sleep(30);
					continue;
				}
				if (line == null) {
					try {
						line = AudioSystem.getTargetDataLine(format);
						line.open(format, buf.length * 4);
						line.start();
						micBroken = false;
					} catch (Exception e) {
						LangLearn.LOGGER.error("Could not open the microphone", e);
						micBroken = true;
						line = null;
						listening = false;
						continue;
					}
				}
				if (rec == null || recSession != session) {
					if (rec != null) rec.close();
					String g = grammar;
					rec = g != null ? new Recognizer(m, SAMPLE_RATE, g) : new Recognizer(m, SAMPLE_RATE);
					recSession = session;
					line.flush(); // drop audio from before this prompt
				}
				int n = line.read(buf, 0, buf.length);
				if (n <= 0) continue;
				if (rec.acceptWaveForm(buf, n)) {
					push(rec.getResult(), "text");
				} else {
					push(rec.getPartialResult(), "partial");
				}
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				LangLearn.LOGGER.error("Speech recognition error", t);
				if (rec != null) {
					try {
						rec.close();
					} catch (Throwable ignored) {
					}
					rec = null;
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static volatile String last = "";

	private static void push(String json, String field) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has(field)) return;
			String t = o.get(field).getAsString().replace("[unk]", "").trim();
			if (t.isEmpty() || t.equals(last)) return;
			last = t;
			heard.add(t);
		} catch (Exception ignored) {
		}
	}
}

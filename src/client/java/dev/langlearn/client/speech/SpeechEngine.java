package dev.langlearn.client.speech;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langlearn.LangLearn;
import dev.langlearn.client.Config;

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
	private static volatile Vosk.Model model = null;

	private static volatile int session = 0;
	private static volatile boolean listening = false;
	private static volatile String grammar = null;
	private static volatile boolean micBroken = false;
	/** Text recognized during the current session (partial and final results). */
	public static final ConcurrentLinkedQueue<Heard> heard = new ConcurrentLinkedQueue<>();

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
		Vosk.Model old = model;
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
				Vosk.Model m = new Vosk.Model(dir.toString());
				loadVocabulary(dir);
				Confusables.prepare(lang, vocab);
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

	/** A recognized piece of speech. {@code constrained} = came from the item-name-only recognizer. */
	public record Heard(String text, boolean constrained, boolean isFinal, double confidence) {}

	/**
	 * Start listening for a new prompt.
	 * @param grammarJson optional Vosk grammar (JSON list of phrases). When given, a second recognizer
	 *                    that can only answer with those phrases runs next to the free one.
	 */
	public static void startListening(String grammarJson) {
		heard.clear();
		lastFree = "";
		lastConstrained = "";
		grammar = grammarJson;
		session++;
		listening = true;
		ensureMicThread();
	}

	public static void stopListening() {
		listening = false;
		session++;
	}

	/** Words the current model knows (from graph/words.txt), or null if unknown. */
	public static java.util.Set<String> vocabulary() {
		return vocab;
	}

	private static volatile java.util.Set<String> vocab = null;

	static void loadVocabulary(Path modelRoot) {
		vocab = null;
		try {
			Path words = modelRoot.resolve("graph").resolve("words.txt");
			java.util.Set<String> set = null;
			if (Files.isRegularFile(words)) {
				set = new java.util.HashSet<>();
				try (Stream<String> lines = Files.lines(words, java.nio.charset.StandardCharsets.UTF_8)) {
					for (String l : (Iterable<String>) lines::iterator) {
						int sp = l.indexOf(' ');
						set.add(sp > 0 ? l.substring(0, sp) : l);
					}
				}
			} else {
				Path gr = modelRoot.resolve("graph").resolve("Gr.fst");
				if (Files.isRegularFile(gr)) set = FstSymbols.read(gr);
			}
			if (set != null) {
				set.removeIf(w -> w.isEmpty() || w.startsWith("<") || w.startsWith("#") || w.startsWith("!") || w.equals("[unk]"));
				vocab = set;
				LangLearn.LOGGER.info("Speech vocabulary: {} words", set.size());
			}
		} catch (Exception e) {
			LangLearn.LOGGER.warn("Could not read model vocabulary", e);
		}
	}

	private static volatile long muteUntil = 0;

	/** Ignore the microphone for the next {@code ms} milliseconds (while the voice reads the word). */
	public static void muteFor(long ms) {
		muteUntil = System.nanoTime() + ms * 1_000_000L;
	}

	/** Current microphone loudness 0..1 (before the automatic boost), for the level bar on the card. */
	public static float micLevel() {
		return micLevel;
	}

	private static volatile float micLevel = 0;

	/**
	 * Automatic gain: quiet microphones made the recognizer hear nothing. We track the recent peak and
	 * scale the audio so speech reaches about half of full volume (at most 20x louder).
	 */
	private static double agcPeak = 3000;

	private static void applyGain(byte[] buf, int n) {
		int peak = 1;
		long sumSq = 0;
		for (int i = 0; i + 1 < n; i += 2) {
			int v = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
			peak = Math.max(peak, Math.abs(v));
			sumSq += (long) v * v;
		}
		int samples = Math.max(1, n / 2);
		micLevel = (float) Math.min(1.0, Math.sqrt((double) sumSq / samples) / 6000.0);
		agcPeak = Math.max(peak, agcPeak * 0.995);
		double gain = Math.min(20.0, 16000.0 / agcPeak);
		for (int i = 0; i + 1 < n; i += 2) {
			int v = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
			int g = (int) Math.max(-32768, Math.min(32767, Math.round(v * gain)));
			buf[i] = (byte) g;
			buf[i + 1] = (byte) (g >> 8);
		}
	}

	private static synchronized void ensureMicThread() {
		if (micThread != null && micThread.isAlive()) return;
		micThread = new Thread(SpeechEngine::micLoop, "LangLearn-Microphone");
		micThread.setDaemon(true);
		micThread.start();
	}

	private static void closeQuietly(Vosk.Recognizer r) {
		if (r == null) return;
		try {
			r.close();
		} catch (Throwable ignored) {
		}
	}

	private static void micLoop() {
		AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
		TargetDataLine line = null;
		Vosk.Recognizer free = null;
		Vosk.Recognizer constrained = null;
		int recSession = -1;
		byte[] buf = new byte[3200]; // 0.1 s of audio
		while (true) {
			try {
				Vosk.Model m = model;
				if (!listening || m == null) {
					if (line != null) {
						line.stop();
						line.close();
						line = null;
					}
					closeQuietly(free);
					closeQuietly(constrained);
					free = null;
					constrained = null;
					micLevel = 0;
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
				if (free == null || recSession != session) {
					closeQuietly(free);
					closeQuietly(constrained);
					constrained = null;
					free = new Vosk.Recognizer(m, SAMPLE_RATE);
					String g = grammar;
					if (g != null) {
						try {
							constrained = new Vosk.Recognizer(m, SAMPLE_RATE, g);
							constrained.setWords(true);
						} catch (Throwable t) {
							LangLearn.LOGGER.warn("Grammar recognizer failed, using free recognition only", t);
						}
					}
					recSession = session;
					line.flush(); // drop audio from before this prompt
				}
				int n = line.read(buf, 0, buf.length);
				if (n <= 0) continue;
				if (System.nanoTime() < muteUntil) {
					micLevel = 0;
					continue; // the word is being played out loud: don't let the recognizer hear it
				}
				applyGain(buf, n);
				if (free.acceptWaveForm(buf, n)) push(free.getResult(), "text", false);
				else push(free.getPartialResult(), "partial", false);
				if (constrained != null) {
					if (constrained.acceptWaveForm(buf, n)) push(constrained.getResult(), "text", true);
					else push(constrained.getPartialResult(), "partial", true);
				}
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				LangLearn.LOGGER.error("Speech recognition error", t);
				closeQuietly(free);
				closeQuietly(constrained);
				free = null;
				constrained = null;
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	/** Marker text for "heard something, but not an item name". */
	public static final String UNSURE = "?";

	private static volatile String lastFree = "";
	private static volatile String lastConstrained = "";

	private static void push(String json, String field, boolean isConstrained) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has(field)) return;
			String raw = o.get(field).getAsString();
			String t = raw.replace("[unk]", "").replaceAll("\\s+", " ").trim();
			if (t.isEmpty()) {
				// the item-name recognizer heard speech but it wasn't any item name
				if (isConstrained && raw.contains("[unk]")) t = UNSURE;
				else return;
			}
			boolean isFinal = field.equals("text");
			// skip repeated partial results; final results (with confidence) always go through
			if (isConstrained) {
				if (!isFinal && t.equals(lastConstrained)) return;
				lastConstrained = isFinal ? "" : t;
			} else {
				if (!isFinal && t.equals(lastFree)) return;
				lastFree = isFinal ? "" : t;
			}
			double conf = -1;
			if (isFinal && o.has("result") && o.get("result").isJsonArray()) {
				// average confidence of the recognized words (only final results have it)
				double sum = 0;
				int count = 0;
				for (var el : o.getAsJsonArray("result")) {
					JsonObject w = el.getAsJsonObject();
					if (w.has("conf")) {
						sum += w.get("conf").getAsDouble();
						count++;
					}
				}
				if (count > 0) conf = sum / count;
			}
			heard.add(new Heard(t, isConstrained, isFinal, conf));
		} catch (Exception ignored) {
		}
	}
}

package dev.langlearn.client.speech;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Our own binding to the Vosk library that always talks UTF-8.
 *
 * Vosk's Java classes pass text through JNA's default encoding, which on Windows is the old
 * Windows code page (e.g. windows-1250/1252). Cyrillic, Chinese and Japanese then turn into "?"
 * both in the word list we give the recognizer and in what it hears back. Here every string is
 * converted to/from UTF-8 bytes ourselves.
 */
final class Vosk {
	static {
		// Loading LibVosk unpacks and loads the native library (and its DLLs on Windows).
		LibVosk.setLogLevel(LogLevel.WARNINGS);
		String name = Platform.isWindows() ? "libvosk" : "vosk";
		NativeLibrary lib = NativeLibrary.getInstance(name,
				Map.of(Library.OPTION_CLASSLOADER, LibVosk.class.getClassLoader()));
		Native.register(Vosk.class, lib);
	}

	static native Pointer vosk_model_new(byte[] path);

	static native void vosk_model_free(Pointer model);

	static native Pointer vosk_recognizer_new(Pointer model, float sampleRate);

	static native Pointer vosk_recognizer_new_grm(Pointer model, float sampleRate, byte[] grammar);

	static native int vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int len);

	static native Pointer vosk_recognizer_result(Pointer recognizer);

	static native Pointer vosk_recognizer_partial_result(Pointer recognizer);

	static native Pointer vosk_recognizer_final_result(Pointer recognizer);

	static native void vosk_recognizer_free(Pointer recognizer);

	static native void vosk_recognizer_set_words(Pointer recognizer, int words);

	static byte[] utf8(String s) {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[b.length + 1]; // C strings end with a zero byte
		System.arraycopy(b, 0, out, 0, b.length);
		return out;
	}

	static String str(Pointer p) {
		return p == null ? "" : p.getString(0, "UTF-8");
	}

	/** A loaded speech model. */
	static final class Model implements AutoCloseable {
		final Pointer ptr;

		Model(String path) {
			ptr = vosk_model_new(utf8(path));
			if (ptr == null) throw new IllegalStateException("Failed to load speech model from " + path);
		}

		@Override
		public void close() {
			vosk_model_free(ptr);
		}
	}

	/** A recognizer; with a grammar it can only answer with the given phrases. */
	static final class Recognizer implements AutoCloseable {
		private final Pointer ptr;

		Recognizer(Model model, float sampleRate) {
			ptr = vosk_recognizer_new(model.ptr, sampleRate);
			if (ptr == null) throw new IllegalStateException("Failed to create recognizer");
		}

		Recognizer(Model model, float sampleRate, String grammarJson) {
			ptr = vosk_recognizer_new_grm(model.ptr, sampleRate, utf8(grammarJson));
			if (ptr == null) throw new IllegalStateException("Failed to create grammar recognizer");
		}

		/** Include per-word confidence ("conf") in final results. */
		void setWords(boolean on) {
			vosk_recognizer_set_words(ptr, on ? 1 : 0);
		}

		boolean acceptWaveForm(byte[] data, int len) {
			return vosk_recognizer_accept_waveform(ptr, data, len) != 0;
		}

		String getResult() {
			return str(vosk_recognizer_result(ptr));
		}

		String getPartialResult() {
			return str(vosk_recognizer_partial_result(ptr));
		}

		String getFinalResult() {
			return str(vosk_recognizer_final_result(ptr));
		}

		@Override
		public void close() {
			vosk_recognizer_free(ptr);
		}
	}
}

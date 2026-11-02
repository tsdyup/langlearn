package dev.langlearn.client.speech;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the word list that small Vosk models keep inside graph/Gr.fst (an OpenFst file with an
 * embedded symbol table). We need it to know which words the recognizer can be asked for.
 */
final class FstSymbols {
	private static final int FST_MAGIC = 0x7eb2fdd6;
	private static final int SYMTAB_MAGIC = 0x7eb2fb74;

	static Set<String> read(Path fst) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(fst), 1 << 16))) {
			if (readInt(in) != FST_MAGIC) return null;
			readString(in); // fst type
			readString(in); // arc type
			readInt(in); // version
			int flags = readInt(in);
			readLong(in); // properties
			readLong(in); // start
			readLong(in); // states
			readLong(in); // arcs
			Set<String> words = null;
			if ((flags & 1) != 0) words = readSymbols(in);
			if ((flags & 2) != 0) words = readSymbols(in); // output symbols = words
			return words;
		}
	}

	private static Set<String> readSymbols(DataInputStream in) throws IOException {
		if (readInt(in) != SYMTAB_MAGIC) throw new IOException("bad symbol table");
		readString(in); // name
		readLong(in); // available key
		long size = readLong(in);
		if (size < 0 || size > 10_000_000) throw new IOException("bad symbol table size");
		Set<String> out = new HashSet<>((int) (size * 1.4));
		for (long i = 0; i < size; i++) {
			out.add(readString(in));
			readLong(in);
		}
		return out;
	}

	private static int readInt(InputStream in) throws IOException {
		byte[] b = in.readNBytes(4);
		if (b.length < 4) throw new IOException("eof");
		return (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
	}

	private static long readLong(InputStream in) throws IOException {
		long lo = readInt(in) & 0xFFFFFFFFL;
		long hi = readInt(in) & 0xFFFFFFFFL;
		return lo | hi << 32;
	}

	private static String readString(InputStream in) throws IOException {
		int n = readInt(in);
		if (n < 0 || n > 1 << 20) throw new IOException("bad string");
		return new String(in.readNBytes(n), StandardCharsets.UTF_8);
	}
}

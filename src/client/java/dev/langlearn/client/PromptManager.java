package dev.langlearn.client;

import dev.langlearn.client.speech.SpeechEngine;
import dev.langlearn.client.speech.Grammar;
import dev.langlearn.client.speech.Tts;
import dev.langlearn.client.text.Hints;
import dev.langlearn.client.text.Matcher;
import dev.langlearn.client.text.Romanizer;
import dev.langlearn.net.FailPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** The game loop of the mod: queue of picked-up items, the active prompt, and the result popup. */
public final class PromptManager {
	public static final class Prompt {
		public ItemStack stack;
		public String key;
		public int count;
		public String word;
		public String roman;
		public String own;
		public String langName;
		public int totalTicks;
		public int ticksLeft;
		public int best;
		public String lastHeard = "";
		public boolean voice;
		public boolean typing;
		public boolean test;
		/** the text shown for letter feedback: the romanization, or the word itself for Latin scripts */
		public String display;
		/** per character of {@link #display}: 1 right, -1 wrong, 0 neutral; null until you've said something */
		public int[] colors;
		public boolean grammar;
		public boolean pieces;
		public Grammar.Strictness strictness = Grammar.Strictness.NORMAL;
		/** pronunciation tips for this word (rotated on the card) */
		public java.util.List<String> tips = java.util.List.of();
		/** what went wrong in the last attempt, or null */
		public String mistake;
		/** best score among results that were shown (used for the letter colours) */
		public int bestShown = -1;
		/** picked up from the ground (short timer) vs chest/crafting/etc. (long timer) */
		public boolean ground;
	}

	public static final class Result {
		public boolean success;
		public ItemStack stack;
		public String word;
		public String roman;
		public int score;
		public int count;
		public int successes;
		public int attempts;
		public int ticksLeft;
		public int totalTicks;
		public boolean test;
	}

	private static final int MAX_QUEUE = 16;
	private static final Deque<Prompt> queue = new ArrayDeque<>();
	private static Prompt active;
	private static Result result;

	public static Prompt active() {
		return active;
	}

	public static Result result() {
		return result;
	}

	public static void clear() {
		queue.clear();
		if (active != null) SpeechEngine.stopListening();
		active = null;
		result = null;
	}

	/** An item arrived in the inventory (from the server), or /langlearn test. */
	public static void onPickup(ItemStack stack, int count, boolean ground, boolean test) {
		Config cfg = Config.get();
		if ((!cfg.enabled && !test) || cfg.language.isEmpty() || stack.isEmpty()) return;
		if (!Translations.load(cfg.language)) return;
		String key = stack.getItem().getDescriptionId();
		String word = Translations.get(key);
		if (word == null || word.isBlank() || word.contains("%")) return;
		if (!test && Progress.isUnlocked(key)) return;

		if (!test) {
			if (active != null && !active.test && active.key.equals(key)) {
				active.count += count;
				return;
			}
			for (Prompt p : queue) {
				if (p.key.equals(key) && !p.test) {
					p.count += count;
					p.ground = p.ground && ground; // keep the longer timer
					return;
				}
			}
		}
		if (queue.size() >= MAX_QUEUE) return;

		Prompt p = new Prompt();
		p.stack = stack.copy();
		p.key = key;
		p.count = count;
		p.word = word;
		p.roman = Romanizer.display(cfg.language, word);
		String own = stack.getHoverName().getString();
		p.own = (cfg.showOwnLanguage && !own.equalsIgnoreCase(word)) ? own : null;
		p.langName = Translations.languageName();
		p.test = test;
		p.ground = ground;
		p.display = p.roman != null ? p.roman : word;
		p.tips = cfg.hints ? Hints.forWord(cfg.language, word, p.roman, 3) : java.util.List.of();
		queue.add(p);
	}

	private static void begin(Prompt p) {
		Config cfg = Config.get();
		boolean voiceReady = SpeechEngine.isReadyFor(cfg.language);
		switch (cfg.mode) {
			case "typing" -> {
				p.voice = false;
				p.typing = true;
			}
			case "both" -> {
				p.voice = voiceReady;
				p.typing = true;
			}
			default -> { // auto
				p.voice = voiceReady;
				p.typing = !voiceReady;
			}
		}
		p.strictness = Grammar.Strictness.of(cfg.strictness);
		p.totalTicks = (p.ground ? cfg.timerSeconds : cfg.timerOtherSeconds) * 20;
		p.ticksLeft = p.totalTicks;
		active = p;
		if (p.voice) {
			String grammar = null;
			if (cfg.itemGrammar) {
				try {
					grammar = Grammar.build(cfg.language, p.word, p.strictness);
				} catch (Throwable t) {
					grammar = null;
				}
			}
			p.grammar = grammar != null;
			p.pieces = p.grammar && Grammar.hasPieces(cfg.language, p.word);
			SpeechEngine.startListening(grammar);
		}
		play(SoundEvents.NOTE_BLOCK_PLING, 1.4f);
		if (cfg.autoHear) hear();
	}

	/** Reads the current word out loud (or the last result's word if no card is open). */
	public static void hear() {
		String lang = Config.get().language;
		String word = active != null ? active.word : result != null ? result.word : null;
		if (word == null || !Tts.available(lang)) return;
		Tts.speak(lang, word, ms -> SpeechEngine.muteFor(ms + 400));
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			if (active != null || !queue.isEmpty()) clear();
			return;
		}

		if (result != null && --result.ticksLeft <= 0) result = null;

		if (active == null) {
			// short pause after a result before the next word pops up
			if (!queue.isEmpty() && (result == null || result.totalTicks - result.ticksLeft >= 20)) {
				begin(queue.poll());
			}
			return;
		}

		Prompt p = active;
		String lang = Config.get().language;
		SpeechEngine.Heard h;
		while ((h = SpeechEngine.heard.poll()) != null) {
			// with the item-name recognizer active, only its answers are shown and coloured:
			// the free recognizer mostly produces gibberish for single words
			if (p.grammar && !h.constrained()) continue;
			if (h.text().equals(SpeechEngine.UNSURE)) {
				p.lastHeard = "(not sure, say it again)";
				continue;
			}
			int s = Matcher.score(lang, p.word, h.text());
			p.lastHeard = readable(lang, h.text());
			if (s >= p.bestShown || p.colors == null) {
				p.colors = Matcher.letterColors(lang, p.display, h.text());
				p.bestShown = s;
			}
			p.best = Math.max(p.best, s);

			if (p.grammar) {
				// strict mode: only a finished answer that is exactly the item, said clearly enough, counts
				if (!h.isFinal()) continue;
				// words built from pieces are recognized less reliably, so a tiny piece may be missing
				if (s >= (p.pieces ? 90 : 100)) {
					double conf = h.confidence() < 0 ? 1.0 : h.confidence();
					int pct = (int) Math.round(conf * 100);
					if (conf >= p.strictness.minConfidence) {
						finish(true, pct);
						return;
					}
					p.mistake = "almost! it wasn't sure (" + pct + "%), say it more clearly";
				} else {
					String m = Hints.mistake(lang, p.word, h.text());
					p.mistake = m != null ? m : "that was a different word";
				}
				play(SoundEvents.NOTE_BLOCK_BASS, 0.8f);
			} else if (s >= Config.get().threshold) {
				finish(true, s);
				return;
			}
		}
		if (p.voice && SpeechEngine.micBroken()) {
			p.voice = false;
			p.typing = true; // no microphone: fall back to typing
		}
		if (--p.ticksLeft <= 0) finish(false, p.best);
	}

	/** Chat message typed while a prompt is active. Returns true if we used it as an answer. */
	public static boolean onChat(String message) {
		Prompt p = active;
		if (p == null || !p.typing) return false;
		String lang = Config.get().language;
		int s = Matcher.score(lang, p.word, message);
		p.lastHeard = readable(lang, message);
		if (s < Config.get().threshold) p.mistake = Hints.mistake(lang, p.word, message);
		if (s >= p.bestShown || p.colors == null) {
			p.colors = Matcher.letterColors(lang, p.display, message);
			p.bestShown = s;
		}
		p.best = Math.max(p.best, s);
		if (s >= Config.get().threshold) finish(true, s);
		else play(SoundEvents.NOTE_BLOCK_BASS, 0.8f);
		return true;
	}

	/** What was heard, in Latin letters so you can read it even for Cyrillic/kanji/hanzi. */
	private static String readable(String lang, String text) {
		String r = Romanizer.display(lang, text);
		return r != null ? r : text;
	}

	private static void finish(boolean success, int score) {
		Prompt p = active;
		active = null;
		SpeechEngine.stopListening();
		if (!p.test) {
			if (success) {
				Progress.recordSuccess(p.key, score);
			} else {
				Progress.recordFail(p.key, score);
				if (ClientPlayNetworking.canSend(FailPayload.TYPE)) {
					ClientPlayNetworking.send(new FailPayload(p.stack, p.count));
				}
			}
		}
		Progress.Entry e = Progress.get(p.key);
		Result r = new Result();
		r.success = success;
		r.stack = p.stack;
		r.word = p.word;
		r.roman = p.roman;
		r.score = score;
		r.count = p.count;
		r.attempts = e.attempts;
		r.successes = e.attempts - e.fails;
		r.totalTicks = success ? 50 : 70;
		r.ticksLeft = r.totalTicks;
		r.test = p.test;
		result = r;
		if (success) {
			play(SoundEvents.NOTE_BLOCK_CHIME, 1.5f);
		} else {
			play(SoundEvents.NOTE_BLOCK_DIDGERIDOO, 0.7f);
		}
	}

	private static void play(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}
}

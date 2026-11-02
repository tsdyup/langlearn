package dev.langlearn.client;

import dev.langlearn.client.speech.SpeechEngine;
import dev.langlearn.client.speech.Grammar;
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
		p.totalTicks = (p.ground ? cfg.timerSeconds : cfg.timerOtherSeconds) * 20;
		p.ticksLeft = p.totalTicks;
		active = p;
		if (p.voice) {
			String grammar = null;
			if (cfg.itemGrammar) {
				try {
					grammar = Grammar.build(cfg.language, p.word);
				} catch (Throwable t) {
					grammar = null;
				}
			}
			p.grammar = grammar != null;
			SpeechEngine.startListening(grammar);
		}
		play(SoundEvents.NOTE_BLOCK_PLING, 1.4f);
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
			// show the item-name recognizer's answer when it exists, it's a real word instead of gibberish
			if (h.constrained() || !p.grammar) p.lastHeard = readable(lang, h.text());
			int s = Matcher.score(lang, p.word, h.text());
			if (s > p.best || p.colors == null) {
				p.colors = Matcher.letterColors(lang, p.display, h.text());
			}
			p.best = Math.max(p.best, s);
			if (s >= Config.get().threshold) {
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
		if (s > p.best || p.colors == null) p.colors = Matcher.letterColors(lang, p.display, message);
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

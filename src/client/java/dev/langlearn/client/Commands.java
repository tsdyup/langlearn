package dev.langlearn.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.langlearn.client.speech.SpeechEngine;
import dev.langlearn.client.speech.VoskModels;
import dev.langlearn.client.text.Japanese;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** /langlearn ... commands (client side, work on any server). */
public final class Commands {
	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("langlearn")
				.executes(Commands::status)
				.then(ClientCommands.literal("language")
						.then(ClientCommands.argument("code", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Translations.availableCodes(), b))
								.executes(ctx -> setLanguage(ctx, StringArgumentType.getString(ctx, "code")))))
				.then(ClientCommands.literal("languages").executes(Commands::listLanguages))
				.then(ClientCommands.literal("timer")
						.then(ClientCommands.literal("pickup")
								.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(3, 60))
										.executes(ctx -> {
											Config.get().timerSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
											Config.save();
											return ok(ctx, "Ground pickups: " + Config.get().timerSeconds + " seconds");
										})))
						.then(ClientCommands.literal("other")
								.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(3, 60))
										.executes(ctx -> {
											Config.get().timerOtherSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
											Config.save();
											return ok(ctx, "Chests/crafting/other: " + Config.get().timerOtherSeconds + " seconds");
										}))))
				.then(ClientCommands.literal("threshold")
						.then(ClientCommands.argument("percent", IntegerArgumentType.integer(10, 100))
								.executes(ctx -> {
									Config.get().threshold = IntegerArgumentType.getInteger(ctx, "percent");
									Config.save();
									return ok(ctx, "You now need " + Config.get().threshold + "% accuracy");
								})))
				.then(ClientCommands.literal("mode")
						.then(mode("auto")).then(mode("typing")).then(mode("both")))
				.then(ClientCommands.literal("grammar")
						.then(ClientCommands.argument("on", BoolArgumentType.bool())
								.executes(ctx -> {
									Config.get().itemGrammar = BoolArgumentType.getBool(ctx, "on");
									Config.save();
									return ok(ctx, "Item-name recognizer " + (Config.get().itemGrammar ? "on" : "off (free recognition only)"));
								})))
				.then(ClientCommands.literal("strictness")
						.then(strict("easy")).then(strict("normal")).then(strict("hard")))
				.then(ClientCommands.literal("hints")
						.then(ClientCommands.argument("on", BoolArgumentType.bool())
								.executes(ctx -> {
									Config.get().hints = BoolArgumentType.getBool(ctx, "on");
									Config.save();
									return ok(ctx, "Pronunciation tips " + (Config.get().hints ? "on" : "off"));
								})))
				.then(ClientCommands.literal("on").executes(ctx -> toggle(ctx, true)))
				.then(ClientCommands.literal("off").executes(ctx -> toggle(ctx, false)))
				.then(ClientCommands.literal("test").executes(Commands::test))
				.then(ClientCommands.literal("reset").executes(ctx -> {
					Progress.reset();
					return ok(ctx, "Progress for " + Config.get().language + " reset");
				}))
		);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> mode(String m) {
		return ClientCommands.literal(m).executes(ctx -> {
			Config.get().mode = m;
			Config.save();
			return ok(ctx, "Mode: " + m);
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> strict(String level) {
		return ClientCommands.literal(level).executes(ctx -> {
			Config.get().strictness = level;
			Config.save();
			String what = switch (level) {
				case "easy" -> "close enough counts";
				case "hard" -> "many sound-alike words compete, and it must be clear (85%+)";
				default -> "sound-alike words compete with the right one";
			};
			return ok(ctx, "Strictness: " + level + " (" + what + ")");
		});
	}

	private static int ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal("[LangLearn] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
		return 1;
	}

	private static int error(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendError(Component.literal("[LangLearn] " + msg));
		return 0;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		Config c = Config.get();
		if (c.language.isEmpty()) {
			return ok(ctx, "No language chosen yet. Use /langlearn language <code>, e.g. ru_ru, zh_cn, ja_jp. See /langlearn languages");
		}
		Translations.load(c.language);
		ok(ctx, "Learning " + Translations.languageName() + " (" + c.language + ")" + (c.enabled ? "" : " [OFF]"));
		ok(ctx, "Words unlocked: " + Progress.unlockedCount() + " / " + Progress.seenCount() + " seen");
		ok(ctx, "Timer " + c.timerSeconds + "s (ground) / " + c.timerOtherSeconds + "s (chests, crafting...), needs " + c.threshold + "%, mode " + c.mode
				+ ", strictness " + c.strictness
				+ (c.itemGrammar ? "" : ", item-name recognizer off"));
		String voice = VoskModels.forLanguage(c.language) == null
				? "no voice model for this language (typing only)"
				: SpeechEngine.status();
		return ok(ctx, "Voice: " + voice);
	}

	public static int setLanguage(CommandContext<FabricClientCommandSource> ctx, String code) {
		if (!Translations.availableCodes().contains(code) || !Translations.load(code)) {
			return error(ctx, "Unknown language '" + code + "'. See /langlearn languages");
		}
		Config.get().language = code;
		Config.save();
		Progress.load(code);
		PromptManager.clear();
		if (code.startsWith("ja")) Japanese.warmUp();
		SpeechEngine.prepare(code);
		ok(ctx, "Now learning " + Translations.languageName() + " (" + code + ")");
		if (VoskModels.forLanguage(code) != null) {
			ok(ctx, "Downloading the free offline speech model the first time (40-130 MB). Until it's ready, type words in chat.");
		} else {
			ok(ctx, "There's no voice model for this language, so you'll type the words in chat.");
		}
		return 1;
	}

	private static int listLanguages(CommandContext<FabricClientCommandSource> ctx) {
		ok(ctx, "Reading language list...");
		Translations.languageNames().thenAccept((Map<String, String> names) -> Minecraft.getInstance().execute(() -> {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, String> e : names.entrySet()) {
				if (!sb.isEmpty()) sb.append(", ");
				sb.append(e.getKey()).append(" ").append(e.getValue());
				if (VoskModels.forLanguage(e.getKey()) != null) sb.append(" [voice]");
			}
			ctx.getSource().sendFeedback(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
			ctx.getSource().sendFeedback(Component.literal("[voice] = you can speak; others are typed. Use /langlearn language <code>")
					.withStyle(ChatFormatting.GOLD));
		}));
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> ctx, boolean on) {
		Config.get().enabled = on;
		Config.save();
		if (!on) PromptManager.clear();
		return ok(ctx, on ? "Enabled" : "Disabled (items are safe)");
	}

	private static int test(CommandContext<FabricClientCommandSource> ctx) {
		if (Config.get().language.isEmpty()) return error(ctx, "Choose a language first: /langlearn language <code>");
		ItemStack held = ctx.getSource().getPlayer().getMainHandItem();
		if (held.isEmpty()) return error(ctx, "Hold an item to practice it (nothing gets deleted in test mode)");
		PromptManager.onPickup(held, 1, true, true);
		return 1;
	}
}

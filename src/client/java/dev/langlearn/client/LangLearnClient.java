package dev.langlearn.client;

import dev.langlearn.LangLearn;
import dev.langlearn.client.speech.SpeechEngine;
import dev.langlearn.client.text.Japanese;
import dev.langlearn.net.FailPayload;
import dev.langlearn.net.PickupPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LangLearnClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Config.load();

		ClientPlayNetworking.registerGlobalReceiver(PickupPayload.TYPE, (payload, context) ->
				context.client().execute(() -> PromptManager.onPickup(payload.stack(), payload.count(), payload.ground(), false)));

		// level ticks stop while the game is paused, so the timer pauses too
		ClientTickEvents.END_LEVEL_TICK.register(level -> PromptManager.tick(net.minecraft.client.Minecraft.getInstance()));

		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				Identifier.fromNamespaceAndPath(LangLearn.MOD_ID, "prompt"), HudOverlay::render);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> Commands.register(dispatcher));

		// typing answers: a chat message sent during a prompt is used as the answer and not sent to the server
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !PromptManager.onChat(message));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			Config cfg = Config.get();
			if (cfg.language.isEmpty()) {
				tell(client, "Pick a language to learn: /langlearn language <code>  (e.g. ru_ru, zh_cn, ja_jp)  -  list: /langlearn languages");
			} else {
				Translations.load(cfg.language);
				Progress.load(cfg.language);
				if (cfg.language.startsWith("ja")) Japanese.warmUp();
				SpeechEngine.prepare(cfg.language);
			}
			if (!ClientPlayNetworking.canSend(FailPayload.TYPE)) {
				tell(client, "This server doesn't have Language Learner installed, so pickups won't be quizzed here. /langlearn test still works.");
			}
		}));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PromptManager::clear));
	}

	private static void tell(net.minecraft.client.Minecraft client, String msg) {
		if (client.player == null) return;
		client.gui.hud.getChat().addClientSystemMessage(Component.literal("[LangLearn] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
	}
}

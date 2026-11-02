package dev.langlearn;

import dev.langlearn.net.FailPayload;
import dev.langlearn.net.PickupPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side of the mod. It only does two things:
 * 1. tell the player's client when they picked something up (see ItemEntityMixin)
 * 2. delete items when the client reports a failed pronunciation.
 * Everything else (the HUD, the microphone, the languages) lives on the client.
 */
public class LangLearn implements ModInitializer {
	public static final String MOD_ID = "langlearn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(PickupPayload.TYPE, PickupPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FailPayload.TYPE, FailPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(FailPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> removeItems(player, payload.stack(), payload.count()));
		});

		InventoryTracker.register();
	}

	/** Called by the mixin after a player picked up {@code count} items of {@code stack} from the ground. */
	public static void onPickup(ServerPlayer player, ItemStack stack, int count) {
		InventoryTracker.onGroundPickup(player, stack, count);
		sendPrompt(player, stack, count, true);
	}

	/** Tells the client to quiz the player. {@code ground} = picked up from the ground (shorter timer). */
	public static void sendPrompt(ServerPlayer player, ItemStack stack, int count, boolean ground) {
		if (count <= 0 || stack.isEmpty() || player.isSpectator()) return;
		if (!ServerPlayNetworking.canSend(player, PickupPayload.TYPE)) return; // client doesn't have the mod
		ItemStack one = stack.copy();
		one.setCount(1);
		ServerPlayNetworking.send(player, new PickupPayload(one, count, ground));
	}

	/** Removes up to {@code count} items matching {@code template}: exact matches first, then same item type. */
	private static void removeItems(ServerPlayer player, ItemStack template, int count) {
		if (count <= 0 || template.isEmpty()) return;
		int left = count;
		left = removeMatching(player, template, left, true);
		if (left > 0) left = removeMatching(player, template, left, false);
		int removed = count - left;
		if (removed > 0) {
			player.getInventory().setChanged();
			player.containerMenu.broadcastChanges();
		}
	}

	private static int removeMatching(ServerPlayer player, ItemStack template, int left, boolean exact) {
		Inventory inv = player.getInventory();
		// the stack held on the mouse cursor (if an inventory screen is open) counts too
		ItemStack carried = player.containerMenu.getCarried();
		if (left > 0 && matches(carried, template, exact)) {
			int take = Math.min(left, carried.getCount());
			carried.shrink(take);
			left -= take;
		}
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!matches(s, template, exact)) continue;
			int take = Math.min(left, s.getCount());
			s.shrink(take);
			if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
			left -= take;
		}
		return left;
	}

	private static boolean matches(ItemStack s, ItemStack template, boolean exact) {
		if (s.isEmpty()) return false;
		return exact ? ItemStack.isSameItemSameComponents(s, template) : s.getItem() == template.getItem();
	}
}

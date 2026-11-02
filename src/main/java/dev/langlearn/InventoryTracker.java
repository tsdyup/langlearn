package dev.langlearn;

import dev.langlearn.net.PickupPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catches items that reach your inventory any other way than picking them up from the ground:
 * chests, crafting, furnaces, villager trades, /give, buckets...
 *
 * Every tick it counts what the player owns (inventory + item on the cursor + 2x2 crafting grid)
 * and compares to the previous count. While a chest/crafting table/etc. is open the count is
 * frozen, and it's compared when you close it. That way, moving an item into an anvil or crafting grid
 * and back doesn't trigger anything, but whatever you really took out of the chest or crafted does.
 */
public final class InventoryTracker {
	private static final class State {
		Map<Item, Integer> baseline;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static int cleanupTimer = 0;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Set<UUID> online = new HashSet<>();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				online.add(p.getUUID());
				tick(p);
			}
			if (++cleanupTimer >= 200) {
				cleanupTimer = 0;
				STATES.keySet().retainAll(online);
			}
		});
	}

	/** Ground pickups are quizzed separately, so add them to the baseline to avoid asking twice. */
	public static void onGroundPickup(ServerPlayer player, ItemStack stack, int count) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.baseline != null) s.baseline.merge(stack.getItem(), count, Integer::sum);
	}

	private static void tick(ServerPlayer player) {
		Map<Item, Integer> totals = new HashMap<>();
		Map<Item, ItemStack> samples = new HashMap<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) count(inv.getItem(i), totals, samples);
		count(player.containerMenu.getCarried(), totals, samples);
		// the 2x2 crafting grid in the inventory screen (slots 1-4); items there still belong to you
		for (int i = 1; i <= 4; i++) count(player.inventoryMenu.getSlot(i).getItem(), totals, samples);

		State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
		boolean menuOpen = player.containerMenu != player.inventoryMenu;

		if (s.baseline == null || player.isCreative() || player.isSpectator()
				|| !ServerPlayNetworking.canSend(player, PickupPayload.TYPE)) {
			s.baseline = totals;
			return;
		}
		if (menuOpen) return; // frozen until the chest / crafting table / furnace is closed

		for (Map.Entry<Item, Integer> e : totals.entrySet()) {
			int gained = e.getValue() - s.baseline.getOrDefault(e.getKey(), 0);
			if (gained > 0) LangLearn.sendPrompt(player, samples.get(e.getKey()), gained, false);
		}
		s.baseline = totals;
	}

	private static void count(ItemStack stack, Map<Item, Integer> totals, Map<Item, ItemStack> samples) {
		if (stack.isEmpty()) return;
		totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
		samples.putIfAbsent(stack.getItem(), stack);
	}
}

package dev.langlearn.net;

import dev.langlearn.LangLearn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Server -> client: "you just got {count} x {stack}". ground = picked up from the ground, otherwise chest/crafting/etc. */
public record PickupPayload(ItemStack stack, int count, boolean ground) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PickupPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LangLearn.MOD_ID, "pickup"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PickupPayload> CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, PickupPayload::stack,
			ByteBufCodecs.VAR_INT, PickupPayload::count,
			ByteBufCodecs.BOOL, PickupPayload::ground,
			PickupPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

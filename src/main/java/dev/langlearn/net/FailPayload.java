package dev.langlearn.net;

import dev.langlearn.LangLearn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client -> server: "I failed to say it, remove {count} x {stack} from my inventory". */
public record FailPayload(ItemStack stack, int count) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FailPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LangLearn.MOD_ID, "fail"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FailPayload> CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, FailPayload::stack,
			ByteBufCodecs.VAR_INT, FailPayload::count,
			FailPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

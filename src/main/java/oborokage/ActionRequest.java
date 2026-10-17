package oborokage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clients send intent only. Equipment, direction, cost and hits are resolved by the server. */
public record ActionRequest(int sequence, int action) implements CustomPacketPayload {
  public static final Type<ActionRequest> TYPE = new Type<>(Oborokage.id("action_request_v2"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ActionRequest> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeVarInt(p.sequence);
            b.writeVarInt(p.action);
          },
          b -> new ActionRequest(b.readVarInt(), b.readVarInt()));

  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}

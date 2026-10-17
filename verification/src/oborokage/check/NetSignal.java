package oborokage.check;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** QA-only request to apply a native mob hit after the real guard packet. */
public record NetSignal(int phase) implements CustomPacketPayload {
  public static final Type<NetSignal> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("oborokage_net_checks", "signal"));
  public static final StreamCodec<RegistryFriendlyByteBuf, NetSignal> CODEC =
      StreamCodec.of((b, p) -> b.writeVarInt(p.phase), b -> new NetSignal(b.readVarInt()));

  @Override
  public Type<NetSignal> type() {
    return TYPE;
  }
}

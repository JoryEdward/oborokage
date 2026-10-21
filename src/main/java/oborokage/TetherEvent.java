package oborokage;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 空弦's string: owner to target for the given ticks; a target of -1 cuts it. */
public record TetherEvent(int owner, int target, int ticks) implements CustomPacketPayload {
  public static final Type<TetherEvent> TYPE = new Type<>(Oborokage.id("tether_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TetherEvent> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeVarInt(p.owner);
            b.writeVarInt(p.target);
            b.writeVarInt(p.ticks);
          },
          b -> new TetherEvent(b.readVarInt(), b.readVarInt(), b.readVarInt()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void send(ServerPlayer owner, int target, int ticks) {
    var event = new TetherEvent(owner.getId(), target, ticks);
    var players = new java.util.HashSet<ServerPlayer>(PlayerLookup.tracking(owner));
    players.add(owner);
    for (var p : players)
      if (p.connection != null && ServerPlayNetworking.canSend(p, TYPE))
        ServerPlayNetworking.send(p, event);
  }
}

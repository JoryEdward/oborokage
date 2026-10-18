package oborokage;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record TechniqueEvent(int actor, int style, Vec3 origin, Vec3 end, float charge)
    implements CustomPacketPayload {
  /**
   * Styles 0-2 are blade techniques and 3 is terrain cutting; they also replay the actor's pose.
   * The rest are accents drawn in place: 4 a frost ring, 5 the tachi's shadow cut on a target, 6 a
   * lightning arc between two points, 7 a void sword-qi line.
   */
  public static final int RING = 4, ECHO = 5, ARC = 6, EDGE = 7, LAST = EDGE;

  public static final Type<TechniqueEvent> TYPE = new Type<>(Oborokage.id("technique_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueEvent> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeVarInt(p.actor);
            b.writeVarInt(p.style);
            b.writeDouble(p.origin.x);
            b.writeDouble(p.origin.y);
            b.writeDouble(p.origin.z);
            b.writeDouble(p.end.x);
            b.writeDouble(p.end.y);
            b.writeDouble(p.end.z);
            b.writeFloat(p.charge);
          },
          b ->
              new TechniqueEvent(
                  b.readVarInt(),
                  b.readVarInt(),
                  new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                  new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                  b.readFloat()));

  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public static void send(ServerPlayer player, int style, Vec3 origin, Vec3 end, float charge) {
    var event = new TechniqueEvent(player.getId(), style, origin, end, charge);
    var players = new java.util.HashSet<ServerPlayer>(PlayerLookup.tracking(player));
    players.add(player);
    for (var p : players)
      if (p.connection != null && ServerPlayNetworking.canSend(p, TYPE))
        ServerPlayNetworking.send(p, event);
  }
}

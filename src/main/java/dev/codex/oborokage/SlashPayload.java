package dev.codex.oborokage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
public record SlashPayload(int owner,Vec3 origin,float yaw,float charge,int kind) implements CustomPacketPayload {
 public static final Type<SlashPayload> TYPE=new Type<>(Oborokage.id("slash"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SlashPayload> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.owner);b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);b.writeFloat(p.yaw);b.writeFloat(p.charge);b.writeVarInt(p.kind);},b->new SlashPayload(b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readVarInt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

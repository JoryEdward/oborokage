package dev.codex.oborokage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
public record ActionEvent(int actor,long actionId,long startTick,long serverTick,int move,int kind,Vec3 origin,float yaw,int intent,int stamina) implements CustomPacketPayload {
 public static final int START=0,CANCEL=1,STATUS=2,IMPACT=3,PARRY=4;
 public static final Type<ActionEvent> TYPE=new Type<>(Oborokage.id("action_event_v1"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ActionEvent> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.actor);b.writeVarLong(p.actionId);b.writeLong(p.startTick);b.writeLong(p.serverTick);b.writeVarInt(p.move);b.writeVarInt(p.kind);b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);b.writeFloat(p.yaw);b.writeVarInt(p.intent);b.writeVarInt(p.stamina);},b->new ActionEvent(b.readVarInt(),b.readVarLong(),b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readVarInt()));
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

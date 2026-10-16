package dev.codex.oborokage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
/** Compatibility facade: the original iaido remains in its unchanged executor. */
public final class Combat {
 public static final int FULL_WINDUP=LegacyIaido.FULL_WINDUP;
 public static float damage(float q){return LegacyIaido.damage(q);}
 public static boolean active(java.util.UUID id){return LegacyIaido.active(id);}
 public static void clear(){LegacyIaido.clear();DualCombat.clear();Feats.clear();}
 public static void markDamaged(java.util.UUID id){LegacyIaido.markDamaged(id);}
 public static boolean start(ServerPlayer p,float q){DualCombat.cancel(p);return LegacyIaido.start(p,q);}
 public static void tick(MinecraftServer s){LegacyIaido.tick(s);DualCombat.tick(s);}
 public static void strikeTarget(ServerLevel l,ServerPlayer p,LivingEntity e,float q){LegacyIaido.strikeTarget(l,p,e,q);}
}

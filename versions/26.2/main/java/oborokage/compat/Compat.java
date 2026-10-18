package oborokage.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;

/** Minecraft 26.2 bindings for APIs whose shape differs across the 26.x line. */
public final class Compat {
  public static final String TARGET = "26.2";

  public static void swing(LivingEntity e, InteractionHand hand) {
    e.swing(hand, true);
  }

  /** Clears the vanilla post-hit invulnerability so a scripted hit window always lands. */
  public static void resetHurtCooldown(LivingEntity e) {
    e.invulnerableTime = 0;
  }

  public static int hurtCooldown(LivingEntity e) {
    return e.invulnerableTime;
  }

  public static boolean deflect(Projectile projectile, Entity by, double speed) {
    return projectile.deflect(ProjectileDeflection.AIM_DEFLECT, by, EntityReference.of(by), true);
  }

  public static void knockback(
      LivingEntity target, double strength, double x, double z, DamageSource source, float damage) {
    target.knockback(strength, x, z, source, damage);
  }

  /** A lightning flash and thunder with no fire and no damage. */
  public static LightningBolt visualLightning(
      net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
    LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
    bolt.setVisualOnly(true);
    bolt.setPos(x, y, z);
    return bolt;
  }

  /** Flags a server-side velocity change so it is sent to clients this tick. */
  public static void syncMotion(Entity e) {
    e.hurtMarked = true;
  }

  private Compat() {}
}

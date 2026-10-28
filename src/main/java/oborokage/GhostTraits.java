package oborokage;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What makes the ghost of a swordsman (the 樱下鬼, the 朽英剑主): struck, it may fade from sight and,
 * {@link #FADE} ticks later, come back behind its attacker with a cut; a fire it carries into the
 * shade soon dies down. The ghost's other traits (no fall damage, no drowning, no freezing) are
 * overrides and tags on the entity itself. One set per ghost.
 */
final class GhostTraits {
  /** How long a fade lasts, in ticks. */
  static final int FADE = 14;

  private final Swordsman ghost;
  private final IntSupplier fadeRecovery;
  private final Supplier<SoundEvent> voice;
  private int fadeCooldown, fadeTicks;
  private LivingEntity fadeFrom;

  /**
   * `fadeRecovery`: ticks before it can fade again (asked each time); `voice`: its fading sound.
   */
  GhostTraits(Swordsman ghost, IntSupplier fadeRecovery, Supplier<SoundEvent> voice) {
    this.ghost = ghost;
    this.fadeRecovery = fadeRecovery;
    this.voice = voice;
  }

  /** After a blow: one time in four (when ready) it fades from its attacker. */
  void afterHurt(ServerLevel level, DamageSource source, boolean hurt) {
    if (hurt
        && ghost.isAlive()
        && fadeCooldown <= 0
        && fadeTicks <= 0
        && source.getEntity() instanceof LivingEntity attacker
        && ghost.getRandom().nextFloat() < .25f) fade(level, attacker);
  }

  /** Fades from sight; {@link #FADE} ticks later it comes back behind the attacker. */
  void fade(ServerLevel level, LivingEntity attacker) {
    fadeCooldown = fadeRecovery.getAsInt();
    fadeTicks = FADE;
    fadeFrom = attacker;
    ghost.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FADE + 2, 0, false, false));
    level.sendParticles(
        ParticleTypes.SOUL, ghost.getX(), ghost.getY() + 1, ghost.getZ(), 18, .3, .5, .3, .02);
    level.playSound(null, ghost.blockPosition(), voice.get(), SoundSource.HOSTILE, 1, .7f);
  }

  private void reappear(ServerLevel level) {
    LivingEntity from = fadeFrom;
    fadeFrom = null;
    ghost.removeEffect(MobEffects.INVISIBILITY);
    if (from != null && from.isAlive() && from.level() == level) {
      Vec3 behind = Vec3.directionFromRotation(0, from.getYRot()).scale(-1.6);
      Vec3 at = from.position().add(behind.x, 0, behind.z);
      AABB box = ghost.getBoundingBox().move(at.subtract(ghost.position()));
      if (level.noCollision(ghost, box)) ghost.teleportTo(at.x, at.y, at.z);
      ghost.getLookControl().setLookAt(from, 180, 180);
      if (ghost.isWithinMeleeAttackRange(from)) {
        oborokage.compat.Compat.swing(ghost, net.minecraft.world.InteractionHand.MAIN_HAND);
        ghost.doHurtTarget(level, from);
      }
    }
    level.sendParticles(
        ParticleTypes.SOUL, ghost.getX(), ghost.getY() + 1, ghost.getZ(), 18, .3, .5, .3, .02);
  }

  /** One server tick: the fade's cooldown and its return. */
  void tick(ServerLevel level) {
    if (fadeCooldown > 0) fadeCooldown--;
    if (fadeTicks > 0 && --fadeTicks == 0) reappear(level);
  }

  /** In the shade a fire it carried in soon dies down instead of burning it away. */
  void coolInShade(ServerLevel level) {
    if (ghost.getRemainingFireTicks() > 20
        && !ghost.isInLava()
        && !level.canSeeSky(ghost.blockPosition().above())) ghost.setRemainingFireTicks(20);
  }
}

package oborokage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 樱下鬼 (Oni Beneath the Blossoms): what is left of a swordsman buried under the cherry trees, a
 * half-seen thing in torn cloth with no legs and one arm, still gripping the broken blade it died
 * with. It walks at night and around blade tombs and burns in daylight (entity type tags {@code
 * minecraft:undead} and {@code minecraft:burn_in_daylight}), but it keeps to the shade where it can
 * and a fire it carries into the shade soon goes out. It parries now and then and no longer steps
 * back. Struck, it may fade from sight and come back behind its attacker with a cut; from a few
 * blocks off it lunges in with a wail. It drifts, so falls do not hurt it, and it does not breathe,
 * so water does not drown it. Petals and soul motes trail from it. It is an enemy of the living: it
 * hunts swordsmen and their master, and they hunt it; struck, it calls the 樱下鬼 around it. It rises
 * only on the open ground of the cherry groves, never in the caves beneath them. It drops bones, 剑魄
 * and, one time in ten, an intent crystal; never the blade it carries.
 */
public class BlossomOni extends Swordsman {
  private int fadeCooldown, fadeTicks, lungeCooldown = 60;
  private LivingEntity fadeFrom;

  /**
   * Natural spawns: the cherry groves' dark open ground only. A spawn below the ground's surface (a
   * cave under the grove) is refused; under the trees' leaves is still open ground.
   */
  public static boolean checkSpawn(
      EntityType<? extends Monster> type,
      ServerLevelAccessor level,
      EntitySpawnReason reason,
      BlockPos pos,
      RandomSource random) {
    if (reason == EntitySpawnReason.NATURAL
        && pos.getY()
            < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()))
      return false;
    return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
  }

  public BlossomOni(EntityType<? extends BlossomOni> type, Level level) {
    super(type, level);
    xpReward = 8;
  }

  public static AttributeSupplier.Builder createAttributes() {
    // A broken blade carries no attack of its own, so the base is the whole blow.
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 30)
        .add(Attributes.MOVEMENT_SPEED, .27)
        .add(Attributes.ATTACK_DAMAGE, 7)
        .add(Attributes.ARMOR, 2)
        .add(Attributes.FOLLOW_RANGE, 24)
        .add(Attributes.KNOCKBACK_RESISTANCE, .2);
  }

  @Override
  protected void registerGoals() {
    super.registerGoals();
    // By day it paths through the shade and, once caught alight, runs for cover.
    goalSelector.addGoal(1, new RestrictSunGoal(this));
    goalSelector.addGoal(1, new FleeSunGoal(this, 1.2));
  }

  /** The broken blade it died with: one of the four long blades' relics. */
  @Override
  protected ItemStack defaultBlade(RandomSource random) {
    BladeStyle[] long_ = {
      BladeStyle.TACHI, BladeStyle.RAIKIRI, BladeStyle.FROSTMOON, BladeStyle.VOIDSTRING
    };
    Item relic = Content.RELICS.get(long_[random.nextInt(long_.length)]);
    return new ItemStack(relic);
  }

  @Override
  protected int homeRadius() {
    return 12;
  }

  @Override
  protected float parryChance() {
    return .15f;
  }

  @Override
  protected float stepChance() {
    return 0;
  }

  @Override
  protected boolean strafes() {
    return false;
  }

  @Override
  protected void afterCut(ServerLevel level, LivingEntity target) {}

  @Override
  public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
    return false;
  }

  @Override
  public boolean canBreatheUnderwater() {
    return true;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    boolean hurt = super.hurtServer(level, source, amount);
    if (hurt
        && isAlive()
        && fadeCooldown <= 0
        && fadeTicks <= 0
        && source.getEntity() instanceof LivingEntity attacker
        && getRandom().nextFloat() < .25f) fade(level, attacker);
    return hurt;
  }

  /** Fades from sight; {@link #FADE} ticks later it comes back behind the attacker. */
  static final int FADE = 14;

  void fade(ServerLevel level, LivingEntity attacker) {
    fadeCooldown = 120;
    fadeTicks = FADE;
    fadeFrom = attacker;
    addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FADE + 2, 0, false, false));
    level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 18, .3, .5, .3, .02);
    level.playSound(null, blockPosition(), getAmbientSound(), SoundSource.HOSTILE, 1, .7f);
  }

  private void reappear(ServerLevel level) {
    LivingEntity from = fadeFrom;
    fadeFrom = null;
    removeEffect(MobEffects.INVISIBILITY);
    if (from != null && from.isAlive() && from.level() == level) {
      Vec3 behind = Vec3.directionFromRotation(0, from.getYRot()).scale(-1.6);
      Vec3 at = from.position().add(behind.x, 0, behind.z);
      AABB box = getBoundingBox().move(at.subtract(position()));
      if (level.noCollision(this, box)) teleportTo(at.x, at.y, at.z);
      getLookControl().setLookAt(from, 180, 180);
      if (isWithinMeleeAttackRange(from)) {
        oborokage.compat.Compat.swing(this, net.minecraft.world.InteractionHand.MAIN_HAND);
        doHurtTarget(level, from);
      }
    }
    level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 18, .3, .5, .3, .02);
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (level().isClientSide()) {
      // Petals and soul motes trail from it.
      if (getRandom().nextInt(6) == 0 && !isInvisible())
        level()
            .addParticle(
                getRandom().nextInt(3) == 0 ? ParticleTypes.SOUL : ParticleTypes.CHERRY_LEAVES,
                getRandomX(.6),
                getY() + .6 + getRandom().nextDouble() * 1.2,
                getRandomZ(.6),
                0,
                -.02,
                0);
      return;
    }
    if (fadeCooldown > 0) fadeCooldown--;
    if (fadeTicks > 0 && --fadeTicks == 0 && level() instanceof ServerLevel level) reappear(level);
    // From a few blocks off it lunges in with a wail.
    if (lungeCooldown > 0) lungeCooldown--;
    LivingEntity t = getTarget();
    if (lungeCooldown == 0 && t != null && t.isAlive() && tickCount % 5 == 0) {
      double d = distanceToSqr(t);
      if (d > 3 * 3 && d < 7 * 7 && getSensing().hasLineOfSight(t)) {
        lungeCooldown = 100 + getRandom().nextInt(60);
        Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize().scale(.95);
        setDeltaMovement(dir.x, .15, dir.z);
        oborokage.compat.Compat.syncMotion(this);
        level()
            .playSound(null, blockPosition(), getAmbientSound(), SoundSource.HOSTILE, 1.2f, 1.4f);
      }
    }
    // In the shade a fire it carried in soon dies down instead of burning it away.
    if (level() instanceof ServerLevel level
        && getRemainingFireTicks() > 20
        && !isInLava()
        && !level.canSeeSky(blockPosition().above())) setRemainingFireTicks(20);
  }

  @Override
  protected SoundEvent getAmbientSound() {
    return Content.BLOSSOM_ONI_AMBIENT;
  }

  @Override
  protected SoundEvent getHurtSound(DamageSource source) {
    return Content.BLOSSOM_ONI_HURT;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return Content.BLOSSOM_ONI_DEATH;
  }
}

package oborokage;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * 剑客: a wandering swordsman of the cherry groves who fights the way the mod teaches. It carries a
 * plain kazuuchi tachi and duels ({@link DuelGoal}): it circles between cuts, rushes a player who
 * stands charging a draw, edges round a shield and backs off when badly hurt. It parries a frontal
 * blow now and then (more often when hurt) and answers with a counter-cut, often steps back after
 * cutting or being cut, and calls the swordsmen around it when attacked. With nothing to fight it
 * practises on a training post on its ground. An ultimate cannot be parried. Its undead (樱下鬼) and
 * its master (落英剑主) build on it.
 *
 * <p>A mob's held weapon adds its own attack bonus to the base attack, so the base stays low: with
 * the kazuuchi's +6 a swordsman hits for 8, about what an armed zombie does.
 */
public class Swordsman extends Monster {
  private int parryCooldown, stepCooldown, counterTicks, rallyCooldown;
  private LivingEntity counterTarget;

  public Swordsman(EntityType<? extends Swordsman> type, Level level) {
    super(type, level);
    xpReward = 12;
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 40)
        .add(Attributes.MOVEMENT_SPEED, .3)
        .add(Attributes.ATTACK_DAMAGE, 2)
        .add(Attributes.ARMOR, 4)
        .add(Attributes.FOLLOW_RANGE, 24)
        .add(Attributes.KNOCKBACK_RESISTANCE, .3);
  }

  /** Chance to parry a frontal blow when the parry is ready. */
  protected float parryChance() {
    return .3f;
  }

  protected int parryRecovery() {
    return 50;
  }

  /** How much more often it parries when badly hurt (below three tenths of its health). */
  protected float hurtParryBonus() {
    return .15f;
  }

  /** Chance to step back after taking a hit. */
  protected float stepChance() {
    return .25f;
  }

  /** The blade it carries: a plain kazuuchi. */
  protected ItemStack defaultBlade(RandomSource random) {
    return new ItemStack(Oborokage.KAZUUCHI);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
    goalSelector.addGoal(2, new DuelGoal(this, 1.15));
    // Like a guardian, each keeps to its ground: a chase may carry it out, then it walks back.
    goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1));
    if (!(this instanceof BlossomOni)) goalSelector.addGoal(5, new PracticeGoal(this));
    goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .8));
    goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
    goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    targetSelector.addGoal(1, new HurtByTargetGoal(this));
    targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    // The living and the dead are enemies: swordsmen (and their master) hunt the 樱下鬼, which
    // hunts them back. (Registered from the constructor: instanceof is safe, fields are not.)
    if (this instanceof BlossomOni)
      targetSelector.addGoal(
          3,
          new NearestAttackableTargetGoal<>(
              this, Swordsman.class, true, (e, level) -> !(e instanceof BlossomOni)));
    else targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, BlossomOni.class, true));
  }

  /** Whether it is gathering an art and should stand still (the master's windups). */
  protected boolean busy() {
    return false;
  }

  /** Whether it circles its foe between cuts (the living do; the 樱下鬼 drifts straight in). */
  protected boolean strafes() {
    return true;
  }

  /** After a cut of its own: the living often step back out of reach again. */
  protected void afterCut(ServerLevel level, LivingEntity target) {
    if (stepCooldown <= 0 && getRandom().nextFloat() < .3f) step(level, target);
  }

  /** The dead and the living each stand with their own. */
  private boolean sameSide(Entity e) {
    return e instanceof Swordsman s && (s instanceof BlossomOni) == (this instanceof BlossomOni);
  }

  /** Attacked: the swordsmen of its side nearby with nothing to fight turn on the attacker. */
  private void rally(ServerLevel level, LivingEntity attacker) {
    if (rallyCooldown > 0
        || sameSide(attacker)
        || attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
    rallyCooldown = 40;
    for (Swordsman s :
        level.getEntitiesOfClass(Swordsman.class, getBoundingBox().inflate(16, 6, 16)))
      if (s != this && sameSide(s) && s.getTarget() == null && s.isAlive()) s.setTarget(attacker);
  }

  /**
   * How far from where it first stood it wanders: a dojo's swordsmen keep to the yard, wanderers of
   * the grove to a small stretch of it.
   */
  protected int homeRadius() {
    return isPersistenceRequired() ? 6 : 12;
  }

  @Override
  protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    setItemSlot(EquipmentSlot.MAINHAND, defaultBlade(random));
    // The blade it carries is never loot.
    setDropChance(EquipmentSlot.MAINHAND, 0);
  }

  @Override
  public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level,
      DifficultyInstance difficulty,
      EntitySpawnReason reason,
      SpawnGroupData data) {
    data = super.finalizeSpawn(level, difficulty, reason, data);
    populateDefaultEquipmentSlots(level.getRandom(), difficulty);
    return data;
  }

  /** Whether the attacker stands in front, where the swordsman's blade can meet the blow. */
  private boolean facing(Entity attacker) {
    Vec3 to = attacker.position().subtract(position()).multiply(1, 0, 1);
    if (to.lengthSqr() < 1e-4) return true;
    double r = Math.toRadians(getYRot());
    Vec3 look = new Vec3(-Math.sin(r), 0, Math.cos(r));
    return look.dot(to.normalize()) > .3;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    float parry = parryChance() + (getHealth() < getMaxHealth() * .3f ? hurtParryBonus() : 0);
    if (parryCooldown <= 0
        && parryChance() > 0
        && !isDeadOrDying()
        && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
        && source.getEntity() instanceof LivingEntity attacker
        && source.getDirectEntity() == attacker
        && !(attacker instanceof net.minecraft.server.level.ServerPlayer sp
            && DualCombat.inUltimate(sp))
        && !(attacker instanceof PetalLord lord && lord.ultimateBlow())
        && !LegacyIaido.fullDrawBlow()
        && facing(attacker)
        && getRandom().nextFloat() < parry) {
      parry(level, attacker);
      return false;
    }
    boolean hurt = super.hurtServer(level, source, amount);
    if (hurt && isAlive() && source.getEntity() instanceof LivingEntity attacker)
      rally(level, attacker);
    if (hurt
        && isAlive()
        && stepCooldown <= 0
        && source.getEntity() instanceof LivingEntity attacker
        && getRandom().nextFloat() < stepChance()) step(level, attacker);
    return hurt;
  }

  private void parry(ServerLevel level, LivingEntity attacker) {
    parryCooldown = parryRecovery();
    counterTarget = attacker;
    counterTicks = 6;
    oborokage.compat.Compat.swing(this, InteractionHand.MAIN_HAND);
    level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, .45f, 1.8f);
    Vec3 eye = getEyePosition();
    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y - .3, eye.z, 12, .2, .2, .2, .1);
    Vec3 push = attacker.position().subtract(position()).multiply(1, 0, 1);
    if (push.lengthSqr() > 1e-4) {
      push = push.normalize().scale(.45);
      attacker.push(push.x, .1, push.z);
      oborokage.compat.Compat.syncMotion(attacker);
    }
  }

  protected void step(ServerLevel level, LivingEntity attacker) {
    stepCooldown = 60;
    Vec3 away = position().subtract(attacker.position()).multiply(1, 0, 1);
    if (away.lengthSqr() < 1e-4) return;
    away = away.normalize().scale(.85);
    setDeltaMovement(away.x, .3, away.z);
    oborokage.compat.Compat.syncMotion(this);
    level.playSound(
        null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, .6f, 1.4f);
    level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + .2, getZ(), 6, .2, .05, .2, .02);
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (!(level() instanceof ServerLevel level)) return;
    // Placed by a structure or a command without its spawn setup: arm it now.
    if (getMainHandItem().isEmpty() && tickCount < 5) {
      setItemSlot(EquipmentSlot.MAINHAND, defaultBlade(getRandom()));
      setDropChance(EquipmentSlot.MAINHAND, 0);
    }
    // Its ground is where it first stood (saved with it, so a reload keeps the same ground).
    if (!hasHome()) setHomeTo(blockPosition(), homeRadius());
    if (parryCooldown > 0) parryCooldown--;
    if (stepCooldown > 0) stepCooldown--;
    if (rallyCooldown > 0) rallyCooldown--;
    if (counterTicks > 0 && --counterTicks == 0) {
      LivingEntity target = counterTarget;
      counterTarget = null;
      if (target != null && target.isAlive() && distanceToSqr(target) < 3.5 * 3.5) {
        oborokage.compat.Compat.swing(this, InteractionHand.MAIN_HAND);
        level.playSound(
            null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, .8f, 1.2f);
        doHurtTarget(level, target);
      }
    }
  }
}

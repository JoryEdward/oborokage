package oborokage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 朽英剑主 (Lord of Withered Blossoms): the 落英剑主 after death, keeping the hall of a ruined dojo. It
 * fights with the master's arts ({@link LordArts}: the sweep, the flash-step, its blade's ultimate)
 * and the ghost's ({@link GhostTraits}: struck, it may fade and come back behind its attacker;
 * falls, drowning and frost do not touch it). The sun does not burn it.
 *
 * <p>By day, with nothing to fight, it sits in meditation: still, heeding nothing until it is
 * struck, and the blow that wakes it cannot be parried. By night it is whole: it fades sooner
 * (every 100 ticks at most instead of 200) and calls up the dead (「百鬼」): gathering for {@link
 * #SUMMON_WINDUP} ticks in a ring of soul fire, it raises three 樱下鬼 about it, one more for each
 * other player near, five at most, never past {@link #SUMMON_CAP} within {@link #SUMMON_RANGE}
 * blocks. Those it calls up drop nothing and fade with it.
 *
 * <p>Health 260, softer than the living master (armour 6, toughness 2); its cut is the same 6 plus
 * the blade's. A player who fells it finds its blade's relic {@link #RELIC_CHANCE} of the time.
 */
public class WitheredLord extends PetalLord {
  public static final float RELIC_CHANCE = .15f;

  /** 「百鬼」: windup and cooldown in ticks, the most 樱下鬼 around it, and how far that is counted. */
  public static final int SUMMON_WINDUP = 30,
      SUMMON_COOLDOWN = 600,
      SUMMON_CAP = 8,
      SUMMON_RANGE = 24;

  private static final EntityDataAccessor<Boolean> MEDITATING =
      SynchedEntityData.defineId(WitheredLord.class, EntityDataSerializers.BOOLEAN);

  private final GhostTraits ghost =
      new GhostTraits(this, () -> night() ? 100 : 200, () -> Content.WITHERED_LORD_AMBIENT);
  int summonCooldown = 200, summonWindup;

  public WitheredLord(EntityType<? extends WitheredLord> type, Level level) {
    super(type, level);
    xpReward = 80;
  }

  public static AttributeSupplier.Builder createAttributes() {
    // With its blade's bonus (12 to 15) it cuts for 18 to 21.
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 260)
        .add(Attributes.MOVEMENT_SPEED, .3)
        .add(Attributes.ATTACK_DAMAGE, 6)
        .add(Attributes.ARMOR, 6)
        .add(Attributes.ARMOR_TOUGHNESS, 2)
        .add(Attributes.FOLLOW_RANGE, 32)
        .add(Attributes.KNOCKBACK_RESISTANCE, .6);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(MEDITATING, false);
  }

  @Override
  public boolean ofTheDead() {
    return true;
  }

  /** Night: it is whole. */
  public boolean night() {
    return level().isDarkOutside();
  }

  /** Whether it sits in meditation (by day, with nothing to fight). */
  public boolean meditating() {
    return entityData.get(MEDITATING);
  }

  /** In meditation it does not move, look about or seek a foe: only a blow wakes it. */
  @Override
  protected boolean isImmobile() {
    return super.isImmobile() || meditating();
  }

  @Override
  public void setTarget(LivingEntity target) {
    if (target != null && meditating()) return;
    super.setTarget(target);
  }

  @Override
  protected float parryChance() {
    return meditating() ? 0 : super.parryChance();
  }

  @Override
  protected float relicChance() {
    return RELIC_CHANCE;
  }

  @Override
  protected String sayings() {
    return "withered_says";
  }

  @Override
  protected boolean busy() {
    return super.busy() || summonWindup > 0;
  }

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
    boolean wasMeditating = meditating();
    boolean hurt = super.hurtServer(level, source, amount);
    if (wasMeditating && isAlive() && source.getEntity() instanceof LivingEntity attacker) {
      // Struck in meditation: it rises to the one who struck it.
      entityData.set(MEDITATING, false);
      setTarget(attacker);
    }
    ghost.afterHurt(level, source, hurt);
    return hurt;
  }

  @Override
  protected void drift() {
    // Ash and soul motes, not petals: its blossoms are long gone.
    if (getRandom().nextInt(4) == 0 && !isInvisible())
      level()
          .addParticle(
              getRandom().nextInt(3) == 0 ? ParticleTypes.SOUL : ParticleTypes.WHITE_ASH,
              getRandomX(1.2),
              getY() + 1.2 + getRandom().nextDouble() * 1.2,
              getRandomZ(1.2),
              0,
              -.02,
              0);
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (!(level() instanceof ServerLevel level) || !isAlive()) return;
    ghost.tick(level);
    ghost.coolInShade(level);
    // By day, ten seconds after its last fight, it sits down; night or a blow brings it up.
    boolean sit =
        !night()
            && getTarget() == null
            && level.getGameTime() - lastFight() > 200
            && onGround()
            && summonWindup == 0;
    if (sit != meditating()) {
      entityData.set(MEDITATING, sit);
      if (sit) getNavigation().stop();
    }
  }

  // ----- 「百鬼」

  @Override
  protected boolean ownArt(ServerLevel level) {
    if (summonCooldown > 0) summonCooldown--;
    if (summonWindup > 0) {
      getNavigation().stop();
      gather(level, --summonWindup);
      if (summonWindup == 0) summon(level);
      return true;
    }
    LivingEntity target = getTarget();
    if (summonCooldown > 0
        || !night()
        || target == null
        || !target.isAlive()
        || arts().busy()
        || tickCount % 4 != 0
        || distanceToSqr(target) > 16 * 16
        || dead(level) >= SUMMON_CAP) return false;
    summonWindup = SUMMON_WINDUP;
    summonCooldown = SUMMON_COOLDOWN;
    var name =
        getType()
            .getDescription()
            .copy()
            .append(" · ")
            .append(Component.translatable("ui.oborokage.withered_art.summon"));
    for (ServerPlayer p : level.players())
      if (distanceToSqr(p) < 32 * 32) p.sendOverlayMessage(name);
    level.playSound(
        null, blockPosition(), Content.WITHERED_LORD_AMBIENT, SoundSource.HOSTILE, 1.6f, .6f);
    return true;
  }

  /** The 樱下鬼 around it. */
  private int dead(ServerLevel level) {
    return level
        .getEntitiesOfClass(
            BlossomOni.class, getBoundingBox().inflate(SUMMON_RANGE), BlossomOni::isAlive)
        .size();
  }

  /** The warning while it gathers: soul fire closing in on it along the ground. */
  private void gather(ServerLevel level, int left) {
    double r = 1.4 + left * .1;
    for (int i = 0; i < 12; i++) {
      double a = Math.PI * 2 * i / 12 - left * .2;
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME,
          getX() + Math.cos(a) * r,
          getY() + .1,
          getZ() + Math.sin(a) * r,
          1,
          0,
          .02,
          0,
          0);
    }
  }

  /** Raises the dead about it: three, one more for each other player near, five at most. */
  private void summon(ServerLevel level) {
    int players = 0;
    for (ServerPlayer p : level.players())
      if (p.isAlive() && !p.isSpectator() && !p.isCreative() && distanceToSqr(p) < 32 * 32)
        players++;
    int n = Math.min(Math.min(5, 3 + Math.max(0, players - 1)), SUMMON_CAP - dead(level));
    for (int i = 0; i < n; i++) {
      BlossomOni oni = Content.BLOSSOM_ONI.create(level, EntitySpawnReason.MOB_SUMMONED);
      if (oni == null) break;
      if (!place(level, oni, Math.PI * 2 * i / n + getRandom().nextDouble() * .6)) continue;
      oni.finalizeSpawn(
          level,
          level.getCurrentDifficultyAt(oni.blockPosition()),
          EntitySpawnReason.MOB_SUMMONED,
          null);
      oni.summonedBy(this);
      level.addFreshEntity(oni);
      if (getTarget() != null) oni.setTarget(getTarget());
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME,
          oni.getX(),
          oni.getY() + .6,
          oni.getZ(),
          14,
          .3,
          .6,
          .3,
          .02);
    }
    level.playSound(
        null, blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2, .5f);
  }

  /** Stands `oni` on clear ground 2 to 3.5 blocks off at `angle`; false if there is none. */
  private boolean place(ServerLevel level, BlossomOni oni, double angle) {
    for (double r = 3.5; r >= 2; r -= .5)
      for (int dy = 1; dy >= -3; dy--) {
        double x = getX() + Math.cos(angle) * r,
            y = Math.floor(getY()) + dy,
            z = getZ() + Math.sin(angle) * r;
        oni.snapTo(x, y, z, getYRot(), 0);
        BlockPos below = BlockPos.containing(x, y - 1, z);
        if (level.noCollision(oni)
            && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
      }
    return false;
  }

  @Override
  public void die(DamageSource source) {
    boolean byPlayer = getKillCredit() instanceof ServerPlayer;
    super.die(source);
    // 「残英」: for everyone who stood in the fight with it, not only the last blow.
    if (byPlayer && level() instanceof ServerLevel level)
      for (ServerPlayer p : level.players())
        if (p.isAlive() && !p.isSpectator() && distanceToSqr(p) < 32 * 32)
          Feats.award(p, "withered_rest");
    // Those it called up fade with it.
    if (level() instanceof ServerLevel level)
      for (BlossomOni oni :
          level.getEntitiesOfClass(
              BlossomOni.class, getBoundingBox().inflate(64), o -> getUUID().equals(o.summoner())))
        oni.dismiss(level);
  }

  @Override
  protected SoundEvent getAmbientSound() {
    return Content.WITHERED_LORD_AMBIENT;
  }

  @Override
  protected SoundEvent getHurtSound(DamageSource source) {
    return Content.WITHERED_LORD_HURT;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return Content.WITHERED_LORD_DEATH;
  }
}

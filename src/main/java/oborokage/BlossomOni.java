package oborokage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.Vec3;

/**
 * 樱下鬼 (Oni Beneath the Blossoms): what is left of a swordsman buried under the cherry trees, a
 * half-seen thing in torn cloth with no legs and one arm, still gripping the broken blade it died
 * with. It walks at night and around blade tombs and burns in daylight (entity type tags {@code
 * minecraft:undead} and {@code minecraft:burn_in_daylight}), but it keeps to the shade where it can
 * and a fire it carries into the shade soon goes out. It parries now and then and no longer steps
 * back. Struck, it may fade from sight and come back behind its attacker with a cut ({@link
 * GhostTraits}); from a few blocks off it lunges in with a wail. It drifts, so falls do not hurt
 * it, and it does not breathe, so water does not drown it. Petals and soul motes trail from it. It
 * is an enemy of the living: it hunts swordsmen and their master, and they hunt it; struck, it
 * calls the 樱下鬼 around it. It rises only on the open ground of the cherry groves, never in the
 * caves beneath them. It drops bones, 剑魄 and, one time in ten, an intent crystal; never the blade
 * it carries. One called up by the 朽英剑主 (「百鬼」) drops nothing and fades with its master. On a
 * full-moon night they walk the groves in a column behind a 鬼将 ({@link NightParade}).
 */
public class BlossomOni extends Swordsman {
  private final GhostTraits ghost =
      new GhostTraits(this, () -> 120, () -> Content.BLOSSOM_ONI_AMBIENT);
  private int lungeCooldown = 60;

  /** The 朽英剑主 that called it up (「百鬼」), or null: such a one drops nothing (saved). */
  private java.util.UUID summoner;

  /** A 鬼将: the elite that leads a 百鬼夜行 (synced, for its soul fire; saved). */
  private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ELITE =
      net.minecraft.network.syncher.SynchedEntityData.defineId(
          BlossomOni.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

  /** Its place in a 百鬼夜行, if any. */
  private final ParadeRole parade = new ParadeRole(this);

  /** The ruined dojo, where the dead gather at night. */
  private static final net.minecraft.tags.TagKey<
          net.minecraft.world.level.levelgen.structure.Structure>
      RUIN =
          net.minecraft.tags.TagKey.create(
              net.minecraft.core.registries.Registries.STRUCTURE, Oborokage.id("ruined_dojo"));

  /**
   * Natural spawns: the cherry groves' dark open ground only. A spawn below the ground's surface (a
   * cave under the grove) is refused; under the trees' leaves is still open ground. In a ruined
   * dojo by night they rise anywhere, under its roof and in its soul-lantern light too; never by
   * day.
   */
  public static boolean checkSpawn(
      EntityType<? extends Monster> type,
      ServerLevelAccessor level,
      EntitySpawnReason reason,
      BlockPos pos,
      RandomSource random) {
    if (reason == EntitySpawnReason.NATURAL && inRuinAtNight(level, pos))
      return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
    if (reason == EntitySpawnReason.NATURAL
        && pos.getY()
            < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()))
      return false;
    return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
  }

  /** Whether `pos` is in a ruined dojo and it is night. */
  public static boolean inRuinAtNight(ServerLevelAccessor level, BlockPos pos) {
    var server = level.getLevel();
    return server.isDarkOutside()
        && server.structureManager().getStructureWithPieceAt(pos, RUIN).isValid();
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
    goalSelector.addGoal(3, new ParadeRole.MarchGoal(this));
  }

  @Override
  protected void defineSynchedData(
      net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(ELITE, false);
  }

  /** Its place in a 百鬼夜行 (null only while it is being built). */
  public ParadeRole parade() {
    return parade;
  }

  /** Whether it is a 鬼将. */
  public boolean elite() {
    return entityData.get(ELITE);
  }

  /** Makes it a 鬼将: larger (×1.35), 90 health, 4 more attack, ringed in soul fire, named. */
  public void makeElite() {
    entityData.set(ELITE, true);
    getAttribute(Attributes.SCALE).setBaseValue(1.35);
    getAttribute(Attributes.MAX_HEALTH).setBaseValue(90);
    getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(11);
    setHealth(getMaxHealth());
  }

  @Override
  protected net.minecraft.network.chat.Component getTypeName() {
    return elite()
        ? net.minecraft.network.chat.Component.translatable("entity.oborokage.blossom_oni.elite")
        : super.getTypeName();
  }

  @Override
  public boolean shouldShowName() {
    return elite() || super.shouldShowName();
  }

  /** Marching in a parade it takes no foe; the parade turns to fight together (ParadeRole). */
  @Override
  public void setTarget(LivingEntity target) {
    if (target != null && parade != null && parade.marching()) return;
    super.setTarget(target);
  }

  @Override
  protected boolean keepsGround() {
    return parade == null || !parade.member();
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
  public boolean ofTheDead() {
    return true;
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

  /** Called up by `lord`: it drops nothing, gives no experience and fades when the lord is gone. */
  public void summonedBy(LivingEntity lord) {
    summoner = lord.getUUID();
  }

  /** The master that called it up, or null if it rose by itself. */
  public java.util.UUID summoner() {
    return summoner;
  }

  /** Fades away for good, in a breath of soul fire. */
  public void dismiss(ServerLevel level) {
    level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 14, .3, .5, .3, .03);
    level.playSound(
        null, blockPosition(), Content.BLOSSOM_ONI_DEATH, SoundSource.HOSTILE, .6f, 1.3f);
    discard();
  }

  @Override
  protected boolean shouldDropLoot(ServerLevel level) {
    return summoner == null && super.shouldDropLoot(level);
  }

  @Override
  protected int getBaseExperienceReward(ServerLevel level) {
    if (summoner != null) return 0;
    return elite() ? 30 : super.getBaseExperienceReward(level);
  }

  /** A 鬼将 leaves 2-3 intent crystals and 3-5 剑魄 besides. */
  @Override
  protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    super.dropCustomDeathLoot(level, source, recentlyHit);
    if (!elite() || summoner != null) return;
    spawnAtLocation(level, new ItemStack(Content.INTENT_CRYSTAL, 2 + getRandom().nextInt(2)));
    spawnAtLocation(level, new ItemStack(Content.BLADE_SOUL, 3 + getRandom().nextInt(3)));
  }

  @Override
  public void die(DamageSource source) {
    boolean byPlayer = getKillCredit() instanceof net.minecraft.server.level.ServerPlayer;
    super.die(source);
    // 「百鬼夜行」: for everyone who stood in the fight with the 鬼将.
    if (elite() && byPlayer && level() instanceof ServerLevel level)
      for (var p : level.players())
        if (p.isAlive() && !p.isSpectator() && distanceToSqr(p) < 32 * 32)
          Feats.award(p, "night_parade");
  }

  @Override
  protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    super.addAdditionalSaveData(out);
    out.storeNullable("Summoner", net.minecraft.core.UUIDUtil.CODEC, summoner);
    out.putBoolean("Elite", elite());
    parade.save(out);
  }

  @Override
  protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    super.readAdditionalSaveData(in);
    summoner = in.read("Summoner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    entityData.set(ELITE, in.getBooleanOr("Elite", false));
    parade.load(in);
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
    boolean hurt = super.hurtServer(level, source, amount);
    ghost.afterHurt(level, source, hurt);
    // A blow at any of a marching parade turns all of it on the attacker.
    if (isAlive()
        && parade.marching()
        && source.getEntity() instanceof LivingEntity attacker
        && !(attacker instanceof Swordsman s && s.ofTheDead())) parade.provoke(level, attacker);
    return hurt;
  }

  /** Fades from its attacker now (the ghost's own rule decides when, after a blow). */
  void fade(ServerLevel level, LivingEntity attacker) {
    ghost.fade(level, attacker);
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (level().isClientSide()) {
      // A 鬼将 is ringed in soul fire.
      if (elite() && !isInvisible())
        for (int i = 0; i < 2; i++) {
          double a = tickCount * .25 + i * Math.PI;
          level()
              .addParticle(
                  ParticleTypes.SOUL_FIRE_FLAME,
                  getX() + Math.cos(a) * .9,
                  getY() + .3 + (tickCount % 20) * .08,
                  getZ() + Math.sin(a) * .9,
                  0,
                  .01,
                  0);
        }
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
    if (level() instanceof ServerLevel level) {
      ghost.tick(level);
      // Called up by a master: when the master is gone (felled, or far away), it goes too.
      if (summoner != null && tickCount % 40 == 0) {
        var lord = level.getEntity(summoner);
        if (!(lord instanceof LivingEntity l) || !l.isAlive() || distanceToSqr(l) > 64 * 64) {
          dismiss(level);
          return;
        }
      }
      parade.tick(level);
      if (isRemoved()) return;
    }
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
    if (level() instanceof ServerLevel level) ghost.coolInShade(level);
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

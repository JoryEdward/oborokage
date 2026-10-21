package oborokage;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 落英剑主 (Lord of Fallen Petals): the master of a cherry-grove dojo. It carries one of the four long
 * named blades (胧影, 赤霆, 霜轮, 空弦, equally likely) and fights with that blade's arts:
 *
 * <ul>
 *   <li>its cuts sweep, catching foes beside the one it strikes;
 *   <li>every few seconds it gathers itself and flash-steps to its target, cutting every foe along
 *       the way with the blade's nature;
 *   <li>every twenty seconds or so (sooner below half health), when it faces several foes or a
 *       strong one, it calls its blade's ultimate after a plain warning: 胧影 双月归寂 (two crescent
 *       sweeps all around), 赤霆 万雷天降 (real lightning on every foe in reach), 霜轮 镜月冰河 (an ice ring
 *       that freezes, then shatters), 空弦 断界 (a line cut through walls that drags foes back).
 * </ul>
 *
 * Its foes are its target, players, the 樱下鬼 and other undead, and anything attacking it; never its
 * own swordsmen. It shows a boss bar to players nearby, never despawns, and no single blow takes
 * more than {@link #MAX_BLOW} from it. Only the 落英剑主 drops relics: the broken form of its own
 * blade, 7.9% of the time when a player fells it.
 */
public class PetalLord extends Swordsman {
  public static final float RELIC_CHANCE = .079f, MAX_BLOW = 60;
  private static final int WINDUP = 12, ULT_WINDUP = 24, BAR_RANGE = 40;

  private final ServerBossEvent bar =
      new ServerBossEvent(
          UUID.randomUUID(),
          Component.translatable("entity.oborokage.petal_lord"),
          BossEvent.BossBarColor.PURPLE,
          BossEvent.BossBarOverlay.NOTCHED_10);
  private int artCooldown = 60, windup, ultCooldown = 200, ultWindup, echo;
  private boolean enraged;

  /** Game time it last fought (had a target or was struck), and last spoke a challenge. */
  private long lastFight, lastChallenge = -10000;

  public PetalLord(EntityType<? extends PetalLord> type, Level level) {
    super(type, level);
    xpReward = 60;
    setPersistenceRequired();
  }

  public static AttributeSupplier.Builder createAttributes() {
    // With its blade's bonus (12 to 15) it cuts for 17 to 20.
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 200)
        .add(Attributes.MOVEMENT_SPEED, .32)
        .add(Attributes.ATTACK_DAMAGE, 5)
        .add(Attributes.ARMOR, 10)
        .add(Attributes.ARMOR_TOUGHNESS, 4)
        .add(Attributes.FOLLOW_RANGE, 32)
        .add(Attributes.KNOCKBACK_RESISTANCE, .8);
  }

  @Override
  protected float parryChance() {
    return .4f;
  }

  @Override
  protected int parryRecovery() {
    return 40;
  }

  @Override
  protected float stepChance() {
    return .3f;
  }

  @Override
  protected ItemStack defaultBlade(RandomSource random) {
    Item[] blades = {Oborokage.TACHI, Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING};
    return new ItemStack(blades[random.nextInt(blades.length)]);
  }

  /** The style of the blade it carries, or null if it was disarmed by a command. */
  public BladeStyle style() {
    return BladeStyle.of(getMainHandItem());
  }

  @Override
  protected boolean busy() {
    return windup > 0 || ultWindup > 0;
  }

  /** The master keeps to its hall. */
  @Override
  protected int homeRadius() {
    return 4;
  }

  @Override
  public boolean removeWhenFarAway(double distance) {
    return false;
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return super.hurtServer(level, source, Math.min(amount, MAX_BLOW));
  }

  @Override
  public void setCustomName(Component name) {
    super.setCustomName(name);
    bar.setName(getDisplayName());
  }

  @Override
  public void stopSeenByPlayer(ServerPlayer player) {
    super.stopSeenByPlayer(player);
    bar.removePlayer(player);
  }

  // ----- foes and blows

  /** Whom its arts strike: never itself or its own swordsmen; players, the dead, its attackers. */
  boolean foe(LivingEntity e) {
    if (e == this || !e.isAlive() || e.isSpectator()) return false;
    if (e instanceof Swordsman s) return s instanceof BlossomOni;
    if (e instanceof Player p) return !p.getAbilities().instabuild;
    return e == getTarget()
        || e == getLastHurtByMob()
        || e instanceof Mob m && m.getTarget() == this
        || e instanceof Monster && e.is(EntityTypeTags.UNDEAD);
  }

  List<LivingEntity> foes(ServerLevel level, AABB area) {
    return level.getEntitiesOfClass(LivingEntity.class, area, this::foe);
  }

  /** Its cut's weight: the attack attribute, which includes the blade in hand. */
  float attack() {
    return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
  }

  private void strike(ServerLevel level, LivingEntity e, float damage) {
    oborokage.compat.Compat.resetHurtCooldown(e);
    e.hurtServer(level, level.damageSources().mobAttack(this), damage);
  }

  /** Its cuts sweep: foes beside the one struck take half the blow. */
  @Override
  public boolean doHurtTarget(ServerLevel level, Entity target) {
    boolean hit = super.doHurtTarget(level, target);
    if (hit) {
      Vec3 look = Vec3.directionFromRotation(0, getYRot());
      for (LivingEntity e : foes(level, getBoundingBox().inflate(2.5, .5, 2.5)))
        if (e != target && e.position().subtract(position()).dot(look) > 0)
          strike(level, e, attack() * .5f);
      level.sendParticles(
          ParticleTypes.SWEEP_ATTACK, getX() + look.x, getY(1), getZ() + look.z, 1, 0, 0, 0, 0);
    }
    return hit;
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (level().isClientSide()) {
      // Petals drift about it.
      if (getRandom().nextInt(5) == 0)
        level()
            .addParticle(
                ParticleTypes.CHERRY_LEAVES,
                getRandomX(1.2),
                getY() + 1.6 + getRandom().nextDouble() * .8,
                getRandomZ(1.2),
                0,
                -.03,
                0);
      return;
    }
    if (!(level() instanceof ServerLevel level)) return;
    long now = level.getGameTime();
    if (getTarget() != null || hurtTime > 0) lastFight = now;
    // Left alone for ten seconds it collects itself: a point of health a second.
    if (now - lastFight > 200 && getHealth() < getMaxHealth() && tickCount % 20 == 0) {
      heal(1);
      if (getHealth() >= getMaxHealth() / 2) enraged = false;
    }
    bar.setProgress(getHealth() / getMaxHealth());
    if (tickCount % 10 == 0) {
      for (ServerPlayer p : level.players())
        if (isAlive() && p.isAlive() && distanceToSqr(p) < BAR_RANGE * BAR_RANGE) bar.addPlayer(p);
        else bar.removePlayer(p);
    }
    if (!isAlive()) return;
    if (!enraged && getHealth() < getMaxHealth() / 2) {
      enraged = true;
      speak(level, "enraged");
      level.playSound(
          null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1, 1.3f);
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 30, .4, .6, .4, .04);
    }
    if (echo > 0 && --echo == 0) crescent(level, 2.0f);
    LivingEntity target = getTarget();
    if (ultWindup > 0) {
      getNavigation().stop();
      gather(level, --ultWindup);
      if (ultWindup == 0) ultimate(level, target);
      return;
    }
    if (windup > 0) {
      windup--;
      level.sendParticles(
          ParticleTypes.ENCHANTED_HIT, getX(), getY() + .9, getZ(), 4, .35, .5, .35, .05);
      if (windup == 0 && target != null && target.isAlive()) draw(level, target);
      return;
    }
    if (ultCooldown > 0) ultCooldown--;
    if (artCooldown > 0) artCooldown--;
    // Its arts are weighed five times a second, not every tick (each look is a ray cast).
    if (target == null || !target.isAlive() || tickCount % 4 != 0) return;
    double d = distanceToSqr(target);
    if (ultCooldown == 0 && d < 16 * 16 && getSensing().hasLineOfSight(target)) {
      int near = foes(level, getBoundingBox().inflate(10)).size();
      if (near >= 2 || target.getMaxHealth() >= 100 || enraged) {
        ultWindup = ULT_WINDUP;
        ultCooldown = enraged ? 260 : 400;
        announce(level);
        return;
      }
    }
    if (artCooldown == 0 && d > 3 * 3 && d < 12 * 12 && getSensing().hasLineOfSight(target)) {
      windup = WINDUP;
      artCooldown = enraged ? 80 : 140;
      getNavigation().stop();
      level.playSound(
          null, blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1, .6f);
    }
  }

  // ----- its voice and its students

  /** A line to the players around it, in chat: the challenge, the turn at half health, its end. */
  private void speak(ServerLevel level, String line) {
    var said =
        Component.translatable("entity.oborokage.petal_lord")
            .append(": ")
            .append(Component.translatable("ui.oborokage.lord_says." + line))
            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
    for (ServerPlayer p : level.players())
      if (distanceToSqr(p) < 32 * 32) p.sendSystemMessage(said);
  }

  /** On a new foe: its students turn on it too, and a player is challenged (once in a while). */
  @Override
  public void setTarget(LivingEntity target) {
    LivingEntity before = getTarget();
    super.setTarget(target);
    if (target == null || target == before || !(level() instanceof ServerLevel level)) return;
    for (Swordsman s :
        level.getEntitiesOfClass(Swordsman.class, getBoundingBox().inflate(20, 6, 20)))
      if (s != this && !(s instanceof BlossomOni) && s.getTarget() == null && s.isAlive())
        s.setTarget(target);
    long now = level.getGameTime();
    if (target instanceof Player && now - lastChallenge > 1200) {
      lastChallenge = now;
      speak(level, "challenge");
    }
  }

  @Override
  public void die(DamageSource source) {
    if (level() instanceof ServerLevel level && !isRemoved()) speak(level, "fall");
    super.die(source);
  }

  // ----- the flash-step

  /** To the target's side in one step if the way is clear; every foe along the way is cut. */
  private void draw(ServerLevel level, LivingEntity target) {
    Vec3 from = position();
    Vec3 flat = target.position().subtract(from).multiply(1, 0, 1);
    if (flat.lengthSqr() < 1e-4) return;
    Vec3 dir = flat.normalize();
    Vec3 dest =
        target.position().subtract(dir.scale(target.getBbWidth() * .5 + getBbWidth() * .5 + .4));
    AABB box = getBoundingBox().move(dest.subtract(from));
    if (level.noCollision(this, box)) teleportTo(dest.x, dest.y, dest.z);
    face(dir);
    for (int i = 0; i <= 12; i++) {
      Vec3 at = from.lerp(position(), i / 12.0).add(0, 1, 0);
      level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
    level.playSound(
        null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3f, .7f);
    oborokage.compat.Compat.swing(this, InteractionHand.MAIN_HAND);
    AABB path = new AABB(from, position()).inflate(1.4, 1, 1.4);
    for (LivingEntity e : foes(level, path.expandTowards(dir.scale(1.5)))) {
      strike(level, e, attack() * 1.5f);
      nature(level, e);
    }
  }

  private void face(Vec3 dir) {
    float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    setYRot(yaw);
    setYHeadRot(yaw);
    setYBodyRot(yaw);
  }

  /** What a cut carries, by the blade in hand. */
  private void nature(ServerLevel level, LivingEntity target) {
    BladeStyle style = style();
    if (style == null) return;
    switch (style) {
      case RAIKIRI -> {
        level.addFreshEntity(
            oborokage.compat.Compat.visualLightning(
                level, target.getX(), target.getY(), target.getZ()));
        strike(level, target, 4);
      }
      case FROSTMOON -> {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), this);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
      }
      case VOIDSTRING -> {
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0), this);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
      }
      default -> {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this);
      }
    }
  }

  // ----- the ultimate

  /** The ultimate's name for the players around, so the warning can be read. */
  private void announce(ServerLevel level) {
    BladeStyle style = style();
    if (style == null) {
      ultWindup = 0;
      return;
    }
    var name =
        Component.translatable("entity.oborokage.petal_lord")
            .append(" · ")
            .append(Component.translatable("ui.oborokage.lord_art." + style.key));
    for (ServerPlayer p : level.players())
      if (distanceToSqr(p) < 32 * 32) p.sendOverlayMessage(name);
    level.playSound(
        null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, 1.6f);
  }

  /** The warning while it gathers: a ring closing in, in the blade's colour. */
  private void gather(ServerLevel level, int left) {
    BladeStyle style = style();
    double r = 1.2 + left * .12;
    for (int i = 0; i < 10; i++) {
      double a = Math.PI * 2 * i / 10 + left * .3;
      double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
      var particle =
          style == BladeStyle.RAIKIRI
              ? ParticleTypes.ELECTRIC_SPARK
              : style == BladeStyle.FROSTMOON
                  ? ParticleTypes.SNOWFLAKE
                  : style == BladeStyle.VOIDSTRING
                      ? ParticleTypes.REVERSE_PORTAL
                      : ParticleTypes.END_ROD;
      level.sendParticles(particle, x, getY() + .2, z, 1, 0, .05, 0, 0);
    }
  }

  private void ultimate(ServerLevel level, LivingEntity target) {
    BladeStyle style = style();
    if (style == null) return;
    oborokage.compat.Compat.swing(this, InteractionHand.MAIN_HAND);
    switch (style) {
      case RAIKIRI -> {
        // 万雷天降: real lightning on every foe within sixteen blocks, eight at most.
        int bolts = 0;
        for (LivingEntity e : foes(level, getBoundingBox().inflate(16, 6, 16))) {
          if (bolts++ >= 8) break;
          var bolt = oborokage.compat.Compat.visualLightning(level, e.getX(), e.getY(), e.getZ());
          level.addFreshEntity(bolt);
          strike(level, e, attack() * 1.6f);
          if (e.isAlive()) e.thunderHit(level, bolt);
        }
        level.playSound(
            null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3, .9f);
      }
      case FROSTMOON -> {
        // 镜月冰河: everything within nine blocks is frozen where it stands, then the ice shatters.
        var ice =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        for (LivingEntity e : foes(level, getBoundingBox().inflate(9, 3, 9))) {
          e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6), this);
          e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
          strike(level, e, attack() * 2.2f);
          level.sendParticles(ice, e.getX(), e.getY() + 1, e.getZ(), 30, .4, .6, .4, .1);
        }
        for (int i = 0; i < 48; i++) {
          double a = Math.PI * 2 * i / 48;
          level.sendParticles(
              ParticleTypes.SNOWFLAKE,
              getX() + Math.cos(a) * 8,
              getY() + .2,
              getZ() + Math.sin(a) * 8,
              2,
              .1,
              .1,
              .1,
              .01);
        }
        level.playSound(
            null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2, .5f);
      }
      case VOIDSTRING -> {
        // 断界: a line cut thirty-two blocks long through walls toward the target; what it cuts is
        // dragged back toward the master.
        Vec3 dir =
            target == null
                ? Vec3.directionFromRotation(0, getYRot())
                : target.getEyePosition().subtract(getEyePosition()).normalize();
        face(new Vec3(dir.x, 0, dir.z).normalize());
        Vec3 from = getEyePosition(), to = from.add(dir.scale(32));
        for (int i = 0; i <= 64; i++) {
          Vec3 at = from.lerp(to, i / 64.0);
          level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 2, .05, .05, .05, 0);
        }
        level.sendParticles(
            ParticleTypes.SONIC_BOOM,
            from.x + dir.x * 2,
            from.y,
            from.z + dir.z * 2,
            1,
            0,
            0,
            0,
            0);
        for (LivingEntity e : foes(level, new AABB(from, to).inflate(1.5))) {
          Vec3 c = e.getBoundingBox().getCenter();
          double along = c.subtract(from).dot(dir);
          if (along < 0 || along > 32 || c.distanceTo(from.add(dir.scale(along))) > 1.8) continue;
          strike(level, e, attack() * 2.4f);
          Vec3 back = position().subtract(e.position()).multiply(1, 0, 1);
          if (back.lengthSqr() > 1) {
            back = back.normalize().scale(1.1);
            e.push(back.x, .25, back.z);
            oborokage.compat.Compat.syncMotion(e);
          }
        }
        level.playSound(
            null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 1.4f);
      }
      default -> {
        // 双月归寂: a crescent sweep all around, and a second, heavier one a moment later.
        crescent(level, 1.5f);
        echo = 8;
      }
    }
  }

  /** 胧影's crescent: everything within six blocks, all around. */
  private void crescent(ServerLevel level, float weight) {
    if (!isAlive()) return;
    for (LivingEntity e : foes(level, getBoundingBox().inflate(6, 2, 6)))
      if (e.distanceToSqr(this) < 6.5 * 6.5) {
        strike(level, e, attack() * weight);
        nature(level, e);
      }
    for (int i = 0; i < 24; i++) {
      double a = Math.PI * 2 * i / 24;
      level.sendParticles(
          ParticleTypes.SWEEP_ATTACK,
          getX() + Math.cos(a) * 3.5,
          getY() + 1,
          getZ() + Math.sin(a) * 3.5,
          1,
          0,
          0,
          0,
          0);
      level.sendParticles(
          ParticleTypes.END_ROD,
          getX() + Math.cos(a) * 5.5,
          getY() + 1.2,
          getZ() + Math.sin(a) * 5.5,
          1,
          0,
          .1,
          0,
          .01);
    }
    level.playSound(
        null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6f, .55f);
  }

  @Override
  protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    super.dropCustomDeathLoot(level, source, recentlyHit);
    BladeStyle style = style();
    if (recentlyHit && style != null && getRandom().nextFloat() < RELIC_CHANCE) {
      Item relic = Content.RELICS.get(style);
      if (relic != null) spawnAtLocation(level, new ItemStack(relic));
    }
  }
}

package oborokage;

import java.util.List;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A sword master's arts, one set per master (落英剑主, and the 朽英剑主 after it): its cuts sweep; every
 * few seconds it gathers itself and flash-steps to its target, cutting every foe on the way with
 * its blade's nature; now and then, facing several foes or a strong one (or once below half
 * health), it gathers for its blade's ultimate after a plain warning. Its foes are its target,
 * players, the other side (the 樱下鬼 and other undead for the living master, the living for the dead
 * one) and anything attacking it; never its own side. The cooldowns and windups live here.
 */
public final class LordArts {
  static final int WINDUP = 12, ULT_WINDUP = 24, FINAL_WINDUP = 20, FINAL_COOLDOWN = 300;
  private final PetalLord lord;
  int artCooldown = 60, windup, ultCooldown = 200, ultWindup, echo;

  /** Its final form (below half health): cooldown, windup, and ticks it still goes on for. */
  int finalCooldown, finalWindup, finalLeft;

  private LivingEntity finalTarget;
  private Vec3 finalCenter;
  private boolean ultimateBlow;

  LordArts(PetalLord lord) {
    this.lord = lord;
  }

  /** Gathering an art: the master stands still for it. */
  boolean busy() {
    return windup > 0 || ultWindup > 0 || finalWindup > 0;
  }

  /** Whether the blow now landing is part of an ultimate, which no blade can parry. */
  boolean ultimateBlow() {
    return ultimateBlow;
  }

  // ----- foes and blows

  /** Whom its arts strike: never itself or its own swordsmen; players, the dead, its attackers. */
  boolean foe(LivingEntity e) {
    if (e == lord || !e.isAlive() || e.isSpectator()) return false;
    if (e instanceof Swordsman s) return s.ofTheDead() != lord.ofTheDead();
    if (e instanceof Player p) return !p.getAbilities().instabuild;
    // The living master's foes include every undead monster; the dead one's do not.
    return e == lord.getTarget()
        || e == lord.getLastHurtByMob()
        || e instanceof Mob m && m.getTarget() == lord
        || !lord.ofTheDead() && e instanceof Monster && e.is(EntityTypeTags.UNDEAD);
  }

  List<LivingEntity> foes(ServerLevel level, AABB area) {
    return level.getEntitiesOfClass(LivingEntity.class, area, this::foe);
  }

  /** Its cut's weight: the attack attribute, which includes the blade in hand. */
  float attack() {
    return (float) lord.getAttributeValue(Attributes.ATTACK_DAMAGE);
  }

  private void strike(ServerLevel level, LivingEntity e, float damage) {
    oborokage.compat.Compat.resetHurtCooldown(e);
    e.hurtServer(level, level.damageSources().mobAttack(lord), damage);
  }

  private void ultimateStrike(ServerLevel level, LivingEntity e, float damage) {
    ultimateBlow = true;
    try {
      strike(level, e, damage);
    } finally {
      ultimateBlow = false;
    }
  }

  /** After a cut that landed: foes beside the one struck take half the blow. */
  void sweep(ServerLevel level, Entity target) {
    Vec3 look = Vec3.directionFromRotation(0, lord.getYRot());
    for (LivingEntity e : foes(level, lord.getBoundingBox().inflate(2.5, .5, 2.5)))
      if (e != target && e.position().subtract(lord.position()).dot(look) > 0)
        strike(level, e, attack() * .5f);
    level.sendParticles(
        ParticleTypes.SWEEP_ATTACK,
        lord.getX() + look.x,
        lord.getY(1),
        lord.getZ() + look.z,
        1,
        0,
        0,
        0,
        0);
  }

  /** One server tick of its arts; `enraged` (below half health) quickens them. */
  void tick(ServerLevel level, boolean enraged) {
    if (echo > 0 && --echo == 0) crescent(level, 2.0f);
    LivingEntity target = lord.getTarget();
    if (ultWindup > 0) {
      lord.getNavigation().stop();
      gather(level, --ultWindup);
      if (ultWindup == 0) ultimate(level, target);
      return;
    }
    if (finalWindup > 0) {
      lord.getNavigation().stop();
      gatherFinal(level, --finalWindup);
      if (finalWindup == 0) startFinal(level, target);
      return;
    }
    if (finalLeft > 0) goOnFinal(level, --finalLeft);
    if (windup > 0) {
      windup--;
      level.sendParticles(
          ParticleTypes.ENCHANTED_HIT,
          lord.getX(),
          lord.getY() + .9,
          lord.getZ(),
          4,
          .35,
          .5,
          .35,
          .05);
      if (windup == 0 && target != null && target.isAlive()) draw(level, target);
      return;
    }
    if (ultCooldown > 0) ultCooldown--;
    if (artCooldown > 0) artCooldown--;
    if (finalCooldown > 0) finalCooldown--;
    // Its arts are weighed five times a second, not every tick (each look is a ray cast).
    if (target == null || !target.isAlive() || lord.tickCount % 4 != 0) return;
    double d = lord.distanceToSqr(target);
    // Below half health its blade's final form opens to it.
    if (enraged
        && finalCooldown == 0
        && finalLeft == 0
        && lord.style() != null
        && d < 10 * 10
        && lord.getSensing().hasLineOfSight(target)) {
      finalWindup = FINAL_WINDUP;
      finalCooldown = FINAL_COOLDOWN;
      lord.getNavigation().stop();
      name(level, "ui.oborokage.lord_final." + lord.style().key);
      return;
    }
    if (ultCooldown == 0 && d < 16 * 16 && lord.getSensing().hasLineOfSight(target)) {
      int near = foes(level, lord.getBoundingBox().inflate(10)).size();
      if (near >= 2 || target.getMaxHealth() >= 100 || enraged) {
        ultWindup = ULT_WINDUP;
        ultCooldown = enraged ? 260 : 400;
        announce(level);
        return;
      }
    }
    if (artCooldown == 0 && d > 3 * 3 && d < 12 * 12 && lord.getSensing().hasLineOfSight(target)) {
      windup = WINDUP;
      artCooldown = enraged ? 80 : 140;
      lord.getNavigation().stop();
      level.playSound(
          null,
          lord.blockPosition(),
          SoundEvents.ARMOR_EQUIP_IRON.value(),
          SoundSource.HOSTILE,
          1,
          .6f);
    }
  }

  // ----- the flash-step

  /** To the target's side in one step if the way is clear; every foe along the way is cut. */
  private void draw(ServerLevel level, LivingEntity target) {
    draw(level, target, 1.5f, false);
  }

  /** The flash-step, cutting for `weight` times its attack; `final_` ones cannot be parried. */
  private void draw(ServerLevel level, LivingEntity target, float weight, boolean final_) {
    Vec3 from = lord.position();
    Vec3 flat = target.position().subtract(from).multiply(1, 0, 1);
    if (flat.lengthSqr() < 1e-4) return;
    Vec3 dir = flat.normalize();
    Vec3 dest =
        target
            .position()
            .subtract(dir.scale(target.getBbWidth() * .5 + lord.getBbWidth() * .5 + .4));
    AABB box = lord.getBoundingBox().move(dest.subtract(from));
    if (level.noCollision(lord, box)) lord.teleportTo(dest.x, dest.y, dest.z);
    face(dir);
    for (int i = 0; i <= 12; i++) {
      Vec3 at = from.lerp(lord.position(), i / 12.0).add(0, 1, 0);
      level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
    level.playSound(
        null,
        lord.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.HOSTILE,
        1.3f,
        .7f);
    oborokage.compat.Compat.swing(lord, InteractionHand.MAIN_HAND);
    AABB path = new AABB(from, lord.position()).inflate(1.4, 1, 1.4);
    for (LivingEntity e : foes(level, path.expandTowards(dir.scale(1.5)))) {
      if (final_) ultimateStrike(level, e, attack() * weight);
      else strike(level, e, attack() * weight);
      nature(level, e);
    }
  }

  private void face(Vec3 dir) {
    float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    lord.setYRot(yaw);
    lord.setYHeadRot(yaw);
    lord.setYBodyRot(yaw);
  }

  /** What a cut carries, by the blade in hand. */
  private void nature(ServerLevel level, LivingEntity target) {
    BladeStyle style = lord.style();
    if (style == null) return;
    switch (style) {
      case RAIKIRI -> {
        level.addFreshEntity(
            oborokage.compat.Compat.visualLightning(
                level, target.getX(), target.getY(), target.getZ()));
        strike(level, target, 4);
      }
      case FROSTMOON -> {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), lord);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
      }
      case VOIDSTRING -> {
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0), lord);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), lord);
      }
      default -> {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), lord);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), lord);
      }
    }
  }

  // ----- the ultimate

  /** The ultimate's name for the players around, so the warning can be read. */
  private void announce(ServerLevel level) {
    BladeStyle style = lord.style();
    if (style == null) {
      ultWindup = 0;
      return;
    }
    name(level, "ui.oborokage.lord_art." + style.key);
  }

  /** An art's name, after the master's, for the players around. */
  private void name(ServerLevel level, String art) {
    var name =
        lord.getType().getDescription().copy().append(" · ").append(Component.translatable(art));
    for (ServerPlayer p : level.players())
      if (lord.distanceToSqr(p) < 32 * 32) p.sendOverlayMessage(name);
    level.playSound(
        null, lord.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, 1.6f);
  }

  /** The warning while it gathers: a ring closing in, in the blade's colour. */
  private void gather(ServerLevel level, int left) {
    BladeStyle style = lord.style();
    double r = 1.2 + left * .12;
    for (int i = 0; i < 10; i++) {
      double a = Math.PI * 2 * i / 10 + left * .3;
      double x = lord.getX() + Math.cos(a) * r, z = lord.getZ() + Math.sin(a) * r;
      var particle =
          style == BladeStyle.RAIKIRI
              ? ParticleTypes.ELECTRIC_SPARK
              : style == BladeStyle.FROSTMOON
                  ? ParticleTypes.SNOWFLAKE
                  : style == BladeStyle.VOIDSTRING
                      ? ParticleTypes.REVERSE_PORTAL
                      : ParticleTypes.END_ROD;
      level.sendParticles(particle, x, lord.getY() + .2, z, 1, 0, .05, 0, 0);
    }
  }

  private void ultimate(ServerLevel level, LivingEntity target) {
    BladeStyle style = lord.style();
    if (style == null) return;
    oborokage.compat.Compat.swing(lord, InteractionHand.MAIN_HAND);
    switch (style) {
      case RAIKIRI -> {
        // 万雷天降: real lightning on every foe within sixteen blocks, eight at most.
        int bolts = 0;
        for (LivingEntity e : foes(level, lord.getBoundingBox().inflate(16, 6, 16))) {
          if (bolts++ >= 8) break;
          var bolt = oborokage.compat.Compat.visualLightning(level, e.getX(), e.getY(), e.getZ());
          level.addFreshEntity(bolt);
          ultimateStrike(level, e, attack() * 1.6f);
          if (e.isAlive()) e.thunderHit(level, bolt);
        }
        level.playSound(
            null,
            lord.blockPosition(),
            SoundEvents.LIGHTNING_BOLT_THUNDER,
            SoundSource.HOSTILE,
            3,
            .9f);
      }
      case FROSTMOON -> {
        // 镜月冰河: everything within nine blocks is frozen where it stands, then the ice shatters.
        var ice =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        for (LivingEntity e : foes(level, lord.getBoundingBox().inflate(9, 3, 9))) {
          e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6), lord);
          e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
          ultimateStrike(level, e, attack() * 2.2f);
          level.sendParticles(ice, e.getX(), e.getY() + 1, e.getZ(), 30, .4, .6, .4, .1);
        }
        for (int i = 0; i < 48; i++) {
          double a = Math.PI * 2 * i / 48;
          level.sendParticles(
              ParticleTypes.SNOWFLAKE,
              lord.getX() + Math.cos(a) * 8,
              lord.getY() + .2,
              lord.getZ() + Math.sin(a) * 8,
              2,
              .1,
              .1,
              .1,
              .01);
        }
        level.playSound(
            null, lord.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2, .5f);
      }
      case VOIDSTRING -> {
        // 断界: a line cut thirty-two blocks long through walls toward the target; what it cuts is
        // dragged back toward the master.
        Vec3 dir =
            target == null
                ? Vec3.directionFromRotation(0, lord.getYRot())
                : target.getEyePosition().subtract(lord.getEyePosition()).normalize();
        face(new Vec3(dir.x, 0, dir.z).normalize());
        Vec3 from = lord.getEyePosition(), to = from.add(dir.scale(32));
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
          ultimateStrike(level, e, attack() * 2.4f);
          Vec3 back = lord.position().subtract(e.position()).multiply(1, 0, 1);
          if (back.lengthSqr() > 1) {
            back = back.normalize().scale(1.1);
            e.push(back.x, .25, back.z);
            oborokage.compat.Compat.syncMotion(e);
          }
        }
        level.playSound(
            null,
            lord.blockPosition(),
            SoundEvents.WARDEN_SONIC_BOOM,
            SoundSource.HOSTILE,
            1.5f,
            1.4f);
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
    if (!lord.isAlive()) return;
    for (LivingEntity e : foes(level, lord.getBoundingBox().inflate(6, 2, 6)))
      if (e.distanceToSqr(lord) < 6.5 * 6.5) {
        ultimateStrike(level, e, attack() * weight);
        nature(level, e);
      }
    for (int i = 0; i < 24; i++) {
      double a = Math.PI * 2 * i / 24;
      level.sendParticles(
          ParticleTypes.SWEEP_ATTACK,
          lord.getX() + Math.cos(a) * 3.5,
          lord.getY() + 1,
          lord.getZ() + Math.sin(a) * 3.5,
          1,
          0,
          0,
          0,
          0);
      level.sendParticles(
          ParticleTypes.END_ROD,
          lord.getX() + Math.cos(a) * 5.5,
          lord.getY() + 1.2,
          lord.getZ() + Math.sin(a) * 5.5,
          1,
          0,
          .1,
          0,
          .01);
    }
    level.playSound(
        null,
        lord.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.HOSTILE,
        1.6f,
        .55f);
  }

  // ----- the final forms (below half health)

  /** The warning while it gathers its final form: its blade's colour rising about it. */
  private void gatherFinal(ServerLevel level, int left) {
    var particle = colour();
    for (int i = 0; i < 6; i++) {
      double a = Math.PI * 2 * i / 6 + left * .4;
      level.sendParticles(
          particle,
          lord.getX() + Math.cos(a) * .9,
          lord.getY() + .2 + (FINAL_WINDUP - left) * .1,
          lord.getZ() + Math.sin(a) * .9,
          1,
          0,
          0,
          0,
          0);
    }
  }

  private net.minecraft.core.particles.SimpleParticleType colour() {
    BladeStyle style = lord.style();
    return style == BladeStyle.RAIKIRI
        ? ParticleTypes.ELECTRIC_SPARK
        : style == BladeStyle.FROSTMOON
            ? ParticleTypes.SNOWFLAKE
            : style == BladeStyle.VOIDSTRING ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD;
  }

  /**
   * The final form begins: 残月追斩 (胧影), two flash-steps, the second after the target where it has
   * gone, each 1.2 times its attack; 雷域 (赤霆), lightning for three seconds on every foe within six
   * blocks, 1.2 times a strike; 冰狱 (霜轮), an eight-block field of ice for five seconds that slows
   * and cuts every foe in it for 0.4 times a second while the master moves faster on it; 弦缚 (空弦), a
   * string drawing the target in, then a void cut for 1.8 times. None can be parried.
   */
  private void startFinal(ServerLevel level, LivingEntity target) {
    BladeStyle style = lord.style();
    if (style == null || target == null || !target.isAlive()) return;
    finalTarget = target;
    finalCenter = lord.position();
    oborokage.compat.Compat.swing(lord, InteractionHand.MAIN_HAND);
    switch (style) {
      case RAIKIRI -> finalLeft = 60;
      case FROSTMOON -> finalLeft = 100;
      case VOIDSTRING -> {
        // The string: the target is drawn to the master's front.
        Vec3 pull = lord.position().subtract(target.position());
        if (pull.lengthSqr() > 4) {
          Vec3 v = pull.normalize().scale(Math.min(2.2, pull.length() * .35)).add(0, .25, 0);
          target.setDeltaMovement(v);
          oborokage.compat.Compat.syncMotion(target);
        }
        Vec3 from = lord.getEyePosition(), to = target.getEyePosition();
        for (int i = 0; i <= 24; i++) {
          Vec3 at = from.lerp(to, i / 24.0);
          level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 2, .02, .02, .02, 0);
        }
        level.playSound(
            null, lord.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.4f, .6f);
        finalLeft = 8;
      }
      default -> {
        draw(level, target, 1.2f, true);
        finalLeft = 9;
      }
    }
  }

  /** The final form going on: `left` ticks still to go. */
  private void goOnFinal(ServerLevel level, int left) {
    BladeStyle style = lord.style();
    LivingEntity target = finalTarget;
    if (style == null) {
      finalLeft = 0;
      return;
    }
    switch (style) {
      case RAIKIRI -> {
        if (left % 5 == 0) ring(level, 6, ParticleTypes.ELECTRIC_SPARK);
        if (left % 20 == 0)
          for (LivingEntity e : foes(level, new AABB(finalCenter, finalCenter).inflate(6, 3, 6))) {
            if (e.position().distanceToSqr(finalCenter) > 6 * 6) continue;
            level.addFreshEntity(
                oborokage.compat.Compat.visualLightning(level, e.getX(), e.getY(), e.getZ()));
            ultimateStrike(level, e, attack() * 1.2f);
          }
      }
      case FROSTMOON -> {
        if (left % 5 == 0) ring(level, 8, ParticleTypes.SNOWFLAKE);
        if (lord.position().distanceToSqr(finalCenter) < 8 * 8)
          lord.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 1, false, false));
        if (left % 20 == 0)
          for (LivingEntity e : foes(level, new AABB(finalCenter, finalCenter).inflate(8, 3, 8))) {
            if (e.position().distanceToSqr(finalCenter) > 8 * 8) continue;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2), lord);
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
            ultimateStrike(level, e, attack() * .4f);
          }
      }
      case VOIDSTRING -> {
        if (left == 0 && target != null && target.isAlive() && lord.distanceToSqr(target) < 5 * 5) {
          face(target.position().subtract(lord.position()).multiply(1, 0, 1).normalize());
          oborokage.compat.Compat.swing(lord, InteractionHand.MAIN_HAND);
          ultimateStrike(level, target, attack() * 1.8f);
          level.sendParticles(
              ParticleTypes.REVERSE_PORTAL,
              target.getX(),
              target.getY() + 1,
              target.getZ(),
              40,
              .4,
              .6,
              .4,
              .3);
          level.playSound(
              null,
              lord.blockPosition(),
              SoundEvents.PLAYER_ATTACK_SWEEP,
              SoundSource.HOSTILE,
              1.5f,
              .5f);
        }
      }
      default -> {
        // The second flash-step, after the target where it has gone.
        if (left == 0 && target != null && target.isAlive() && lord.distanceToSqr(target) < 16 * 16)
          draw(level, target, 1.2f, true);
      }
    }
    if (left == 0) finalTarget = null;
  }

  /** A ring of `particle` on the ground, `radius` blocks round where the final form began. */
  private void ring(
      ServerLevel level, double radius, net.minecraft.core.particles.SimpleParticleType particle) {
    for (int i = 0; i < 24; i++) {
      double a = Math.PI * 2 * i / 24;
      level.sendParticles(
          particle,
          finalCenter.x + Math.cos(a) * radius,
          finalCenter.y + .2,
          finalCenter.z + Math.sin(a) * radius,
          1,
          0,
          .05,
          0,
          0);
    }
  }
}

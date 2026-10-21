package oborokage;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import oborokage.compat.Compat;

/**
 * Server choreography of the five blade ultimates. Generic hit windows still run in DualCombat;
 * this class adds the scripted beats (blink strikes, freezes, the realm-severing slash), sounds,
 * the closing zanshin and feats. Every hit passes TargetRules and maxTargetsPerWindow.
 */
final class Ultimates {
  static final int STORM_FIRST = 8, STORM_STEP = 5, STORM_CHAIN = 6, STORM_FINALE = 44;
  static final int FROST_FREEZE = 18;
  static final int VOID_SLASH = 28, VOID_AFTERSHOCK = 40;
  static final double VOID_LENGTH = 48, VOID_HALF_WIDTH = 2.2;

  private Ultimates() {}

  static void tick(DualCombat.State s, int age) {
    switch (s.move.id()) {
      case Move.ULTIMATE -> tachi(s, age);
      case Move.ULT_MOON -> moon(s, age);
      case Move.ULT_STORM -> storm(s, age);
      case Move.ULT_FROST -> frost(s, age);
      case Move.ULT_VOID -> voidSlash(s, age);
      default -> {}
    }
  }

  /** Extra damage factor a generic window applies to this target during an ultimate. */
  static float windowScale(DualCombat.State s, LivingEntity target) {
    if (s.move.id() == Move.ULT_FROST && s.frozen.contains(target.getUUID())) return 1.6f;
    return 1;
  }

  static void finish(DualCombat.State s) {
    var cfg = ServerConfig.get();
    if (cfg.zanshinTicks > 0 && s.p.connection != null) {
      // 残心: after the ultimate the body stays light and ready for a moment.
      // 残心 inscription: twice as long.
      int ticks = cfg.zanshinTicks * (Inscription.has(s.main, Inscription.ZANSHIN) ? 2 : 1);
      s.p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0, true, false));
    }
    BladeStyle style = BladeStyle.ofUltimate(s.move.id());
    if (style != null) Feats.awardCriterion(s.p, "all_ultimates", style.key);
    s.frozen.clear();
    s.ultTargets.clear();
  }

  /** Called after any ultimate damage: feats for a felled colossus. */
  static void afterStrike(DualCombat.State s, LivingEntity target) {
    if (!target.isAlive() && Feats.enemy(target) && target.getMaxHealth() >= 150)
      Feats.award(s.p, "shake_heavens");
  }

  // ---------------------------------------------------------------- 胧影 · 双月归寂

  private static void tachi(DualCombat.State s, int age) {
    if (age == 0) sound(s, SoundEvents.BELL_RESONATE, 1.2f, .6f);
    if (age == 9 || age == 13) sound(s, SoundEvents.AMETHYST_CLUSTER_BREAK, 1, 1.4f);
    if (age == 23) {
      sound(s, SoundEvents.BELL_BLOCK, 2, .5f);
      sound(s, SoundEvents.ENDER_DRAGON_GROWL, .35f, 1.9f);
    }
  }

  // ---------------------------------------------------------------- 胧月 · 百花缭乱

  private static void moon(DualCombat.State s, int age) {
    if (age == 0) {
      sound(s, SoundEvents.BELL_RESONATE, 1.4f, 1.2f);
      sound(s, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, .8f);
    }
    if (age >= 8 && age <= 22 && age % 2 == 0)
      sound(s, SoundEvents.PLAYER_ATTACK_SWEEP, .8f, 1.1f + (age - 8) * .04f);
    if (age == 32) {
      sound(s, SoundEvents.BELL_BLOCK, 2, 1.3f);
      sound(s, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6f, .7f);
    }
  }

  // ---------------------------------------------------------------- 雷切 · 万雷天降

  private static void storm(DualCombat.State s, int age) {
    ServerPlayer p = s.p;
    if (age == 0) {
      sound(s, SoundEvents.TRIDENT_THUNDER.value(), 1.4f, 1.2f);
      s.ultOrigin = p.position();
    }
    if (age == 4) s.ultTargets = chain(s);
    int k = (age - STORM_FIRST) / STORM_STEP;
    if (age >= STORM_FIRST && (age - STORM_FIRST) % STORM_STEP == 0 && k < STORM_CHAIN) {
      LivingEntity target = k < s.ultTargets.size() ? s.ultTargets.get(k) : null;
      if (target != null && (!target.isAlive() || target.level() != p.level())) target = null;
      if (target != null) blinkTo(s, target);
      else if (k < 3) blinkForward(s, 3.2);
      else return;
      Vec3 at = target != null ? target.position() : p.position().add(s.direction.scale(1.5));
      var bolt = Compat.visualLightning((ServerLevel) p.level(), at.x, at.y, at.z);
      p.level().addFreshEntity(bolt);
      for (LivingEntity e : near(s, at, target == null ? 2.5 : 1.6)) {
        strike(s, e, s.move.damage(), false);
        thunder(s, e, bolt);
        BladeArts.thunderChain(p, s.main, e, scaled(s, s.move.damage()) * .4f, s.struck);
      }
      sound(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, .9f + k * .08f);
    }
    if (age == STORM_FINALE) {
      Vec3 c = p.position();
      ServerLevel level = (ServerLevel) p.level();
      for (int i = 0; i < 4; i++) {
        double a = i * Math.PI / 2 + .4;
        level.addFreshEntity(
            Compat.visualLightning(level, c.x + Math.cos(a) * 2.6, c.y, c.z + Math.sin(a) * 2.6));
      }
      var bolt = Compat.visualLightning(level, c.x, c.y, c.z);
      level.addFreshEntity(bolt);
      for (LivingEntity e : near(s, c, 6.5)) {
        strike(s, e, s.move.damage() * 1.3f, true);
        thunder(s, e, bolt);
        e.push(0, .55, 0);
        Compat.syncMotion(e);
      }
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + .5, c.z, 1, 0, 0, 0, 0);
      sound(s, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.4f, .7f);
      sound(s, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, .8f);
    }
  }

  /** Greedy nearest-neighbour path through up to six visible enemies within sixteen blocks. */
  private static List<LivingEntity> chain(DualCombat.State s) {
    ServerPlayer p = s.p;
    List<LivingEntity> pool =
        new ArrayList<>(
            p.level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    p.getBoundingBox().inflate(s.move.reach()),
                    e -> Feats.enemy(e) && TargetRules.allowed(p, e) && visible(p, e)));
    List<LivingEntity> out = new ArrayList<>();
    Vec3 from = p.position();
    while (!pool.isEmpty() && out.size() < STORM_CHAIN) {
      Vec3 f = from;
      LivingEntity next =
          Collections.min(pool, Comparator.comparingDouble(e -> e.position().distanceToSqr(f)));
      if (next.position().distanceToSqr(p.position()) > s.move.reach() * s.move.reach()) break;
      pool.remove(next);
      out.add(next);
      from = next.position();
    }
    return out;
  }

  private static void blinkTo(DualCombat.State s, LivingEntity target) {
    ServerPlayer p = s.p;
    Vec3 to = target.position();
    Vec3 flat = new Vec3(to.x - p.getX(), 0, to.z - p.getZ());
    if (flat.lengthSqr() < 1e-4) return;
    Vec3 dir = flat.normalize();
    Vec3 dest = to.subtract(dir.scale(target.getBbWidth() * .5 + .9));
    s.direction = dir;
    s.yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    travel(s, new Vec3(dest.x - p.getX(), dest.y - p.getY(), dest.z - p.getZ()));
  }

  private static void blinkForward(DualCombat.State s, double distance) {
    travel(s, s.direction.scale(distance));
  }

  /** Collision-respecting relocation; a wall stops the blink rather than being passed. */
  private static void travel(DualCombat.State s, Vec3 delta) {
    ServerPlayer p = s.p;
    p.setDeltaMovement(Vec3.ZERO);
    p.move(MoverType.SELF, delta);
    p.resetFallDistance();
    p.setYRot(s.yaw);
    if (p.connection != null)
      p.connection.teleport(p.getX(), p.getY(), p.getZ(), s.yaw, p.getXRot());
  }

  // ---------------------------------------------------------------- 霜天 · 镜月冰河

  private static void frost(DualCombat.State s, int age) {
    if (age == 0) {
      sound(s, SoundEvents.BEACON_ACTIVATE, 1.3f, 1.6f);
      sound(s, SoundEvents.POWDER_SNOW_BREAK, 1.2f, .6f);
    }
    if (age == FROST_FREEZE) {
      ServerPlayer p = s.p;
      for (LivingEntity e : near(s, p.position(), s.move.reach())) {
        if (!visible(p, e)) continue;
        s.frozen.add(e.getUUID());
        // Held still in the ice until the shatter at tick 38.
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 26, 6), p);
        e.setDeltaMovement(Vec3.ZERO);
        if (e.canFreeze())
          e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 80));
        BladeArts.frostHit(p, e);
        DualCombat.send(s, ActionEvent.IMPACT, e.position());
      }
      sound(s, SoundEvents.GLASS_BREAK, 1.6f, .55f);
      sound(s, SoundEvents.PLAYER_HURT_FREEZE, 1.2f, .7f);
    }
    if (age == s.move.hits()[0]) {
      sound(s, SoundEvents.GLASS_BREAK, 2, 1.25f);
      sound(s, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6f, .5f);
    }
  }

  // ---------------------------------------------------------------- 空弦 · 断界

  private static void voidSlash(DualCombat.State s, int age) {
    if (age == 0) sound(s, SoundEvents.WARDEN_SONIC_CHARGE, 1.6f, .7f);
    if (age == 14) sound(s, SoundEvents.BEACON_ACTIVATE, 1.4f, .5f);
    if (age == VOID_SLASH || age == VOID_AFTERSHOCK) {
      boolean main = age == VOID_SLASH;
      ServerPlayer p = s.p;
      Vec3 o = p.position(), d = s.direction;
      int hits = 0;
      AABB box =
          new AABB(o, o.add(d.scale(VOID_LENGTH)))
              .inflate(VOID_HALF_WIDTH + 1, 0, VOID_HALF_WIDTH + 1)
              .expandTowards(0, 14, 0)
              .expandTowards(0, -3, 0);
      for (LivingEntity e :
          p.level().getEntitiesOfClass(LivingEntity.class, box, e -> TargetRules.allowed(p, e))) {
        if (hits >= ServerConfig.get().maxTargetsPerWindow) break;
        Vec3 rel = e.position().subtract(o);
        double along = rel.x * d.x + rel.z * d.z;
        double side = rel.x * -d.z + rel.z * d.x;
        // 断界 cuts through what stands between; walls do not shelter the far bank.
        if (along < -.5
            || along > VOID_LENGTH
            || Math.abs(side) > VOID_HALF_WIDTH + e.getBbWidth() / 2) continue;
        hits++;
        strike(s, e, main ? s.move.damage() : s.move.damage() * .4f, true);
        if (!main && e.isAlive()) {
          // Pull back toward the closing fissure: against the side offset, along its normal.
          e.push(d.z * side * .18, .35, -d.x * side * .18);
          Compat.syncMotion(e);
        }
        DualCombat.send(s, ActionEvent.IMPACT, e.position().add(0, e.getBbHeight() * .5, 0));
      }
      if (main) {
        sound(s, SoundEvents.WARDEN_SONIC_BOOM, 3, .8f);
        sound(s, SoundEvents.END_PORTAL_SPAWN, .7f, 1.4f);
      } else sound(s, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.6f, .6f);
    }
  }

  // ---------------------------------------------------------------- shared

  private static float scaled(DualCombat.State s, float base) {
    return WeaponDamage.scaled(s.p, s.main, base) * BladeStyle.power(s.main);
  }

  private static void strike(DualCombat.State s, LivingEntity e, float base, boolean sweeping) {
    Compat.resetHurtCooldown(e);
    if (WeaponDamage.apply(s.p, e, s.main, scaled(s, base), sweeping)) {
      s.struck.add(e.getUUID());
      afterStrike(s, e);
      BladeArts.thunderMark(s.p, s.main, e);
    }
  }

  /**
   * The thunder is real for whatever the blade struck: vanilla lightning effects follow (a creeper
   * becomes charged, a pig turns, the struck burn). The bolt itself stays visual-only, so it sets
   * no blocks alight and never touches the wielder or the creatures the blade spared.
   */
  private static void thunder(DualCombat.State s, LivingEntity e, LightningBolt bolt) {
    if (!e.isAlive() || e.isRemoved() || !s.struck.contains(e.getUUID())) return;
    bolt.setCause(s.p);
    e.thunderHit((ServerLevel) s.p.level(), bolt);
  }

  private static List<LivingEntity> near(DualCombat.State s, Vec3 at, double radius) {
    List<LivingEntity> out = new ArrayList<>();
    for (LivingEntity e :
        s.p
            .level()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(at, at).inflate(radius, 3, radius),
                e -> TargetRules.allowed(s.p, e))) {
      if (out.size() >= ServerConfig.get().maxTargetsPerWindow) break;
      if (e.position().distanceToSqr(at) <= radius * radius) out.add(e);
    }
    return out;
  }

  private static boolean visible(ServerPlayer p, LivingEntity e) {
    return p.level()
            .clip(
                new ClipContext(
                    p.getEyePosition(),
                    e.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    p))
            .getType()
        == HitResult.Type.MISS;
  }

  private static void sound(DualCombat.State s, SoundEvent sound, float volume, float pitch) {
    s.level.playSound(null, s.p.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
  }
}

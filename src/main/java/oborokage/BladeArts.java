package oborokage;

import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/**
 * Blade-specific riders shared by the dual chain and the charged techniques. Every effect is
 * server-side, passes TargetRules and is bounded in targets, lifetime and entries.
 */
public final class BladeArts {
  private record Mark(UUID owner, long until) {}

  private record Frost(int stacks, long until) {}

  private record Pulse(
      ServerPlayer owner,
      ServerLevel level,
      ItemStack blade,
      Vec3 center,
      double radius,
      float damage,
      long at) {}

  private record Echo(
      ServerPlayer owner,
      ServerLevel level,
      ItemStack blade,
      List<LivingEntity> targets,
      float damage,
      long at) {}

  private static final List<Echo> echoes = new ArrayList<>();
  private static final Map<UUID, Mark> marks = new HashMap<>();
  private static final Map<UUID, Frost> frost = new HashMap<>();
  private static final List<Pulse> pulses = new ArrayList<>();
  private static final int MAX_ENTRIES = 512, MAX_PULSES = 64, CHAIN_ARCS = 2;
  private static final DustParticleOptions VOID_DUST = new DustParticleOptions(0x9765e7, .45f);
  private static final DustParticleOptions MARK_DUST = new DustParticleOptions(0xd3b6ff, .6f);

  public static boolean enabled() {
    return ServerConfig.get().bladeArts;
  }

  /** Arts are on and this wielder still has the strength for them (see {@link Vigor}). */
  public static boolean enabled(net.minecraft.world.entity.player.Player p) {
    return enabled() && !Vigor.fatigued(p);
  }

  public static void clear() {
    marks.clear();
    frost.clear();
    pulses.clear();
    echoes.clear();
    charges.clear();
    fields.clear();
    tethers.clear();
  }

  public static void tick(MinecraftServer server) {
    long now = server.overworld().getGameTime();
    for (var it = pulses.iterator(); it.hasNext(); ) {
      Pulse pulse = it.next();
      if (pulse.owner.isRemoved() || !pulse.owner.isAlive() || pulse.owner.level() != pulse.level) {
        it.remove();
        continue;
      }
      if (pulse.level.getGameTime() < pulse.at) continue;
      it.remove();
      frostPulse(pulse);
    }
    for (var it = echoes.iterator(); it.hasNext(); ) {
      Echo echo = it.next();
      if (echo.owner.isRemoved() || !echo.owner.isAlive() || echo.owner.level() != echo.level) {
        it.remove();
        continue;
      }
      if (echo.level.getGameTime() < echo.at) continue;
      it.remove();
      echo(echo);
    }
    if (now % 20 == 0) {
      marks.values().removeIf(m -> m.until < now);
      frost.values().removeIf(f -> f.until < now);
      charges.values().removeIf(c -> c.until < now && c.calm < now);
    }
    tickFields();
    tickTethers();
  }

  // ----- 空弦 · 虚印 -----

  public static void mark(ServerPlayer owner, LivingEntity target) {
    if (!enabled(owner) || !Feats.enemy(target)) return;
    int ticks = ServerConfig.get().voidMarkTicks;
    if (marks.size() >= MAX_ENTRIES) marks.clear();
    marks.put(target.getUUID(), new Mark(owner.getUUID(), owner.level().getGameTime() + ticks));
    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), owner);
  }

  /** A marked enemy takes one empowered dual hit from the player that marked it. */
  public static boolean consumeMark(ServerPlayer owner, LivingEntity target) {
    Mark mark = marks.get(target.getUUID());
    if (mark == null
        || !mark.owner.equals(owner.getUUID())
        || mark.until < owner.level().getGameTime()) return false;
    marks.remove(target.getUUID());
    target.removeEffect(MobEffects.GLOWING);
    var level = (ServerLevel) owner.level();
    Vec3 c = target.getBoundingBox().getCenter();
    level.sendParticles(MARK_DUST, c.x, c.y, c.z, 14, .3, .4, .3, .02);
    level.playSound(
        null,
        target.blockPosition(),
        SoundEvents.AMETHYST_CLUSTER_BREAK,
        SoundSource.PLAYERS,
        .8f,
        1.5f);
    return true;
  }

  // ----- 胧影 · 残月 -----

  /** The tachi's shadow repeats a finished cut on the enemies it struck, a moment later. */
  public static void scheduleEcho(
      ServerPlayer owner, ItemStack blade, List<LivingEntity> targets, float damage, int delay) {
    if (!enabled(owner) || targets.isEmpty() || echoes.size() >= MAX_PULSES) return;
    echoes.add(
        new Echo(
            owner,
            (ServerLevel) owner.level(),
            blade,
            List.copyOf(targets),
            damage,
            owner.level().getGameTime() + delay));
  }

  private static void echo(Echo echo) {
    ServerPlayer p = echo.owner;
    int processed = 0;
    for (LivingEntity target : echo.targets) {
      if (processed >= ServerConfig.get().maxTargetsPerWindow) break;
      if (!target.isAlive()
          || target.isRemoved()
          || target.level() != echo.level
          || target.distanceToSqr(p) > 12 * 12
          || !TargetRules.allowed(p, target)) continue;
      processed++;
      Vec3 c = target.getBoundingBox().getCenter();
      oborokage.compat.Compat.resetHurtCooldown(target);
      if (WeaponDamage.apply(p, target, echo.blade, echo.damage, false))
        DualCombat.reward(p, target, ServerConfig.get().hitIntent);
      Vec3 away = c.subtract(p.position()).multiply(1, 0, 1);
      if (away.lengthSqr() < 1e-4) away = p.getLookAngle();
      TechniqueEvent.send(p, TechniqueEvent.ECHO, c, c.add(away.normalize()), 1);
    }
    if (processed > 0)
      echo.level.playSound(
          null,
          p.blockPosition(),
          SoundEvents.AMETHYST_CLUSTER_BREAK,
          SoundSource.PLAYERS,
          .8f,
          1.3f);
  }

  // ----- 霜轮 · 霜蚀 -----

  /** Adds one frost stack; the third stack freezes the target and resets. */
  public static boolean frostHit(ServerPlayer owner, LivingEntity target) {
    if (!enabled(owner) || !target.isAlive()) return false;
    long now = owner.level().getGameTime();
    Frost old = frost.get(target.getUUID());
    int stacks = (old == null || old.until < now ? 0 : old.stacks) + 1;
    if (stacks < 3) {
      if (frost.size() >= MAX_ENTRIES) frost.clear();
      frost.put(target.getUUID(), new Frost(stacks, now + 100));
      return false;
    }
    frost.remove(target.getUUID());
    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3), owner);
    if (target.canFreeze())
      target.setTicksFrozen(
          Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 60));
    var level = (ServerLevel) owner.level();
    Vec3 c = target.getBoundingBox().getCenter();
    level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 24, .35, .5, .35, .04);
    level.playSound(
        null,
        target.blockPosition(),
        SoundEvents.PLAYER_HURT_FREEZE,
        SoundSource.PLAYERS,
        .9f,
        1.2f);
    return true;
  }

  public static void schedulePulse(
      ServerPlayer owner, ItemStack blade, Vec3 center, double radius, float damage, int delay) {
    if (!enabled(owner) || pulses.size() >= MAX_PULSES) return;
    pulses.add(
        new Pulse(
            owner,
            (ServerLevel) owner.level(),
            blade,
            center,
            radius,
            damage,
            owner.level().getGameTime() + delay));
  }

  private static void frostPulse(Pulse pulse) {
    ServerPlayer p = pulse.owner;
    int processed = 0;
    for (LivingEntity target :
        pulse.level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(pulse.center, pulse.center).inflate(pulse.radius, 2.5, pulse.radius),
            e -> TargetRules.allowed(p, e))) {
      if (processed >= ServerConfig.get().maxTargetsPerWindow) break;
      Vec3 c = target.getBoundingBox().getCenter();
      if (c.distanceToSqr(pulse.center) > pulse.radius * pulse.radius
          || !visible(pulse.level, p, pulse.center, c)) continue;
      processed++;
      oborokage.compat.Compat.resetHurtCooldown(target);
      if (WeaponDamage.apply(p, target, pulse.blade, pulse.damage, true)) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), p);
        frostHit(p, target);
      }
    }
    for (int i = 0; i < 32; i++) {
      double a = i * Math.PI * 2 / 32;
      pulse.level.sendParticles(
          ParticleTypes.SNOWFLAKE,
          pulse.center.x + Math.cos(a) * pulse.radius,
          pulse.center.y,
          pulse.center.z + Math.sin(a) * pulse.radius,
          1,
          .02,
          .02,
          .02,
          .01);
    }
    pulse.level.playSound(
        null, BlockPosOf(pulse.center), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .7f, 1.6f);
    // Style 4 draws the ring only; it must not replay the owner's swing pose.
    TechniqueEvent.send(
        p,
        TechniqueEvent.RING,
        pulse.center.add(0, .8, 0),
        pulse.center.add(pulse.radius, .8, 0),
        1);
  }

  // ----- 赤霆 · 雷链 -----

  /** Arcs from a struck enemy to at most two further enemies. Returns arcs that dealt damage. */
  public static int thunderChain(
      ServerPlayer p, ItemStack blade, LivingEntity from, float damage, Set<UUID> struck) {
    if (!enabled(p) || from == null) return 0;
    var level = (ServerLevel) p.level();
    Vec3 origin = from.getBoundingBox().getCenter();
    int arcs = 0;
    List<LivingEntity> candidates =
        level.getEntitiesOfClass(
            LivingEntity.class,
            from.getBoundingBox().inflate(4.5),
            e ->
                e != from
                    && !struck.contains(e.getUUID())
                    && Feats.enemy(e)
                    && TargetRules.allowed(p, e));
    candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
    for (LivingEntity target : candidates) {
      if (arcs >= CHAIN_ARCS) break;
      Vec3 to = target.getBoundingBox().getCenter();
      if (to.distanceToSqr(origin) > 4.5 * 4.5 || !visible(level, p, origin, to)) continue;
      struck.add(target.getUUID());
      oborokage.compat.Compat.resetHurtCooldown(target);
      if (!WeaponDamage.apply(p, target, blade, damage, false)) continue;
      arcs++;
      line(level, ParticleTypes.ELECTRIC_SPARK, origin, to, 10);
      TechniqueEvent.send(p, TechniqueEvent.ARC, origin, to, 1);
    }
    if (arcs > 0)
      level.playSound(
          null,
          from.blockPosition(),
          SoundEvents.LIGHTNING_BOLT_IMPACT,
          SoundSource.PLAYERS,
          .45f,
          1.7f);
    return arcs;
  }

  // ----- 空弦 · 虚刃 -----

  /** A short sword-qi line that reaches enemies behind the first row. */
  public static int voidEdge(
      ServerPlayer p, ItemStack blade, Vec3 direction, float damage, Set<UUID> struck) {
    if (!enabled(p)) return 0;
    var level = (ServerLevel) p.level();
    Vec3 origin = p.getEyePosition().add(0, -.35, 0);
    Vec3 dir = direction.lengthSqr() < 1e-4 ? p.getLookAngle() : direction.normalize();
    Vec3 end = origin.add(dir.scale(7));
    var wall =
        level.clip(
            new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
    double distance = origin.distanceTo(end);
    int hits = 0;
    for (LivingEntity target :
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(origin, end).inflate(1.2),
            e -> !struck.contains(e.getUUID()) && TargetRules.allowed(p, e))) {
      if (hits >= ServerConfig.get().maxTargetsPerWindow) break;
      Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
      double along = delta.dot(dir);
      if (along < 0
          || along > distance + target.getBbWidth() * .5
          || delta.subtract(dir.scale(along)).lengthSqr()
              > Math.pow(.7 + target.getBbWidth() * .5, 2)) continue;
      if (!visible(level, p, origin, target.getBoundingBox().getCenter())) continue;
      struck.add(target.getUUID());
      oborokage.compat.Compat.resetHurtCooldown(target);
      if (WeaponDamage.apply(p, target, blade, damage, false)) hits++;
    }
    line(level, VOID_DUST, origin.add(dir.scale(1.2)), end, (int) Math.min(28, distance * 4));
    if (distance > 1.4)
      TechniqueEvent.send(p, TechniqueEvent.EDGE, origin.add(dir.scale(1.2)), end, 1);
    level.playSound(
        null,
        p.blockPosition(),
        SoundEvents.ILLUSIONER_MIRROR_MOVE,
        SoundSource.PLAYERS,
        .45f,
        1.8f);
    return hits;
  }

  // ----- 赤霆 · 积雷 -----

  private record Charge(UUID owner, int stacks, long until, long calm) {}

  private static final Map<UUID, Charge> charges = new HashMap<>();
  public static final int THUNDER_STACKS = 5;

  /**
   * Every landed 赤霆 blow lays a thunder mark on the target; the fifth calls real lightning down on
   * it: a bolt that sets no blocks alight, extra damage and vanilla's lightning effects (a creeper
   * is charged). The target then cannot be marked again for a second.
   */
  public static void thunderMark(ServerPlayer p, ItemStack blade, LivingEntity target) {
    if (!enabled(p) || !blade.is(Oborokage.RAIKIRI) || !target.isAlive()) return;
    var level = (ServerLevel) p.level();
    long now = level.getGameTime();
    Charge old = charges.get(target.getUUID());
    if (old != null && old.calm > now) return;
    int stacks =
        (old == null || old.until < now || !old.owner.equals(p.getUUID()) ? 0 : old.stacks) + 1;
    Vec3 c = target.getBoundingBox().getCenter();
    if (stacks < THUNDER_STACKS) {
      if (charges.size() >= MAX_ENTRIES) charges.clear();
      charges.put(target.getUUID(), new Charge(p.getUUID(), stacks, now + 100, 0));
      level.sendParticles(Oborokage.SPARK, c.x, c.y, c.z, 3 * stacks, .3, .4, .3, .02);
      return;
    }
    charges.put(target.getUUID(), new Charge(p.getUUID(), 0, now + 100, now + 20));
    var bolt =
        oborokage.compat.Compat.visualLightning(level, target.getX(), target.getY(), target.getZ());
    level.addFreshEntity(bolt);
    oborokage.compat.Compat.resetHurtCooldown(target);
    WeaponDamage.apply(
        p, target, blade, WeaponDamage.scaled(p, blade, 20) * BladeStyle.power(blade), false);
    if (target.isAlive()) {
      bolt.setCause(p);
      target.thunderHit(level, bolt);
    }
    // Drawn as a bright arc from the sky; charge 2 tells the client it was a mark's lightning.
    TechniqueEvent.send(p, TechniqueEvent.ARC, c.add(0, 7, 0), c, 2);
    Vigor.spend(p, Vigor.THUNDER);
  }

  /** Thunder marks a target carries (for checks). */
  public static int thunderStacks(LivingEntity target) {
    Charge c = charges.get(target.getUUID());
    return c == null ? 0 : c.stacks;
  }

  // ----- 胧月 · 花阵 and 霜轮 · 冰面 -----

  private record Field(
      ServerPlayer owner,
      ServerLevel level,
      Vec3 center,
      double radius,
      long start,
      long until,
      boolean ice) {}

  private static final List<Field> fields = new ArrayList<>();
  public static final int BLOOM_TICKS = 60, ICE_TICKS = 60;

  /**
   * 落花 leaves a ring of petals for three seconds. Once a second it mends the wielder, the players
   * the wielder may not cut (all of them unless PvP is on) and the wielder's pets standing in it by
   * one health, paid for from the wielder's food. A spent wielder's petals mend no one.
   */
  public static void bloom(ServerPlayer p, Vec3 center) {
    if (!enabled(p)) return;
    var level = (ServerLevel) p.level();
    long now = level.getGameTime();
    fields.removeIf(f -> !f.ice && f.owner == p);
    if (fields.size() >= MAX_PULSES) return;
    fields.add(new Field(p, level, center, 3, now, now + BLOOM_TICKS, false));
    TechniqueEvent.send(p, TechniqueEvent.BLOOM, center.add(0, .1, 0), center.add(3, .1, 0), 1);
  }

  /**
   * A full frost ring glazes the ground for three seconds: enemies on it are slowed and skid, the
   * wielder runs faster across it.
   */
  public static void iceFloor(ServerPlayer p, Vec3 center, double radius) {
    if (!enabled(p)) return;
    var level = (ServerLevel) p.level();
    long now = level.getGameTime();
    fields.removeIf(f -> f.ice && f.owner == p);
    if (fields.size() >= MAX_PULSES) return;
    fields.add(new Field(p, level, center, radius, now, now + ICE_TICKS, true));
    TechniqueEvent.send(p, TechniqueEvent.ICE, center.add(0, .1, 0), center.add(radius, .1, 0), 1);
  }

  /** Whom 落花's petals mend besides the wielder: players the wielder may not cut, and own pets. */
  public static boolean mends(ServerPlayer owner, LivingEntity e) {
    if (e instanceof ServerPlayer q) return !q.isSpectator() && !TargetRules.allowed(owner, q);
    return e instanceof net.minecraft.world.entity.OwnableEntity pet
        && pet.getOwnerReference() != null
        && pet.getOwnerReference().getUUID().equals(owner.getUUID());
  }

  private static boolean inside(Field f, LivingEntity e) {
    Vec3 d = e.position().subtract(f.center);
    return Math.abs(d.y) < 2 && d.x * d.x + d.z * d.z <= f.radius * f.radius;
  }

  private static void tickFields() {
    for (var it = fields.iterator(); it.hasNext(); ) {
      Field f = it.next();
      long now = f.level.getGameTime();
      if (now > f.until
          || f.owner.isRemoved()
          || !f.owner.isAlive()
          || f.owner.level() != f.level) {
        it.remove();
        continue;
      }
      long age = now - f.start;
      AABB box = new AABB(f.center, f.center).inflate(f.radius, 2, f.radius);
      if (!f.ice && age > 0 && age % 20 == 0 && !Vigor.fatigued(f.owner)) {
        // The wielder is looked at directly; others are found around the ring.
        List<LivingEntity> friends = new ArrayList<>();
        if (inside(f, f.owner)) friends.add(f.owner);
        for (LivingEntity e :
            f.level.getEntitiesOfClass(LivingEntity.class, box, e -> inside(f, e))) {
          if (e != f.owner && mends(f.owner, e)) friends.add(e);
        }
        for (LivingEntity e : friends) {
          if (!e.isAlive() || e.getHealth() >= e.getMaxHealth()) continue;
          e.heal(1);
          Vigor.spend(f.owner, Vigor.BLOOM_HEAL);
          Vec3 c = e.getBoundingBox().getCenter();
          f.level.sendParticles(Oborokage.PETAL, c.x, c.y + .4, c.z, 4, .3, .3, .3, .01);
        }
      }
      if (f.ice && age % 5 == 0) {
        if (inside(f, f.owner))
          f.owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 0, false, false, true));
        for (LivingEntity e :
            f.level.getEntitiesOfClass(LivingEntity.class, box, e -> inside(f, e))) {
          if (e == f.owner || !TargetRules.allowed(f.owner, e)) continue;
          e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 1, false, false), f.owner);
          // Skidding: a small push the way the creature is already moving.
          if (age % 10 == 0) {
            Vec3 v = e.getDeltaMovement().multiply(1, 0, 1);
            if (v.lengthSqr() > 1e-4) {
              v = v.normalize().scale(.18);
              e.push(v.x, 0, v.z);
              oborokage.compat.Compat.syncMotion(e);
            }
          }
        }
      }
    }
  }

  // ----- 空弦 · 弦线 -----

  private record Tether(ServerPlayer owner, LivingEntity target, long until) {}

  private static final Map<UUID, Tether> tethers = new HashMap<>();
  public static final int TETHER_TICKS = 100;
  public static final double TETHER_RANGE = 32;

  /** A full 空弦 line that strikes leaves a string to its farthest target for five seconds. */
  public static void tether(ServerPlayer p, LivingEntity target) {
    if (!enabled(p) || target == null || !target.isAlive()) return;
    if (tethers.size() >= MAX_ENTRIES) tethers.clear();
    tethers.put(p.getUUID(), new Tether(p, target, p.level().getGameTime() + TETHER_TICKS));
    TetherEvent.send(p, target.getId(), TETHER_TICKS);
  }

  /** The target at the end of the wielder's string, or null. */
  public static LivingEntity tethered(ServerPlayer p) {
    Tether t = tethers.get(p.getUUID());
    return t != null && valid(t) ? t.target : null;
  }

  public static void untether(ServerPlayer p) {
    if (tethers.remove(p.getUUID()) != null) TetherEvent.send(p, -1, 0);
  }

  private static boolean valid(Tether t) {
    return t.until >= t.owner.level().getGameTime()
        && t.target.isAlive()
        && !t.target.isRemoved()
        && t.target.level() == t.owner.level()
        && !t.owner.isRemoved()
        && t.owner.distanceToSqr(t.target) <= TETHER_RANGE * TETHER_RANGE;
  }

  private static void tickTethers() {
    for (var it = tethers.values().iterator(); it.hasNext(); ) {
      Tether t = it.next();
      if (valid(t)) continue;
      it.remove();
      if (!t.owner.isRemoved()) TetherEvent.send(t.owner, -1, 0);
    }
  }

  private static boolean visible(ServerLevel level, ServerPlayer p, Vec3 from, Vec3 to) {
    return level
            .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
            .getType()
        == HitResult.Type.MISS;
  }

  private static <T extends ParticleOptions> void line(
      ServerLevel level, T particle, Vec3 from, Vec3 to, int points) {
    for (int i = 0; i <= points; i++) {
      Vec3 at = from.lerp(to, i / (double) Math.max(1, points));
      level.sendParticles(particle, at.x, at.y, at.z, 1, .02, .02, .02, .005);
    }
  }

  private static net.minecraft.core.BlockPos BlockPosOf(Vec3 v) {
    return net.minecraft.core.BlockPos.containing(v);
  }

  private BladeArts() {}
}

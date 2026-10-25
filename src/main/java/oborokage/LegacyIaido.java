package oborokage;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/**
 * 胧月一闪. A charged draw dashes through everything in its path. Below full charge every cut lands as
 * the blade passes. At full charge the dash is longer and wider, and what it passes through is only
 * marked: the enemies stand pinned while the swordsman stands still, and when the blade clicks home
 * in its scabbard (SHEATH) every one of them falls at once.
 */
public final class LegacyIaido {
  public static final int FULL_WINDUP = 12, SHEATH = 34, FULL_END = 42;
  private static final Map<UUID, Strike> ACTIVE = new HashMap<>();

  public static void markDamaged(UUID id) {
    Strike s = ACTIVE.get(id);
    if (s != null) s.tookDamage = true;
  }

  public static boolean active(UUID id) {
    return ACTIVE.containsKey(id);
  }

  public static void clear() {
    ACTIVE.clear();
  }

  public static float damage(float q) {
    return 15f + 985f * q * q;
  }

  public static boolean start(ServerPlayer p, float charge) {
    if (active(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
    float q = Math.max(0, Math.min(1, charge));
    // On the ground the draw runs level; gliding or flying it follows the eyes, so a dive released
    // at the bottom of the stoop lands on what the wielder is looking at.
    Vec3 d =
        p.isFallFlying() || p.getAbilities().flying
            ? p.getLookAngle().normalize()
            : new Vec3(
                -Math.sin(Math.toRadians(p.getYRot())), 0, Math.cos(Math.toRadians(p.getYRot())));
    ACTIVE.put(p.getUUID(), new Strike(p, q, d));
    broadcast(p, new SlashPayload(p.getId(), p.position(), p.getYRot(), q, 0));
    return true;
  }

  public static void broadcast(ServerPlayer p, SlashPayload msg) {
    var targets = new HashSet<ServerPlayer>(PlayerLookup.tracking(p));
    targets.add(p);
    for (var receiver : targets)
      if (receiver.connection != null && ServerPlayNetworking.canSend(receiver, SlashPayload.TYPE))
        ServerPlayNetworking.send(receiver, msg);
  }

  public static void tick(MinecraftServer server) {
    var it = ACTIVE.values().iterator();
    while (it.hasNext()) {
      Strike s = it.next();
      ServerPlayer p = s.player;
      if (!p.isAlive() || p.isRemoved() || p.isSpectator() || p.level() != s.level) {
        it.remove();
        continue;
      }
      int age = s.age++;
      int delay = s.full ? FULL_WINDUP : 0;
      if (age < delay) continue;
      if (age == delay) {
        oborokage.compat.Compat.swing(p, InteractionHand.MAIN_HAND);
        s.level.playSound(
            null,
            p.blockPosition(),
            SoundEvents.PLAYER_ATTACK_SWEEP,
            SoundSource.PLAYERS,
            1.1f,
            s.full ? .6f : 1f);
        s.level.playSound(
            null,
            p.blockPosition(),
            SoundEvents.TRIDENT_THROW.value(),
            SoundSource.PLAYERS,
            .8f,
            .75f);
      }
      if (age < delay + 6 && !s.blocked && p.isPassenger()) {
        // Mounted: the mount keeps its course and the draw's line runs on ahead of the rider.
        Vec3 requested = s.direction.scale(s.full ? 2.5 : .9 + 1.1 * s.charge);
        Vec3 from = s.ahead == null ? p.position() : s.ahead;
        s.ahead = from.add(requested);
        sweep(s, from, s.ahead);
      } else if (age < delay + 6 && !s.blocked) {
        Vec3 from = p.position();
        Vec3 requested = s.direction.scale(s.full ? 2.5 : .9 + 1.1 * s.charge);
        p.setDeltaMovement(Vec3.ZERO);
        p.move(MoverType.SELF, requested);
        Vec3 to = p.position();
        s.blocked = to.subtract(from).lengthSqr() < requested.lengthSqr() * .96;
        // Rotation is sent as relative zero: the client keeps the view it is turning, instead of
        // snapping back to the server's last known angles every tick of the dash.
        if (p.connection != null)
          p.connection.teleport(
              new net.minecraft.world.entity.PositionMoveRotation(to, Vec3.ZERO, 0, 0),
              java.util.Set.of(
                  net.minecraft.world.entity.Relative.Y_ROT,
                  net.minecraft.world.entity.Relative.X_ROT));
        sweep(s, from, to);
      }
      if (age == delay + 6 && p.isFallFlying()) {
        // Out of the stoop the glide carries on along the line of the cut.
        p.setDeltaMovement(s.direction.scale(1.2));
        if (p.connection != null)
          p.connection.send(
              new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
      }
      if (age == delay + 9 && !s.full) {
        s.level.playSound(
            null,
            p.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_BREAK,
            SoundSource.PLAYERS,
            .7f,
            1.7f);
      }
      if (s.full && age > delay + 5 && age < SHEATH) pin(s);
      if (s.full && age == SHEATH - 1) {
        // The blade slides home: a soft ring, then the click.
        s.level.playSound(
            null,
            p.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            .9f,
            .6f);
      }
      if (s.full && age == SHEATH) sever(s);
      if (age >= (s.full ? FULL_END : delay + 14)) {
        if (s.full && s.kills >= 3 && !s.tookDamage) {
          var advancement = server.getAdvancements().get(Oborokage.id("moon_still_hangs"));
          if (advancement != null) p.getAdvancements().award(advancement, "flawless_iaido");
        }
        it.remove();
      }
    }
  }

  /** Holds the marked still until the sheath: no drift, no stepping away. */
  private static void pin(Strike s) {
    for (LivingEntity target : s.marked) {
      if (!target.isAlive() || target.isRemoved() || target.level() != s.level) continue;
      Vec3 v = target.getDeltaMovement();
      target.setDeltaMovement(0, Math.min(0, v.y), 0);
      if (target instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
    }
  }

  /** The click of the scabbard: every marked enemy is cut at once. */
  private static void sever(Strike s) {
    ServerPlayer p = s.player;
    int cut = 0;
    for (LivingEntity target : s.marked) {
      if (!target.isAlive()
          || target.isRemoved()
          || target.level() != s.level
          || target.distanceToSqr(p) > 48 * 48) continue;
      Vec3 at = target.position().add(0, target.getBbHeight() * .5, 0);
      strikeTarget(s.level, p, target, s.charge);
      cut++;
      if (!target.isAlive() && !(target instanceof Player)) s.kills++;
      broadcast(p, new SlashPayload(p.getId(), at, p.getYRot(), s.charge, 2));
    }
    s.marked.clear();
    s.level.playSound(
        null,
        p.blockPosition(),
        SoundEvents.AMETHYST_CLUSTER_BREAK,
        SoundSource.PLAYERS,
        1.2f,
        .8f);
    if (cut > 0)
      s.level.playSound(
          null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, .5f);
  }

  private static void sweep(Strike s, Vec3 from, Vec3 to) {
    // The full draw cuts a wider swath.
    double w = s.full ? 2.2 : 1.65;
    AABB area = new AABB(from, to).inflate(w, s.full ? 1.9 : 1.7, w).move(0, .8, 0);
    for (LivingEntity target :
        s.level.getEntitiesOfClass(
            LivingEntity.class, area, e -> e != s.player && e.isAlive() && !e.isSpectator())) {
      if (!TargetRules.allowed(s.player, target)) continue;
      if (s.hit.contains(target.getUUID())) continue;
      Vec3 anchor = to.add(0, 1, 0);
      // Closest point on large boss hitboxes avoids aiming through a wall at its far-away center.
      AABB b = target.getBoundingBox();
      Vec3 point =
          new Vec3(
              Math.clamp(anchor.x, b.minX, b.maxX),
              Math.clamp(anchor.y, b.minY, b.maxY),
              Math.clamp(anchor.z, b.minZ, b.maxZ));
      if (s.level
              .clip(
                  new ClipContext(
                      anchor, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.player))
              .getType()
          != HitResult.Type.MISS) continue;
      s.hit.add(target.getUUID());
      DualCombat.reward(s.player, target, ServerConfig.get().iaidoIntent);
      if (s.full && s.marked.size() < 64) {
        // Marked now, cut when the blade is sheathed.
        s.marked.add(target);
        target.setDeltaMovement(Vec3.ZERO);
      } else {
        strikeTarget(s.level, s.player, target, s.charge);
        if (!target.isAlive() && !(target instanceof Player)) s.kills++;
      }
      broadcast(
          s.player,
          new SlashPayload(
              s.player.getId(),
              target.position().add(0, target.getBbHeight() * .5, 0),
              s.player.getYRot(),
              s.charge,
              1));
    }
  }

  private static boolean fullDraw;

  /** Whether the blow now landing is a full-charge draw (奥义 · 胧月一闪), which no blade parries. */
  public static boolean fullDrawBlow() {
    return fullDraw;
  }

  public static void strikeTarget(
      ServerLevel level, ServerPlayer owner, LivingEntity target, float q) {
    if (!TargetRules.allowed(owner, target)) return;
    if (target instanceof Player p
        && (!Oborokage.allowPlayerDamage
            || p.getAbilities().instabuild
            || p.isSpectator()
            || !owner.canHarmPlayer(p))) return;
    oborokage.compat.Compat.resetHurtCooldown(target);
    target.setLastHurtByPlayer(owner, 100);
    boolean execute = q >= 1 && !target.is(TargetRules.FINISHER_EXEMPT);
    DamageSource source =
        execute
            ? new DamageSource(level.damageSources().genericKill().typeHolder(), owner)
            : level.damageSources().playerAttack(owner);
    if (execute) {
      target.setAbsorptionAmount(0);
      target.stopUsingItem();
    }
    fullDraw = q >= 1;
    try {
      target.hurtServer(level, source, damage(q));
    } finally {
      fullDraw = false;
    }
    // Custom high-health / damage-cancelling entities still use their normal death callback.
    if (execute && target.isAlive()) {
      target.setHealth(0);
      target.die(source);
    }
    if (execute && target.isAlive() && ServerConfig.get().finisherRetry) {
      oborokage.compat.Compat.resetHurtCooldown(target);
      target.hurtServer(
          level, source, Math.max(1000, target.getHealth() + target.getAbsorptionAmount()));
      if (target.isAlive()) {
        target.setHealth(0);
        target.die(source);
      }
    }
    if (execute && target.isAlive()) FinisherDiagnostics.failed(target);
  }

  private static final class Strike {
    final ServerPlayer player;
    final ServerLevel level;
    final float charge;
    final boolean full;
    final Vec3 direction;

    /** Mounted draws: how far ahead of the rider the line has run. */
    Vec3 ahead;

    final Set<UUID> hit = new HashSet<>();
    final List<LivingEntity> marked = new ArrayList<>();
    int age, kills;
    boolean blocked, tookDamage;

    Strike(ServerPlayer p, float q, Vec3 d) {
      player = p;
      level = (ServerLevel) p.level();
      charge = q;
      full = q >= 1;
      direction = d;
    }
  }
}

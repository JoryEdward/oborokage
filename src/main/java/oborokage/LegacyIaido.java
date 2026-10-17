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

public final class LegacyIaido {
  public static final int FULL_WINDUP = 12;
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
    if (active(p.getUUID())
        || !p.isAlive()
        || p.isSpectator()
        || p.isPassenger()
        || p.isFallFlying()) return false;
    float q = Math.max(0, Math.min(1, charge));
    Vec3 d =
        new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0, Math.cos(Math.toRadians(p.getYRot())));
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
      if (!p.isAlive()
          || p.isRemoved()
          || p.isSpectator()
          || p.level() != s.level
          || p.isPassenger()) {
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
      if (age < delay + 6 && !s.blocked) {
        Vec3 from = p.position();
        Vec3 requested = s.direction.scale(.9 + 1.1 * s.charge);
        p.setDeltaMovement(Vec3.ZERO);
        p.move(MoverType.SELF, requested);
        Vec3 to = p.position();
        s.blocked =
            to.subtract(from).horizontalDistanceSqr() < requested.horizontalDistanceSqr() * .96;
        if (p.connection != null) p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
        sweep(s, from, to);
      }
      if (age == delay + 9) {
        s.level.playSound(
            null,
            p.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_BREAK,
            SoundSource.PLAYERS,
            .7f,
            1.7f);
      }
      if (age >= (s.full ? 38 : delay + 14)) {
        if (s.full && s.kills >= 3 && !s.tookDamage) {
          var advancement = server.getAdvancements().get(Oborokage.id("moon_still_hangs"));
          if (advancement != null) p.getAdvancements().award(advancement, "flawless_iaido");
        }
        it.remove();
      }
    }
  }

  private static void sweep(Strike s, Vec3 from, Vec3 to) {
    AABB area = new AABB(from, to).inflate(1.65, 1.7, 1.65).move(0, .8, 0);
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
      strikeTarget(s.level, s.player, target, s.charge);
      if (!target.isAlive() && !(target instanceof Player)) s.kills++;
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
    target.hurtServer(level, source, damage(q));
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
    final Set<UUID> hit = new HashSet<>();
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

package oborokage;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server authority for new dual-blade moves. Legacy iaido is never gated by these resources. */
public final class DualCombat {
  private static final Map<UUID, State> states = new HashMap<>();
  private static long nextAction;

  private record Pressure(int count, long until, long brokenUntil) {}

  public static final class State {
    final ServerPlayer p;
    final ServerLevel level;
    int requestsThisTick;
    long requestTick = -1, bufferedUntil;
    int lastSequence = -1, intent, stamina = 100, chainHands, nextCombo = Move.MAIN;
    int actionSequence = -1,
        bufferedSequence = -1,
        lastSentIntent = -1,
        lastSentStamina = -1,
        lastSentMove = -1;
    long regenAfter, lastStatus;
    boolean guardSucceeded, wasEquipped, motionActive;
    Vec3 lastMotionPosition;
    final Map<UUID, Long> parryRewards = new HashMap<>();
    long actionId,
        start,
        chainId,
        comboUntil,
        dodgeUntil,
        ultimateUntil,
        guardStart,
        guardHeartbeat,
        guardRestart,
        counterUntil,
        lastUsefulHit,
        lastDecay;
    UUID counterSource, lastParrySource;
    long lastParryTick = -99;
    Move move;
    Vec3 direction = Vec3.ZERO;
    float yaw;
    boolean buffered, guarding, blocked;
    double dodgeDistance;
    long stepFollowUntil;
    long riposteUntil, lastEvadeTick = -99, evadeRewardAfter;
    // Ultimate scratch state: blink path, frozen targets and everything already struck.
    List<LivingEntity> ultTargets = new ArrayList<>();
    final Set<UUID> frozen = new HashSet<>(), struck = new HashSet<>();
    Vec3 ultOrigin = Vec3.ZERO;
    float beforeDamageTotal = Float.NaN;
    ItemStack main, off;
    final Set<String> hits = new HashSet<>();
    final Map<UUID, Pressure> pressure = new HashMap<>();
    final Map<UUID, Long> rewarded = new HashMap<>();

    State(ServerPlayer p) {
      this.p = p;
      level = (ServerLevel) p.level();
    }

    public int intent() {
      return intent;
    }

    public int stamina() {
      return stamina;
    }

    public int move() {
      return move == null ? 0 : move.id();
    }
  }

  /** Holding any of the five blades in the main hand: enough for the ultimate and the HUD. */
  public static boolean holdsBlade(Player p) {
    return BladeStyle.of(p.getMainHandItem()) != null;
  }

  public static boolean equipped(Player p) {
    return Oborokage.mainBlade(p.getMainHandItem()) && p.getOffhandItem().is(Oborokage.WAKIZASHI);
  }

  public static State state(ServerPlayer p) {
    return states.computeIfAbsent(p.getUUID(), id -> new State(p));
  }

  public static void clear() {
    states.clear();
    nextAction = 0;
  }

  public static void remove(ServerPlayer p) {
    states.remove(p.getUUID());
  }

  public static void cancel(ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (s != null) {
      boolean active = s.move != null || s.guarding || s.buffered || s.motionActive;
      stopMotion(s);
      releaseGuard(s);
      s.move = null;
      s.buffered = false;
      s.guarding = false;
      s.nextCombo = Move.MAIN;
      s.chainHands = 0;
      if (active) send(s, ActionEvent.CANCEL, p.position());
    }
  }

  public static void request(ServerPlayer p, ActionRequest request) {
    if (request.action() < 0 || request.action() > 4 || request.sequence() < 0) return;
    State s = state(p);
    if (request.sequence() <= s.lastSequence) return;
    s.lastSequence = request.sequence();
    long requestTime = p.level().getGameTime();
    if (s.requestTick != requestTime) {
      s.requestTick = requestTime;
      s.requestsThisTick = 0;
    }
    if (++s.requestsThisTick > 8) return;
    // The ultimate belongs to whichever blade is drawn; every other action needs both blades.
    if (!(request.action() == 4 ? holdsBlade(p) : equipped(p))
        || !p.isAlive()
        || p.isSpectator()
        || p.isPassenger()
        || p.isFallFlying()
        || LegacyIaido.active(p.getUUID())
        || p.isUsingItem()) {
      reject(s, request.sequence());
      return;
    }
    long now = p.level().getGameTime();
    switch (request.action()) {
      case 0 -> {
        if (s.move != null) {
          if (!Move.ultimate(s.move.id()) && s.move.id() != Move.DODGE) {
            s.buffered = true;
            s.bufferedUntil = now + 8;
            s.bufferedSequence = request.sequence();
          } else reject(s, request.sequence());
          return;
        }
        if (now < s.counterUntil && s.counterSource != null) {
          begin(s, Move.COUNTER);
          return;
        }
        if (now > s.comboUntil) {
          s.nextCombo = Move.MAIN;
          s.chainHands = 0;
          s.chainId = ++nextAction;
        }
        begin(s, s.nextCombo);
      }
      case 1 -> {
        s.guardHeartbeat = now;
        if (s.guarding) return;
        var cfg = ServerConfig.get();
        if (s.move == null
            && now >= s.guardRestart
            && s.stamina >= cfg.guardEntryCost + cfg.perfectGuardCost) {
          s.guarding = true;
          s.guardSucceeded = false;
          s.actionId = ++nextAction;
          s.actionSequence = request.sequence();
          s.guardStart = now;
          s.guardRestart = now + cfg.guardRearmTicks;
          s.stamina -= cfg.guardEntryCost;
          s.regenAfter = now + cfg.staminaRegenDelayTicks;
          send(s, ActionEvent.START, p.position(), Move.GUARD);
        } else reject(s, request.sequence());
      }
      case 2 -> {
        if (s.guarding) {
          releaseGuard(s);
          send(s, ActionEvent.CANCEL, p.position(), Move.GUARD);
        }
      }
      case 3 -> {
        if (s.move == null && now >= s.dodgeUntil) {
          begin(s, Move.DODGE);
          s.dodgeUntil = now + ServerConfig.get().dodgeCooldownTicks;
        } else reject(s, request.sequence());
      }
      case 4 -> {
        if (s.move == null && now >= s.ultimateUntil && s.intent >= 100) {
          s.intent -= 100;
          s.ultimateUntil = now + ServerConfig.get().ultimateCooldownTicks;
          begin(s, BladeStyle.of(p.getMainHandItem()).ultimate);
        } else reject(s, request.sequence());
      }
    }
  }

  private static void begin(State s, int id) {
    begin(s, id, s.lastSequence);
  }

  private static void begin(State s, int id, int sequence) {
    if (id == Move.MAIN) {
      s.chainHands = 0;
      s.chainId = ++nextAction;
    }
    releaseGuard(s);
    s.move = Move.of(id);
    s.actionSequence = sequence;
    s.start = s.level.getGameTime();
    s.actionId = ++nextAction;
    s.yaw = s.p.getYRot();
    s.hits.clear();
    s.buffered = false;
    s.blocked = false;
    s.dodgeDistance = 0;
    s.struck.clear();
    s.frozen.clear();
    s.ultTargets.clear();
    s.main = s.p.getMainHandItem();
    s.off = s.p.getOffhandItem();
    Vec3 forward = forward(s.yaw);
    if (id == Move.DODGE) {
      s.chainHands = 0;
      s.chainId = ++nextAction;
      var input = s.p.getLastClientInput();
      double z = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0),
          x = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
      // up × forward is the player's left (yaw 0 faces +Z, so left is +X), matching vanilla input.
      s.direction = forward.scale(z).add(new Vec3(0, 1, 0).cross(forward).scale(x));
      if (s.direction.lengthSqr() < .1) s.direction = forward.scale(-1);
      else s.direction = s.direction.normalize();
    } else s.direction = forward;
    send(s, ActionEvent.START, s.p.position());
  }

  public static void tick(MinecraftServer server) {
    Set<UUID> live = new HashSet<>();
    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
      live.add(p.getUUID());
      State old = states.get(p.getUUID());
      if (old != null && (old.p != p || old.level != p.level())) {
        states.remove(p.getUUID());
        Feats.damaged(p);
      }
      if (equipped(p)) state(p);
      Feats.tick(p);
    }
    for (var it = states.values().iterator(); it.hasNext(); ) {
      State s = it.next();
      ServerPlayer p = s.p;
      long now = s.level.getGameTime();
      if (!p.isAlive() || p.isRemoved() || p.level() != s.level) {
        send(s, ActionEvent.CANCEL, p.position());
        it.remove();
        continue;
      }
      live.add(p.getUUID());
      boolean ultimate = s.move != null && Move.ultimate(s.move.id());
      if (!(ultimate ? holdsBlade(p) : equipped(p))
          || p.isPassenger()
          || p.isFallFlying()
          || p.isUsingItem()
          || LegacyIaido.active(p.getUUID())) {
        if (s.move != null || s.guarding) cancel(p);
      }
      if (s.guarding && now - s.guardHeartbeat > 30) {
        releaseGuard(s);
        send(s, ActionEvent.CANCEL, p.position(), Move.GUARD);
      }
      if (!s.guarding && now >= s.regenAfter) s.stamina = Math.min(100, s.stamina + 1);
      // A full gauge holds: the ultimate waits for the right moment instead of draining away.
      if (s.intent > 0
          && s.intent < 100
          && now - s.lastUsefulHit > 160
          && now - s.lastDecay >= 20) {
        s.intent--;
        s.lastDecay = now;
      }
      s.pressure
          .entrySet()
          .removeIf(e -> e.getValue().until < now && e.getValue().brokenUntil < now);
      s.rewarded.entrySet().removeIf(e -> e.getValue() + 100 < now);
      s.parryRewards.entrySet().removeIf(e -> e.getValue() + 200 < now);
      if (s.move != null) {
        if (p.getMainHandItem() != s.main || p.getOffhandItem() != s.off) {
          cancel(p);
          continue;
        }
        int age = (int) (now - s.start);
        int id = s.move.id();
        if (s.motionActive && s.lastMotionPosition != null) {
          s.dodgeDistance += p.position().subtract(s.lastMotionPosition).horizontalDistance();
          s.lastMotionPosition = p.position();
        }
        if (id == Move.DODGE && age >= 0 && age < 5)
          move(s, s.direction.scale(ServerConfig.get().dodgeDistance / 5));
        if (id == Move.DODGE && age == 5 || id == Move.ULTIMATE && age == 11) stopMotion(s);
        if (id == Move.ULTIMATE && age >= 7 && age < 11) move(s, s.direction.scale(.9));
        if (Move.ultimate(id)) Ultimates.tick(s, age);
        for (int i = 0; i < s.move.hits().length; i++) if (age == s.move.hits()[i]) hit(s, i);
        if (age >= s.move.length()) {
          if (Move.ultimate(id)) Ultimates.finish(s);
          if (id == Move.DODGE && s.dodgeDistance >= 2 && !s.blocked) s.stepFollowUntil = now + 12;
          s.move = null;
          s.nextCombo =
              id == Move.MAIN || id == Move.DODGE
                  ? Move.OFF
                  : id == Move.OFF ? Move.CROSS : Move.MAIN;
          s.comboUntil = now + 20;
          boolean next = s.buffered && now <= s.bufferedUntil;
          s.buffered = false;
          if (next) {
            begin(s, s.nextCombo, s.bufferedSequence);
          }
        }
      }
      if (p.connection != null && now - s.lastStatus >= 2) ownerStatus(s);
      // Idle, fully recovered states of players who put the blades away carry no information.
      if (!holdsBlade(p)
          && !s.wasEquipped
          && s.move == null
          && !s.guarding
          && s.intent == 0
          && s.stamina >= 100) it.remove();
    }
    Feats.retain(live);
  }

  private static void move(State s, Vec3 delta) {
    if (s.blocked) return;
    Vec3 permitted =
        Entity.collideBoundingBox(s.p, delta, s.p.getBoundingBox(), s.level, List.of());
    if (permitted.horizontalDistanceSqr() < delta.horizontalDistanceSqr() * .96) s.blocked = true;
    s.motionActive = true;
    if (s.lastMotionPosition == null) s.lastMotionPosition = s.p.position();
    if (s.p.connection == null) {
      s.p.move(MoverType.SELF, permitted);
      return;
    }
    // Use normal client movement/collision and velocity reconciliation, never position teleports.
    s.p.setDeltaMovement(permitted.x, s.p.getDeltaMovement().y, permitted.z);
    s.p.connection.send(
        new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(s.p));
  }

  private static void stopMotion(State s) {
    if (!s.motionActive) return;
    s.motionActive = false;
    s.lastMotionPosition = null;
    s.p.setDeltaMovement(0, s.p.getDeltaMovement().y, 0);
    if (s.p.connection != null)
      s.p.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(s.p));
  }

  private static void hit(State s, int window) {
    Move m = s.move;
    long now = s.level.getGameTime();
    var cfg = ServerConfig.get();
    boolean execute = m.id() == Move.ULTIMATE && window == 2;
    boolean finalCross = m.id() == Move.CROSS && window == m.hits().length - 1;
    boolean ultimate = Move.ultimate(m.id());
    boolean riposte = !ultimate && now <= s.riposteUntil;
    int hand = m.hands()[window];
    // A solo ultimate has no offhand blade; its offhand beats are cut by the main blade.
    ItemStack blade = hand == 0 || BladeStyle.of(s.off) == null ? s.main : s.off;
    Item style = s.main.getItem();
    boolean arts = BladeArts.enabled();
    float reach = m.id() == Move.ULTIMATE ? (float) cfg.ultimateReach : m.reach();
    float baseDamage =
        WeaponDamage.scaled(s.p, blade, m.damage()) * BladeStyle.power(blade) * m.weights()[window];
    if (riposte) baseDamage *= (float) cfg.riposteMultiplier;
    int targets = 0;
    List<LivingEntity> struck = new ArrayList<>();
    Set<UUID> struckIds = new HashSet<>();
    oborokage.compat.Compat.swing(
        s.p, blade == s.main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    s.level.playSound(
        null,
        s.p.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.PLAYERS,
        .75f,
        ultimate ? .65f + .05f * window : hand == 1 ? 1.4f : 1f);
    for (LivingEntity target :
        s.level.getEntitiesOfClass(
            LivingEntity.class,
            s.p.getBoundingBox().inflate(reach, 2.0, reach),
            t -> allowed(s.p, t))) {
      if (targets >= cfg.maxTargetsPerWindow) break;
      AABB b = target.getBoundingBox();
      Vec3 anchor = s.p.getEyePosition().add(0, -.35, 0);
      Vec3 point =
          new Vec3(
              Math.clamp(anchor.x, b.minX, b.maxX),
              Math.clamp(anchor.y, b.minY, b.maxY),
              Math.clamp(anchor.z, b.minZ, b.maxZ));
      Vec3 d = point.subtract(anchor);
      if (d.lengthSqr() > reach * reach || Math.abs(d.y) > 2.2) continue;
      Vec3 horizontal = new Vec3(d.x, 0, d.z);
      if (horizontal.lengthSqr() > .15
          && horizontal.normalize().dot(s.direction) < Math.cos(Math.toRadians(m.arc() / 2)))
        continue;
      if (s.level
              .clip(
                  new ClipContext(
                      anchor, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.p))
              .getType()
          != HitResult.Type.MISS) continue;
      if (!s.hits.add(window + ":" + target.getUUID())) continue;
      targets++;
      struckIds.add(target.getUUID());
      float before = target.getHealth() + target.getAbsorptionAmount();
      Pressure pressure = s.pressure.get(target.getUUID());
      boolean broken = pressure != null && pressure.brokenUntil >= now;
      boolean marked = !execute && arts && BladeArts.consumeMark(s.p, target);
      oborokage.compat.Compat.resetHurtCooldown(target);
      if (execute) LegacyIaido.strikeTarget(s.level, s.p, target, 1);
      else {
        WeaponDamage.apply(
            s.p,
            target,
            blade,
            baseDamage
                * (broken ? 1.3f : 1)
                * (marked ? (float) cfg.voidMarkBonus : 1)
                * (ultimate ? Ultimates.windowScale(s, target) : 1),
            m.id() == Move.CROSS || ultimate);
      }
      if (ultimate) Ultimates.afterStrike(s, target);
      boolean useful =
          before > target.getHealth() + target.getAbsorptionAmount() || !target.isAlive();
      if (!useful) continue;
      if (Feats.enemy(target)) {
        struck.add(target);
        s.chainHands |= hand == 0 ? 1 : 2;
        Feats.hit(s.p, hand);
        Long previous = s.rewarded.put(target.getUUID(), now);
        if (previous == null || now - previous >= 8) {
          gainIntent(s, cfg.hitIntent);
          s.lastUsefulHit = now;
        }
        if (marked) gainIntent(s, cfg.voidMarkIntent);
        int n = pressure == null || pressure.until < now ? 1 : pressure.count + 1;
        // 胧影 · 月蚀: the original blade's cross presses posture one step harder.
        if (arts && m.id() == Move.CROSS && style == Oborokage.TACHI) n++;
        if ((n >= 3 || ultimate) && !broken) {
          s.pressure.put(
              target.getUUID(), new Pressure(0, now + 80, now + cfg.postureDurationTicks));
          if (target.getMaxHealth() < 100)
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 2));
        } else if (!broken) s.pressure.put(target.getUUID(), new Pressure(n, now + 80, 0));
        // 霜轮 · 霜蚀: every chain hit with the frost blade stacks frost; three stacks freeze.
        if (arts && style == Oborokage.FROSTMOON && target.isAlive())
          BladeArts.frostHit(s.p, target);
        if (broken && m.id() == Move.CROSS) Feats.award(s.p, "break_and_cut");
        if (hand == 1 && s.stepFollowUntil >= now) {
          Feats.award(s.p, "decisive_step");
          s.stepFollowUntil = 0;
        }
        if (!target.isAlive()) {
          if (m.id() == Move.CROSS && s.chainHands == 3) Feats.award(s.p, "alternating_finish");
          if (m.id() == Move.COUNTER && target.getUUID().equals(s.counterSource))
            Feats.award(s.p, "borrowed_blade");
          if (execute) Feats.award(s.p, "twin_moon_ultimate");
          Feats.kill(s.p, target, s.actionId, s.chainHands);
        }
      }
      send(s, ActionEvent.IMPACT, target.position().add(0, target.getBbHeight() * .5, 0));
    }
    if (arts && finalCross) crossArt(s, style, baseDamage, struck, struckIds);
    if (riposte && targets > 0) {
      s.riposteUntil = 0;
      s.level.playSound(
          null, s.p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, 1.2f);
    }
    // Like a vanilla sword, an ordinary chain swing that meets nothing does not wear the blade.
    if (targets > 0 || ultimate)
      blade.hurtAndBreak(
          execute ? 8 : ultimate ? 2 : 1,
          s.p,
          blade == s.main ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
  }

  /** The closing cross cut takes on the main blade's style. */
  private static void crossArt(
      State s, Item style, float baseDamage, List<LivingEntity> struck, Set<UUID> struckIds) {
    if (style == Oborokage.RAIKIRI) {
      // 赤霆 · 雷链: each struck enemy arcs to up to two others, at most four arcs per cross.
      int arcs = 0;
      for (LivingEntity from : struck) {
        if (arcs >= 4) break;
        arcs += BladeArts.thunderChain(s.p, s.main, from, baseDamage * .4f, struckIds);
      }
      if (arcs >= 2) Feats.award(s.p, "thunder_chain");
    } else if (style == Oborokage.VOIDSTRING) {
      // 空弦 · 虚刃: a short sword-qi line reaches the rank behind the first.
      BladeArts.voidEdge(s.p, s.main, s.direction, baseDamage * .5f, struckIds);
    }
  }

  /** Intent for a useful hit on an enemy by any blade action, throttled per target. */
  public static void reward(ServerPlayer p, LivingEntity target, int amount) {
    if (!Feats.enemy(target)) return;
    State s = state(p);
    long now = s.level.getGameTime();
    Long previous = s.rewarded.get(target.getUUID());
    if (previous != null && now - previous < 8) return;
    s.rewarded.put(target.getUUID(), now);
    gainIntent(s, amount);
    s.lastUsefulHit = now;
  }

  private static void gainIntent(State s, int amount) {
    s.intent =
        Math.min(
            100, s.intent + (int) Math.round(amount * ServerConfig.get().intentGainMultiplier));
  }

  public static boolean allowed(ServerPlayer p, LivingEntity e) {
    return TargetRules.allowed(p, e);
  }

  private static Vec3 forward(float yaw) {
    return new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
  }

  /** The entity a guard faces: the melee attacker, or the projectile itself. */
  private static Entity guardSource(DamageSource source) {
    if (source.is(DamageTypes.MOB_ATTACK)
        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
        || source.is(DamageTypes.PLAYER_ATTACK))
      return source.getEntity() instanceof LivingEntity attacker
              && source.getDirectEntity() == attacker
          ? attacker
          : null;
    if (ServerConfig.get().guardProjectiles
        && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
        && source.getDirectEntity() != null) return source.getDirectEntity();
    return null;
  }

  private static boolean guardable(State s, DamageSource source) {
    if (!s.guarding || !equipped(s.p) || s.p.isUsingItem() || s.move != null) return false;
    Entity from = guardSource(source);
    if (from == null) return false;
    Vec3 v = from.position().subtract(s.p.position());
    if (!(from instanceof LivingEntity)) {
      // A projectile is already touching the hitbox; its flight path tells where it came from.
      Vec3 flight = from.getDeltaMovement().scale(-1);
      if (flight.horizontalDistanceSqr() > 1e-4) v = flight;
    }
    v = new Vec3(v.x, 0, v.z);
    if (v.lengthSqr() < 1e-6) return false;
    return forward(s.p.getYRot()).dot(v.normalize()) > .2;
  }

  /** 见切: the first frames of a step slip any attack that comes from an entity. */
  private static boolean evade(State s, DamageSource source) {
    var cfg = ServerConfig.get();
    if (cfg.perfectDodgeTicks <= 0 || s.move == null || s.move.id() != Move.DODGE) return false;
    long now = s.level.getGameTime();
    if (now - s.start > cfg.perfectDodgeTicks) return false;
    Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    if (from == null
        || from == s.p
        || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
    if (s.lastEvadeTick != now) {
      s.lastEvadeTick = now;
      s.riposteUntil = s.start + s.move.length() + cfg.riposteWindowTicks;
      if (now >= s.evadeRewardAfter) {
        gainIntent(s, cfg.perfectDodgeIntent);
        s.lastUsefulHit = now;
        s.evadeRewardAfter = now + 20;
      }
      Feats.award(s.p, "afterimage");
      send(s, ActionEvent.EVADE, s.p.position().add(0, 1, 0));
      s.level.playSound(
          null,
          s.p.blockPosition(),
          SoundEvents.ILLUSIONER_MIRROR_MOVE,
          SoundSource.PLAYERS,
          .7f,
          1.4f);
    }
    return true;
  }

  public static boolean allowDamage(LivingEntity e, DamageSource source, float amount) {
    if (!(e instanceof ServerPlayer p) || amount <= 0) return true;
    if (p.isInvulnerableTo((ServerLevel) p.level(), source)
        || oborokage.compat.Compat.hurtCooldown(p) > 10
            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)
            && amount <= ((oborokage.mixin.LivingDamageAccess) p).oborokage$lastHurt()) return true;
    State s = states.get(p.getUUID());
    if (s == null) return true;
    if (evade(s, source)) return false;
    // While an ultimate plays out, attacks from creatures and projectiles cannot interrupt it.
    if (s.move != null
        && Move.ultimate(s.move.id())
        && ServerConfig.get().ultimateGuard
        && (source.getEntity() != null || source.getDirectEntity() != null)
        && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
    if (!guardable(s, source)) return true;
    long now = p.level().getGameTime();
    if (now - s.guardStart >= guardWindow(p) || s.stamina < ServerConfig.get().perfectGuardCost)
      return true;
    // Projectiles without an owner (dispensers) are credited to the projectile itself.
    Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    if (s.lastParryTick == now && Objects.equals(s.lastParrySource, attacker.getUUID()))
      return false;
    s.lastParryTick = now;
    s.lastParrySource = attacker.getUUID();
    rewardParry(s, attacker.getUUID());
    s.counterSource = attacker.getUUID();
    s.counterUntil = now + 12;
    Feats.parry(p);
    send(
        s,
        ActionEvent.PARRY,
        p.getEyePosition().add(forward(p.getYRot()).scale(1.0)).add(0, -.15, 0));
    s.level.playSound(
        null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .22f, 1.9f);
    return false;
  }

  /** Called before normal health damage; never refunds damage after a death. */
  public static float guardedAmount(LivingEntity e, DamageSource source, float amount) {
    if (!(e instanceof ServerPlayer p) || amount <= 0) return amount;
    State s = states.get(p.getUUID());
    if (s == null || !guardable(s, source)) return amount;
    long now = p.level().getGameTime();
    if (now - s.guardStart < guardWindow(p)) return amount;
    int cost = Math.max(12, (int) Math.ceil(amount * 2));
    if (s.stamina < cost) {
      s.stamina = 0;
      releaseGuard(s);
      s.guardRestart = Math.max(s.guardRestart, now + 30);
      send(s, ActionEvent.CANCEL, p.position(), Move.GUARD);
      return amount;
    }
    s.stamina -= cost;
    s.guardSucceeded = true;
    s.regenAfter = now + ServerConfig.get().staminaRegenDelayTicks;
    return amount * .35f;
  }

  public static void captureDamage(ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (s != null) s.beforeDamageTotal = p.getHealth() + p.getAbsorptionAmount();
  }

  public static void afterDamage(ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (s != null) {
      float before = s.beforeDamageTotal;
      s.beforeDamageTotal = Float.NaN;
      if (before > p.getHealth() + p.getAbsorptionAmount() + .0001f) damaged(p);
    }
  }

  public static void damaged(ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (s != null) {
      s.intent = Math.max(0, s.intent - 15);
      s.counterUntil = 0;
    }
    Feats.damaged(p);
  }

  private static void ownerStatus(State s) {
    boolean equip = equipped(s.p) || holdsBlade(s.p);
    int move = s.move == null ? s.guarding ? Move.GUARD : 0 : s.move.id();
    if (!equip && !s.wasEquipped) return;
    if (equip == s.wasEquipped
        && s.intent == s.lastSentIntent
        && s.stamina == s.lastSentStamina
        && move == s.lastSentMove) return;
    s.wasEquipped = equip;
    s.lastSentIntent = s.intent;
    s.lastSentStamina = s.stamina;
    s.lastSentMove = move;
    s.lastStatus = s.level.getGameTime();
    if (ServerPlayNetworking.canSend(s.p, ActionEvent.TYPE))
      ServerPlayNetworking.send(s.p, event(s, ActionEvent.STATUS, s.p.position(), move));
  }

  public static int guardWindow(ServerPlayer p) {
    int latency = p.connection == null ? 0 : p.connection.latency();
    return ServerConfig.get().guardWindowTicks
        + Math.clamp(
            (int) Math.ceil(Math.max(0, latency) / 100.0),
            0,
            ServerConfig.get().guardLatencyCapTicks);
  }

  private static void releaseGuard(State s) {
    if (!s.guarding) return;
    s.guarding = false;
    long now = s.level.getGameTime();
    s.guardRestart =
        Math.max(
            s.guardRestart,
            now
                + (s.guardSucceeded
                    ? ServerConfig.get().guardRearmTicks
                    : ServerConfig.get().emptyGuardRecoveryTicks));
    s.regenAfter = Math.max(s.regenAfter, now + ServerConfig.get().staminaRegenDelayTicks);
  }

  private static void rewardParry(State s, UUID source) {
    long now = s.level.getGameTime();
    s.stamina -= ServerConfig.get().perfectGuardCost;
    s.guardSucceeded = true;
    s.regenAfter = now + ServerConfig.get().staminaRegenDelayTicks;
    Long prev = s.parryRewards.get(source);
    if (prev == null || now - prev >= ServerConfig.get().parryRewardCooldownTicks) {
      if (s.parryRewards.size() > 128) s.parryRewards.clear();
      s.parryRewards.put(source, now);
      gainIntent(s, ServerConfig.get().parryIntent);
      s.lastUsefulHit = now;
    }
  }

  public static boolean reflectArrow(
      net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow, ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (arrow.getOwner() == p
        || !ServerConfig.get().reflectArrows
        || s == null
        || !s.guarding
        || !equipped(p)
        || p.isUsingItem()
        || s.move != null
        || s.level.getGameTime() - s.guardStart >= guardWindow(p)
        || s.stamina < ServerConfig.get().perfectGuardCost) return false;
    Vec3 incoming = arrow.getDeltaMovement();
    if (incoming.lengthSqr() < .01 || p.getLookAngle().dot(incoming.normalize().scale(-1)) < .2)
      return false;
    UUID source = arrow.getOwner() == null ? arrow.getUUID() : arrow.getOwner().getUUID();
    if (!oborokage.compat.Compat.deflect(arrow, p, Math.max(.5, incoming.length()))) return false;
    arrow.addTag("oborokage_reflected");
    arrow.setPos(p.getEyePosition().add(p.getLookAngle().scale(.65)));
    rewardParry(s, source);
    Feats.parry(p);
    send(s, ActionEvent.PARRY, p.getEyePosition());
    return true;
  }

  private static void reject(State s, int sequence) {
    if (s.p.connection != null && ServerPlayNetworking.canSend(s.p, ActionEvent.TYPE)) {
      var e = event(s, ActionEvent.REJECT, s.p.position(), s.move == null ? 0 : s.move.id());
      ServerPlayNetworking.send(
          s.p,
          new ActionEvent(
              e.actor(),
              e.actionId(),
              e.startTick(),
              e.serverTick(),
              e.move(),
              e.kind(),
              e.origin(),
              e.yaw(),
              e.intent(),
              e.stamina(),
              sequence));
    }
  }

  /** Current action for a newly tracking viewer, or null when this player has no dual state. */
  public static ActionEvent snapshot(ServerPlayer p) {
    State s = states.get(p.getUUID());
    if (s == null) return null;
    return event(
        s,
        ActionEvent.START,
        p.position(),
        s.move == null ? s.guarding ? Move.GUARD : 0 : s.move.id());
  }

  private static ActionEvent event(State s, int kind, Vec3 at, int move) {
    long now = s.level.getGameTime();
    return new ActionEvent(
        s.p.getId(),
        s.actionId,
        move == Move.GUARD ? s.guardStart : s.start,
        now,
        move,
        kind,
        at,
        move == Move.GUARD ? s.p.getYRot() : s.yaw,
        s.intent,
        s.stamina,
        s.actionSequence);
  }

  static void send(State s, int kind, Vec3 at) {
    send(s, kind, at, s.move == null ? 0 : s.move.id());
  }

  private static void send(State s, int kind, Vec3 at, int move) {
    var receivers = new HashSet<ServerPlayer>(PlayerLookup.tracking(s.p));
    receivers.add(s.p);
    for (var p : receivers)
      if (p.connection != null && ServerPlayNetworking.canSend(p, ActionEvent.TYPE))
        ServerPlayNetworking.send(p, event(s, kind, at, move));
  }
}

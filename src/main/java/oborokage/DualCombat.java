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
      stopMotion(s);
      releaseGuard(s);
      s.move = null;
      s.buffered = false;
      s.guarding = false;
      s.nextCombo = Move.MAIN;
      s.chainHands = 0;
      send(s, ActionEvent.CANCEL, p.position());
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
    if (!equipped(p)
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
          if (s.move.id() != Move.ULTIMATE && s.move.id() != Move.DODGE) {
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
          begin(s, Move.ULTIMATE);
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
    s.main = s.p.getMainHandItem();
    s.off = s.p.getOffhandItem();
    Vec3 forward = forward(s.yaw);
    if (id == Move.DODGE) {
      s.chainHands = 0;
      s.chainId = ++nextAction;
      var input = s.p.getLastClientInput();
      double z = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0),
          x = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
      s.direction = forward.scale(z).add(forward.cross(new Vec3(0, 1, 0)).scale(x));
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
      if (!equipped(p)
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
      if (s.intent > 0 && now - s.lastUsefulHit > 160 && now - s.lastDecay >= 20) {
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
        for (int i = 0; i < s.move.hits().length; i++) if (age == s.move.hits()[i]) hit(s, i);
        if (age >= s.move.length()) {
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
    boolean execute = m.id() == Move.ULTIMATE && window == 2;
    int hand = m.hands()[window];
    ItemStack blade = hand == 0 ? s.main : s.off;
    float reach = m.id() == Move.ULTIMATE ? (float) ServerConfig.get().ultimateReach : m.reach();
    float baseDamage = WeaponDamage.scaled(s.p, blade, m.damage());
    int targets = 0;
    s.p.swing(
        hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
        net.minecraft.world.item.component.SwingAnimation.DEFAULT,
        true);
    s.level.playSound(
        null,
        s.p.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.PLAYERS,
        .75f,
        m.id() == Move.ULTIMATE ? .65f : hand == 1 ? 1.4f : 1f);
    for (LivingEntity target :
        s.level.getEntitiesOfClass(
            LivingEntity.class,
            s.p.getBoundingBox().inflate(reach, 2.0, reach),
            t -> allowed(s.p, t))) {
      if (targets >= ServerConfig.get().maxTargetsPerWindow) break;
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
      float before = target.getHealth() + target.getAbsorptionAmount();
      Pressure pressure = s.pressure.get(target.getUUID());
      boolean broken = pressure != null && pressure.brokenUntil >= now;
      target.damageCooldownTime = 0;
      if (execute) LegacyIaido.strikeTarget(s.level, s.p, target, 1);
      else {
        WeaponDamage.apply(
            s.p,
            target,
            blade,
            baseDamage * (broken ? 1.3f : 1),
            m.id() == Move.CROSS || m.id() == Move.ULTIMATE);
      }
      boolean useful =
          before > target.getHealth() + target.getAbsorptionAmount() || !target.isAlive();
      if (!useful) continue;
      if (Feats.enemy(target)) {
        s.chainHands |= hand == 0 ? 1 : 2;
        Feats.hit(s.p, hand);
        Long previous = s.rewarded.put(target.getUUID(), now);
        if (previous == null || now - previous >= 8) {
          s.intent =
              Math.min(
                  100,
                  s.intent
                      + (int)
                          Math.round(
                              ServerConfig.get().hitIntent
                                  * ServerConfig.get().intentGainMultiplier));
          s.lastUsefulHit = now;
        }
        int n = pressure == null || pressure.until < now ? 1 : pressure.count + 1;
        if ((n >= 3 || m.id() == Move.ULTIMATE) && !broken) {
          s.pressure.put(
              target.getUUID(),
              new Pressure(0, now + 80, now + ServerConfig.get().postureDurationTicks));
          if (target.getMaxHealth() < 100)
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 2));
        } else if (!broken) s.pressure.put(target.getUUID(), new Pressure(n, now + 80, 0));
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
    blade.hurtAndBreak(
        execute ? 8 : m.id() == Move.ULTIMATE ? 2 : 1,
        s.p,
        hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
  }

  public static boolean allowed(ServerPlayer p, LivingEntity e) {
    return TargetRules.allowed(p, e);
  }

  private static Vec3 forward(float yaw) {
    return new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
  }

  private static boolean guardable(State s, DamageSource source) {
    if (!s.guarding || !equipped(s.p) || s.p.isUsingItem() || s.move != null) return false;
    if (!source.is(DamageTypes.MOB_ATTACK)
        && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
        && !source.is(DamageTypes.PLAYER_ATTACK)) return false;
    if (!(source.getEntity() instanceof LivingEntity attacker)
        || source.getDirectEntity() != attacker) return false;
    Vec3 v = attacker.position().subtract(s.p.position());
    v = new Vec3(v.x, 0, v.z).normalize();
    return forward(s.p.getYRot()).dot(v) > .2;
  }

  public static boolean allowDamage(LivingEntity e, DamageSource source, float amount) {
    if (!(e instanceof ServerPlayer p) || amount <= 0) return true;
    if (p.isInvulnerableTo((ServerLevel) p.level(), source)
        || p.damageCooldownTime > 10
            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)
            && amount <= ((oborokage.mixin.LivingDamageAccess) p).oborokage$lastHurt()) return true;
    State s = states.get(p.getUUID());
    if (s == null || !guardable(s, source)) return true;
    long now = p.level().getGameTime();
    if (now - s.guardStart >= guardWindow(p) || s.stamina < ServerConfig.get().perfectGuardCost)
      return true;
    var attacker = source.getEntity();
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
    boolean equip = equipped(s.p);
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
      s.intent =
          Math.min(
              100,
              s.intent
                  + (int)
                      Math.round(
                          ServerConfig.get().parryIntent
                              * ServerConfig.get().intentGainMultiplier));
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
    if (!arrow.deflect(
        net.minecraft.world.entity.projectile.ProjectileDeflection.AIM_DEFLECT,
        p,
        EntityReference.of(p),
        true,
        Math.max(.5, incoming.length()))) return false;
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

  public static ActionEvent snapshot(ServerPlayer p) {
    State s = state(p);
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

  private static void send(State s, int kind, Vec3 at) {
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

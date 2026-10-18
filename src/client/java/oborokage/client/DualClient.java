package oborokage.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.*;
import oborokage.*;

public final class DualClient {
  public static final Map<Integer, Scene> scenes = new HashMap<>();
  public static int intent, stamina = 100;
  private static int sequence, nextCombo = Move.MAIN, pendingAttack = -1;
  private static long comboUntil, stepUntil, ultimateUntil, counterUntil;
  private static boolean guided, guidedSolo;
  private static Object level;
  private static boolean guardHeld, guardPredictionRejected;
  private static final Set<Integer> guardRequests = new HashSet<>();
  private static int heartbeat;
  private static int comboCount;
  private static long comboShownUntil, riposteShownUntil;
  private static KeyMapping guard, step, ultimate;

  public static final class Scene {
    public ActionEvent event;
    public float age;
    public boolean predicted;
    float stopAt = -99, stopLen;
    // Per-cut heading and pitch the light is drawn with (see SlashFx).
    float[] cutYaw, cutPitch;

    Scene(ActionEvent e, float age) {
      event = e;
      this.age = age;
    }

    /**
     * Visual time of the scene. After a landed cut the picture holds still for a beat (hitstop) and
     * then runs a little fast until it is back on time; the logical age never waits, so the chain
     * stays in step with the server.
     */
    public float time(float partial) {
      float t = age + partial, d = t - stopAt;
      if (d < 0 || stopLen <= 0) return t;
      float lag = d < stopLen ? d : Math.max(0, stopLen - (d - stopLen) / 2);
      return t - lag;
    }

    void hitstop(float at, float length) {
      if (age + 1 - stopAt < 4) return;
      stopAt = at;
      stopLen = length;
    }
  }

  public static void technique(TechniqueEvent e) {
    var c = Minecraft.getInstance();
    if (c.level == null) return;
    int move = Move.STORM + e.style();
    long now = c.level.getGameTime();
    float yaw =
        (float) (Math.toDegrees(Math.atan2(-(e.end().x - e.origin().x), e.end().z - e.origin().z)));
    scenes.put(
        e.actor(),
        new Scene(
            new ActionEvent(
                e.actor(), -1, now, now, move, ActionEvent.START, e.origin(), yaw, 0, 100, -1),
            0));
  }

  /** Ultimates open and close with the blade in its scabbard. */
  public static boolean sheathedPhase(Scene s) {
    int m = s.event.move();
    if (m == Move.ULTIMATE) return s.age < 7 || s.age >= 34;
    return Move.ultimate(m) && (s.age < 4 || s.age >= Move.of(m).length() - 8);
  }

  public static Scene scene(int actor) {
    return scenes.get(actor);
  }

  public static void init() {
    var cat = KeyMapping.Category.register(Oborokage.id("combat"));
    guard =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.guard",
                oborokage.client.compat.ClientCompat.keyboard(),
                InputConstants.KEY_H,
                cat));
    step =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.step",
                oborokage.client.compat.ClientCompat.keyboard(),
                InputConstants.KEY_V,
                cat));
    ultimate =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.ultimate",
                oborokage.client.compat.ClientCompat.keyboard(),
                InputConstants.KEY_J,
                cat));
    ClientPlayNetworking.registerGlobalReceiver(
        ActionEvent.TYPE, (p, c) -> c.client().execute(() -> receive(p)));
    ClientTickEvents.END_CLIENT_TICK.register(DualClient::tick);
    LevelRenderEvents.COLLECT_SUBMITS.register(SlashFx::submit);
    net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT
        .register(
            (type, renderer, helper, context) -> {
              if (renderer
                  instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar)
                helper.register(new WaistSheaths(avatar, context.getItemModelResolver()));
            });
    HudElementRegistry.addLast(
        Oborokage.id("dual"),
        (g, d) -> {
          Minecraft c = Minecraft.getInstance();
          if (c.player == null
              || !DualCombat.holdsBlade(c.player) && !DualCombat.equipped(c.player)
              || c.player.isUsingItem()
              || Cinematics.playing()) return;
          g.pose().pushMatrix();
          int
              x =
                  Math.clamp(
                      g.guiWidth() / 2 - 60 + ClientConfig.get().hudOffsetX,
                      2,
                      Math.max(2, g.guiWidth() - (int) (124 * ClientConfig.get().hudScale))),
              y =
                  Math.clamp(
                      g.guiHeight() - 73 + ClientConfig.get().hudOffsetY,
                      14,
                      Math.max(14, g.guiHeight() - (int) (22 * ClientConfig.get().hudScale)));
          g.pose().translate(x, y);
          g.pose().scale((float) ClientConfig.get().hudScale);
          x = 0;
          y = 0;
          g.fill(x - 2, y - 2, x + 122, y + 19, 0xa6100e1e);
          g.fill(x, y, x + intent * 120 / 100, y + 3, 0xffd8c5ff);
          g.fill(x, y + 6, x + stamina * 120 / 100, y + 8, 0xff8bd4e5);
          g.text(
              c.font,
              Component.translatable("ui.oborokage.intent")
                  .append(" " + intent + " / ")
                  .append(Component.translatable("ui.oborokage.stamina"))
                  .append(" " + stamina),
              x,
              y + 10,
              0xffe2d6ef);
          // Guard and step need both blades; a single blade only adds the ultimate key.
          String controls =
              DualCombat.equipped(c.player)
                  ? guard.getTranslatedKeyMessage().getString()
                      + " · "
                      + step.getTranslatedKeyMessage().getString()
                      + " · "
                      + ultimate.getTranslatedKeyMessage().getString()
                  : ultimate.getTranslatedKeyMessage().getString();
          g.text(c.font, controls, x, y - 12, 0xffb7a6d5);
          long now = c.level == null ? 0 : c.level.getGameTime();
          if (comboCount >= 2 && now <= comboShownUntil) {
            int fade = (int) (255 * Math.min(1, (comboShownUntil - now) / 20f));
            g.text(
                c.font,
                Component.translatable("ui.oborokage.combo", comboCount),
                x + 126,
                y + 1,
                (Math.max(24, fade) << 24) | 0xf2e5ff);
          }
          if (now <= riposteShownUntil)
            g.text(
                c.font,
                Component.translatable("ui.oborokage.riposte"),
                x + 126,
                y + 10,
                0xffffd88a);
          g.pose().popMatrix();
        });
  }

  public static void request(int action) {
    request(action, true);
  }

  private static void request(int action, boolean freshPress) {
    if (!ClientPlayNetworking.canSend(ActionRequest.TYPE)) return;
    int seq = ++sequence;
    if (action == 1) {
      if (freshPress) {
        guardPredictionRejected = false;
        guardRequests.clear();
      }
      guardRequests.add(seq);
      guardRequests.removeIf(id -> id < seq - 64);
    }
    var c = Minecraft.getInstance();
    if (ClientConfig.get().localPrediction
        && c.player != null
        && c.level != null
        && (action == 4 || action == 0
            ? DualCombat.holdsBlade(c.player)
            : DualCombat.equipped(c.player))
        && !c.player.isUsingItem()) {
      Scene old = scenes.get(c.player.getId());
      long now = c.level.getGameTime();
      if (action == 2 && old != null && old.event.move() == Move.GUARD)
        scenes.remove(c.player.getId());
      int move =
          switch (action) {
            case 0 -> {
              boolean dual = DualCombat.equipped(c.player);
              if (dual && now < counterUntil) yield Move.COUNTER;
              yield now > comboUntil
                  ? dual ? Move.MAIN : Move.SOLO_1
                  : Move.follow(nextCombo, dual);
            }
            case 1 -> guardPredictionRejected ? 0 : Move.GUARD;
            case 3 -> now >= stepUntil ? Move.DODGE : 0;
            case 4 ->
                intent >= 100 && now >= ultimateUntil
                    ? BladeStyle.of(c.player.getMainHandItem()).ultimate
                    : 0;
            default -> 0;
          };
      if (action == 0
          && old != null
          && old.event.move() != Move.GUARD
          && !Move.ultimate(old.event.move())
          && old.event.move() != Move.DODGE) pendingAttack = seq;
      else if (move != 0 && (old == null || action == 0 && old.event.move() == Move.GUARD)) {
        predict(c, move, seq);
        if (move == Move.DODGE) stepUntil = now + 16;
        if (Move.ultimate(move)) ultimateUntil = now + 100;
      }
    }
    ClientPlayNetworking.send(new ActionRequest(seq, action));
  }

  private static void predict(Minecraft c, int move, int seq) {
    long now = c.level.getGameTime();
    var event =
        new ActionEvent(
            c.player.getId(),
            -seq,
            now,
            now,
            move,
            ActionEvent.START,
            c.player.position(),
            c.player.getYRot(),
            intent,
            stamina,
            seq);
    Scene scene = new Scene(event, 0);
    scene.predicted = true;
    scenes.put(c.player.getId(), scene);
    if (Move.ultimate(move)) Cinematics.begin(move, event.yaw(), 0);
  }

  private static boolean hasTargetNearby(Minecraft c) {
    if (c.level == null || c.player == null) return false;
    var eye = c.player.getEyePosition();
    var look = c.player.getLookAngle();
    for (var entity :
        c.level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            c.player.getBoundingBox().inflate(3.8, 2.0, 3.8),
            e -> e != c.player && e.isAlive() && !e.isSpectator() && Feats.enemy(e))) {
      Vec3 d = entity.getBoundingBox().getCenter().subtract(eye);
      if (d.lengthSqr() <= 3.8 * 3.8 && (d.lengthSqr() < 1.0 || d.normalize().dot(look) > 0.1)) {
        return true;
      }
    }
    return false;
  }

  public static boolean interceptAttack() {
    var c = Minecraft.getInstance();
    if (c.player == null
        || oborokage.client.compat.ClientCompat.screenOpen(c)
        || !DualCombat.chainsReady(c.player)
        || !ClientPlayNetworking.canSend(ActionRequest.TYPE)) return false;
    // Non-living entities (end crystals, item frames, boats) keep the ordinary attack.
    if (c.hitResult instanceof EntityHitResult entityHit
        && !(entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity))
      return false;
    if (c.hitResult != null && c.hitResult.getType() == HitResult.Type.BLOCK) {
      if (c.player.isShiftKeyDown() || !hasTargetNearby(c)) return false;
    }
    request(0);
    return true;
  }

  private static void receive(ActionEvent e) {
    var c = Minecraft.getInstance();
    if (c.level == null || c.player == null) return;
    boolean local = e.actor() == c.player.getId();
    if (local) {
      intent = e.intent();
      stamina = e.stamina();
    }
    var previous = scenes.get(e.actor());
    if (e.kind() < 0 || e.kind() > ActionEvent.EVADE || !Move.known(e.move())) return;
    if (e.kind() == ActionEvent.EVADE) {
      if (local) riposteShownUntil = c.level.getGameTime() + 26;
      for (int i = 0; i < 16; i++) {
        double a = i * Math.PI * 2 / 16;
        c.level.addParticle(
            new DustParticleOptions(i % 2 == 0 ? 0xe8e2ff : 0x8f7ad0, .7f),
            e.origin().x + Math.cos(a) * .55,
            e.origin().y - .4 + (i % 4) * .3,
            e.origin().z + Math.sin(a) * .55,
            0,
            .01,
            0);
      }
      return;
    }
    if (e.kind() == ActionEvent.REJECT) {
      if (local && guardRequests.remove(e.sequence())) guardPredictionRejected = true;
      if (local && pendingAttack == e.sequence()) pendingAttack = -1;
      if (local
          && previous != null
          && previous.predicted
          && previous.event.sequence() == e.sequence()) {
        scenes.remove(e.actor());
        OborokageClient.cancelPresentation(e.actor());
      }
      return;
    }
    if (e.kind() == ActionEvent.START
        && previous != null
        && previous.predicted
        && previous.event.sequence() == e.sequence()
        && previous.event.move() == e.move()) {
      previous.event = e;
      previous.predicted = false;
      previous.age = Math.min(previous.age, Move.of(e.move()).length());
      return;
    }
    if (e.kind() == ActionEvent.STATUS) {
      if (previous != null
          && !previous.predicted
          && previous.event.move() == Move.GUARD
          && e.move() != Move.GUARD) scenes.remove(e.actor());
      return;
    }
    if (e.kind() == ActionEvent.CANCEL) {
      if (previous != null
          && (!previous.predicted || previous.event.sequence() == e.sequence())
          && e.actionId() >= previous.event.actionId()) {
        scenes.remove(e.actor());
        if (local && Move.ultimate(previous.event.move()))
          OborokageClient.cancelPresentation(e.actor());
      }
      return;
    }
    if (e.kind() == ActionEvent.IMPACT || e.kind() == ActionEvent.PARRY) {
      if (local && e.kind() == ActionEvent.PARRY) counterUntil = c.level.getGameTime() + 12;
      if (e.kind() == ActionEvent.IMPACT) {
        UltimateFx.impact(e.actor(), e.origin());
        boolean big = previous != null && Move.finisher(previous.event.move());
        SlashFx.impact(e.actor(), e.origin(), big);
        // Other players' cuts hold on contact here; the local one already did, on prediction.
        if (!local && previous != null && !Move.ultimate(previous.event.move()))
          previous.hitstop(previous.age, big ? 2 : 1.2f);
      }
      if (local && e.kind() == ActionEvent.IMPACT) {
        long now = c.level.getGameTime();
        comboCount = now <= comboShownUntil ? comboCount + 1 : 1;
        comboShownUntil = now + 60;
      }
      var actorEntity = c.level.getEntity(e.actor());
      var hitStyle =
          actorEntity instanceof net.minecraft.world.entity.player.Player pl
              ? BladeStyle.of(pl.getMainHandItem())
              : null;
      ParticleOptions burst =
          e.kind() == ActionEvent.PARRY || hitStyle == null
              ? ParticleTypes.ELECTRIC_SPARK
              : switch (hitStyle) {
                case RAIKIRI -> Oborokage.SPARK;
                case FROSTMOON -> Oborokage.SHARD;
                case VOIDSTRING -> Oborokage.MOTE;
                case WAKIZASHI -> Oborokage.PETAL;
                default -> ParticleTypes.ELECTRIC_SPARK;
              };
      for (int i = 0; i < (e.kind() == ActionEvent.PARRY ? 12 : 7); i++) {
        double a = i * Math.PI * 2 / 18;
        c.level.addParticle(
            burst,
            e.origin().x + Math.cos(a) * .16,
            e.origin().y,
            e.origin().z + Math.sin(a) * .16,
            Math.cos(a) * .04,
            .015,
            Math.sin(a) * .04);
      }
      return;
    }
    if (e.move() == 0
        || previous != null && !previous.predicted && e.actionId() < previous.event.actionId())
      return;
    OborokageClient.cancelPresentation(e.actor());
    long elapsed = c.level.getGameTime() - e.startTick();
    if (elapsed < 0 || elapsed > 80) elapsed = e.serverTick() - e.startTick();
    scenes.put(
        e.actor(),
        new Scene(
            e, Math.clamp(elapsed, 0, e.move() == Move.GUARD ? 600 : Move.of(e.move()).length())));
    if (local && Move.ultimate(e.move()))
      Cinematics.begin(
          e.move(), e.yaw(), (int) Math.clamp(elapsed, 0, Move.of(e.move()).length() - 1));
  }

  private static void tick(Minecraft c) {
    if (level != c.level) {
      scenes.clear();
      guardHeld = false;
      guardPredictionRejected = false;
      guardRequests.clear();
      intent = 0;
      stamina = 100;
      level = c.level;
      pendingAttack = -1;
      nextCombo = Move.MAIN;
      comboUntil = stepUntil = ultimateUntil = counterUntil = 0;
      comboCount = 0;
      comboShownUntil = riposteShownUntil = 0;
    }
    if (c.player == null || c.level == null) return;
    // A single blade has its own chain since 0.6.0; say so once, the first time one is drawn.
    if (!guidedSolo && DualCombat.holdsBlade(c.player) && !DualCombat.equipped(c.player)) {
      guidedSolo = true;
      if (ClientConfig.get().tutorial)
        c.player.sendSystemMessage(
            Component.translatable("ui.oborokage.guide_solo", ultimate.getTranslatedKeyMessage()));
    }
    if (!guided && DualCombat.equipped(c.player)) {
      guided = true;
      if (ClientConfig.get().tutorial)
        c.player.sendSystemMessage(
            Component.translatable(
                "ui.oborokage.guide",
                guard.getTranslatedKeyMessage(),
                step.getTranslatedKeyMessage(),
                ultimate.getTranslatedKeyMessage()));
      if (ClientConfig.get().keyConflictWarning)
        for (KeyMapping ours : new KeyMapping[] {guard, step, ultimate})
          if (!ours.isUnbound())
            for (KeyMapping other : c.options.keyMappings)
              // Debug keys only act while F3 is held, so sharing a letter with them is no conflict.
              if (other != ours
                  && other.getCategory() != KeyMapping.Category.DEBUG
                  && ours.same(other)) {
                c.player.sendSystemMessage(
                    Component.translatable(
                        "ui.oborokage.key_conflict",
                        Component.translatable(ours.getName()),
                        Component.translatable(other.getName())));
                break;
              }
    }
    boolean pressed =
        !oborokage.client.compat.ClientCompat.screenOpen(c)
            && DualCombat.equipped(c.player)
            && guard.isDown();
    if (pressed != guardHeld) {
      request(pressed ? 1 : 2);
      guardHeld = pressed;
      heartbeat = 0;
    }
    if (pressed && ++heartbeat >= 20) {
      // Keep server retries alive after rejection, without repeatedly flashing a local pose.
      request(1, false);
      heartbeat = 0;
    }
    while (step.consumeClick())
      if (!oborokage.client.compat.ClientCompat.screenOpen(c) && DualCombat.equipped(c.player))
        request(3);
    while (ultimate.consumeClick())
      if (!oborokage.client.compat.ClientCompat.screenOpen(c) && DualCombat.holdsBlade(c.player))
        request(4);
    int predictedNext = 0, predictedSequence = -1;
    var it = scenes.values().iterator();
    while (it.hasNext()) {
      var scene = it.next();
      var entity = c.level.getEntity(scene.event.actor());
      if (entity == null || !entity.isAlive()) {
        it.remove();
        continue;
      }
      boolean local = scene.event.actor() == c.player.getId();
      int sm = scene.event.move();
      boolean held =
          Move.ultimate(sm) || Move.solo(sm)
              ? DualCombat.holdsBlade(c.player)
              : sm >= Move.STORM && sm <= Move.TERRAIN
                  ? Oborokage.mainBlade(c.player.getMainHandItem())
                  : DualCombat.equipped(c.player);
      if (local && (!held || c.player.isUsingItem())) {
        it.remove();
        pendingAttack = -1;
        OborokageClient.cancelPresentation(scene.event.actor());
        continue;
      }
      if (scene.event.move() == Move.GUARD) {
        scene.age = Math.min(3, scene.age + 1);
        continue;
      }
      float before = scene.age;
      scene.age++;
      if (local) contact(c, scene, before);
      if (!scene.predicted) {
        long elapsed = c.level.getGameTime() - scene.event.startTick();
        if (elapsed >= 0 && elapsed < 80) {
          float gap = elapsed - scene.age;
          if (Math.abs(gap) > 1) scene.age += Math.clamp(gap, -.25f, .25f);
        }
      }
      if (scene.age > Move.of(scene.event.move()).length()) {
        if (local) {
          int id = scene.event.move();
          nextCombo = Move.next(id);
          comboUntil = c.level.getGameTime() + 20;
          if (pendingAttack >= 0) {
            predictedNext = Move.follow(nextCombo, DualCombat.equipped(c.player));
            predictedSequence = pendingAttack;
            pendingAttack = -1;
          }
        }
        it.remove();
      }
    }
    if (predictedNext != 0) predict(c, predictedNext, predictedSequence);
    SlashFx.tick(c);
  }

  /**
   * The local cut meets something: hold the picture, kick the camera and, on a closing cut, flash
   * the screen edge in the blade's colour. Judged on the client at the moment the edge passes, so
   * the feedback does not wait for the server's round trip.
   */
  private static void contact(Minecraft c, Scene scene, float before) {
    int id = scene.event.move();
    if (id == Move.GUARD || id == Move.DODGE || Move.ultimate(id)) return;
    Move m = Move.of(id);
    for (int w = 0; w < m.hits().length; w++) {
      int hit = m.hits()[w];
      if (!(before < hit && scene.age >= hit) || !struckSomething(c, m)) continue;
      boolean big = Move.finisher(id) && w == m.hits().length - 1;
      scene.hitstop(hit, big ? 2.2f : 1.3f);
      var style = BladeStyle.of(c.player.getMainHandItem());
      int rgb = style == null ? 0xbca6f4 : style.primary;
      Cinematics.kick(big ? 1.5f : .7f, w % 2 == 0 ? 1 : -1, big ? rgb : 0);
    }
  }

  /** Same cone as the server's plain cut: along the current line of sight, up and down too. */
  private static boolean struckSomething(Minecraft c, Move m) {
    Vec3 eye = c.player.getEyePosition();
    Vec3 look = c.player.getLookAngle();
    double reach = m.reach() + .6, cos = Math.cos(Math.toRadians(Math.min(359, m.arc()) / 2));
    for (var e :
        c.level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            c.player.getBoundingBox().inflate(reach),
            e -> e != c.player && e.isAlive() && !e.isSpectator())) {
      Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
      if (d.lengthSqr() > reach * reach) continue;
      if (d.lengthSqr() < .2 || m.arc() >= 359 || d.normalize().dot(look) >= cos) return true;
    }
    return false;
  }

  public static float cut(Scene s, float partial, int hand) {
    float t = s.time(partial);
    int move = s.event.move();
    if (move == Move.GUARD) return 0;
    if (move == Move.DODGE) return (float) Math.sin(Math.min(1, t / 6) * Math.PI) * .35f;
    int[] hits = Move.of(move).hits();
    int[] hands = Move.of(move).hands();
    float best = 0;
    for (int i = 0; i < hits.length; i++)
      if (hands[i] == hand || move == Move.ULTIMATE && i == 2) {
        float a = (t - (hits[i] - 3)) / 5;
        if (a >= 0 && a <= 1.8)
          best = Math.max(best, a <= 1 ? (float) (a * a * (3 - 2 * a)) : (1 - (a - 1) / .8f));
      }
    return Math.clamp(best, 0, 1);
  }

  public static boolean pose(int id, HumanoidModel<?> model, float partial, HumanoidArm arm) {
    Scene s = scene(id);
    if (s == null) return false;
    var main = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    var off = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
    float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
    float a = cut(s, partial, 0), b = cut(s, partial, 1);
    int move = s.event.move();
    if (move == Move.GUARD) {
      main.xRot = -1.13f;
      main.yRot = -.62f * sign;
      main.zRot = -.48f * sign;
      off.xRot = -1.13f;
      off.yRot = .62f * sign;
      off.zRot = .48f * sign;
      model.body.xRot = .1f;
      return true;
    }
    if (Move.solo(move)) {
      SoloPose.thirdPerson(model, main, off, sign, move, s.time(partial));
      return true;
    }
    if (Move.ultimate(move) && move != Move.ULTIMATE) {
      ultimatePose(model, main, off, sign, move, s.age + partial);
      return true;
    }
    if (move >= Move.STORM && move <= Move.TERRAIN) {
      singleBladePose(model, main, off, sign, move, a);
      return true;
    }
    main.xRot = -.9f + .72f * a;
    main.yRot = sign * (-1.35f + 2.75f * a);
    main.zRot = sign * (-.35f + .45f * a);
    off.xRot = -.78f + .68f * b;
    off.yRot = sign * (1.35f - 2.8f * b);
    off.zRot = sign * (.45f - .5f * b);
    if (move != Move.ULTIMATE) {
      // Plain cuts follow the eyes: both arms lift or drop with the head.
      float aim = model.head.xRot * .6f;
      main.xRot += aim;
      off.xRot += aim;
    }
    model.body.xRot = .10f;
    model.body.yRot = sign * (-.2f + .48f * a - .4f * b);
    if (move == Move.ULTIMATE && s.age > 18 && s.age < 28)
      model.body.yRot += sign * (float) Math.sin((s.age - 18 + partial) * Math.PI / 10) * 1.0f;
    model.rightLeg.xRot = -.22f;
    model.leftLeg.xRot = .27f;
    model.rightLeg.zRot = .07f;
    model.leftLeg.zRot = -.07f;
    return true;
  }

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  /** A quick strike envelope: 0 before `at`, peaks 1.5 ticks later, gone by 5. */
  private static float strike(float t, float at) {
    float d = t - at + 1.5f;
    return d < 0 || d > 5 ? 0 : d < 1.5f ? ease(d / 1.5f) : 1 - ease((d - 1.5f) / 3.5f);
  }

  private static void ultimatePose(
      HumanoidModel<?> model,
      net.minecraft.client.model.geom.ModelPart main,
      net.minecraft.client.model.geom.ModelPart off,
      float sign,
      int move,
      float t) {
    model.rightLeg.xRot = -.3f;
    model.leftLeg.xRot = .34f;
    model.rightLeg.zRot = .1f;
    model.leftLeg.zRot = -.1f;
    switch (move) {
      case Move.ULT_MOON -> {
        // Whirling cuts that swing wider each beat, then the blade rises and blooms down.
        float beat = t >= 8 && t < 24 ? (float) Math.sin((t - 8) * Math.PI / 2) : 0;
        float rise = ease((t - 26) / 4) * (1 - ease((t - 32) / 2));
        main.xRot = -1.35f - 1.5f * rise + .9f * ease((t - 32) / 2) * (t < 40 ? 1 : 0);
        main.yRot = sign * 1.45f * beat;
        main.zRot = sign * -.3f * beat;
        off.xRot = -.9f + .4f * beat;
        off.yRot = -sign * .9f * beat;
        model.body.yRot = sign * .7f * beat;
        model.body.xRot = .12f;
      }
      case Move.ULT_STORM -> {
        // Leaning lunges: the blade waits low behind and snaps forward at every blink.
        float hit = 0;
        for (int k = 0; k < 6; k++) hit = Math.max(hit, strike(t, 8 + 5 * k));
        float raise = ease((t - 38) / 5) * (1 - ease((t - 44) / 2));
        float slam = ease((t - 44) / 2) * (1 - ease((t - 52) / 4));
        main.xRot = .55f - 2.2f * hit - 3.4f * raise - .2f * slam;
        main.yRot = sign * (.35f - .5f * hit);
        main.zRot = 0;
        off.xRot = .7f - .5f * hit;
        off.yRot = -sign * .2f;
        model.body.xRot = .32f - .2f * raise + .25f * slam;
        model.body.yRot = sign * -.25f * hit;
      }
      case Move.ULT_FROST -> {
        // The blade is raised to the sky, held while the lake freezes, then spun into the shatter.
        float up = ease(t / 10) * (1 - ease((t - 35) / 2));
        float spin = t >= 35 && t < 41 ? (float) Math.sin((t - 35) / 6 * Math.PI) : 0;
        main.xRot = -3.05f * up - 1.4f * spin - .3f;
        main.yRot = sign * (1.6f * spin - .1f);
        main.zRot = sign * -.15f * up;
        off.xRot = -1.0f * up - .3f;
        off.yRot = -sign * .3f * up;
        off.zRot = -sign * .5f * up;
        model.body.yRot = sign * 1.0f * spin;
        model.body.xRot = -.05f * up;
      }
      case Move.ULT_VOID -> {
        // Drawn back like a bowstring, the free hand sighting down the line, then one downstroke.
        float draw = ease(t / 24) * (1 - ease((t - 27) / 1.5f));
        float cut = ease((t - 27) / 2.5f) * (1 - ease((t - 48) / 8));
        main.xRot = -2.3f * draw - .4f * cut - (t >= 27 && t < 29 ? 2.6f * (1 - (t - 27) / 2) : 0);
        main.yRot = sign * (.95f * draw - .1f * cut);
        main.zRot = sign * .2f * draw;
        off.xRot = -1.55f * draw - .3f;
        off.yRot = -sign * .25f * draw;
        model.body.yRot = sign * (-.55f * draw + .2f * cut);
        model.body.xRot = .05f + .22f * cut;
      }
      default -> {}
    }
  }

  private static void singleBladePose(
      HumanoidModel<?> model,
      net.minecraft.client.model.geom.ModelPart main,
      net.minecraft.client.model.geom.ModelPart off,
      float sign,
      int move,
      float cut) {
    // The free arm balances the body instead of holding an imaginary second sword.
    off.xRot = -.12f + .16f * cut;
    off.yRot = 0;
    off.zRot = sign * (.10f + .12f * cut);
    main.xRot = -.95f + .65f * cut;
    main.yRot = sign * (-1.15f + 2.6f * cut);
    main.zRot = sign * (-.42f + .85f * cut);
    model.body.xRot = .08f;
    model.body.yRot = sign * (-.25f + .65f * cut);
    if (move == Move.FROST) {
      main.xRot = -1.3f + .2f * cut;
      main.zRot = sign * (-.8f + .2f * cut);
      model.body.yRot = sign * (-.5f + 1.1f * cut);
    } else if (move == Move.VOID) {
      main.xRot = -1.55f + .25f * cut;
      main.yRot = sign * (-.8f + .95f * cut);
      model.body.xRot = .12f + .1f * cut;
    } else if (move == Move.TERRAIN) {
      main.xRot = -2.15f + 2.4f * cut;
      main.yRot = sign * (-.45f + .8f * cut);
      model.body.xRot = .12f + .18f * cut;
    }
    model.rightLeg.xRot = -.22f;
    model.leftLeg.xRot = .27f;
    model.rightLeg.zRot = .07f;
    model.leftLeg.zRot = -.07f;
  }

  public static void itemPose(PoseStack pose, InteractionHand hand, float partial) {
    var c = Minecraft.getInstance();
    if (c.player == null) return;
    Scene s = scene(c.player.getId());
    if (s == null) return;
    float side = hand == InteractionHand.MAIN_HAND ? 1 : -1;
    if (c.player.getMainArm() == HumanoidArm.LEFT) side = -side;
    if (Move.solo(s.event.move())) {
      if (hand == InteractionHand.MAIN_HAND)
        SoloPose.firstPerson(pose, side, s.event.move(), s.time(partial));
      return;
    }
    float q = cut(s, partial, hand == InteractionHand.MAIN_HAND ? 0 : 1);
    if (s.event.move() == Move.GUARD) {
      pose.translate(-side * .15, .25, -.18);
      oborokage.client.compat.ClientCompat.rotate(pose, Axis.ZP, -side * 38);
      return;
    }
    pose.translate(-side * .36 * q, .12 * q, -.20 * q);
    oborokage.client.compat.ClientCompat.rotate(pose, Axis.ZP, -side * 75 * q);
    oborokage.client.compat.ClientCompat.rotate(pose, Axis.YP, -side * 24 * q);
  }
}

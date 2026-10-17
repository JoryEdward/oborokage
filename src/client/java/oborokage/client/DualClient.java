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
  private static boolean guided;
  private static Object level;
  private static boolean guardHeld, guardPredictionRejected;
  private static final Set<Integer> guardRequests = new HashSet<>();
  private static int heartbeat;
  private static KeyMapping guard, step, ultimate;

  public static final class Scene {
    public ActionEvent event;
    public float age;
    public boolean predicted;

    Scene(ActionEvent e, float age) {
      event = e;
      this.age = age;
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

  public static Scene scene(int actor) {
    return scenes.get(actor);
  }

  public static void init() {
    var cat = KeyMapping.Category.register(Oborokage.id("combat"));
    guard =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.guard", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, cat));
    step =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.step", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, cat));
    ultimate =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.oborokage.ultimate", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, cat));
    ClientPlayNetworking.registerGlobalReceiver(
        ActionEvent.TYPE, (p, c) -> c.client().execute(() -> receive(p)));
    ClientTickEvents.END_CLIENT_TICK.register(DualClient::tick);
    LevelRenderEvents.COLLECT_SUBMITS.register(BladeTrails::submit);
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
              || !DualCombat.equipped(c.player)
              || c.player.isUsingItem()
              || OborokageClient.localFull && OborokageClient.localAge >= 0) return;
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
          String controls =
              guard.getTranslatedKeyMessage().getString()
                  + " · "
                  + step.getTranslatedKeyMessage().getString()
                  + " · "
                  + ultimate.getTranslatedKeyMessage().getString();
          g.text(c.font, controls, x, y - 12, 0xffb7a6d5);
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
        && DualCombat.equipped(c.player)
        && !c.player.isUsingItem()) {
      Scene old = scenes.get(c.player.getId());
      long now = c.level.getGameTime();
      if (action == 2 && old != null && old.event.move() == Move.GUARD)
        scenes.remove(c.player.getId());
      int move =
          switch (action) {
            case 0 -> now < counterUntil ? Move.COUNTER : now > comboUntil ? Move.MAIN : nextCombo;
            case 1 -> guardPredictionRejected ? 0 : Move.GUARD;
            case 3 -> now >= stepUntil ? Move.DODGE : 0;
            case 4 -> intent >= 100 && now >= ultimateUntil ? Move.ULTIMATE : 0;
            default -> 0;
          };
      if (action == 0
          && old != null
          && old.event.move() != Move.GUARD
          && old.event.move() != Move.ULTIMATE
          && old.event.move() != Move.DODGE) pendingAttack = seq;
      else if (move != 0 && (old == null || action == 0 && old.event.move() == Move.GUARD)) {
        predict(c, move, seq);
        if (move == Move.DODGE) stepUntil = now + 16;
        if (move == Move.ULTIMATE) ultimateUntil = now + 100;
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
    if (move == Move.ULTIMATE) OborokageClient.beginDualCamera(event.yaw(), 0);
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
        || c.gui.screen() != null
        || !DualCombat.equipped(c.player)
        || !ClientPlayNetworking.canSend(ActionRequest.TYPE)) return false;
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
    if (e.kind() < 0 || e.kind() > ActionEvent.REJECT || !Move.known(e.move())) return;
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
        if (local && previous.event.move() == Move.ULTIMATE)
          OborokageClient.cancelPresentation(e.actor());
      }
      return;
    }
    if (e.kind() == ActionEvent.IMPACT || e.kind() == ActionEvent.PARRY) {
      if (local && e.kind() == ActionEvent.PARRY) counterUntil = c.level.getGameTime() + 12;
      for (int i = 0; i < (e.kind() == ActionEvent.PARRY ? 12 : 7); i++) {
        double a = i * Math.PI * 2 / 18;
        c.level.addParticle(
            ParticleTypes.ELECTRIC_SPARK,
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
    if (local && e.move() == Move.ULTIMATE)
      OborokageClient.beginDualCamera(e.yaw(), (int) Math.clamp(elapsed, 0, 47));
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
    }
    if (c.player == null || c.level == null) return;
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
              if (other != ours && ours.same(other)) {
                c.player.sendSystemMessage(
                    Component.translatable(
                        "ui.oborokage.key_conflict",
                        Component.translatable(ours.getName()),
                        Component.translatable(other.getName())));
                break;
              }
    }
    boolean pressed = c.gui.screen() == null && DualCombat.equipped(c.player) && guard.isDown();
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
      if (c.gui.screen() == null && DualCombat.equipped(c.player)) request(3);
    while (ultimate.consumeClick())
      if (c.gui.screen() == null && DualCombat.equipped(c.player)) request(4);
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
      if (local
          && (!(scene.event.move() >= Move.STORM
                  ? Oborokage.mainBlade(c.player.getMainHandItem())
                  : DualCombat.equipped(c.player))
              || c.player.isUsingItem())) {
        it.remove();
        pendingAttack = -1;
        OborokageClient.cancelPresentation(scene.event.actor());
        continue;
      }
      if (scene.event.move() == Move.GUARD) {
        scene.age = Math.min(3, scene.age + 1);
        continue;
      }
      scene.age++;
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
          nextCombo =
              id == Move.MAIN || id == Move.DODGE
                  ? Move.OFF
                  : id == Move.OFF ? Move.CROSS : Move.MAIN;
          comboUntil = c.level.getGameTime() + 20;
          if (pendingAttack >= 0) {
            predictedNext = nextCombo;
            predictedSequence = pendingAttack;
            pendingAttack = -1;
          }
        }
        it.remove();
      }
    }
    if (predictedNext != 0) predict(c, predictedNext, predictedSequence);
  }

  public static float cut(Scene s, float partial, int hand) {
    float t = s.age + partial;
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
    if (move >= Move.STORM) {
      singleBladePose(model, main, off, sign, move, a);
      return true;
    }
    main.xRot = -.9f + .72f * a;
    main.yRot = sign * (-1.35f + 2.75f * a);
    main.zRot = sign * (-.35f + .45f * a);
    off.xRot = -.78f + .68f * b;
    off.yRot = sign * (1.35f - 2.8f * b);
    off.zRot = sign * (.45f - .5f * b);
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
    float q = cut(s, partial, hand == InteractionHand.MAIN_HAND ? 0 : 1);
    if (s.event.move() == Move.GUARD) {
      pose.translate(-side * .15, .25, -.18);
      pose.rotateDegrees(Axis.ZP, -side * 38);
      return;
    }
    pose.translate(-side * .36 * q, .12 * q, -.20 * q);
    pose.rotateDegrees(Axis.ZP, -side * 75 * q);
    pose.rotateDegrees(Axis.YP, -side * 24 * q);
  }
}

package oborokage.client;

import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** World-space finite blade ribbons. No camera-facing full-screen slashes. */
final class BladeTrails {
  static void submit(LevelRenderContext context) {
    var c = Minecraft.getInstance();
    if (c.level == null) return;
    var camera = context.levelState().cameraRenderState.pos;
    float partial = context.levelState().worldPartialTicks;
    int n = 0, vertices = 0;
    var cfg = ClientConfig.get();
    for (var scene : ListCopy.scenes()) {
      if (n++ >= cfg.trailMaxPlayers) break;
      var e = c.level.getEntity(scene.event.actor());
      if (e == null
          || e.position().distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance
          || scene.event.move() == Move.GUARD
          || scene.event.move() == Move.DODGE) continue;
      var move = Move.of(scene.event.move());
      int bladeColor =
          e instanceof net.minecraft.world.entity.player.Player player
                  && player.getMainHandItem().is(Oborokage.RAIKIRI)
              ? 0xf28b56
              : e instanceof net.minecraft.world.entity.player.Player player
                      && player.getMainHandItem().is(Oborokage.FROSTMOON)
                  ? 0x8cdeef
                  : e instanceof net.minecraft.world.entity.player.Player player
                          && player.getMainHandItem().is(Oborokage.VOIDSTRING)
                      ? 0xbb87eb
                      : 0xbca6f4;
      float age = scene.age + partial;
      for (int w = 0; w < move.hits().length; w++) {
        float t = age - (move.hits()[w] - 2);
        if (t < 0 || t > 7) continue;
        int logical = move.hands()[w];
        int hand =
            e instanceof net.minecraft.world.entity.player.Player player
                    && player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                ? 1 - logical
                : logical;
        boolean finalCut = move.id() == Move.ULTIMATE && w == 2;
        int segments =
            Math.min(
                cfg.trailSegments,
                (cfg.trailVertexBudget / 2 - vertices) / (8 * (finalCut ? 4 : 2)));
        if (segments < 8) continue;
        int cost = segments * 8 * (finalCut ? 4 : 2);
        vertices += cost;
        var pose = context.poseStack();
        pose.pushPose();
        pose.translate(e.getPosition(partial).subtract(camera).add(0, 1.05, 0));
        float yaw = scene.event.yaw();
        float alpha = Math.min(1, (7 - t) / 3), q = Math.min(1, t / 4);
        context
            .submitNodeCollector()
            .submitCustomGeometry(
                pose,
                (cfg.trailShaderCompat
                    ? RenderTypes.entityTranslucentEmissive(Oborokage.id("textures/fx/white.png"))
                    : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true)),
                (p, v) -> {
                  ribbon(
                      p,
                      v,
                      yaw,
                      hand,
                      q,
                      alpha,
                      finalCut ? 3.4f : logical == 0 ? 2.7f : 2.0f,
                      .18f,
                      logical == 0 ? bladeColor : 0x9bd6e5,
                      segments);
                  ribbon(
                      p,
                      v,
                      yaw,
                      hand,
                      q,
                      alpha * .95f,
                      finalCut ? 3.4f : logical == 0 ? 2.7f : 2.0f,
                      .025f,
                      0xf5f3ff,
                      segments);
                  if (finalCut) {
                    ribbon(p, v, yaw, 1 - hand, q, alpha, 3.0f, .14f, 0xb59de8, segments);
                    ribbon(p, v, yaw, 1 - hand, q, alpha * .95f, 3.0f, .025f, 0xffffff, segments);
                  }
                });
        pose.popPose();
      }
    }
  }

  private static void ribbon(
      PoseStack.Pose p,
      VertexConsumer v,
      float yaw,
      int hand,
      float q,
      float alpha,
      float radius,
      float width,
      int rgb,
      int count) {
    for (int i = 0; i < count; i++) {
      float u = i / (float) count, u2 = (i + 1f) / count;
      float a = -1.15f + 2.30f * q * u, b = -1.15f + 2.30f * q * u2;
      float fade = alpha * (.2f + .8f * u);
      Vec3[] quad = {
        point(yaw, hand, a, radius - width),
        point(yaw, hand, a, radius + width),
        point(yaw, hand, b, radius + width),
        point(yaw, hand, b, radius - width)
      };
      int color = (int) (255 * fade) << 24 | rgb;
      for (Vec3 pos : quad) vertex(p, v, pos, color);
      for (int j = 3; j >= 0; j--) vertex(p, v, quad[j], color);
    }
  }

  private static Vec3 point(float yaw, int hand, float a, float radius) {
    float sign = hand == 0 ? 1 : -1;
    double x = Math.sin(a) * radius * sign,
        z = Math.cos(a) * radius,
        y = Math.sin(a) * radius * (hand == 0 ? -.22 : .32);
    double r = Math.toRadians(yaw);
    return new Vec3(x * Math.cos(r) - z * Math.sin(r), y, x * Math.sin(r) + z * Math.cos(r));
  }

  private static void vertex(PoseStack.Pose p, VertexConsumer v, Vec3 a, int color) {
    v.addVertex(p, (float) a.x, (float) a.y, (float) a.z)
        .setColor(color)
        .setUv(.5f, .5f)
        .setLight(0x00f000f0)
        .setOverlay(0)
        .setUv3(0, 0)
        .setNormal(p, 0, 1, 0);
  }

  private static final class ListCopy {
    static java.util.List<DualClient.Scene> scenes() {
      return java.util.List.copyOf(DualClient.scenes.values());
    }
  }
}

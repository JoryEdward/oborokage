package oborokage.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** Finite world-space frost rings and sword-energy ribbons; no damage logic. */
public final class TechniqueFx {
  private record Fx(TechniqueEvent event, int age) {}

  private static final ArrayList<Fx> effects = new ArrayList<>();
  private static Object world;

  public static void init() {
    ClientPlayNetworking.registerGlobalReceiver(
        TechniqueEvent.TYPE,
        (e, c) ->
            c.client()
                .execute(
                    () -> {
                      if (e.style() < 0
                          || e.style() > TechniqueEvent.RING
                          || !Double.isFinite(e.origin().lengthSqr())
                          || !Double.isFinite(e.end().lengthSqr())
                          || e.origin().distanceToSqr(e.end()) > 32 * 32) return;
                      if (effects.size() >= ClientConfig.get().trailMaxPlayers)
                        effects.removeFirst();
                      effects.add(new Fx(e, 0));
                      if (e.style() != TechniqueEvent.RING) DualClient.technique(e);
                    }));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (world != c.level) {
            effects.clear();
            world = c.level;
          }
          for (int i = effects.size() - 1; i >= 0; i--) {
            Fx f = effects.get(i);
            if (f.age >= 12) effects.remove(i);
            else effects.set(i, new Fx(f.event, f.age + 1));
          }
        });
    LevelRenderEvents.COLLECT_SUBMITS.register(
        context -> {
          var c = Minecraft.getInstance();
          if (c.level == null) return;
          Vec3 camera = context.levelState().cameraRenderState.pos;
          var cfg = ClientConfig.get();
          int budget = 0;
          for (Fx f : effects) {
            if (f.event.origin().distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance)
              continue;
            int segments = Math.min(cfg.trailSegments, (cfg.trailVertexBudget / 2 - budget) / 16);
            if (segments < 8) break;
            int cost = segments * 16;
            budget += cost;
            float age = f.age + oborokage.client.compat.ClientCompat.partialTick(context),
                fade = Math.max(0, 1 - age / 12);
            var pose = context.poseStack();
            pose.pushPose();
            pose.translate(f.event.origin().subtract(camera));
            context
                .submitNodeCollector()
                .submitCustomGeometry(
                    pose,
                    cfg.trailShaderCompat
                        ? RenderTypes.entityTranslucentEmissive(
                            Oborokage.id("textures/fx/white.png"))
                        : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true),
                    (p, v) -> {
                      Vec3 direction = f.event.end().subtract(f.event.origin());
                      if (f.event.style() == 1 || f.event.style() == TechniqueEvent.RING) {
                        double radius = direction.length() * (.65 + .35 * Math.min(1, age / 5));
                        ring(p, v, radius, .10, fade * .6f, 0x79dfea, segments);
                        ring(p, v, radius + .14, .018, fade, 0xf3fbff, segments);
                      } else if (f.event.style() == 2) {
                        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
                        if (side.lengthSqr() < .1) side = new Vec3(1, 0, 0);
                        beam(p, v, direction, side, .16, fade * .45f, 0x9665e7);
                        beam(p, v, direction, side, .025, fade, 0xf5eaff);
                        beam(
                            p,
                            v,
                            direction,
                            direction.normalize().cross(side),
                            .07,
                            fade * .5f,
                            0xc9a1ff);
                      }
                    });
            pose.popPose();
          }
        });
  }

  private static void ring(
      PoseStack.Pose pose,
      VertexConsumer v,
      double radius,
      double width,
      float alpha,
      int color,
      int segments) {
    for (int i = 0; i < segments; i++) {
      double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
      quad(
          pose,
          v,
          new Vec3(Math.cos(a) * (radius - width), -.6, Math.sin(a) * (radius - width)),
          new Vec3(Math.cos(a) * (radius + width), -.6, Math.sin(a) * (radius + width)),
          new Vec3(Math.cos(b) * (radius + width), -.6, Math.sin(b) * (radius + width)),
          new Vec3(Math.cos(b) * (radius - width), -.6, Math.sin(b) * (radius - width)),
          alpha,
          color);
    }
  }

  private static void beam(
      PoseStack.Pose pose,
      VertexConsumer v,
      Vec3 end,
      Vec3 side,
      double width,
      float alpha,
      int color) {
    Vec3 w = side.scale(width);
    Vec3 start = end.normalize().scale(Math.min(.9, end.length() * .5));
    quad(pose, v, start.subtract(w), start.add(w), end.add(w), end.subtract(w), alpha, color);
  }

  private static void quad(
      PoseStack.Pose pose, VertexConsumer v, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha, int rgb) {
    int color = (int) (255 * Math.clamp(alpha, 0, 1)) << 24 | rgb;
    Vec3[] points =
        ClientConfig.get().trailShaderCompat
            ? new Vec3[] {a, b, c, d}
            : new Vec3[] {a, b, c, d, d, c, b, a};
    for (Vec3 point : points)
      oborokage.client.compat.ClientCompat.vertex(
          v, pose, (float) point.x, (float) point.y, (float) point.z, color);
  }

  private TechniqueFx() {}
}

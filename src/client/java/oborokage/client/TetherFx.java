package oborokage.client;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** 空弦's string between the wielder's hand and its target, trembling faintly while it lasts. */
final class TetherFx {
  private record Tether(int target, long until) {}

  private static final Map<Integer, Tether> tethers = new HashMap<>();
  private static long clock;
  private static Object world;

  static void init() {
    ClientPlayNetworking.registerGlobalReceiver(
        TetherEvent.TYPE,
        (e, c) ->
            c.client()
                .execute(
                    () -> {
                      if (e.target() < 0 || e.ticks() <= 0) tethers.remove(e.owner());
                      else {
                        tethers.put(e.owner(), new Tether(e.target(), clock + e.ticks()));
                        var mc = Minecraft.getInstance();
                        if (mc.player != null && e.owner() == mc.player.getId())
                          Codex.see("void_string");
                      }
                    }));
  }

  /** Whether this player's string is drawn (for predicting the string dash). */
  static boolean active(int owner) {
    Tether t = tethers.get(owner);
    return t != null && t.until > clock;
  }

  static void tick(Minecraft c) {
    clock++;
    if (world != c.level) {
      tethers.clear();
      world = c.level;
    }
    tethers.values().removeIf(t -> t.until <= clock);
  }

  static void submit(LevelRenderContext context) {
    var c = Minecraft.getInstance();
    if (c.level == null || tethers.isEmpty()) return;
    Vec3 camera = context.levelState().cameraRenderState.pos;
    float partial = oborokage.client.compat.ClientCompat.partialTick(context);
    var cfg = ClientConfig.get();
    boolean compat = cfg.trailShaderCompat;
    var type =
        compat
            ? RenderTypes.entityTranslucentEmissive(Oborokage.id("textures/fx/white.png"))
            : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true);
    for (var entry : Map.copyOf(tethers).entrySet()) {
      Entity owner = c.level.getEntity(entry.getKey()),
          target = c.level.getEntity(entry.getValue().target);
      if (owner == null || target == null) continue;
      Vec3 a = owner.getPosition(partial).add(0, 1.15, 0);
      Vec3 b = target.getPosition(partial).add(0, target.getBbHeight() * .55, 0);
      if (a.distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance) continue;
      float t = clock + partial;
      Vec3 eye = camera.subtract(a);
      var pose = context.poseStack();
      pose.pushPose();
      pose.translate(a.subtract(camera));
      Vec3 span = b.subtract(a);
      context
          .submitNodeCollector()
          .submitCustomGeometry(
              pose,
              type,
              (p, v) -> {
                Geo g = new Geo(p, v, 1024, !compat).near(eye, 1.2);
                BladeStyle st = BladeStyle.VOIDSTRING;
                int n = 16;
                Vec3 prev = Vec3.ZERO;
                Vec3 side = span.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                for (int i = 1; i <= n; i++) {
                  double u = i / (double) n;
                  // A slight sag and a fine tremble along the string.
                  double sag = -Math.sin(Math.PI * u) * .25;
                  double shake = Math.sin(Math.PI * u) * Math.sin(t * 1.9 + u * 9) * .04;
                  Vec3 q = span.scale(u).add(0, sag, 0).add(side.scale(shake));
                  g.beam(prev, q, .09, st.secondary, .45f, eye);
                  g.beam(prev, q, .025, st.primary, .95f, eye);
                  prev = q;
                }
              });
      pose.popPose();
    }
  }

  private TetherFx() {}
}

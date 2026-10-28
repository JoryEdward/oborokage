package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * World-space spectacle for the five ultimates and the full-charge iaido. Everyone near the actor
 * sees it; the local camera and screen effects are Cinematics' job. Everything is driven by the
 * scene age, so late joiners and replays line up with the server timeline.
 */
public final class UltimateFx {
  /** Scene kind of an iaido released before full charge. */
  private static final int QUICK_DRAW = -2;

  static final class State {
    final int kind;
    final long key;
    final Vec3 origin;

    /** The ground below the origin: an ultimate may begin in mid-air, as after a Warden's blow. */
    final Vec3 floor;

    final float yaw;
    final List<Vec3> trail = new ArrayList<>();
    final List<Vec3> marks = new ArrayList<>();
    int spawned = -1;
    float charge = 1;

    State(int kind, long key, Vec3 origin, Vec3 floor, float yaw) {
      this.kind = kind;
      this.key = key;
      this.origin = origin;
      this.floor = floor;
      this.yaw = yaw;
    }
  }

  private static final Map<Integer, State> states = new HashMap<>();
  private static Object world;

  public static void init() {
    ClientTickEvents.END_CLIENT_TICK.register(UltimateFx::tick);
    LevelRenderEvents.COLLECT_SUBMITS.register(UltimateFx::render);
  }

  /** A server-confirmed impact during an ultimate: frost spires and void bursts anchor here. */
  public static void impact(int actor, Vec3 at) {
    State s = states.get(actor);
    if (s != null && s.marks.size() < 48) s.marks.add(at);
  }

  // ---------------------------------------------------------------- scene tracking

  private record Scene(
      int actor, int kind, long key, Vec3 origin, float yaw, float age, float charge) {}

  private static List<Scene> scenes() {
    List<Scene> out = new ArrayList<>();
    for (var e : DualClient.scenes.entrySet()) {
      var ev = e.getValue().event;
      if (Move.ultimate(ev.move()))
        out.add(
            new Scene(
                e.getKey(), ev.move(), ev.actionId(), ev.origin(), ev.yaw(), e.getValue().age, 1));
    }
    OborokageClient.forEachIaido(
        (owner, origin, yaw, charge, age) -> {
          // A partial draw gets a lighter scene of its own, scaled by its charge.
          int kind = charge >= 1 ? Cinematics.IAIDO : QUICK_DRAW;
          if (kind == QUICK_DRAW && age > 14) return;
          out.add(new Scene(owner, kind, origin.hashCode(), origin, yaw, age, charge));
        });
    return out;
  }

  /** The ground below `origin` (a liquid's surface counts), never above it; the origin if none. */
  private static Vec3 floor(Minecraft c, Vec3 origin) {
    var hit =
        c.level.clip(
            new net.minecraft.world.level.ClipContext(
                origin.add(0, .5, 0),
                origin.add(0, -24, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return origin;
    return new Vec3(origin.x, Math.min(origin.y, hit.getLocation().y), origin.z);
  }

  private static void tick(Minecraft c) {
    if (world != c.level) {
      states.clear();
      world = c.level;
    }
    if (c.level == null || c.player == null) return;
    Set<Integer> live = new HashSet<>();
    for (Scene sc : scenes()) {
      live.add(sc.actor);
      State s = states.get(sc.actor);
      if (s == null || s.key != sc.key || s.kind != sc.kind) {
        s = new State(sc.kind, sc.key, sc.origin, floor(c, sc.origin), sc.yaw);
        s.charge = sc.charge;
        states.put(sc.actor, s);
      }
      Entity actor = c.level.getEntity(sc.actor);
      if (actor != null && s.trail.size() < 80) s.trail.add(actor.position());
      int age = (int) sc.age;
      if (age != s.spawned && actor != null && actor.distanceToSqr(c.player) < 96 * 96) {
        s.spawned = age;
        particles(c, s, actor, age);
      }
    }
    states.keySet().retainAll(live);
  }

  // ---------------------------------------------------------------- particles

  private static void add(
      Minecraft c, ParticleOptions p, Vec3 at, double dx, double dy, double dz) {
    c.level.addParticle(p, true, true, at.x, at.y, at.z, dx, dy, dz);
  }

  private static void particles(Minecraft c, State s, Entity actor, int t) {
    RandomSource r = c.level.getRandom();
    Vec3 at = actor.position();
    Vec3 f = forward(s.yaw);
    switch (s.kind) {
      case Move.ULTIMATE -> {
        if (t < 44)
          for (int i = 0; i < 3; i++)
            add(
                c,
                Oborokage.PETAL,
                at.add(r.nextGaussian() * 4, 4 + r.nextDouble() * 3, r.nextGaussian() * 4),
                0,
                -.04,
                0);
        if (t == 9 || t == 13)
          for (int i = 0; i < 24; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(0, 1.1, 0),
                r.nextGaussian() * .3,
                r.nextDouble() * .2,
                r.nextGaussian() * .3);
        if (t == 23)
          for (int i = 0; i < 90; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            add(
                c,
                Oborokage.PETAL,
                at.add(0, 1.2, 0),
                Math.cos(a) * .5,
                .1 + r.nextDouble() * .25,
                Math.sin(a) * .5);
          }
      }
      case Move.ULT_MOON -> {
        if (t < 32)
          for (int i = 0; i < 6; i++) {
            double a = t * .5 + i * Math.PI / 3, rad = 3.2 + r.nextDouble() * .6;
            Vec3 p = at.add(Math.cos(a) * rad, .2 + r.nextDouble() * 2.6, Math.sin(a) * rad);
            add(c, Oborokage.PETAL, p, -Math.sin(a) * .18, .05, Math.cos(a) * .18);
          }
        if (t == 32)
          for (int i = 0; i < 110; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            add(
                c,
                Oborokage.PETAL,
                at.add(0, 1, 0),
                Math.cos(a) * .7,
                .05 + r.nextDouble() * .3,
                Math.sin(a) * .7);
          }
      }
      case Move.ULT_STORM -> {
        if (t < 44)
          for (int i = 0; i < 4; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(r.nextGaussian() * .6, .2 + r.nextDouble() * 1.8, r.nextGaussian() * .6),
                0,
                .03,
                0);
        if (t >= 8 && t <= 33 && (t - 8) % 5 == 0)
          for (int i = 0; i < 36; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(0, 1, 0),
                r.nextGaussian() * .45,
                r.nextDouble() * .4,
                r.nextGaussian() * .45);
        if (t == 44)
          for (int i = 0; i < 140; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(0, .3, 0),
                r.nextGaussian() * .5,
                .4 + r.nextDouble() * .9,
                r.nextGaussian() * .5);
      }
      case Move.ULT_FROST -> {
        double reach = 12 * ease(t / 18f);
        if (t < 48)
          for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2,
                rad = Math.sqrt(r.nextDouble()) * Math.max(2, reach);
            add(
                c,
                i % 2 == 0 ? Oborokage.SHARD : ParticleTypes.SNOWFLAKE,
                s.origin.add(Math.cos(a) * rad, 6 + r.nextDouble() * 4, Math.sin(a) * rad),
                0,
                -.15,
                0);
          }
        if (t == 18 || t == 38)
          for (Vec3 m : s.marks)
            for (int i = 0; i < (t == 18 ? 18 : 44); i++)
              add(
                  c,
                  Oborokage.SHARD,
                  m.add(0, .8, 0),
                  r.nextGaussian() * (t == 18 ? .1 : .45),
                  r.nextDouble() * (t == 18 ? .2 : .5),
                  r.nextGaussian() * (t == 18 ? .1 : .45));
        if (t == 38)
          for (int i = 0; i < 120; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            add(
                c,
                Oborokage.SHARD,
                s.floor.add(Math.cos(a) * 12, .3, Math.sin(a) * 12),
                -Math.cos(a) * .3,
                .3 + r.nextDouble() * .3,
                -Math.sin(a) * .3);
          }
      }
      case Move.ULT_VOID -> {
        if (t < 27)
          for (int i = 0; i < 9; i++) {
            Vec3 d =
                new Vec3(r.nextGaussian(), r.nextGaussian() * .6 + .3, r.nextGaussian())
                    .normalize();
            Vec3 p = at.add(0, 1.4, 0).add(d.scale(10 + r.nextDouble() * 4));
            add(c, Oborokage.MOTE, p, -d.x * .32, -d.y * .32, -d.z * .32);
          }
        if (t == 28)
          for (int i = 0; i < 180; i++) {
            Vec3 p = s.origin.add(f.scale(r.nextDouble() * 48)).add(0, r.nextDouble() * 20, 0);
            add(
                c,
                Oborokage.MOTE,
                p,
                r.nextGaussian() * .2,
                .1 + r.nextDouble() * .2,
                r.nextGaussian() * .2);
          }
        if (t > 28 && t < 52)
          for (int i = 0; i < 6; i++) {
            Vec3 p =
                s.origin
                    .add(f.scale(r.nextDouble() * 48))
                    .add(r.nextGaussian() * 3, 0, r.nextGaussian() * 3);
            add(c, Oborokage.MOTE, p, 0, .4 + r.nextDouble() * .4, 0);
          }
      }
      case Cinematics.IAIDO -> {
        if (t < 12) {
          for (int i = 0; i < 2; i++)
            add(
                c,
                Oborokage.PETAL,
                at.add(r.nextGaussian() * 3, 3 + r.nextDouble() * 2, r.nextGaussian() * 3),
                0,
                -.02,
                0);
          // Light gathers toward the hilt.
          for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 from = at.add(Math.cos(a) * 3, .4 + r.nextDouble() * 1.6, Math.sin(a) * 3);
            Vec3 v = at.add(0, 1, 0).subtract(from).scale(.12);
            add(c, ParticleTypes.END_ROD, from, v.x, v.y, v.z);
          }
        }
        if (t >= 12 && t < 18)
          for (int i = 0; i < 6; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(0, 1, 0),
                r.nextGaussian() * .2,
                r.nextDouble() * .1,
                r.nextGaussian() * .2);
        if (t == LegacyIaido.SHEATH) {
          for (int i = 0; i < 50; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            add(
                c,
                Oborokage.PETAL,
                at.add(0, 1.2, 0),
                Math.cos(a) * .35,
                .12 + r.nextDouble() * .2,
                Math.sin(a) * .35);
          }
          if (s.trail.size() > 18) {
            Vec3 a = s.trail.get(12), b = s.trail.get(18);
            for (double d = 0; d < 1; d += .05)
              add(
                  c,
                  ParticleTypes.END_ROD,
                  a.lerp(b, d).add(0, 1, 0),
                  r.nextGaussian() * .05,
                  .03,
                  r.nextGaussian() * .05);
          }
        }
        // Petals drift down over the whole path after the cut.
        if (t > LegacyIaido.SHEATH && t < 56 && s.trail.size() > 18) {
          Vec3 a = s.trail.get(12), b = s.trail.get(18);
          for (int i = 0; i < 4; i++) {
            Vec3 p =
                a.lerp(b, r.nextDouble())
                    .add(r.nextGaussian() * 1.5, 2.5 + r.nextDouble() * 2, r.nextGaussian() * 1.5);
            add(c, Oborokage.PETAL, p, 0, -.03, 0);
          }
        }
      }
      default -> {}
    }
  }

  // ---------------------------------------------------------------- geometry

  private static void render(LevelRenderContext context) {
    var c = Minecraft.getInstance();
    if (c.level == null || states.isEmpty()) return;
    Vec3 camera = context.levelState().cameraRenderState.pos;
    float partial = oborokage.client.compat.ClientCompat.partialTick(context);
    var cfg = ClientConfig.get();
    int budget = Math.max(4096, cfg.trailVertexBudget);
    // Light, not matter: the unlit beacon pipeline keeps every face the same brightness. The
    // emissive entity pipeline shades by face orientation, which turned the moon grey.
    var renderType = RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true);
    boolean twoSided = true;
    for (Scene sc : scenes()) {
      State s = states.get(sc.actor);
      if (s == null || s.origin.distanceToSqr(camera) > 160 * 160) continue;
      float t = sc.age + partial;
      Vec3 eye = camera.subtract(s.origin);
      var pose = context.poseStack();
      pose.pushPose();
      pose.translate(s.origin.subtract(camera));
      int share = budget / Math.max(1, states.size());
      List<Vec3> trail = List.copyOf(s.trail), marks = List.copyOf(s.marks);
      context
          .submitNodeCollector()
          .submitCustomGeometry(
              pose,
              renderType,
              (p, v) ->
                  draw(new Geo(p, v, share, twoSided).near(eye, 1.6), s, t, eye, trail, marks));
      pose.popPose();
    }
  }

  private static void draw(Geo g, State s, float t, Vec3 eye, List<Vec3> trail, List<Vec3> marks) {
    Vec3 f = forward(s.yaw), r = f.cross(UP).normalize();
    double drop = s.floor.y - s.origin.y;
    switch (s.kind) {
      case Move.ULTIMATE -> TachiFx.draw(g, t, f, r);
      case Move.ULT_MOON -> MoonFx.draw(g, t, f, r, drop);
      case Move.ULT_STORM -> StormFx.draw(g, s, t, eye, trail);
      case Move.ULT_FROST -> FrostFx.draw(g, s, t, f, r, marks, drop);
      case Move.ULT_VOID -> VoidFx.draw(g, t, f, r, eye, drop);
      case Cinematics.IAIDO -> IaidoFx.draw(g, s, t, f, r, eye, trail, marks);
      case QUICK_DRAW -> IaidoFx.quick(g, s, t, f, r, eye, trail);
      default -> {}
    }
  }

  private UltimateFx() {}
}

package oborokage.client;

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
  private static final Vec3 UP = new Vec3(0, 1, 0);

  private static final class State {
    final int kind;
    final long key;
    final Vec3 origin;
    final float yaw;
    final List<Vec3> trail = new ArrayList<>();
    final List<Vec3> marks = new ArrayList<>();
    int spawned = -1;

    State(int kind, long key, Vec3 origin, float yaw) {
      this.kind = kind;
      this.key = key;
      this.origin = origin;
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

  private record Scene(int actor, int kind, long key, Vec3 origin, float yaw, float age) {}

  private static List<Scene> scenes() {
    List<Scene> out = new ArrayList<>();
    for (var e : DualClient.scenes.entrySet()) {
      var ev = e.getValue().event;
      if (Move.ultimate(ev.move()))
        out.add(
            new Scene(
                e.getKey(), ev.move(), ev.actionId(), ev.origin(), ev.yaw(), e.getValue().age));
    }
    OborokageClient.forEachFullIaido(
        (owner, origin, yaw, age) ->
            out.add(new Scene(owner, Cinematics.IAIDO, origin.hashCode(), origin, yaw, age)));
    return out;
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
        s = new State(sc.kind, sc.key, sc.origin, sc.yaw);
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
                s.origin.add(Math.cos(a) * 12, .3, Math.sin(a) * 12),
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
        if (t < 12)
          for (int i = 0; i < 2; i++)
            add(
                c,
                Oborokage.PETAL,
                at.add(r.nextGaussian() * 3, 3 + r.nextDouble() * 2, r.nextGaussian() * 3),
                0,
                -.04,
                0);
        if (t >= 12 && t < 18)
          for (int i = 0; i < 6; i++)
            add(
                c,
                Oborokage.SPARK,
                at.add(0, 1, 0),
                r.nextGaussian() * .2,
                r.nextDouble() * .1,
                r.nextGaussian() * .2);
        if (t == 34)
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
              (p, v) -> draw(new Geo(p, v, share, twoSided), s, t, eye, trail, marks));
      pose.popPose();
    }
  }

  private static void draw(Geo g, State s, float t, Vec3 eye, List<Vec3> trail, List<Vec3> marks) {
    Vec3 f = forward(s.yaw), r = f.cross(UP).normalize();
    switch (s.kind) {
      case Move.ULTIMATE -> tachi(g, t, f, r);
      case Move.ULT_MOON -> moon(g, t, f, r);
      case Move.ULT_STORM -> storm(g, s, t, eye, trail);
      case Move.ULT_FROST -> frost(g, s, t, f, r, marks);
      case Move.ULT_VOID -> voidCut(g, t, f, r, eye);
      case Cinematics.IAIDO -> iaido(g, s, t, f, r, eye, trail);
      default -> {}
    }
  }

  /** 胧影 · 双月归寂: a moon rises behind; two moons cross; the heavens' cut splits the moon. */
  private static void tachi(Geo g, float t, Vec3 f, Vec3 r) {
    float moonA = fadeIn(t, 0, 8) * fadeOut(t, 40, 48);
    Vec3 moon = f.scale(-14).add(0, 3 + 7 * ease(t / 12), 0);
    if (t < 23) {
      g.disc(moon, r, UP, 6.5, 0xfffaff, 0xd8c8ff, .9f * moonA, 40);
      g.ring(moon, r, UP, 6.5, 9.5, 0xbca6f4, .28f * moonA, 40);
    } else {
      double split = Math.min(1.8, (t - 23) * .2);
      g.disc(
          moon.add(r.scale(split)),
          r,
          UP,
          6.5,
          0xfffaff,
          0xd8c8ff,
          .9f * moonA,
          -Math.PI / 2,
          Math.PI / 2,
          20);
      g.disc(
          moon.add(r.scale(-split)),
          r,
          UP,
          6.5,
          0xfffaff,
          0xd8c8ff,
          .9f * moonA,
          Math.PI / 2,
          Math.PI * 1.5,
          20);
      g.ring(moon, r, UP, 6.5, 10 + (t - 23) * .4, 0xbca6f4, .22f * moonA, 40);
      float cut = fadeOut(t, 25, 34);
      g.band(moon.subtract(r.scale(11)), moon.add(r.scale(11)), UP, .5 * cut, 0xffffff, cut);
    }
    double dash = Math.min(3.6, Math.max(0, t - 7) * .9);
    Vec3 body = f.scale(dash).add(0, 1.1, 0);
    if (t >= 9 && t < 32) {
      float k = ease((t - 9) / 3), a = fadeOut(t, 22, 32);
      Vec3 w = tilt(f, 18);
      g.crescent(
          body,
          r,
          w,
          5.6,
          1.4,
          Math.toRadians(-110),
          Math.toRadians(-110 + 220 * k),
          0xbca6f4,
          .35f * a,
          40);
      g.crescent(
          body,
          r,
          w,
          5.6,
          .45,
          Math.toRadians(-110),
          Math.toRadians(-110 + 220 * k),
          0xf6efff,
          .95f * a,
          40);
    }
    if (t >= 13 && t < 34) {
      float k = ease((t - 13) / 3), a = fadeOut(t, 24, 34);
      Vec3 w = tilt(f, -18);
      g.crescent(
          body,
          r,
          w,
          5.2,
          1.3,
          Math.toRadians(110),
          Math.toRadians(110 - 220 * k),
          0x9f86e8,
          .35f * a,
          40);
      g.crescent(
          body,
          r,
          w,
          5.2,
          .42,
          Math.toRadians(110),
          Math.toRadians(110 - 220 * k),
          0xfffaff,
          .95f * a,
          40);
    }
    if (t >= 23 && t < 42) {
      float k = ease((t - 23) / 2.5f), a = fadeOut(t, 32, 42);
      Vec3 c = body.add(f.scale(1));
      g.crescent(
          c,
          f,
          UP,
          9.5,
          2.6,
          Math.toRadians(-80),
          Math.toRadians(-80 + 160 * k),
          0xbca6f4,
          .3f * a,
          48);
      g.crescent(
          c, f, UP, 9.5, 1.0, Math.toRadians(-80), Math.toRadians(-80 + 160 * k), 0xffffff, a, 48);
    }
    if (t >= 23 && t < 37) {
      float s = (t - 23) / 14;
      double R = 1 + 14 * ease(s);
      g.ring(body.add(0, -1.05, 0), r, f, Math.max(0, R - .8), R, 0xd8c8ff, .7f * (1 - s), 56);
    }
  }

  /** 胧月 · 百花缭乱: eight whirling cuts weave a cage of light; a lotus blooms and bursts. */
  private static void moon(Geo g, float t, Vec3 f, Vec3 r) {
    Vec3 c = new Vec3(0, 1, 0);
    int[] colors = {0xffffff, 0xd9cdff, 0xffc8ea};
    for (int k = 0; k < 8; k++) {
      float tk = 8 + 2 * k;
      if (t < tk || t >= tk + 14) continue;
      float sweep = ease((t - tk) / 2), a = fadeOut(t, tk + 6, tk + 14);
      Vec3 u = rotY(r, k * 67), w = tilt(rotY(f, k * 67), (k % 2 == 0 ? 1 : -1) * (12 + k * 5));
      double start = k * .7;
      g.crescent(c, u, w, 4.4 + .1 * k, 1.0, start, start + 2.6 * sweep, 0xd9cdff, .25f * a, 28);
      g.crescent(
          c, u, w, 4.4 + .1 * k, .32, start, start + 2.6 * sweep, colors[k % 3], .9f * a, 28);
    }
    if (t >= 10 && t < 46) {
      float open = ease((t - 10) / 22), bloom = t > 32 ? ease((t - 32) / 8) : 0;
      float a = fadeIn(t, 10, 14) * fadeOut(t, 38, 46);
      for (int i = 0; i < 8; i++) {
        Vec3 dir = rotY(f, i * 45 + t * 1.5f);
        double lift = Math.toRadians((1 - open) * 34 - bloom * 6);
        Vec3 d = dir.scale(Math.cos(lift)).add(UP.scale(Math.sin(lift)));
        double len = 5.6 + 2.4 * bloom;
        Vec3 base = new Vec3(0, .06, 0),
            tip = base.add(d.scale(len)),
            mid = base.add(d.scale(len * .45));
        Vec3 side = UP.cross(dir).normalize().scale(1.05 + .3 * bloom);
        g.quad(
            base,
            mid.add(side),
            tip,
            mid.subtract(side),
            Geo.argb(0xffffff, .34f * a),
            Geo.argb(0xd9cdff, .24f * a),
            Geo.argb(0xffc8ea, .08f * a),
            Geo.argb(0xd9cdff, .24f * a));
        g.band(base, tip, side, .07, 0xffffff, .7f * a);
      }
    }
    if (t >= 32 && t < 44) {
      float s = (t - 32) / 12;
      double R = 1 + 11 * ease(s);
      g.ring(new Vec3(0, .08, 0), r, f, Math.max(0, R - .7), R, 0xffc8ea, .8f * (1 - s), 56);
    }
  }

  /** 雷切 · 万雷天降: afterimages tear the air between blinks; the sky answers with a pillar. */
  private static void storm(Geo g, State s, float t, Vec3 eye, List<Vec3> trail) {
    int now = (int) t;
    for (int i = 1; i < trail.size() && i <= now; i++) {
      float age = t - i;
      if (age > 9) continue;
      Vec3 a = trail.get(i - 1).subtract(s.origin).add(0, 1, 0),
          b = trail.get(i).subtract(s.origin).add(0, 1, 0);
      if (a.distanceToSqr(b) < .04) continue;
      float alpha = 1 - age / 9;
      g.beam(a, b, .42, 0xff2410, .55f * alpha, eye);
      g.beam(a, b, .12, 0xfff0d6, .95f * alpha, eye);
    }
    Vec3 here =
        trail.isEmpty() ? Vec3.ZERO : trail.get(Math.min(trail.size() - 1, now)).subtract(s.origin);
    if (t < 10) {
      float a = (float) (.55 + .45 * Math.sin(t * 2.3)) * fadeIn(t, 0, 3);
      for (int i = 0; i < 3; i++)
        g.bolt(
            here.add(0, 1.6, 0),
            here.add((i - 1) * 3, 26, i * 2 - 2),
            .2,
            0xff5a2c,
            a,
            eye,
            (long) (t / 2) * 31 + i,
            7);
    }
    for (int k = 0; k < 6; k++) {
      int at = 8 + 5 * k;
      if (t >= at && t < at + 3 && at < trail.size()) {
        Vec3 p = trail.get(at).subtract(s.origin);
        float a = 1 - (t - at) / 3;
        g.bolt(p.add(0, 30, 0), p, .55, 0xff2410, .8f * a, eye, at * 7L, 9);
        g.bolt(p.add(0, 30, 0), p, .16, 0xffffff, a, eye, at * 7L, 9);
      }
    }
    if (t >= 44 && t < 56) {
      float a = fadeOut(t, 48, 56);
      Vec3 top = here.add(0, 50, 0);
      g.beam(top, here, 6.5, 0xff3a14, .22f * a, eye);
      g.bolt(top, here, 2.2, 0xff2410, .78f * a, eye, 4401, 11);
      g.bolt(top, here, .55, 0xfff6e8, a, eye, 4401, 11);
      float sw = (t - 44) / 12;
      double R = 1 + 11 * ease(sw);
      g.ring(
          here.add(0, .06, 0),
          new Vec3(1, 0, 0),
          new Vec3(0, 0, 1),
          Math.max(0, R - .9),
          R,
          0xffc35a,
          .8f * (1 - sw),
          56);
    }
  }

  /** 霜天 · 镜月冰河: a mirror lake freezes outward, spires seize the foes, the moon cracks. */
  private static void frost(Geo g, State s, float t, Vec3 f, Vec3 r, List<Vec3> marks) {
    double R = 12 * ease(t / 18);
    float a = fadeIn(t, 0, 4) * fadeOut(t, 48, 58);
    Vec3 c = new Vec3(0, .05, 0);
    g.disc(c, r, f, R, 0xe8fbff, 0x7fe6ff, .2f * a, 48);
    g.ring(c, r, f, Math.max(0, R - .35), R, 0xffffff, .9f * a, 64);
    g.ring(c.add(0, .01, 0), r, f, R + .2, R + .9, 0x7fe6ff, .3f * a, 64);
    for (int i = 0; i < 6; i++) {
      Vec3 dir = rotY(f, i * 60);
      Vec3 side = UP.cross(dir).normalize();
      g.band(c.add(0, .02, 0), c.add(dir.scale(R)), side, .2, 0xffffff, .55f * a);
      for (int sgn = -1; sgn <= 1; sgn += 2) {
        Vec3 from = c.add(dir.scale(R * .55)).add(0, .02, 0);
        Vec3 b = rotY(dir, sgn * 40);
        g.band(from, from.add(b.scale(R * .22)), UP.cross(b).normalize(), .14, 0xe8fbff, .5f * a);
      }
    }
    if (t >= 18 && t < 39) {
      float grow = ease((t - 18) / 4);
      for (Vec3 m : marks) {
        Vec3 base = m.subtract(s.origin);
        g.spike(base, UP, 3.0 * grow, .7, 0xbfeaff, .8f);
        for (int i = 0; i < 4; i++) {
          Vec3 d = tilt(rotY(f, i * 90 + 45), 70);
          g.spike(base, d, (1.6 + .3 * i) * grow, .5, 0xd8f6ff, .7f);
        }
      }
    }
    if (t >= 16 && t < 48) {
      float m = fadeIn(t, 16, 22) * fadeOut(t, 40, 48);
      Vec3 top = new Vec3(0, 11, 0);
      g.disc(top, r, f, 4.2, 0xffffff, 0x9fe8ff, .85f * m, 40);
      g.ring(top, r, f, 4.2, 6.2, 0x7fe6ff, .25f * m, 40);
      g.disc(c.add(0, .03, 0), r, f, 4.2, 0xffffff, 0x9fe8ff, .35f * m, 40);
      if (t >= 38) {
        java.util.Random rand = new java.util.Random(38);
        for (int i = 0; i < 7; i++) {
          Vec3 d = rotY(f, rand.nextInt(360));
          g.band(top, top.add(d.scale(4.2)), UP.cross(d).normalize(), .12, 0xffffff, m);
          g.band(
              c.add(0, .04, 0),
              c.add(0, .04, 0).add(d.scale(4.2 + 3 * ease((t - 38) / 4))),
              UP.cross(d).normalize(),
              .12,
              0xffffff,
              m);
        }
      }
    }
    if (t >= 38 && t < 45) {
      float sw = (t - 38) / 7;
      g.ring(c.add(0, .02, 0), r, f, R, R + 2.5 * sw + .4, 0xffffff, .9f * (1 - sw), 64);
    }
  }

  /** 空弦 · 断界: the sky's string is drawn back; released, it severs the world along a line. */
  private static void voidCut(Geo g, float t, Vec3 f, Vec3 r, Vec3 eye) {
    Vec3 blade = new Vec3(0, 1.4, 0).add(f.scale(.4));
    Vec3 A = f.scale(-10).add(r.scale(22)).add(0, 16, 0),
        B = f.scale(-10).add(r.scale(-22)).add(0, 16, 0);
    Vec3 mid = A.add(B).scale(.5);
    if (t < 34) {
      Vec3 M = t < 28 ? mid.lerp(blade, ease(t / 26)) : blade.lerp(mid, Math.min(1, (t - 28) / 2));
      float a = (t < 28 ? .95f : 1 - (t - 28) / 6) * fadeIn(t, 0, 4);
      g.beam(A, M, 1.4, 0xa66bff, .25f * a, eye);
      g.beam(M, B, 1.4, 0xa66bff, .25f * a, eye);
      g.beam(A, M, .22, 0xf2e6ff, a, eye);
      g.beam(M, B, .22, 0xf2e6ff, a, eye);
    }
    if (t >= 28) {
      float L = (float) (48 * ease((t - 28) / 4));
      float wallA = t < 40 ? fadeOut(t, 34, 40) : .6f * fadeIn(t, 40, 41) * fadeOut(t, 42, 47);
      if (wallA > 0) {
        g.wall(Vec3.ZERO, f, L, 30, 0xffffff, .85f * wallA, 48);
        g.wall(r.scale(.45), f, L, 34, 0xa66bff, .32f * wallA, 32);
        g.wall(r.scale(-.45), f, L, 34, 0xa66bff, .32f * wallA, 32);
        Vec3 edge = f.scale(L);
        g.band(edge, edge.add(0, 30, 0), r, .7, 0xffffff, wallA);
      }
      float scar = fadeOut(t, 54, 62);
      g.band(new Vec3(0, .06, 0), f.scale(48).add(0, .06, 0), r, .8, 0x7a3dff, .6f * scar);
      g.band(new Vec3(0, .07, 0), f.scale(48).add(0, .07, 0), r, .22, 0xffffff, .85f * scar);
      double w = t < 40 ? .7 : .7 * Math.max(0, 1 - (t - 40) / 14);
      float fis = fadeOut(t, 50, 62);
      if (w > 0) {
        Vec3 s0 = new Vec3(0, 26, 0), s1 = f.scale(48).add(0, 34, 0);
        g.bolt(s0, s1, w * 3.6, 0xa66bff, .3f * fis, eye, 77, 14);
        g.bolt(s0, s1, w, 0xffffff, fis, eye, 77, 14);
      }
      if (t >= 40 && t < 50) {
        float sw = (t - 40) / 10;
        for (int i = 0; i < 6; i++)
          g.ring(
              f.scale(6 + 8 * i).add(0, .08, 0),
              r,
              f,
              3.4 * sw,
              3.4 * sw + .5,
              0xd2b6ff,
              .7f * (1 - sw),
              24);
      }
    }
  }

  /** 胧月一闪 (full charge): a smaller moon, the dash written in light, the sheath's ring. */
  private static void iaido(Geo g, State s, float t, Vec3 f, Vec3 r, Vec3 eye, List<Vec3> trail) {
    float moonA = fadeIn(t, 0, 8) * fadeOut(t, 36, 48);
    Vec3 moon = f.scale(-10).add(0, 2.5 + 4 * ease(t / 12), 0);
    g.disc(moon, r, UP, 4.2, 0xfffaff, 0xd8c8ff, .85f * moonA, 36);
    g.ring(moon, r, UP, 4.2, 6.2, 0xbca6f4, .25f * moonA, 36);
    if (t >= 18) {
      float cut = fadeOut(t, 22, 32);
      g.band(moon.subtract(r.scale(7)), moon.add(r.scale(7)), UP, .3 * cut, 0xffffff, cut);
    }
    if (t >= 12 && trail.size() > 13) {
      Vec3 start = trail.get(12).subtract(s.origin);
      Vec3 end = trail.get(Math.min(trail.size() - 1, (int) t)).subtract(s.origin);
      float a = fadeOut(t, 22, 34);
      if (start.distanceToSqr(end) > .05) {
        g.band(start.add(0, 1.05, 0), end.add(0, 1.05, 0), UP, 1.1, 0xbca6f4, .35f * a);
        g.band(start.add(0, 1.05, 0), end.add(0, 1.05, 0), UP, .18, 0xffffff, a);
      }
    }
    if (t >= 34 && t < 46) {
      float sw = (t - 34) / 12;
      Vec3 here = trail.isEmpty() ? Vec3.ZERO : trail.get(trail.size() - 1).subtract(s.origin);
      g.ring(
          here.add(0, .06, 0),
          r,
          f,
          5 * ease(sw),
          5 * ease(sw) + .35,
          0xd8c8ff,
          .8f * (1 - sw),
          48);
    }
  }

  // ---------------------------------------------------------------- math

  static Vec3 forward(float yaw) {
    return new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
  }

  private static Vec3 rotY(Vec3 v, double degrees) {
    double a = Math.toRadians(degrees), cs = Math.cos(a), sn = Math.sin(a);
    return new Vec3(v.x * cs - v.z * sn, v.y, v.x * sn + v.z * cs);
  }

  /** The horizontal direction d tipped toward the sky by `degrees`. */
  private static Vec3 tilt(Vec3 d, double degrees) {
    double a = Math.toRadians(degrees);
    return d.scale(Math.cos(a)).add(UP.scale(Math.sin(a)));
  }

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  private static float fadeIn(float t, float from, float to) {
    return Math.clamp((t - from) / (to - from), 0, 1);
  }

  private static float fadeOut(float t, float from, float to) {
    return 1 - Math.clamp((t - from) / (to - from), 0, 1);
  }

  private UltimateFx() {}
}

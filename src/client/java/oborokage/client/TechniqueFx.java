package oborokage.client;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * World light for the charged techniques and the blade riders; no damage logic. Everything scales
 * with the release's charge, so a quick tap and a full draw look as different as they hit.
 *
 * <ul>
 *   <li>赤霆 · 雷突: a lightning path along the assault, branches, a scorched line and a burst.
 *   <li>霜轮 · 霜环: a ring racing out over a frost glyph, ice spikes bursting up along it.
 *   <li>空弦 · 虚弦: a string of light in a dark halo that trembles after release, and a rift where it
 *       ends.
 *   <li>Riders: the frost echo ring, the tachi's shadow cut, lightning arcs and void sword-qi.
 * </ul>
 */
public final class TechniqueFx {
  private static final Vec3 UP = new Vec3(0, 1, 0);

  private record Fx(TechniqueEvent event, int age) {}

  private static final ArrayList<Fx> effects = new ArrayList<>();
  private static Object world;

  private static int life(int style) {
    return switch (style) {
      case 0 -> 11;
      case 1, 2 -> 16;
      case TechniqueEvent.RING -> 12;
      case TechniqueEvent.ECHO -> 9;
      case TechniqueEvent.ARC -> 7;
      case TechniqueEvent.EDGE -> 11;
      case TechniqueEvent.PLUNGE -> 16;
      case TechniqueEvent.BLOOM -> oborokage.BladeArts.BLOOM_TICKS;
      case TechniqueEvent.ICE -> oborokage.BladeArts.ICE_TICKS;
      default -> 0;
    };
  }

  public static void init() {
    ClientPlayNetworking.registerGlobalReceiver(
        TechniqueEvent.TYPE,
        (e, c) ->
            c.client()
                .execute(
                    () -> {
                      if (e.style() < 0
                          || e.style() > TechniqueEvent.LAST
                          || !Double.isFinite(e.origin().lengthSqr())
                          || !Double.isFinite(e.end().lengthSqr())
                          || e.origin().distanceToSqr(e.end()) > 32 * 32) return;
                      if (effects.size() >= 48) effects.removeFirst();
                      if (life(e.style()) > 0) effects.add(new Fx(e, 0));
                      if (e.style() < TechniqueEvent.RING) DualClient.technique(e);
                      arrived(e);
                    }));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (world != c.level) {
            effects.clear();
            world = c.level;
          }
          for (int i = effects.size() - 1; i >= 0; i--) {
            Fx f = effects.get(i);
            if (f.age >= life(f.event.style())) effects.remove(i);
            else {
              particles(c, f);
              effects.set(i, new Fx(f.event, f.age + 1));
            }
          }
        });
    LevelRenderEvents.COLLECT_SUBMITS.register(
        context -> {
          var c = Minecraft.getInstance();
          if (c.level == null || effects.isEmpty()) return;
          Vec3 camera = context.levelState().cameraRenderState.pos;
          var cfg = ClientConfig.get();
          boolean compat = cfg.trailShaderCompat;
          var type =
              compat
                  ? RenderTypes.entityTranslucentEmissive(Oborokage.id("textures/fx/white.png"))
                  : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true);
          float partial = oborokage.client.compat.ClientCompat.partialTick(context);
          int share = Math.max(1024, cfg.trailVertexBudget / Math.max(1, effects.size()));
          for (Fx f : List.copyOf(effects)) {
            if (f.event.origin().distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance)
              continue;
            float age = f.age + partial;
            Vec3 eye = camera.subtract(f.event.origin());
            var pose = context.poseStack();
            pose.pushPose();
            pose.translate(f.event.origin().subtract(camera));
            context
                .submitNodeCollector()
                .submitCustomGeometry(
                    pose,
                    type,
                    (p, v) ->
                        draw(new Geo(p, v, share, !compat).near(eye, 2.4), f.event, age, eye));
            pose.popPose();
          }
        });
  }

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  /** Draws one effect relative to its origin. */
  private static void draw(Geo g, TechniqueEvent e, float age, Vec3 eye) {
    Vec3 end = e.end().subtract(e.origin());
    float q = Math.clamp(e.charge(), 0, 1);
    float k = age / life(e.style()), fade = 1 - k;
    switch (e.style()) {
      case 0 -> storm(g, end, q, age, fade, eye, e.actor());
      case 1 -> frost(g, end.length(), q, age, fade, true);
      case 2 -> voidLine(g, end, q, age, fade, eye, true);
      case TechniqueEvent.RING -> frost(g, end.length(), .4f, age, fade, false);
      case TechniqueEvent.ECHO -> echo(g, end, age, fade, eye);
      case TechniqueEvent.ARC -> {
        BladeStyle st = BladeStyle.RAIKIRI;
        long seed = (long) age * 131L + e.actor();
        float flicker = (age % 2 < 1 ? 1 : .65f) * fade;
        g.bolt(Vec3.ZERO, end, .07, st.primary, .85f * flicker, eye, seed, 8);
        g.bolt(Vec3.ZERO, end, .02, st.accent, flicker, eye, seed, 8);
      }
      case TechniqueEvent.EDGE -> voidLine(g, end, .35f, age, fade, eye, false);
      case TechniqueEvent.PLUNGE -> plunge(g, end.length(), styleOf(e), age, fade, eye);
      case TechniqueEvent.BLOOM -> bloom(g, end.length(), age);
      case TechniqueEvent.ICE -> ice(g, end.length(), age);
      default -> {}
    }
  }

  /** The plunge's event carries the blade as ordinal / 4 in its charge. */
  private static BladeStyle styleOf(TechniqueEvent e) {
    int i = Math.round(e.charge() * 4);
    BladeStyle[] all = BladeStyle.values();
    return all[Math.clamp(i, 0, all.length - 1)];
  }

  /** First sight of an event: land the actor's plunge, note it in the codex, burst particles. */
  private static void arrived(TechniqueEvent e) {
    var c = Minecraft.getInstance();
    if (c.level == null || c.player == null) return;
    boolean local = e.actor() == c.player.getId();
    switch (e.style()) {
      case TechniqueEvent.PLUNGE -> {
        DualClient.landed(e.actor());
        BladeStyle st = styleOf(e);
        var r = c.level.getRandom();
        var particle =
            switch (st) {
              case WAKIZASHI -> Oborokage.PETAL;
              case RAIKIRI -> Oborokage.SPARK;
              case FROSTMOON -> Oborokage.SHARD;
              case VOIDSTRING -> Oborokage.MOTE;
              default -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            };
        for (int i = 0; i < 40; i++) {
          double a = r.nextDouble() * Math.PI * 2;
          c.level.addParticle(
              particle,
              e.origin().x,
              e.origin().y + .2,
              e.origin().z,
              Math.cos(a) * .45,
              .12 + r.nextDouble() * .25,
              Math.sin(a) * .45);
        }
      }
      case TechniqueEvent.BLOOM -> {
        if (local) Codex.see("moon_bloom");
      }
      case TechniqueEvent.ICE -> {
        if (local) Codex.see("frost_ice");
      }
      case TechniqueEvent.ARC -> {
        if (local
            && BladeStyle.of(c.player.getMainHandItem()) == BladeStyle.RAIKIRI
            && e.charge() >= 2) Codex.see("storm_mark");
      }
      default -> {}
    }
  }

  /** Lingering fields shed particles while they last. */
  private static void particles(Minecraft c, Fx f) {
    if (c.level == null) return;
    var e = f.event;
    var r = c.level.getRandom();
    double radius = e.end().subtract(e.origin()).length();
    if (e.style() == TechniqueEvent.BLOOM && f.age % 3 == 0) {
      double a = r.nextDouble() * Math.PI * 2, d = radius * Math.sqrt(r.nextDouble());
      c.level.addParticle(
          Oborokage.PETAL,
          e.origin().x + Math.cos(a) * d,
          e.origin().y + 1.6 + r.nextDouble(),
          e.origin().z + Math.sin(a) * d,
          0,
          -.02,
          0);
    }
    if (e.style() == TechniqueEvent.ICE && f.age % 4 == 0) {
      double a = r.nextDouble() * Math.PI * 2, d = radius * Math.sqrt(r.nextDouble());
      c.level.addParticle(
          net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
          e.origin().x + Math.cos(a) * d,
          e.origin().y + .2,
          e.origin().z + Math.sin(a) * d,
          0,
          .02,
          0);
    }
  }

  /** The plunge's landing: a shock ring, cracks and the blade's own mark. */
  private static void plunge(Geo g, double radius, BladeStyle st, float age, float fade, Vec3 eye) {
    Vec3 c = Vec3.ZERO, x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
    double r = radius * (.3 + .9 * ease(age / 5));
    g.ring(c, x, z, Math.max(0, r - .25), r, st.primary, .75f * fade, 48);
    g.ring(c, x, z, Math.max(0, r - .05), r, st.accent, fade, 48);
    double crack = radius * ease(age / 3);
    for (int i = 0; i < 8; i++) {
      double a = i * Math.PI / 4 + (i % 2) * .2;
      Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), side = new Vec3(-d.z, 0, d.x);
      g.band(
          c.add(0, .02, 0),
          d.scale(crack * (.6 + .4 * (i % 3) / 2.0)).add(0, .02, 0),
          side,
          .09,
          st.secondary,
          .8f * fade);
    }
    switch (st) {
      case RAIKIRI -> {
        long seed = (long) (age / 2) * 911L;
        if (age < 8) {
          g.bolt(new Vec3(0, 9, 0), c, .22, st.primary, .9f * fade, eye, seed, 10);
          g.bolt(new Vec3(0, 9, 0), c, .06, st.accent, fade, eye, seed, 10);
        }
      }
      case FROSTMOON -> {
        for (int i = 0; i < 12; i++) {
          double a = i * Math.PI / 6;
          Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
          float grow = ease((age - 1) / 3);
          g.spike(
              d.scale(radius * .7),
              d.scale(.3).add(UP),
              (.5 + .3 * (i % 3) / 2.0) * grow,
              .2,
              st.primary,
              .85f * fade);
        }
      }
      case VOIDSTRING -> {
        for (int k = 0; k < 3; k++) {
          double rr = radius * 1.8 * Math.max(0, 1 - (age + k * 3) / 12);
          if (rr > .2) g.ring(c, x, z, rr - .06, rr, st.primary, .7f * fade, 40);
        }
      }
      default -> {
        // 胧影 and 胧月: a crescent stands where the blade went in.
        Vec3 side = eye.cross(UP);
        side = side.lengthSqr() < 1e-4 ? x : side.normalize();
        double rise = 1.4 * ease(age / 4);
        g.crescent(
            c.add(0, rise * .3, 0),
            side,
            UP,
            1.3,
            .3,
            Math.toRadians(20),
            Math.toRadians(160),
            st.primary,
            .8f * fade,
            16);
      }
    }
  }

  /** 花阵: a slow ring of petals around a soft blush on the ground. */
  private static void bloom(Geo g, double radius, float age) {
    BladeStyle st = BladeStyle.WAKIZASHI;
    float life = oborokage.BladeArts.BLOOM_TICKS;
    float a = Math.min(1, age / 6) * Math.min(1, (life - age) / 10);
    Vec3 c = Vec3.ZERO, x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
    g.disc(c.add(0, .02, 0), x, z, radius, 0xfff0fb, st.primary, .14f * a, 32);
    g.ring(c, x, z, radius - .1, radius, st.primary, .7f * a, 48);
    for (int i = 0; i < 10; i++) {
      double ang = i * Math.PI / 5 + age * .03;
      g.crescent(c.add(0, .04, 0), x, z, radius * .82, .22, ang, ang + .4, 0xfff0fb, .6f * a, 6);
    }
  }

  /** 冰面: a pale glaze with a frost glyph and a hard rim. */
  private static void ice(Geo g, double radius, float age) {
    BladeStyle st = BladeStyle.FROSTMOON;
    float life = oborokage.BladeArts.ICE_TICKS;
    float a = Math.min(1, age / 4) * Math.min(1, (life - age) / 12);
    Vec3 c = Vec3.ZERO, x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
    g.disc(c.add(0, .02, 0), x, z, radius, 0xeefcff, st.primary, .22f * a, 40);
    g.ring(c, x, z, radius - .12, radius, st.accent, .8f * a, 48);
    for (int i = 0; i < 6; i++) {
      double ang = i * Math.PI / 3;
      Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang)), s = new Vec3(-d.z, 0, d.x);
      g.band(c.add(0, .03, 0), d.scale(radius * .85).add(0, .03, 0), s, .06, st.accent, .55f * a);
    }
  }

  /** 雷突: the path the assault burned through the air, from waist height. */
  private static void storm(Geo g, Vec3 end, float q, float age, float fade, Vec3 eye, int actor) {
    BladeStyle st = BladeStyle.RAIKIRI;
    Vec3 a = new Vec3(0, -.6, 0), b = end.add(0, -.6, 0);
    float size = .5f + .5f * q;
    long seed = (long) (age / 2) * 977L + actor;
    float flicker = (((int) age) % 3 == 0 ? .7f : 1) * fade;
    // The path grows out from the blade in the first two ticks.
    Vec3 head = a.lerp(b, ease(age / 2.5f));
    g.bolt(a, head, .13 * size, st.primary, .8f * flicker, eye, seed, 14);
    g.bolt(a, head, .035 * size, st.accent, flicker, eye, seed, 14);
    Random r = new Random(seed);
    int branches = 2 + (int) (5 * q);
    for (int i = 0; i < branches; i++) {
      Vec3 from = a.lerp(head, r.nextDouble());
      Vec3 to =
          from.add(
              (r.nextDouble() - .5) * 2.2 * size,
              (r.nextDouble() - .2) * 1.4 * size,
              (r.nextDouble() - .5) * 2.2 * size);
      g.bolt(from, to, .025 * size, st.secondary, .75f * flicker, eye, seed + i, 5);
    }
    // A scorched line on the ground, slow to cool.
    Vec3 dir = b.subtract(a);
    Vec3 side = dir.cross(UP);
    if (side.lengthSqr() > 1e-4) {
      g.band(a.add(0, -.95, 0), head.add(0, -.95, 0), side, .5 * size, 0x5a1408, .5f * fade);
      g.band(a.add(0, -.94, 0), head.add(0, -.94, 0), side, .12 * size, st.primary, .6f * fade);
    }
    // The burst where it stopped.
    if (age >= 2) {
      float k = Math.clamp((age - 2) / 6, 0, 1);
      double len = (.8 + q) * (.5 + .5 * k);
      g.beam(b.add(-len, 0, 0), b.add(len, 0, 0), .06 * (1 - k), st.primary, .9f * (1 - k), eye);
      g.beam(b.add(0, -len, 0), b.add(0, len, 0), .06 * (1 - k), st.primary, .9f * (1 - k), eye);
      g.ring(
          b,
          new Vec3(1, 0, 0),
          new Vec3(0, 0, 1),
          len * .7,
          len * .8,
          st.accent,
          .8f * (1 - k),
          24);
    }
  }

  /** 霜环: an outward ring over a frost glyph; ice spikes erupt along it. */
  private static void frost(Geo g, double radius, float q, float age, float fade, boolean full) {
    BladeStyle st = BladeStyle.FROSTMOON;
    Vec3 c = new Vec3(0, -1.5, 0), x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
    double r = radius * (.3 + .7 * ease(age / 5));
    float ringA = fade * (age < 8 ? 1 : fade);
    g.ring(c, x, z, Math.max(0, r - .18), r, st.primary, .7f * ringA, 48);
    g.ring(c, x, z, Math.max(0, r - .04), r, st.accent, ringA, 48);
    if (full) {
      // The frost glyph: six spokes, each with a pair of barbs.
      for (int i = 0; i < 6; i++) {
        double a = i * Math.PI / 3;
        Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), s = new Vec3(-d.z, 0, d.x);
        double len = radius * .55 * ease(age / 4);
        g.band(c.add(0, .01, 0), c.add(d.scale(len)).add(0, .01, 0), s, .07, st.accent, .6f * fade);
        Vec3 m = c.add(d.scale(len * .6)).add(0, .01, 0);
        g.band(m, m.add(d.add(s).scale(len * .2)), d.subtract(s), .05, st.accent, .5f * fade);
        g.band(m, m.add(d.subtract(s).scale(len * .2)), d.add(s), .05, st.accent, .5f * fade);
      }
    }
    // Spikes along the ring once it has passed, growing fast and melting slowly.
    int count = full ? 10 + (int) (14 * q) : 8;
    for (int i = 0; i < count; i++) {
      double a = i * Math.PI * 2 / count + (i % 2) * .12;
      double at = radius * (.9 + .1 * ((i * 7) % 3) / 2.0);
      if (r < at * .95) continue;
      float grow = ease((age - 2 - (float) (i % 3) * .5f) / 3);
      Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
      double len = (full ? .5 + .7 * q : .4) * (.7 + .3 * ((i * 5) % 4) / 3.0) * grow;
      g.spike(c.add(d.scale(at)), d.scale(.4).add(UP), len, .22, st.primary, .85f * fade);
    }
  }

  /** 虚弦 and 虚刃: a string of light in a dark halo; it trembles after release. */
  private static void voidLine(
      Geo g, Vec3 end, float q, float age, float fade, Vec3 eye, boolean full) {
    BladeStyle st = BladeStyle.VOIDSTRING;
    Vec3 dir = end.normalize();
    Vec3 side = dir.cross(UP);
    if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
    side = side.normalize();
    Vec3 a = dir.scale(Math.min(.9, end.length() * .4));
    Vec3 b = a.lerp(end, ease(age / 2));
    float size = .6f + .5f * q;
    g.beam(a, b, .55 * size, st.secondary, .55f * fade, eye);
    g.beam(a, b, .16 * size, st.primary, .85f * fade, eye);
    // The core trembles like a plucked string, the tremor dying away.
    int n = 14;
    Vec3 prev = a;
    double amp = .12 * size * Math.max(0, 1 - age / 10);
    for (int i = 1; i <= n; i++) {
      double u = i / (double) n;
      Vec3 p = a.lerp(b, u).add(side.scale(Math.sin(Math.PI * u) * Math.sin(age * 2.6) * amp));
      g.beam(prev, p, .035 * size, 0xffffff, fade, eye);
      prev = p;
    }
    if (full && age >= 2) {
      // A rift opens where the string ends.
      float open = ease((age - 2) / 3) * fade;
      double h = (.6 + .6 * q) * open;
      g.beam(b.add(0, -h, 0), b.add(0, h, 0), .3 * open, st.secondary, .8f * open, eye);
      g.beam(b.add(0, -h, 0), b.add(0, h, 0), .05, 0xffffff, open, eye);
    }
  }

  /** 残月: the tachi's shadow cut, a crescent through the struck enemy. */
  private static void echo(Geo g, Vec3 away, float age, float fade, Vec3 eye) {
    BladeStyle st = BladeStyle.TACHI;
    Vec3 f = new Vec3(away.x, 0, away.z);
    if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
    f = f.normalize();
    Vec3 right = f.cross(UP);
    double roll = Math.toRadians(50);
    Vec3 s = right.scale(Math.cos(roll)).add(UP.scale(Math.sin(roll)));
    Vec3 c = f.scale(-1.1);
    double sweep = Math.toRadians(70) * ease(age / 3);
    g.crescent(c, f, s, 1.1, .32, sweep, -sweep, st.primary, .85f * fade, 16);
    g.crescent(c, f, s, 1.14, .07, sweep, -sweep, st.accent, fade, 16);
    g.crescent(c, f, s, 1.0, .18, sweep * .8, -sweep * .8, st.secondary, .4f * fade, 12);
  }

  private TechniqueFx() {}
}

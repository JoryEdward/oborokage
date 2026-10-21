package oborokage.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * The light of every plain cut: dual chain, counter, single-blade chain, techniques and the tachi
 * ultimate's three cuts. A cut is a swept sheet with a bright edge, a white core and afterimages,
 * and each blade dresses it in its own way:
 *
 * <ul>
 *   <li>胧影: a heavy moonlit crescent trailed by two hazy shadows.
 *   <li>胧月: a thin, fast pearl edge shedding petals.
 *   <li>赤霆: a jagged, flickering lightning edge throwing branches and sparks.
 *   <li>霜轮: a pale band that leaves a fringe of ice spikes behind it.
 *   <li>空弦: a hair-thin string in a dark halo that hangs on as a trembling thread.
 * </ul>
 *
 * The closing cut of a chain is larger and brighter and sends a ring across the ground. Impacts
 * flare at the struck point; the local player also feels them as a short hitstop and camera kick.
 */
final class SlashFx {
  private static final Vec3 UP = new Vec3(0, 1, 0);

  /** A cut's plane and sweep, relative to the actor's feet and the scene heading (degrees). */
  record Cut(double roll, double from, double to, double radius, double height) {
    double sweep() {
      return Math.abs(to - from);
    }
  }

  private record Impact(Vec3 at, BladeStyle style, boolean big, int born) {}

  private static final ArrayDeque<Impact> impacts = new ArrayDeque<>();
  private static int clock;

  /**
   * Cuts are written for the hand that makes them: positive angles start on that hand's side, and
   * the frame mirrors for the left hand, so the cross's second cut is simply the first one again.
   */
  static Cut cut(int move, int window, BladeStyle style) {
    return switch (move) {
      case Move.MAIN -> new Cut(12, 75, -75, 2.7, 1.15);
      case Move.OFF -> new Cut(-18, 75, -75, 2.1, 1.1);
      case Move.CROSS -> new Cut(38, 82, -82, 3.0, 1.2);
      case Move.COUNTER ->
          window == 0 ? new Cut(-20, 85, -85, 3.0, 1.1) : new Cut(50, 85, -85, 3.1, 1.2);
      case Move.ULTIMATE ->
          window < 2 ? new Cut(25, 85, -85, 3.4, 1.2) : new Cut(90, 110, -35, 3.4, 1.4);
      case Move.ULT_MOON ->
          window < 8
              ? new Cut(window % 2 == 0 ? 16 : -16, 180, -180, 3.2, 1.1)
              : new Cut(0, 180, -180, 4.2, .9);
      case Move.ULT_FROST -> new Cut(0, 180, -180, 4.5, 1.0);
      case Move.FROST -> new Cut(0, 180, -180, 3.0, 1.0);
      case Move.SOLO_1 -> new Cut(40, 80, -80, 2.7, 1.2);
      case Move.SOLO_2 -> new Cut(22, -80, 80, 2.7, 1.1);
      case Move.SOLO_3 ->
          style == BladeStyle.WAKIZASHI
              ? new Cut(6, 180, -180, 2.8, 1.1)
              : new Cut(8, 110, -110, 3.2, 1.15);
      default -> new Cut(15, 75, -75, 2.6, 1.15);
    };
  }

  /** Ticks for the edge to cross the whole sweep; the short blade is quicker. */
  private static float duration(Cut cut, BladeStyle style) {
    float d = 3.5f * (float) Math.max(1, cut.sweep() / 160);
    return style == BladeStyle.WAKIZASHI ? d * .75f : d;
  }

  /** How long the light outlives the edge: ice and the void thread hang on. */
  private static float linger(BladeStyle style) {
    return switch (style) {
      case FROSTMOON -> 10;
      case VOIDSTRING -> 12;
      case WAKIZASHI -> 4;
      default -> 6;
    };
  }

  /** One live cut on a scene, resolved for drawing or particles. */
  private record Live(
      DualClient.Scene scene,
      Entity actor,
      Cut cut,
      BladeStyle style,
      boolean big,
      int hand,
      float t,
      float dur,
      float yaw,
      float pitch,
      int[] pal,
      boolean honed) {}

  /** 绯月 inscription: crimson light edged in gold, whatever the blade. */
  private static final int[] CRIMSON = {0xff3b4a, 0x7a0f22, 0xffd27a};

  private static int[] palette(BladeStyle style, net.minecraft.world.item.ItemStack stack) {
    if (Inscription.has(stack, Inscription.CRIMSON)) return CRIMSON;
    return new int[] {style.primary, style.secondary, style.accent};
  }

  private static List<Live> live(Minecraft c, float partial) {
    List<Live> out = new ArrayList<>();
    for (var scene : List.copyOf(DualClient.scenes.values())) {
      int id = scene.event.move();
      if (id == Move.GUARD || id == Move.DODGE) continue;
      // The other ultimates draw their own light in UltimateFx.
      if (Move.ultimate(id) && id != Move.ULTIMATE && id != Move.ULT_MOON && id != Move.ULT_FROST)
        continue;
      Entity e = c.level.getEntity(scene.event.actor());
      if (e == null) continue;
      // Plain cuts follow the view; ultimates keep the heading they were cast with.
      boolean plain = !Move.ultimate(id);
      boolean eyes = e == c.player && firstPerson(c);
      BladeStyle main =
          e instanceof Player p ? BladeStyle.of(p.getMainHandItem()) : BladeStyle.TACHI;
      if (main == null) main = BladeStyle.TACHI;
      Move move = Move.of(id);
      float age = scene.time(partial);
      if (plain && (scene.cutYaw == null || scene.cutYaw.length != move.hits().length)) {
        scene.cutYaw = new float[move.hits().length];
        scene.cutPitch = new float[move.hits().length];
        Arrays.fill(scene.cutYaw, Float.NaN);
      }
      for (int w = 0; w < move.hits().length; w++) {
        int logical = move.hands()[w];
        BladeStyle style = logical == 1 && !Move.ultimate(id) ? BladeStyle.WAKIZASHI : main;
        Cut cut = cut(id, w, style);
        float dur = duration(cut, style);
        float t = age - (move.hits()[w] - 2);
        if (t < 0 || t > dur + linger(style)) continue;
        boolean big =
            Move.finisher(id) && w == move.hits().length - 1
                || id == Move.ULTIMATE
                || id == Move.ULT_MOON && w == 8;
        int hand =
            e instanceof Player p && p.getMainArm() == HumanoidArm.LEFT ? 1 - logical : logical;
        float yaw = scene.event.yaw(), pitch = 0;
        if (plain) {
          // While the edge travels the cut turns with the view; once drawn it stays where it was
          // cut. Seen through the player's own eyes it always stays in front of them.
          if (eyes || t <= dur || Float.isNaN(scene.cutYaw[w])) {
            scene.cutYaw[w] = e.getViewYRot(partial);
            scene.cutPitch[w] = e.getViewXRot(partial);
          }
          yaw = scene.cutYaw[w];
          pitch = scene.cutPitch[w];
        }
        var stack =
            e instanceof Player pl
                ? logical == 1 && !Move.ultimate(id) ? pl.getOffhandItem() : pl.getMainHandItem()
                : net.minecraft.world.item.ItemStack.EMPTY;
        out.add(
            new Live(
                scene,
                e,
                cut,
                style,
                big,
                hand,
                t,
                dur,
                yaw,
                pitch,
                palette(style, stack),
                Hone.honed(stack, c.level.getGameTime())));
      }
    }
    return out;
  }

  // ------------------------------------------------------------------ geometry

  private static Vec3 forward(float yaw) {
    double r = Math.toRadians(yaw);
    return new Vec3(-Math.sin(r), 0, Math.cos(r));
  }

  /**
   * The cut's frame relative to the actor: centre, the axis through the middle of the sweep, the
   * in-plane side axis, and a radius scale.
   */
  private record Frame(Vec3 center, Vec3 f, Vec3 s, double scale) {
    Vec3 at(double angle, double r) {
      double a = Math.toRadians(angle);
      return center.add(f.scale(Math.cos(a) * r)).add(s.scale(Math.sin(a) * r));
    }
  }

  /** The local player's own cuts while looking through their eyes. */
  private static boolean firstPerson(Live l) {
    var c = Minecraft.getInstance();
    return l.actor == c.player && firstPerson(c);
  }

  private static boolean firstPerson(Minecraft c) {
    return c.options.getCameraType().isFirstPerson() && !Cinematics.cameraActive();
  }

  private static Frame frame(Live l) {
    Vec3 f = forward(l.yaw);
    Vec3 right = f.cross(UP);
    Vec3 up = UP;
    // The whole cut tips up or down with the eyes.
    if (l.pitch != 0) {
      double pitch = Math.toRadians(l.pitch);
      f = f.scale(Math.cos(pitch)).add(0, -Math.sin(pitch), 0);
      up = right.cross(f);
    }
    // A left-handed player's cuts mirror; a cut of the left hand leaves from the left shoulder.
    if (l.hand == 1) right = right.scale(-1);
    double roll = Math.toRadians(l.cut.roll());
    Vec3 s = right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
    if (firstPerson(l)) {
      // Through the eyes a cut is a crescent across the view, on a plane that faces the camera.
      Vec3 middle = up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll)));
      Vec3 center = new Vec3(0, 1.42, 0).add(f.scale(1.8)).subtract(middle.scale(.3));
      return new Frame(center, middle, s, .42);
    }
    // A cut whose plane holds the line of sight is a hairline from behind the shoulder; tipping
    // the far side up turns its face to the usual camera. Spins tip less, to stay above ground.
    double tilt = Math.toRadians(l.cut.sweep() >= 300 ? 12 : 22);
    Vec3 middle = f.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
    Vec3 side = s.subtract(middle.scale(s.dot(middle))).normalize();
    Vec3 center = new Vec3(0, l.cut.height() - .15, 0).add(right.scale(.22));
    return new Frame(center, middle, side, 1);
  }

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  // ------------------------------------------------------------------ render

  static void submit(LevelRenderContext context) {
    var c = Minecraft.getInstance();
    if (c.level == null) return;
    Vec3 camera = context.levelState().cameraRenderState.pos;
    float partial = oborokage.client.compat.ClientCompat.partialTick(context);
    var cfg = ClientConfig.get();
    List<Live> cuts = live(c, partial);
    cuts.removeIf(
        l -> l.actor.position().distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance);
    if (cuts.size() > cfg.trailMaxPlayers * 3) cuts = cuts.subList(0, cfg.trailMaxPlayers * 3);
    boolean compat = cfg.trailShaderCompat;
    var type =
        compat
            ? RenderTypes.entityTranslucentEmissive(Oborokage.id("textures/fx/white.png"))
            : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true);
    int share = cfg.trailVertexBudget / Math.max(1, cuts.size() + (impacts.isEmpty() ? 0 : 1));
    for (Live l : cuts) {
      Vec3 origin = l.actor.getPosition(partial);
      Vec3 eye = camera.subtract(origin);
      var pose = context.poseStack();
      pose.pushPose();
      pose.translate(origin.subtract(camera));
      context
          .submitNodeCollector()
          .submitCustomGeometry(
              pose,
              type,
              (p, v) ->
                  draw(
                      new Geo(p, v, share, !compat).near(eye, firstPerson(l) ? .9 : 2.2),
                      l,
                      eye,
                      cfg));
      pose.popPose();
    }
    if (!impacts.isEmpty()) {
      float now = clock + partial;
      List<Impact> list = List.copyOf(impacts);
      var pose = context.poseStack();
      pose.pushPose();
      pose.translate(camera.scale(-1));
      context
          .submitNodeCollector()
          .submitCustomGeometry(
              pose,
              type,
              (p, v) -> {
                Geo g = new Geo(p, v, share, !compat).near(camera, 1.6);
                for (Impact i : list) flare(g, i, now - i.born, camera);
              });
      pose.popPose();
    }
  }

  private static void draw(Geo g, Live l, Vec3 eye, ClientConfig.Values cfg) {
    Frame fr = frame(l);
    BladeStyle st = l.style;
    float t = l.t, dur = l.dur;
    float head = ease(t / dur), tail = ease((t - 2) / (dur + 1.5f));
    // The void's string spans the whole cut and hangs there until it fades.
    if (st == BladeStyle.VOIDSTRING) tail = 0;
    float fade = t <= dur ? 1 : Math.max(0, 1 - (t - dur) / linger(st));
    // A honed edge burns brighter.
    if (l.honed) fade = Math.min(1.25f, fade * 1.25f);
    // Through the player's own eyes the light sits an arm's length away: keep it fine.
    float big = (l.big ? 1.35f : 1) * (fr.scale() < 1 ? .35f : 1);
    double r = l.cut.radius() * (l.big ? 1.12 : 1) * fr.scale();
    double from = l.cut.from(), span = l.cut.to() - from;
    int n = Math.max(6, (int) (cfg.trailSegments * Math.max(.4, l.cut.sweep() / 180)));
    double edgeW =
        .2
            * big
            * switch (st) {
              case WAKIZASHI -> .6;
              case VOIDSTRING -> .35;
              case TACHI -> 1.25;
              default -> 1;
            };
    boolean edgeVisible = t <= dur + 2 || st == BladeStyle.FROSTMOON || st == BladeStyle.VOIDSTRING;
    // 1. The swept sheet: transparent near the hilt, brightest under the edge, fading to the tail.
    if (head > tail) {
      float sheetA = (st == BladeStyle.VOIDSTRING ? .5f : .34f) * fade * (l.big ? 1.15f : 1);
      int dark = st == BladeStyle.VOIDSTRING ? l.pal[1] : l.pal[0];
      for (int i = 0; i < n; i++) {
        double u0 = tail + (head - tail) * i / (double) n,
            u1 = tail + (head - tail) * (i + 1) / (double) n;
        float k0 = (float) ((u0 - tail) / (head - tail)),
            k1 = (float) ((u1 - tail) / (head - tail));
        double a0 = from + span * u0, a1 = from + span * u1;
        int o0 = Geo.argb(dark, sheetA * k0), o1 = Geo.argb(dark, sheetA * k1);
        int in = Geo.argb(l.pal[1], 0);
        g.quad(fr.at(a0, r * .42), fr.at(a0, r), fr.at(a1, r), fr.at(a1, r * .42), in, o0, o1, in);
      }
    }
    // 2. Afterimages: the edge a moment behind, fainter and a little inside.
    int echoes = st == BladeStyle.TACHI ? 2 : st == BladeStyle.WAKIZASHI ? 0 : 1;
    for (int k = 1; k <= echoes && t <= dur + 3; k++) {
      float lag = .16f * k;
      float eh = Math.max(0, head - lag), et = Math.max(0, tail - lag);
      if (eh > et)
        edge(
            g,
            fr,
            from,
            span,
            et,
            eh,
            r * (1 - .06 * k),
            edgeW * .8,
            l.pal[1],
            .42f * fade / k,
            n / 2,
            eye);
    }
    // 3. The edge itself and its white core.
    if (edgeVisible && head > tail) {
      switch (st) {
        case RAIKIRI -> {
          // Jagged and re-struck every tick, with short branches flying off the arc.
          long seed = (long) (t * 3) * 7919L + l.scene.event.actionId();
          bolt(g, fr, from, span, tail, head, r, edgeW * .7, l.pal[0], .9f * fade, eye, seed);
          bolt(g, fr, from, span, tail, head, r, edgeW * .2, l.pal[2], fade, eye, seed);
          Random rnd = new Random(seed);
          int branches = l.big ? 5 : 3;
          for (int b = 0; b < branches; b++) {
            double u = tail + (head - tail) * rnd.nextDouble();
            Vec3 p0 = fr.at(from + span * u, r);
            Vec3 out = p0.subtract(fr.center).normalize();
            Vec3 p1 =
                p0.add(out.scale((.4 + rnd.nextDouble() * .7) * fr.scale()))
                    .add(0, (rnd.nextDouble() - .3) * .6 * fr.scale(), 0);
            g.bolt(p0, p1, .03 * big, l.pal[1], .8f * fade, eye, seed + b, 4);
          }
        }
        case VOIDSTRING -> {
          // A dark halo around a hair-thin string; once cut, the thread trembles and decays.
          float settle = Math.max(0, t - dur);
          double wobble = settle > 0 ? Math.sin(t * 2.4) * .06 * (1 - settle / linger(st)) : 0;
          edge(
              g,
              fr,
              from,
              span,
              tail,
              head,
              r + wobble,
              edgeW * 3.2,
              l.pal[1],
              .55f * fade,
              n,
              eye);
          edge(g, fr, from, span, tail, head, r + wobble, edgeW, l.pal[0], .95f * fade, n, eye);
          edge(g, fr, from, span, tail, head, r + wobble, .018 * big, 0xffffff, fade, n, eye);
        }
        default -> {
          edge(g, fr, from, span, tail, head, r, edgeW, l.pal[0], .9f * fade, n, eye);
          edge(g, fr, from, span, tail, head, r, edgeW * .22, l.pal[2], fade, n, eye);
        }
      }
    }
    // 4. Frost leaves spikes along the path; they grow as the edge passes and melt slowly.
    if (st == BladeStyle.FROSTMOON) {
      int spikes = (int) (l.cut.sweep() / 14);
      for (int i = 0; i < spikes; i++) {
        double u = (i + .5) / spikes;
        if (u > head) break;
        float grow = Math.min(1, (float) (head - u) * 6);
        Vec3 base = fr.at(from + span * u, r - .05);
        Vec3 out = base.subtract(fr.center).normalize().add(0, .25, 0);
        double len = (.3 + .25 * ((i * 37) % 5) / 4.0) * grow * big;
        g.spike(base, out, len, .14 * big, l.pal[0], .85f * fade);
      }
    }
    // 5. A glint rides the edge's head.
    if (t <= dur + 1) {
      Vec3 tip = fr.at(from + span * head, r);
      float glint = (t <= dur ? 1 : 1 - (t - dur)) * (l.big ? 1.3f : 1) * (float) fr.scale();
      // A small four-point star turned to the eye, not a world-axis cross.
      Vec3 view = eye.subtract(tip).normalize();
      Vec3 across = view.cross(UP);
      if (across.lengthSqr() < 1e-4) across = new Vec3(1, 0, 0);
      across = across.normalize();
      Vec3 rise = across.cross(view).normalize();
      double s = .3 * glint;
      g.disc(tip, across, rise, .1 * glint, l.pal[2], l.pal[0], .45f, 10);
      g.beam(
          tip.subtract(across.scale(s)),
          tip.add(across.scale(s)),
          .025 * glint,
          l.pal[2],
          .85f,
          eye);
      g.beam(
          tip.subtract(rise.scale(s * .6)),
          tip.add(rise.scale(s * .6)),
          .02 * glint,
          l.pal[2],
          .7f,
          eye);
    }
    // 6. A finisher sends a ring across the ground.
    if (l.big && t >= dur * .6 && fr.scale() >= 1) {
      float k = Math.min(1, (t - dur * .6f) / 7);
      double ring = .6 + 3.4 * Math.sqrt(k);
      Vec3 c = new Vec3(0, .06, 0), x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
      g.ring(c, x, z, ring - .18, ring, l.pal[0], .7f * (1 - k), 40);
      g.ring(c, x, z, ring - .04, ring, l.pal[2], .9f * (1 - k), 40);
    }
  }

  /**
   * A band along the arc between two sweep fractions, swelling toward the head. It lies in the
   * cut's plane and is doubled by a ribbon turned to the eye, so a cut seen edge-on (a level cut
   * from behind the shoulder) still reads as a blade of light rather than a hairline.
   */
  private static void edge(
      Geo g,
      Frame fr,
      double from,
      double span,
      double tail,
      double head,
      double r,
      double w,
      int rgb,
      float alpha,
      int n,
      Vec3 eye) {
    if (head <= tail || alpha <= 0) return;
    n = Math.max(3, n);
    Vec3 prev = null;
    for (int i = 0; i < n; i++) {
      double k0 = i / (double) n, k1 = (i + 1) / (double) n;
      double u0 = tail + (head - tail) * k0, u1 = tail + (head - tail) * k1;
      double w0 = w * (.2 + .8 * k0), w1 = w * (.2 + .8 * k1);
      double a0 = from + span * u0, a1 = from + span * u1;
      float f0 = alpha * (float) (.15 + .85 * k0), f1 = alpha * (float) (.15 + .85 * k1);
      int c0 = Geo.argb(rgb, f0), c1 = Geo.argb(rgb, f1);
      g.quad(
          fr.at(a0, r - w0),
          fr.at(a0, r + w0 * .35),
          fr.at(a1, r + w1 * .35),
          fr.at(a1, r - w1),
          c0,
          c0,
          c1,
          c1);
      Vec3 p0 = prev == null ? fr.at(a0, r) : prev, p1 = fr.at(a1, r);
      g.beam(p0, p1, w1 * .55, rgb, f1 * .8f, eye);
      prev = p1;
    }
  }

  /** The thunder edge: a jagged polyline along the arc, facing the eye. */
  private static void bolt(
      Geo g,
      Frame fr,
      double from,
      double span,
      double tail,
      double head,
      double r,
      double w,
      int rgb,
      float alpha,
      Vec3 eye,
      long seed) {
    Random rnd = new Random(seed);
    int n = 16;
    Vec3 prev = null;
    for (int i = 0; i <= n; i++) {
      double u = tail + (head - tail) * i / n;
      double jitter = i == 0 || i == n ? 0 : (rnd.nextDouble() - .5) * .35 * fr.scale();
      Vec3 p =
          fr.at(from + span * u, r + jitter).add(0, (rnd.nextDouble() - .5) * .18 * fr.scale(), 0);
      if (prev != null) g.beam(prev, p, w * (.4 + .6 * i / n), rgb, alpha, eye);
      prev = p;
    }
  }

  /** A struck point: crossed rays turned to the eye and a quick ring. */
  private static void flare(Geo g, Impact i, float age, Vec3 camera) {
    float life = i.big ? 7 : 5;
    if (age < 0 || age > life) return;
    float k = age / life, a = 1 - k;
    Vec3 c = i.at, eye = camera;
    // A flare right in front of the eyes stays small instead of crossing the whole view.
    double near = Math.clamp(c.distanceTo(eye) / 5, .3, 1);
    double len = (i.big ? 1.1 : .7) * (.6 + .6 * Math.sqrt(k)) * near;
    BladeStyle st = i.style;
    double tilt = st == BladeStyle.RAIKIRI ? .5 : st == BladeStyle.VOIDSTRING ? .15 : .35;
    Vec3 d1 = new Vec3(Math.cos(tilt), Math.sin(tilt), 0).scale(len);
    Vec3 d2 = new Vec3(-Math.sin(tilt), Math.cos(tilt), 0).scale(len * .6);
    Vec3 view = eye.subtract(c).normalize();
    // Turn the rays into the plane facing the eye.
    Vec3 side = view.cross(UP);
    if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
    side = side.normalize();
    Vec3 up = side.cross(view).normalize();
    Vec3 r1 = side.scale(d1.x).add(up.scale(d1.y)), r2 = side.scale(d2.x).add(up.scale(d2.y));
    g.disc(
        c, side, up, (i.big ? .5 : .32) * (1 - .5 * k) * near, st.accent, st.primary, .35f * a, 16);
    g.beam(c.subtract(r1), c.add(r1), .05 * a * near, st.primary, .8f * a, eye);
    g.beam(c.subtract(r1.scale(.7)), c.add(r1.scale(.7)), .014, st.accent, a, eye);
    g.beam(c.subtract(r2), c.add(r2), .035 * a * near, st.primary, .6f * a, eye);
    double ring = ((i.big ? .9 : .55) * Math.sqrt(k) + .1) * near;
    g.ring(c, side, up, ring * .85, ring, st.primary, .55f * a, 24);
  }

  // ------------------------------------------------------------------ ticking

  static void impact(int actor, Vec3 at, boolean big) {
    var c = Minecraft.getInstance();
    if (c.level == null) return;
    Entity e = c.level.getEntity(actor);
    BladeStyle st = e instanceof Player p ? BladeStyle.of(p.getMainHandItem()) : null;
    if (impacts.size() >= 32) impacts.removeFirst();
    impacts.addLast(new Impact(at, st == null ? BladeStyle.TACHI : st, big, clock));
  }

  static void tick(Minecraft c) {
    clock++;
    impacts.removeIf(i -> clock - i.born > 8);
    if (c.level == null || c.player == null) {
      impacts.clear();
      return;
    }
    RandomSource rnd = c.level.getRandom();
    for (Live l : live(c, 0)) {
      if (l.actor.distanceToSqr(c.player) > 48 * 48 || l.t > l.dur + 1) continue;
      Frame fr = frame(l);
      float head = ease(l.t / l.dur);
      if (fr.scale() < 1) continue;
      double r = l.cut.radius() * (l.big ? 1.12 : 1);
      Vec3 base = l.actor.position();
      Vec3 tip = base.add(fr.at(l.cut.from() + (l.cut.to() - l.cut.from()) * head, r));
      Vec3 out = tip.subtract(base.add(fr.center)).normalize();
      int count = l.big ? 5 : 2;
      ParticleOptions p =
          switch (l.style) {
            case WAKIZASHI -> Oborokage.PETAL;
            case RAIKIRI -> Oborokage.SPARK;
            case FROSTMOON -> rnd.nextBoolean() ? Oborokage.SHARD : ParticleTypes.SNOWFLAKE;
            case VOIDSTRING -> Oborokage.MOTE;
            default -> new net.minecraft.core.particles.DustParticleOptions(l.style.primary, .7f);
          };
      double speed = l.style == BladeStyle.RAIKIRI ? .25 : l.style == BladeStyle.TACHI ? .03 : .08;
      for (int i = 0; i < count; i++)
        c.level.addParticle(
            p,
            tip.x + rnd.nextGaussian() * .12,
            tip.y + rnd.nextGaussian() * .12,
            tip.z + rnd.nextGaussian() * .12,
            out.x * speed + rnd.nextGaussian() * .02,
            out.y * speed + .01,
            out.z * speed + rnd.nextGaussian() * .02);
    }
  }

  private SlashFx() {}
}

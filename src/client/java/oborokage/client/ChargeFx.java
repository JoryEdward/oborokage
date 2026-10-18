package oborokage.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * The build-up of a charged draw, in four steps. Each quarter of the charge adds a layer to the
 * light under the player's feet, rings out with a pulse and a chime, and fills one segment of the
 * gauge; a full charge raises the blade's own sign:
 *
 * <ol>
 *   <li>a turning ring of dashes;
 *   <li>a counter-turning ring of tick marks;
 *   <li>four crescent arcs on a wide ring;
 *   <li>pillars of light around the feet;
 *   <li>full: 胧影 a crescent moon behind the head, 赤霆 bolts crackling up to the hilt, 霜轮 a crown of
 *       ice spikes over a frost glyph, 空弦 strings drawn taut to a point overhead.
 * </ol>
 */
final class ChargeFx {
  private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);

  private record Pulse(int player, int stage, int born) {}

  private static final Map<Integer, Integer> stages = new HashMap<>();
  private static final List<Pulse> pulses = new ArrayList<>();
  private static int clock;

  /** Charge fraction of a player drawing a blade, or -1 when not charging. */
  static float charge(Player p) {
    if (!p.isUsingItem() || !Oborokage.mainBlade(p.getUseItem())) return -1;
    return Math.min(1, p.getTicksUsingItem() / (float) OborokageClient.fullChargeTicks(p));
  }

  static int stage(float q) {
    return q >= 1 ? 4 : (int) (q * 4);
  }

  static void tick(Minecraft c) {
    clock++;
    pulses.removeIf(p -> clock - p.born > 10);
    if (c.level == null || c.player == null) {
      stages.clear();
      pulses.clear();
      return;
    }
    Set<Integer> seen = new HashSet<>();
    for (Player p : c.level.players()) {
      float q = charge(p);
      if (q < 0 || p.distanceToSqr(c.player) > 48 * 48) continue;
      seen.add(p.getId());
      int stage = stage(q);
      Integer old = stages.put(p.getId(), stage);
      if (old != null && stage > old) {
        pulses.add(new Pulse(p.getId(), stage, clock));
        // The server chimes at the half and the full charge; the quarters ring here.
        if (stage == 1 || stage == 3)
          c.level.playLocalSound(
              p.getX(),
              p.getY(),
              p.getZ(),
              SoundEvents.AMETHYST_BLOCK_RESONATE,
              SoundSource.PLAYERS,
              .45f,
              .8f + .25f * stage,
              false);
      }
    }
    stages.keySet().retainAll(seen);
  }

  static void submit(LevelRenderContext context) {
    var c = Minecraft.getInstance();
    if (c.level == null || stages.isEmpty() && pulses.isEmpty()) return;
    Vec3 camera = context.levelState().cameraRenderState.pos;
    float partial = oborokage.client.compat.ClientCompat.partialTick(context);
    var cfg = ClientConfig.get();
    boolean compat = cfg.trailShaderCompat;
    var type =
        compat
            ? RenderTypes.entityTranslucentEmissive(Oborokage.id("textures/fx/white.png"))
            : RenderTypes.beaconBeam(Oborokage.id("textures/fx/white.png"), true);
    int share = Math.max(2048, cfg.trailVertexBudget / 4);
    for (Player p : c.level.players()) {
      Integer stage = stages.get(p.getId());
      boolean pulsing = pulses.stream().anyMatch(x -> x.player == p.getId());
      if (stage == null && !pulsing) continue;
      if (p.position().distanceToSqr(camera) > cfg.trailDistance * cfg.trailDistance) continue;
      float q = Math.max(0, charge(p));
      BladeStyle style = BladeStyle.of(p.getUseItem());
      if (style == null) style = BladeStyle.of(p.getMainHandItem());
      if (style == null) style = BladeStyle.TACHI;
      Vec3 origin = p.getPosition(partial);
      Vec3 eye = camera.subtract(origin);
      float t = clock + partial;
      float yaw = p.getViewYRot(partial);
      BladeStyle st = style;
      List<Pulse> mine = pulses.stream().filter(x -> x.player == p.getId()).toList();
      boolean self = p == c.player && c.options.getCameraType().isFirstPerson();
      var pose = context.poseStack();
      pose.pushPose();
      pose.translate(origin.subtract(camera));
      context
          .submitNodeCollector()
          .submitCustomGeometry(
              pose,
              type,
              (ps, v) ->
                  draw(
                      new Geo(ps, v, share, !compat).near(eye, 1.2),
                      st,
                      stage == null ? -1 : stage,
                      q,
                      t,
                      yaw,
                      eye,
                      mine,
                      self));
      pose.popPose();
    }
  }

  private static void draw(
      Geo g,
      BladeStyle st,
      int stage,
      float q,
      float t,
      float yaw,
      Vec3 eye,
      List<Pulse> pulses,
      boolean self) {
    Vec3 ground = new Vec3(0, .04, 0);
    for (Pulse p : pulses) {
      float k = Math.clamp((t - p.born) / 9f, 0, 1);
      double r = .8 + (p.stage == 4 ? 2.9 : 2.0) * Math.sqrt(k);
      g.ring(ground, X, Z, r - .22, r, st.primary, .75f * (1 - k), 48);
      g.ring(ground, X, Z, r - .05, r, st.accent, (1 - k), 48);
    }
    if (stage < 0) return;
    float glow = .45f + .3f * q;
    // 1. Dashes turning slowly round the feet.
    double spin = t * .05;
    for (int i = 0; i < 12; i++) {
      double a = spin + i * Math.PI / 6;
      ringArc(g, ground, 1.15, .05, a, a + .32, st.primary, glow, 4);
    }
    // 2. Tick marks turning the other way.
    if (stage >= 1)
      for (int i = 0; i < 8; i++) {
        double a = -t * .08 + i * Math.PI / 4;
        Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), s = new Vec3(-d.z, 0, d.x);
        Vec3 in = ground.add(d.scale(1.42)), out = ground.add(d.scale(1.7));
        g.quad(
            in.add(s.scale(.035)),
            out.add(s.scale(.035)),
            out.subtract(s.scale(.035)),
            in.subtract(s.scale(.035)),
            Geo.argb(st.accent, glow));
      }
    // 3. Four crescents on a wide ring.
    if (stage >= 2) {
      double a0 = t * .11;
      for (int i = 0; i < 4; i++) {
        double a = a0 + i * Math.PI / 2;
        g.crescent(ground, X, Z, 1.95, .16, a, a + 1.0, st.primary, .8f * glow, 10);
      }
    }
    // 4. Pillars of light; a first-person view would look straight down them, so skip there.
    if (stage >= 3 && !self)
      for (int i = 0; i < 6; i++) {
        double a = i * Math.PI / 3 + t * .03;
        Vec3 base = ground.add(Math.cos(a) * 1.15, 0, Math.sin(a) * 1.15);
        double h = .7 + .35 * Math.sin(t * .3 + i);
        g.beam(base, base.add(0, h, 0), .07, st.primary, .5f * glow, eye);
        g.beam(base, base.add(0, h * .8, 0), .02, st.accent, .8f * glow, eye);
      }
    if (stage < 4) return;
    float beat = .75f + .25f * (float) Math.sin(t * .35);
    double r = Math.toRadians(yaw);
    Vec3 f = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = f.cross(UP);
    g.disc(ground.add(0, .01, 0), X, Z, 1.1, st.accent, st.secondary, .18f * beat, 32);
    switch (st) {
      case RAIKIRI -> {
        long seed = (long) (t * 2);
        Vec3 hilt = new Vec3(0, 1.0, 0).add(right.scale(-.2));
        for (int i = 0; i < 5; i++) {
          double a = i * Math.PI * 2 / 5 + (seed % 7);
          Vec3 from = ground.add(Math.cos(a) * 1.15, 0, Math.sin(a) * 1.15);
          g.bolt(from, hilt, .06, st.primary, .85f * beat, eye, seed * 31 + i, 5);
          g.bolt(from, hilt, .018, st.accent, beat, eye, seed * 31 + i, 5);
        }
      }
      case FROSTMOON -> {
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4 + Math.PI / 8;
          Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
          g.spike(
              ground.add(d.scale(1.55)),
              d.scale(.35).add(UP),
              .55 + .2 * ((i * 3) % 4) / 3.0,
              .2,
              st.primary,
              .85f * beat);
        }
        for (int i = 0; i < 6; i++) {
          double a = i * Math.PI / 3;
          Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)), s = new Vec3(-d.z, 0, d.x);
          g.band(
              ground.add(0, .01, 0),
              ground.add(d.scale(1.05)).add(0, .01, 0),
              s,
              .05,
              st.accent,
              .7f * beat);
          Vec3 m = ground.add(d.scale(.65)).add(0, .01, 0);
          g.band(m, m.add(d.add(s).scale(.22)), d.subtract(s), .035, st.accent, .6f * beat);
          g.band(m, m.add(d.subtract(s).scale(.22)), d.add(s), .035, st.accent, .6f * beat);
        }
      }
      case VOIDSTRING -> {
        Vec3 apex = new Vec3(0, 2.7, 0);
        for (int i = 0; i < 6; i++) {
          double a = i * Math.PI / 3 + t * .02;
          Vec3 from = ground.add(Math.cos(a) * 1.95, 0, Math.sin(a) * 1.95);
          Vec3 mid = from.lerp(apex, .5).add(right.scale(Math.sin(t * 1.7 + i) * .04));
          g.beam(from, mid, .03, st.primary, .8f * beat, eye);
          g.beam(mid, apex, .03, st.primary, .8f * beat, eye);
        }
        g.disc(apex, right, UP, .12, 0xffffff, st.primary, beat, 12);
      }
      default -> {
        // The moon rises behind the head; it rocks a little as the breath is held.
        Vec3 moon = new Vec3(0, 2.3, 0).add(f.scale(-.5));
        double tilt = .25 + .05 * Math.sin(t * .2);
        g.disc(moon, right, UP, .62, st.accent, st.secondary, .16f * beat, 20);
        g.crescent(
            moon,
            right,
            UP,
            .5,
            .2,
            Math.PI * (.5 + tilt),
            Math.PI * (1.5 + tilt),
            st.accent,
            .9f * beat,
            16);
      }
    }
  }

  private static void ringArc(
      Geo g, Vec3 c, double r, double w, double a0, double a1, int rgb, float alpha, int n) {
    int col = Geo.argb(rgb, alpha);
    for (int i = 0; i < n; i++) {
      double b0 = a0 + (a1 - a0) * i / n, b1 = a0 + (a1 - a0) * (i + 1) / n;
      g.quad(
          Geo.on(c, X, Z, b0, r - w),
          Geo.on(c, X, Z, b0, r + w),
          Geo.on(c, X, Z, b1, r + w),
          Geo.on(c, X, Z, b1, r - w),
          col);
    }
  }

  /** The gauge above the hotbar, cut into four segments, and a tint closing in on the view. */
  static void hud(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) {
    Minecraft c = Minecraft.getInstance();
    if (c.player == null) return;
    float q = charge(c.player);
    if (q < 0) return;
    BladeStyle st = BladeStyle.of(c.player.getUseItem());
    if (st == null) st = BladeStyle.TACHI;
    int stage = stage(q);
    int w = g.guiWidth(), h = g.guiHeight();
    float beat = (float) (.5 + .5 * Math.sin((clock + partial) * .35));
    if (OborokageClient.cinematic && stage > 0) {
      float a = .05f * stage + (stage == 4 ? .05f * beat : 0);
      for (int i = 0; i < 8; i++) {
        int inset = (int) (i * Math.min(w, h) * .016f);
        int alpha = (int) (255 * a * (8 - i) / 8f);
        if (alpha <= 0) continue;
        int col = alpha << 24 | (st.secondary & 0xffffff);
        g.fill(0, 0, w, inset + 1, col);
        g.fill(0, h - inset - 1, w, h, col);
        g.fill(0, 0, inset + 1, h, col);
        g.fill(w - inset - 1, 0, w, h, col);
      }
    }
    int y = h - 64, x0 = w / 2 - 50;
    g.fill(x0 - 2, y - 2, x0 + 102, y + 5, 0xb5100e1e);
    for (int i = 0; i < 4; i++) {
      int sx = x0 + i * 25, ex = sx + 23;
      float fill = Math.clamp(q * 4 - i, 0, 1);
      g.fill(sx, y, ex, y + 3, 0x50000000 | (st.secondary & 0xffffff));
      if (fill > 0)
        g.fill(
            sx,
            y,
            sx + (int) (23 * fill),
            y + 3,
            (fill >= 1 ? 0xff000000 : 0xd0000000)
                | ((stage == 4 ? (beat > .5f ? st.accent : st.primary) : st.primary) & 0xffffff));
    }
    String label =
        stage == 4
            ? Component.translatable("ui.oborokage.ready").getString()
            : Component.translatable("ui.oborokage.charge").getString()
                + (stage > 0
                    ? "  " + Component.translatable("ui.oborokage.stage." + stage).getString()
                    : "");
    g.centeredText(
        c.font,
        label,
        w / 2,
        y - 14,
        stage == 4 ? 0xff000000 | st.accent : 0xff000000 | st.primary);
  }

  private ChargeFx() {}
}

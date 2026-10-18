package oborokage.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import oborokage.Move;

/**
 * The local player's own ultimate presentation: a keyframed third-person camera, a tinted vignette,
 * flashes, letterbox and a title card per blade. World effects that other players also see live in
 * UltimateFx; this class only touches the local screen and camera.
 */
public final class Cinematics {
  public static final int IAIDO = -1;

  /** One camera keyframe: tick, yaw offset from the scene heading, distance, pitch, lift. */
  private record Key(float t, float yaw, float dist, float pitch, float lift) {}

  private record Look(
      int length,
      Key[] camera,
      int tint,
      float tintMax,
      int flash,
      int[] flashes,
      int titleFrom,
      String title,
      String subtitle,
      int titleColor,
      int lineColor,
      int[] shakes) {}

  /**
   * 胧月一闪: low in front while the moon rises behind the stance; a whip to the side for the dash;
   * then round in front of the still swordsman, the marked enemies frozen behind, creeping in until
   * the scabbard clicks; and a slow pull back over the fallen.
   */
  private static final Look IAIDO_LOOK =
      new Look(
          64,
          new Key[] {
            new Key(0, 160, 4.4f, -8, 1.15f),
            new Key(11, 140, 3.4f, -5, 1.05f),
            new Key(13, 85, 6.5f, 6, 1.2f),
            new Key(18, 100, 8.5f, 10, 1.3f),
            new Key(22, 178, 4.6f, 3, 1.4f),
            new Key(34, 188, 3.9f, 1, 1.4f),
            new Key(44, 196, 4.6f, 6, 1.45f),
            new Key(64, 205, 8.5f, 18, 1.7f)
          },
          0x070512,
          .62f,
          0xf6f0ff,
          new int[] {12, 34},
          34,
          "ui.oborokage.ultimate",
          "O B O R O K A G E   /   I A I",
          0xe7ddff,
          0x9c7dd9,
          new int[] {34});

  private static final Look TACHI =
      new Look(
          48,
          new Key[] {
            new Key(0, 165, 4.2f, -12, 1.0f),
            // From the side while the moons cross, so foes in front never hide the cuts.
            new Key(9, 112, 5.6f, -6, 1.4f),
            new Key(23, 82, 6.6f, 4, 1.5f),
            new Key(30, 70, 8.0f, 10, 1.5f),
            new Key(48, 60, 8.6f, 14, 1.5f)
          },
          0x0b0818,
          .55f,
          0xfdf8ff,
          new int[] {9, 13, 23},
          23,
          "ui.oborokage.ult.tachi",
          "O B O R O K A G E   /   S O U G E T S U",
          0xf1e6ff,
          0xbca6f4,
          new int[] {23});
  private static final Look MOON =
      new Look(
          46,
          new Key[] {
            new Key(0, 200, 4.6f, 18, 1.2f),
            new Key(8, 250, 4.6f, 20, 1.2f),
            new Key(22, 470, 4.8f, 20, 1.3f),
            new Key(32, 560, 6.5f, 12, 1.4f),
            new Key(46, 590, 7.2f, 10, 1.4f)
          },
          0x1d1430,
          .42f,
          0xffeefa,
          new int[] {32},
          30,
          "ui.oborokage.ult.moon",
          "O B O R O T S U K I   /   H Y A K K A",
          0xfff0fb,
          0xd9cdff,
          new int[] {32});
  private static final Look STORM =
      new Look(
          56,
          new Key[] {
            new Key(0, 150, 4.5f, -10, 1.0f),
            new Key(8, 30, 6.0f, 8, 1.2f),
            new Key(36, 20, 6.4f, 10, 1.2f),
            new Key(42, 120, 9.5f, 24, 1.6f),
            new Key(56, 140, 10f, 22, 1.6f)
          },
          0x260404,
          .5f,
          0xfff0e0,
          new int[] {8, 13, 18, 23, 28, 33, 44},
          42,
          "ui.oborokage.ult.storm",
          "R A I K I R I   /   B A N R A I",
          0xfff0e0,
          0xff5a2c,
          new int[] {8, 13, 18, 23, 28, 33, 44});
  private static final Look FROST =
      new Look(
          58,
          new Key[] {
            new Key(0, 30, 6, 24, 1.2f),
            new Key(18, 40, 12.5f, 62, 1.5f),
            new Key(34, 75, 12.5f, 60, 1.5f),
            new Key(40, 150, 5.2f, 10, 1.3f),
            new Key(58, 165, 6.0f, 12, 1.3f)
          },
          0x0a2236,
          .45f,
          0xf2fdff,
          new int[] {18, 38},
          36,
          "ui.oborokage.ult.frost",
          "S O U T E N   /   K Y O U G E T S U",
          0xf4fdff,
          0x7fe6ff,
          new int[] {18, 38});
  private static final Look VOID =
      new Look(
          62,
          new Key[] {
            // Front-low and looking up while the sky's string is drawn back, then behind the cut.
            new Key(0, 150, 4.6f, -12, 1.4f),
            new Key(24, 128, 5.4f, -18, 1.4f),
            new Key(30, -20, 6.0f, 8, 1.6f),
            new Key(40, -28, 15.5f, 21, 2.0f),
            new Key(62, -32, 17f, 22, 2.0f)
          },
          0x0e0419,
          .62f,
          0xfaf3ff,
          new int[] {28, 40},
          28,
          "ui.oborokage.ult.void",
          "K U U G E N   /   D A N K A I",
          0xf6eeff,
          0xa66bff,
          new int[] {28, 40});

  public static int kind;
  private static float age = -1;
  private static float yaw;
  private static CameraType previous;

  /**
   * The title card outlives the camera work: it inks in a few ticks after the cast, flares on the
   * decisive beat and lingers on the plain game view after the cinematic hands the camera back.
   */
  private static final int TITLE_IN = 4, TITLE_LINGER = 60, TITLE_FADE = 24;

  private static int titleKind;
  private static float titleClock = -1;

  // Contact feedback for plain cuts: a short camera kick and, on a closing cut, an edge flash.
  private static int clock;
  private static float kickAt = -99, kickPower, kickSide;
  private static int flashColor;

  /** A landed cut: camera kick of `power` degrees toward `side`; `color` != 0 also flashes. */
  public static void kick(float power, float side, int color) {
    kickAt = clock;
    kickPower = power;
    kickSide = side;
    flashColor = color;
  }

  /** Camera offset {yaw, pitch} from the last kick, or null when it has settled. */
  public static float[] kickOffset(float partial) {
    float d = clock + partial - kickAt;
    if (d < 0 || d > 5 || !OborokageClient.screenShake || playing()) return null;
    float k = kickPower * (1 - d / 5) * (1 - d / 5);
    return new float[] {
      kickSide * k * (float) Math.cos(d * 2.6) * .8f, k * (float) Math.sin(d * 3.1 + .6)
    };
  }

  private static Look look(int kind) {
    return switch (kind) {
      case IAIDO -> IAIDO_LOOK;
      case Move.ULTIMATE -> TACHI;
      case Move.ULT_MOON -> MOON;
      case Move.ULT_STORM -> STORM;
      case Move.ULT_FROST -> FROST;
      case Move.ULT_VOID -> VOID;
      default -> null;
    };
  }

  public static boolean playing() {
    return kind != 0 && age >= 0;
  }

  public static float age() {
    return age;
  }

  public static void begin(int newKind, float sceneYaw, int startAge) {
    Minecraft c = Minecraft.getInstance();
    restore(c);
    if (look(newKind) == null) return;
    kind = newKind;
    age = startAge;
    yaw = sceneYaw;
    titleKind = newKind;
    titleClock = startAge;
    if (OborokageClient.cinematic && OborokageClient.thirdPersonUltimate) {
      previous = c.options.getCameraType();
      c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
  }

  /**
   * Stops an ultimate that was cut short. A title that is only lingering after a finished cinematic
   * stays: a new attack right after the ultimate should not wipe its name.
   */
  public static void cancel() {
    if (playing()) clearTitle();
    end();
  }

  /** Drops everything, title included: a new level, death or disconnect. */
  public static void reset() {
    clearTitle();
    end();
  }

  private static void end() {
    restore(Minecraft.getInstance());
    kind = 0;
    age = -1;
  }

  private static void clearTitle() {
    titleKind = 0;
    titleClock = -1;
  }

  public static boolean titleShowing() {
    return titleKind != 0;
  }

  private static void restore(Minecraft c) {
    if (previous != null) {
      if (c.options.getCameraType() == CameraType.THIRD_PERSON_BACK)
        c.options.setCameraType(previous);
      previous = null;
    }
  }

  static void tick(Minecraft c) {
    clock++;
    if (c.player == null || !c.player.isAlive()) {
      reset();
      return;
    }
    if (titleKind != 0 && ++titleClock > look(titleKind).length + TITLE_LINGER) clearTitle();
    if (!playing()) return;
    Look l = look(kind);
    if (++age > l.length) {
      end();
      return;
    }
    if (previous != null
        && (oborokage.client.compat.ClientCompat.screenOpen(c)
            || c.options.getCameraType() != CameraType.THIRD_PERSON_BACK)) {
      // The player took the camera back (F5 or a screen): stop steering it.
      previous = null;
    }
  }

  /** True while this class steers the camera. */
  public static boolean cameraActive() {
    return playing() && previous != null;
  }

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  /** Camera for the current tick: {yaw, pitch, distance, lift}. */
  public static float[] shot(float partial) {
    Look l = look(kind);
    float t = age + partial;
    Key[] k = l.camera;
    Key a = k[0], b = k[k.length - 1];
    for (int i = 0; i + 1 < k.length; i++)
      if (t >= k[i].t && t <= k[i + 1].t) {
        a = k[i];
        b = k[i + 1];
        break;
      }
    float s = b.t == a.t ? 1 : ease((t - a.t) / (b.t - a.t));
    float y = yaw + a.yaw + (b.yaw - a.yaw) * s;
    float pitch = a.pitch + (b.pitch - a.pitch) * s;
    float dist = a.dist + (b.dist - a.dist) * s;
    float lift = a.lift + (b.lift - a.lift) * s;
    if (OborokageClient.screenShake)
      for (int at : l.shakes)
        if (t >= at && t < at + 7) {
          float amp = 1.1f * (at + 7 - t) / 7;
          y += (float) Math.sin(t * 3.1) * amp;
          pitch += (float) Math.cos(t * 2.7) * amp;
        }
    return new float[] {y, pitch, dist, lift};
  }

  /** Letterbox, tint, vignette, flashes and the title card. */
  static void overlay(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) {
    if (!OborokageClient.cinematic) return;
    if (playing()) frame(g, partial);
    else edgeFlash(g, partial);
    if (titleKind != 0) title(g, partial);
  }

  /** A closing cut's flash: the screen's rim lights up in the blade's colour for a few frames. */
  private static void edgeFlash(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) {
    float d = clock + partial - kickAt;
    if (flashColor == 0 || d < 0 || d > 6) return;
    int w = g.guiWidth(), h = g.guiHeight();
    float a = (1 - d / 6) * (1 - d / 6);
    for (int i = 0; i < 10; i++) {
      int inset = (int) (i * Math.min(w, h) * .012f);
      int alpha = (int) (255 * .1f * a * (10 - i) / 10f);
      if (alpha <= 0) continue;
      int col = alpha << 24 | (flashColor & 0xffffff);
      g.fill(0, 0, w, inset + 1, col);
      g.fill(0, h - inset - 1, w, h, col);
      g.fill(0, 0, inset + 1, h, col);
      g.fill(w - inset - 1, 0, w, h, col);
    }
  }

  private static void frame(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) {
    Minecraft c = Minecraft.getInstance();
    Look l = look(kind);
    int w = g.guiWidth(), h = g.guiHeight();
    float t = age + partial, len = l.length;
    float in = Math.min(1, t / 5), out = Math.min(1, (len - t) / 8);
    float env = Math.max(0, Math.min(in, out));
    // Tint and a soft vignette in the blade's darkness.
    int tintA = (int) (255 * l.tintMax * .45f * env);
    if (tintA > 0) g.fill(0, 0, w, h, tintA << 24 | (l.tint & 0xffffff));
    for (int i = 0; i < 12; i++) {
      int inset = (int) (i * Math.min(w, h) * .018f);
      int a = (int) (255 * l.tintMax * env * (12 - i) / 12f * .09f);
      if (a <= 0) continue;
      int col = a << 24 | (l.tint & 0xffffff);
      g.fill(0, 0, w, inset + 1, col);
      g.fill(0, h - inset - 1, w, h, col);
      g.fill(0, 0, inset + 1, h, col);
      g.fill(w - inset - 1, 0, w, h, col);
    }
    int bar = (int) (h * .1 * env);
    if (bar > 0) {
      g.fill(0, 0, w, bar, 0xee050309);
      g.fill(0, h - bar, w, h, 0xee050309);
      g.fill(0, bar, w, bar + 1, 0x80000000 | (l.lineColor & 0xffffff));
      g.fill(0, h - bar - 1, w, h - bar, 0x80000000 | (l.lineColor & 0xffffff));
    }
    // Flashes: a hard white beat that falls off over a few ticks.
    for (int at : l.flashes) {
      float d = t - at;
      if (d >= 0 && d < 6) {
        boolean last = at == l.flashes[l.flashes.length - 1];
        float peak = last ? (kind == IAIDO ? .55f : .5f) : .24f;
        float strength = peak * (float) Math.pow(1 - d / 6, 2);
        g.fill(0, 0, w, h, (int) (255 * strength) << 24 | (l.flash & 0xffffff));
      }
    }
    // 胧月一闪: the world holds its breath until the click, then the screen is cut across.
    if (kind == IAIDO) {
      float hush = t < 18 ? 0 : t < 34 ? Math.min(1, (t - 18) / 6) : Math.max(0, 1 - (t - 34) / 4);
      if (hush > 0) {
        g.fill(0, 0, w, h, (int) (255 * .1f * hush) << 24 | 0x05030c);
        for (int i = 0; i < 10; i++) {
          int inset = (int) (i * Math.min(w, h) * .02f);
          int a = (int) (255 * .14f * hush * (10 - i) / 10f);
          int col = a << 24 | 0x05030c;
          g.fill(0, 0, w, inset + 1, col);
          g.fill(0, h - inset - 1, w, h, col);
          g.fill(0, 0, inset + 1, h, col);
          g.fill(w - inset - 1, 0, w, h, col);
        }
      }
      if (t >= 34 && t < 42) {
        float a = 1 - (t - 34) / 8;
        int y = (int) (h * .52), gap = (int) (3 + 6 * (t - 34) / 8);
        g.fill(0, y - 1, w, y + 1, (int) (255 * a) << 24 | 0xffffff);
        g.fill(0, y - gap - 1, w, y - gap, (int) (120 * a) << 24 | (l.lineColor & 0xffffff));
        g.fill(0, y + gap, w, y + gap + 1, (int) (120 * a) << 24 | (l.lineColor & 0xffffff));
      }
    }
    // 空弦 · 断界: the screen itself appears cut along the stroke.
    if (kind == Move.ULT_VOID && t >= 28 && t < 38) {
      float a = 1 - (t - 28) / 10;
      for (int x = 0; x < w; x += 2) {
        int y = (int) (h * .78 - x * (h * .56 / w));
        g.fill(x, y - 1, x + 2, y + 2, (int) (255 * a) << 24 | 0xffffff);
        g.fill(x, y + 3, x + 2, y + 5, (int) (140 * a) << 24 | (l.lineColor & 0xffffff));
      }
    }
  }

  /**
   * Title card: the name inks in a character at a time shortly after the cast, holds through the
   * whole ultimate, flares with a fresh brush stroke on the decisive beat and fades out well after
   * the camera has returned.
   */
  private static void title(net.minecraft.client.gui.GuiGraphicsExtractor g, float partial) {
    Minecraft c = Minecraft.getInstance();
    Look l = look(titleKind);
    int w = g.guiWidth(), h = g.guiHeight();
    float t = titleClock + partial, start = Math.min(TITLE_IN, l.titleFrom);
    float end = l.length + TITLE_LINGER;
    if (t < start || t >= end) return;
    float local = t - start;
    float a = Math.min(1, local / 4) * Math.min(1, (end - t) / TITLE_FADE);
    // The decisive beat: a short swell in size and brightness, then the subtitle settles in.
    float beat = t - l.titleFrom;
    float flare = beat >= 0 && beat < 10 ? (float) Math.sin(beat / 10 * Math.PI) : 0;
    String title = Component.translatable(l.title).getString();
    int shown = Math.min(title.length(), (int) (local * 1.2f) + 1);
    int x = (int) (w * .08), y = (int) (h * .36);
    float scale = 3.2f + .35f * flare;
    g.pose().pushMatrix();
    g.pose().translate(x, y);
    g.pose().scale(scale, scale);
    g.text(c.font, title.substring(0, shown), 1, 1, (int) (a * 150) << 24);
    if (flare > 0)
      g.text(
          c.font,
          title.substring(0, shown),
          0,
          -1,
          (int) (a * 110 * flare) << 24 | (l.lineColor & 0xffffff));
    g.text(
        c.font,
        title.substring(0, shown),
        0,
        0,
        (int) (a * 255) << 24 | (lerp(l.titleColor, 0xffffff, flare) & 0xffffff));
    g.pose().popMatrix();
    int lineY = y + 36;
    int lineW = (int) (w * .34 * Math.min(1, local / 8));
    g.fill(x, lineY, x + lineW, lineY + 2, (int) (a * 230) << 24 | (l.lineColor & 0xffffff));
    g.fill(
        x,
        lineY + 2,
        x + lineW * 2 / 3,
        lineY + 3,
        (int) (a * 120) << 24 | (l.lineColor & 0xffffff));
    if (beat >= 0) {
      // A second, brighter stroke races along the first on the decisive beat.
      int strokeW = (int) (w * .34 * Math.min(1, beat / 5));
      float sa = a * Math.max(.35f, 1 - beat / 14);
      g.fill(x, lineY - 1, x + strokeW, lineY, (int) (sa * 255) << 24 | 0xffffff);
    }
    float sub = Math.min(1, Math.max(0, (local - 6) / 6));
    if (sub > 0)
      g.text(
          c.font,
          l.subtitle,
          x + 2,
          y + 46,
          (int) (a * sub * 230) << 24 | (l.lineColor & 0xffffff));
  }

  private static int lerp(int from, int to, float s) {
    int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * s);
    int gr = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * s);
    int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * s);
    return r << 16 | gr << 8 | b;
  }

  private Cinematics() {}
}

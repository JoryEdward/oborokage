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

  private static final Look IAIDO_LOOK =
      new Look(
          48,
          new Key[] {
            new Key(0, 120, 4.6f, 8, 1.25f),
            new Key(12, 75, 5.8f, 10, 1.25f),
            new Key(20, 43, 5.8f, 12, 1.3f),
            new Key(48, 98, 4.8f, 12, 1.3f)
          },
          0x090713,
          .45f,
          0xf2e8ff,
          new int[] {12},
          1,
          "ui.oborokage.ultimate",
          "O B O R O K A G E   /   I A I",
          0xe7ddff,
          0x9c7dd9,
          new int[] {12});
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
    if (OborokageClient.cinematic && OborokageClient.thirdPersonUltimate) {
      previous = c.options.getCameraType();
      c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
  }

  public static void cancel() {
    restore(Minecraft.getInstance());
    kind = 0;
    age = -1;
  }

  private static void restore(Minecraft c) {
    if (previous != null) {
      if (c.options.getCameraType() == CameraType.THIRD_PERSON_BACK)
        c.options.setCameraType(previous);
      previous = null;
    }
  }

  static void tick(Minecraft c) {
    if (!playing()) return;
    Look l = look(kind);
    if (++age > l.length || c.player == null || !c.player.isAlive()) {
      cancel();
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
    if (!playing() || !OborokageClient.cinematic) return;
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
        float peak = last ? (kind == IAIDO ? .36f : .5f) : .24f;
        float strength = peak * (float) Math.pow(1 - d / 6, 2);
        g.fill(0, 0, w, h, (int) (255 * strength) << 24 | (l.flash & 0xffffff));
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
    // Title card: characters ink in one by one, then a brush line and the romanised name.
    if (t >= l.titleFrom && t < len - 2) {
      float local = t - l.titleFrom;
      float a = Math.min(1, local / 3) * Math.min(1, (len - 2 - t) / 6);
      String title = Component.translatable(l.title).getString();
      int shown = Math.min(title.length(), (int) (local * 1.6f) + 1);
      int x = (int) (w * .08), y = (int) (h * .36);
      g.pose().pushMatrix();
      g.pose().translate(x, y);
      g.pose().scale(3.2f, 3.2f);
      g.text(c.font, title.substring(0, shown), 1, 1, (int) (a * 150) << 24);
      g.text(
          c.font,
          title.substring(0, shown),
          0,
          0,
          (int) (a * 255) << 24 | (l.titleColor & 0xffffff));
      g.pose().popMatrix();
      int lineW = (int) (w * .34 * Math.min(1, local / 6));
      g.fill(x, y + 36, x + lineW, y + 38, (int) (a * 230) << 24 | (l.lineColor & 0xffffff));
      g.fill(
          x, y + 38, x + lineW * 2 / 3, y + 39, (int) (a * 120) << 24 | (l.lineColor & 0xffffff));
      g.text(c.font, l.subtitle, x + 2, y + 46, (int) (a * 230) << 24 | (l.lineColor & 0xffffff));
    }
  }

  private Cinematics() {}
}

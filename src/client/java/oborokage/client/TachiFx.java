package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 胧影 · 双月归寂 (drawn by {@link UltimateFx}). */
final class TachiFx {
  /** 胧影 · 双月归寂: a moon rises behind; two moons cross; the heavens' cut splits the moon. */
  static void draw(Geo g, float t, Vec3 f, Vec3 r) {
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

  private TachiFx() {}
}

package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 空弦 · 断界 (drawn by {@link UltimateFx}). */
final class VoidFx {
  /** 空弦 · 断界: the sky's string is drawn back; released, it severs the world along a line. */
  static void draw(Geo g, float t, Vec3 f, Vec3 r, Vec3 eye, double drop) {
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
      g.band(
          new Vec3(0, drop + .06, 0),
          f.scale(48).add(0, drop + .06, 0),
          r,
          .8,
          0x7a3dff,
          .6f * scar);
      g.band(
          new Vec3(0, drop + .07, 0),
          f.scale(48).add(0, drop + .07, 0),
          r,
          .22,
          0xffffff,
          .85f * scar);
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
              f.scale(6 + 8 * i).add(0, drop + .08, 0),
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

  private VoidFx() {}
}

package oborokage.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.Vec3;
import oborokage.client.compat.ClientCompat;

/**
 * World-space light geometry for ultimates: double-sided, full-bright quads with per-vertex colour.
 * Every primitive draws relative to the pose origin and respects a vertex budget, so a crowded
 * fight degrades detail instead of frame time.
 */
final class Geo {
  private final PoseStack.Pose pose;
  private final VertexConsumer v;
  private final boolean twoSided;
  private int budget;
  private Vec3 eye;
  private double reach;

  /**
   * @param twoSided emit each quad in both windings. Only for a render type that culls back faces:
   *     the emissive effect pipeline does not cull, and a second copy there blends twice with
   *     different per-face lighting (it showed as a dark half on the moon).
   */
  Geo(PoseStack.Pose pose, VertexConsumer v, int budget, boolean twoSided) {
    this.pose = pose;
    this.v = v;
    this.budget = budget;
    this.twoSided = twoSided;
  }

  /**
   * Fades every vertex that comes within reach of the eye (relative to the pose origin). Light that
   * sweeps past the camera would otherwise fill the screen with one flat quad.
   */
  Geo near(Vec3 eye, double reach) {
    this.eye = eye;
    this.reach = reach;
    return this;
  }

  static int argb(int rgb, float alpha) {
    return (int) (255 * Math.clamp(alpha, 0, 1)) << 24 | (rgb & 0xffffff);
  }

  /** Segment count for a primitive, reduced when the budget runs low. */
  private int cost() {
    return twoSided ? 8 : 4;
  }

  int segments(int wanted) {
    return Math.max(0, Math.min(wanted, budget / cost()));
  }

  void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
    if (budget < cost()) return;
    budget -= cost();
    put(a, ca);
    put(b, cb);
    put(c, cc);
    put(d, cd);
    if (!twoSided) return;
    put(d, cd);
    put(c, cc);
    put(b, cb);
    put(a, ca);
  }

  void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
    quad(a, b, c, d, color, color, color, color);
  }

  private void put(Vec3 p, int color) {
    if (eye != null) {
      double d = p.distanceTo(eye);
      if (d < reach) {
        float k = (float) Math.clamp((d - .4) / (reach - .4), 0, 1);
        color = (int) ((color >>> 24) * k * k) << 24 | (color & 0xffffff);
      }
    }
    ClientCompat.vertex(v, pose, (float) p.x, (float) p.y, (float) p.z, color);
  }

  static Vec3 on(Vec3 c, Vec3 u, Vec3 w, double angle, double r) {
    return c.add(u.scale(Math.cos(angle) * r)).add(w.scale(Math.sin(angle) * r));
  }

  /** A crescent band on the circle (c, u, w): thickness swells in the middle and tapers away. */
  void crescent(
      Vec3 c,
      Vec3 u,
      Vec3 w,
      double r,
      double thick,
      double a0,
      double a1,
      int rgb,
      float alpha,
      int wanted) {
    int n = segments(wanted);
    for (int i = 0; i < n; i++) {
      double s0 = i / (double) n, s1 = (i + 1) / (double) n;
      double b0 = a0 + (a1 - a0) * s0, b1 = a0 + (a1 - a0) * s1;
      double t0 = thick * Math.sin(Math.PI * s0), t1 = thick * Math.sin(Math.PI * s1);
      int c0 = argb(rgb, alpha * (.25f + .75f * (float) s0)),
          c1 = argb(rgb, alpha * (.25f + .75f * (float) s1));
      quad(
          on(c, u, w, b0, r - t0 * .3),
          on(c, u, w, b0, r + t0),
          on(c, u, w, b1, r + t1),
          on(c, u, w, b1, r - t1 * .3),
          c0,
          c0,
          c1,
          c1);
    }
  }

  /** A filled disc, bright core fading to its rim colour. */
  void disc(Vec3 c, Vec3 u, Vec3 w, double r, int core, int rim, float alpha, int wanted) {
    disc(c, u, w, r, core, rim, alpha, 0, Math.PI * 2, wanted);
  }

  void disc(
      Vec3 c,
      Vec3 u,
      Vec3 w,
      double r,
      int core,
      int rim,
      float alpha,
      double a0,
      double a1,
      int wanted) {
    int n = segments(wanted);
    int ci = argb(core, alpha), ri = argb(rim, alpha * .85f);
    for (int i = 0; i < n; i++) {
      double b0 = a0 + (a1 - a0) * i / n, b1 = a0 + (a1 - a0) * (i + 1) / n;
      quad(c, c, on(c, u, w, b0, r), on(c, u, w, b1, r), ci, ci, ri, ri);
    }
  }

  void ring(Vec3 c, Vec3 u, Vec3 w, double r0, double r1, int rgb, float alpha, int wanted) {
    int n = segments(wanted);
    int inner = argb(rgb, alpha * .2f), outer = argb(rgb, alpha);
    for (int i = 0; i < n; i++) {
      double b0 = Math.PI * 2 * i / n, b1 = Math.PI * 2 * (i + 1) / n;
      quad(
          on(c, u, w, b0, r0),
          on(c, u, w, b0, r1),
          on(c, u, w, b1, r1),
          on(c, u, w, b1, r0),
          inner,
          outer,
          outer,
          inner);
    }
  }

  /** A flat ribbon from a to b, `side` giving its width direction. */
  void band(Vec3 a, Vec3 b, Vec3 side, double width, int rgb, float alpha) {
    Vec3 s = side.normalize().scale(width / 2);
    int c = argb(rgb, alpha);
    quad(a.subtract(s), a.add(s), b.add(s), b.subtract(s), c);
  }

  /** A ribbon from a to b that always faces the eye (eye is relative to the pose origin). */
  void beam(Vec3 a, Vec3 b, double width, int rgb, float alpha, Vec3 eye) {
    Vec3 axis = b.subtract(a);
    Vec3 side = axis.cross(eye.subtract(a.add(b).scale(.5)));
    if (side.lengthSqr() < 1e-8) return;
    band(a, b, side, width, rgb, alpha);
  }

  /** A jagged bolt from a to b, deterministic for a given seed. */
  void bolt(Vec3 a, Vec3 b, double width, int rgb, float alpha, Vec3 eye, long seed, int kinks) {
    java.util.Random r = new java.util.Random(seed);
    Vec3 prev = a;
    double len = a.distanceTo(b);
    for (int i = 1; i <= kinks; i++) {
      Vec3 p = a.lerp(b, i / (double) kinks);
      if (i < kinks)
        p =
            p.add(
                (r.nextDouble() - .5) * len * .12,
                (r.nextDouble() - .5) * len * .05,
                (r.nextDouble() - .5) * len * .12);
      beam(prev, p, width, rgb, alpha, eye);
      prev = p;
    }
  }

  /** A vertical sheet: bright along the ground line, fading upward and toward both ends. */
  void wall(Vec3 o, Vec3 along, double length, double height, int rgb, float alpha, int wanted) {
    Vec3 f = along.normalize();
    int n = segments(wanted);
    for (int i = 0; i < n; i++) {
      double s0 = i / (double) n, s1 = (i + 1) / (double) n;
      float e0 = (float) Math.min(1, Math.min(s0 * 6, (1 - s0) * 3)),
          e1 = (float) Math.min(1, Math.min(s1 * 6, (1 - s1) * 3));
      Vec3 p0 = o.add(f.scale(length * s0)), p1 = o.add(f.scale(length * s1));
      Vec3 up = new Vec3(0, height, 0);
      quad(
          p0,
          p0.add(up),
          p1.add(up),
          p1,
          argb(rgb, alpha * e0),
          argb(rgb, 0),
          argb(rgb, 0),
          argb(rgb, alpha * e1));
    }
  }

  /** An ice spire: two crossed tapering quads. */
  void spike(Vec3 base, Vec3 dir, double length, double width, int rgb, float alpha) {
    Vec3 d = dir.normalize();
    Vec3 s1 = d.cross(new Vec3(0, 0, 1));
    if (s1.lengthSqr() < 1e-4) s1 = d.cross(new Vec3(1, 0, 0));
    s1 = s1.normalize().scale(width / 2);
    Vec3 s2 = d.cross(s1).normalize().scale(width / 2);
    Vec3 tip = base.add(d.scale(length));
    int c = argb(rgb, alpha), ct = argb(0xffffff, alpha);
    quad(base.subtract(s1), base.add(s1), tip, tip, c, c, ct, ct);
    quad(base.subtract(s2), base.add(s2), tip, tip, c, c, ct, ct);
  }
}

package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 霜天 · 镜月冰河 (drawn by {@link UltimateFx}). */
final class FrostFx {
  /** 霜天 · 镜月冰河: a mirror lake freezes outward, spires seize the foes, the moon cracks. */
  static void draw(
      Geo g, UltimateFx.State s, float t, Vec3 f, Vec3 r, List<Vec3> marks, double drop) {
    double R = 12 * ease(t / 18);
    float a = fadeIn(t, 0, 4) * fadeOut(t, 48, 58);
    // The lake lies on the ground and the mirror moon hangs above it, wherever the cast began.
    Vec3 c = new Vec3(0, drop + .05, 0);
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
      Vec3 top = new Vec3(0, drop + 11, 0);
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

  private FrostFx() {}
}

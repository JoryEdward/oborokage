package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 胧月一闪 and the quick draw (drawn by {@link UltimateFx}). */
final class IaidoFx {
  /**
   * 胧月一闪 (full charge). Windup: a great pale moon climbs behind the stance and the ground ripples
   * like still water. Dash (12-18): the path is written as a razor line in a broad glow, with speed
   * lines and fading afterimages. Stillness: the path hangs in the air as a hairline, and a fine
   * cut crosses the moon and every marked enemy, brightening as the blade slides home. Sheath (34):
   * the moon falls apart along its cut, the hairline flares and every mark bursts.
   */
  static void draw(
      Geo g,
      UltimateFx.State s,
      float t,
      Vec3 f,
      Vec3 r,
      Vec3 eye,
      List<Vec3> trail,
      List<Vec3> marks) {
    final int dash = Combat.FULL_WINDUP, sheath = LegacyIaido.SHEATH;
    float moonA = fadeIn(t, 0, 8) * fadeOut(t, 48, 60);
    Vec3 moon = f.scale(-12).add(0, 2.5 + 5 * ease(t / 12), 0);
    // The moon's cut runs on a slant across its face.
    Vec3 slant = r.scale(Math.cos(Math.toRadians(20))).add(UP.scale(Math.sin(Math.toRadians(20))));
    Vec3 across = slant.cross(f).normalize();
    if (t < sheath) {
      g.disc(moon, r, UP, 4.6, 0xfffaff, 0xd8c8ff, .85f * moonA, 40);
      g.ring(moon, r, UP, 4.6, 7.2, 0xbca6f4, .22f * moonA, 40);
      if (t >= dash + 6) {
        float tension = fadeIn(t, dash + 6, sheath);
        g.band(
            moon.subtract(slant.scale(6)),
            moon.add(slant.scale(6)),
            across,
            .06 + .12 * tension,
            0xffffff,
            (.4f + .6f * tension) * moonA);
      }
    } else {
      // The two halves slide apart along the cut and fade.
      float split = ease((t - sheath) / 10);
      double a0 = Math.toRadians(20);
      Vec3 up = moon.add(across.scale(.9 * split)).add(slant.scale(.5 * split));
      Vec3 down = moon.subtract(across.scale(.9 * split)).subtract(slant.scale(.5 * split));
      Vec3 u = r, w = UP;
      g.disc(up, u, w, 4.6, 0xfffaff, 0xd8c8ff, .85f * moonA, a0, a0 + Math.PI, 20);
      g.disc(down, u, w, 4.6, 0xfffaff, 0xd8c8ff, .85f * moonA, a0 + Math.PI, a0 + Math.PI * 2, 20);
      float flare = fadeOut(t, sheath, sheath + 6);
      g.band(
          moon.subtract(slant.scale(8)),
          moon.add(slant.scale(8)),
          across,
          .5 * flare,
          0xffffff,
          flare * moonA);
    }
    // Still water at the feet while the breath is drawn.
    if (t < dash + 2) {
      float calm = fadeOut(t, dash - 2, dash + 2);
      for (int k = 0; k < 3; k++) {
        double rad = (t * .35 + k * 1.5) % 4.5;
        g.ring(
            UP.scale(.05),
            r,
            f,
            rad,
            rad + .06,
            0xd8c8ff,
            .35f * calm * (float) (1 - rad / 4.5),
            40);
      }
    }
    // The dash runs from where the draw began to the farthest point reached along it. Position
    // updates and the scene clock can differ by a tick or two, so the path is read from the trail
    // rather than from fixed ticks.
    Vec3 start = new Vec3(0, 1.0, 0), end = start;
    double farthest = 0;
    for (int i = 0; i < Math.min(trail.size(), dash + 10); i++) {
      Vec3 at = trail.get(i).subtract(s.origin);
      if (at.dot(f) > farthest) {
        farthest = at.dot(f);
        end = at.add(0, 1.0, 0);
      }
    }
    {
      Vec3 path = end.subtract(start);
      double length = path.length();
      if (length > .1) {
        Vec3 dir = path.scale(1 / length);
        if (t < dash + 8) {
          // The dash itself: glow, razor core, speed lines and afterimage slits.
          float a = fadeOut(t, dash + 5, dash + 8);
          // Layered so the glow falls off softly instead of reading as a flat band.
          g.beam(start, end, .9, 0xd8c8ff, .16f * a, eye);
          g.beam(start, end, .45, 0xe8dcff, .3f * a, eye);
          g.beam(start, end, .18, 0xf6f0ff, .6f * a, eye);
          g.beam(start, end, .06, 0xffffff, a, eye);
          Vec3 side = dir.cross(UP).normalize();
          for (int i = 0; i < 14; i++) {
            double ang = i * 2.39996, rad = .8 + (i * 7 % 5) * .45;
            Vec3 off = side.scale(Math.cos(ang) * rad).add(UP.scale(Math.sin(ang) * rad * .7));
            double back = 2 + (i * 3 % 4);
            Vec3 b = end.add(off).subtract(dir.scale(1 + (i % 3)));
            Vec3 e2 = b.subtract(dir.scale(back));
            g.beam(e2, b, .03, i % 2 == 0 ? 0xffffff : 0xd8c8ff, .55f * a, eye);
          }
          for (double d = 1.5; d < length; d += 1.5) {
            float passed = (float) (t - dash - d / 2.5);
            if (passed < 0) continue;
            float ghost = Math.max(0, 1 - passed / 5) * a;
            Vec3 base = start.add(dir.scale(d)).add(0, -1.0, 0);
            g.beam(base, base.add(0, 1.9, 0), .1, 0xbca6f4, .55f * ghost, eye);
            g.beam(base.add(0, .2, 0), base.add(0, 1.7, 0), .025, 0xffffff, .8f * ghost, eye);
          }
        } else if (t < sheath + 6) {
          // The cut hangs in the air, a hairline that tightens until the click, then flares.
          float tension = fadeIn(t, dash + 8, sheath);
          float flare = t >= sheath ? fadeOut(t, sheath, sheath + 6) : 0;
          g.beam(start, end, .35 + 1.2 * flare, 0xbca6f4, .12f + .3f * flare, eye);
          g.beam(
              start, end, .025 + .02 * tension + .15 * flare, 0xffffff, .55f + .45f * tension, eye);
        }
      }
    }
    // Every marked enemy carries a fine cut until the sheath, then bursts.
    for (Vec3 m : marks) {
      Vec3 at = m.subtract(s.origin);
      Vec3 d = r.scale(.7).add(UP.scale(.7));
      if (t < sheath) {
        float tension = fadeIn(t, dash + 2, sheath);
        g.beam(at.subtract(d), at.add(d), .03 + .03 * tension, 0xffffff, .5f + .5f * tension, eye);
        g.beam(at.subtract(d), at.add(d), .25, 0xbca6f4, .15f + .2f * tension, eye);
      } else if (t < sheath + 8) {
        float k = (t - sheath) / 8;
        Vec3 big = d.scale(1.6 + .8 * k);
        g.beam(at.subtract(big), at.add(big), .3 * (1 - k), 0xffffff, 1 - k, eye);
        g.ring(
            at, r, UP, 1.6 * Math.sqrt(k), 1.6 * Math.sqrt(k) + .15, 0xd8c8ff, .8f * (1 - k), 24);
      }
    }
    // The sheath's ring around the swordsman.
    if (t >= sheath && t < sheath + 12) {
      float sw = (t - sheath) / 12;
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

  /**
   * A draw before full charge: the dash written as a short light line and one level crescent where
   * the blade left the scabbard. Both grow with the charge.
   */
  static void quick(
      Geo g, UltimateFx.State s, float t, Vec3 f, Vec3 r, Vec3 eye, List<Vec3> trail) {
    float a = fadeOut(t, 5, 12);
    if (trail.size() > 1) {
      Vec3 start = trail.get(0).subtract(s.origin).add(0, 1.0, 0);
      Vec3 end = trail.get(Math.min(trail.size() - 1, 6)).subtract(s.origin).add(0, 1.0, 0);
      if (start.distanceToSqr(end) > .05) {
        g.beam(start, end, .5 + .6 * s.charge, 0xbca6f4, .3f * a, eye);
        g.beam(start, end, .04 + .03 * s.charge, 0xffffff, .9f * a, eye);
      }
    }
    float sweep = ease(t / 3);
    double rad = 2.2 + 1.3 * s.charge, half = Math.toRadians(75) * sweep;
    Vec3 c = UP.scale(1.05).add(f.scale(.3));
    g.crescent(c, f, r, rad, .28 + .2 * s.charge, half, -half, 0xbca6f4, .85f * a, 18);
    g.crescent(c, f, r, rad + .04, .06, half, -half, 0xffffff, a, 18);
  }

  private IaidoFx() {}
}

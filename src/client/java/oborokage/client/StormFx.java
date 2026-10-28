package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 雷切 · 万雷天降 (drawn by {@link UltimateFx}). */
final class StormFx {
  /** 雷切 · 万雷天降: afterimages tear the air between blinks; the sky answers with a pillar. */
  static void draw(Geo g, UltimateFx.State s, float t, Vec3 eye, List<Vec3> trail) {
    int now = (int) t;
    for (int i = 1; i < trail.size() && i <= now; i++) {
      float age = t - i;
      if (age > 9) continue;
      Vec3 a = trail.get(i - 1).subtract(s.origin).add(0, 1, 0),
          b = trail.get(i).subtract(s.origin).add(0, 1, 0);
      if (a.distanceToSqr(b) < .04) continue;
      float alpha = 1 - age / 9;
      g.beam(a, b, .42, 0xff2410, .55f * alpha, eye);
      g.beam(a, b, .12, 0xfff0d6, .95f * alpha, eye);
    }
    Vec3 here =
        trail.isEmpty() ? Vec3.ZERO : trail.get(Math.min(trail.size() - 1, now)).subtract(s.origin);
    if (t < 10) {
      float a = (float) (.55 + .45 * Math.sin(t * 2.3)) * fadeIn(t, 0, 3);
      for (int i = 0; i < 3; i++)
        g.bolt(
            here.add(0, 1.6, 0),
            here.add((i - 1) * 3, 26, i * 2 - 2),
            .2,
            0xff5a2c,
            a,
            eye,
            (long) (t / 2) * 31 + i,
            7);
    }
    for (int k = 0; k < 6; k++) {
      int at = 8 + 5 * k;
      if (t >= at && t < at + 3 && at < trail.size()) {
        Vec3 p = trail.get(at).subtract(s.origin);
        float a = 1 - (t - at) / 3;
        g.bolt(p.add(0, 30, 0), p, .55, 0xff2410, .8f * a, eye, at * 7L, 9);
        g.bolt(p.add(0, 30, 0), p, .16, 0xffffff, a, eye, at * 7L, 9);
      }
    }
    if (t >= 44 && t < 56) {
      float a = fadeOut(t, 48, 56);
      Vec3 top = here.add(0, 50, 0);
      g.beam(top, here, 6.5, 0xff3a14, .22f * a, eye);
      g.bolt(top, here, 2.2, 0xff2410, .78f * a, eye, 4401, 11);
      g.bolt(top, here, .55, 0xfff6e8, a, eye, 4401, 11);
      float sw = (t - 44) / 12;
      double R = 1 + 11 * ease(sw);
      g.ring(
          here.add(0, .06, 0),
          new Vec3(1, 0, 0),
          new Vec3(0, 0, 1),
          Math.max(0, R - .9),
          R,
          0xffc35a,
          .8f * (1 - sw),
          56);
    }
  }

  private StormFx() {}
}

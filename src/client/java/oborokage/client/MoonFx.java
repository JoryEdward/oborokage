package oborokage.client;

import static oborokage.client.FxShapes.*;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** The world-space light of 胧月 · 百花缭乱 (drawn by {@link UltimateFx}). */
final class MoonFx {
  /** 胧月 · 百花缭乱: eight whirling cuts weave a cage of light; a lotus blooms and bursts. */
  static void draw(Geo g, float t, Vec3 f, Vec3 r, double drop) {
    Vec3 c = new Vec3(0, 1, 0);
    int[] colors = {0xffffff, 0xd9cdff, 0xffc8ea};
    for (int k = 0; k < 8; k++) {
      float tk = 8 + 2 * k;
      if (t < tk || t >= tk + 14) continue;
      float sweep = ease((t - tk) / 2), a = fadeOut(t, tk + 6, tk + 14);
      Vec3 u = rotY(r, k * 67), w = tilt(rotY(f, k * 67), (k % 2 == 0 ? 1 : -1) * (12 + k * 5));
      double start = k * .7;
      g.crescent(c, u, w, 4.4 + .1 * k, 1.0, start, start + 2.6 * sweep, 0xd9cdff, .25f * a, 28);
      g.crescent(
          c, u, w, 4.4 + .1 * k, .32, start, start + 2.6 * sweep, colors[k % 3], .9f * a, 28);
    }
    if (t >= 10 && t < 46) {
      float open = ease((t - 10) / 22), bloom = t > 32 ? ease((t - 32) / 8) : 0;
      float a = fadeIn(t, 10, 14) * fadeOut(t, 38, 46);
      for (int i = 0; i < 8; i++) {
        Vec3 dir = rotY(f, i * 45 + t * 1.5f);
        double lift = Math.toRadians((1 - open) * 34 - bloom * 6);
        Vec3 d = dir.scale(Math.cos(lift)).add(UP.scale(Math.sin(lift)));
        double len = 5.6 + 2.4 * bloom;
        Vec3 base = new Vec3(0, drop + .06, 0),
            tip = base.add(d.scale(len)),
            mid = base.add(d.scale(len * .45));
        Vec3 side = UP.cross(dir).normalize().scale(1.05 + .3 * bloom);
        g.quad(
            base,
            mid.add(side),
            tip,
            mid.subtract(side),
            Geo.argb(0xffffff, .34f * a),
            Geo.argb(0xd9cdff, .24f * a),
            Geo.argb(0xffc8ea, .08f * a),
            Geo.argb(0xd9cdff, .24f * a));
        g.band(base, tip, side, .07, 0xffffff, .7f * a);
      }
    }
    if (t >= 32 && t < 44) {
      float s = (t - 32) / 12;
      double R = 1 + 11 * ease(s);
      g.ring(new Vec3(0, drop + .08, 0), r, f, Math.max(0, R - .7), R, 0xffc8ea, .8f * (1 - s), 56);
    }
  }

  private MoonFx() {}
}

package oborokage.client;

import net.minecraft.world.phys.Vec3;

/** Shared math for the ultimate effects: directions, turns and the fade curves. */
final class FxShapes {
  static final Vec3 UP = new Vec3(0, 1, 0);

  static Vec3 forward(float yaw) {
    return new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
  }

  static Vec3 rotY(Vec3 v, double degrees) {
    double a = Math.toRadians(degrees), cs = Math.cos(a), sn = Math.sin(a);
    return new Vec3(v.x * cs - v.z * sn, v.y, v.x * sn + v.z * cs);
  }

  /** The horizontal direction d tipped toward the sky by `degrees`. */
  static Vec3 tilt(Vec3 d, double degrees) {
    double a = Math.toRadians(degrees);
    return d.scale(Math.cos(a)).add(UP.scale(Math.sin(a)));
  }

  static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  static float fadeIn(float t, float from, float to) {
    return Math.clamp((t - from) / (to - from), 0, 1);
  }

  static float fadeOut(float t, float from, float to) {
    return 1 - Math.clamp((t - from) / (to - from), 0, 1);
  }

  private FxShapes() {}
}

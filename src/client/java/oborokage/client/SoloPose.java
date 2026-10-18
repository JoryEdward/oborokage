package oborokage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import oborokage.Move;
import oborokage.client.compat.ClientCompat;

/**
 * Body and first-person motion of the single-blade chain: both hands on the hilt, a falling
 * diagonal cut, a flatter rising cut and a stepping full-width finisher. Each cut has a wind-up, a
 * fast stroke, a short hold and a return to the middle guard, so consecutive cuts flow.
 */
final class SoloPose {
  /** Arm and body angles: main x/y/z, support x/y/z, body yaw, body pitch. */
  private static final float[] GUARD = {-.95f, -.15f, 0, -.9f, -.55f, .05f, 0, .08f};

  private static final float[][] START = {
    {-2.55f, -.45f, -.1f, -2.4f, -.8f, .1f, -.32f, .02f},
    {-.6f, .8f, .15f, -.55f, .3f, 0, .42f, .14f},
    {-1.55f, -1.3f, -.3f, -1.4f, -1.55f, .05f, -.6f, .08f}
  };
  private static final float[][] END = {
    {-.55f, .85f, .15f, -.5f, .35f, 0, .45f, .16f},
    {-2.2f, -.85f, -.3f, -2f, -1.15f, .1f, -.42f, .02f},
    {-1.3f, 1.3f, .2f, -1.2f, .85f, 0, .75f, .18f}
  };

  private static float ease(float x) {
    x = Math.clamp(x, 0, 1);
    return x * x * (3 - 2 * x);
  }

  /** Stroke progress: wind-up before, 0..1 during the three-tick stroke, held after. */
  static float stroke(int move, float t) {
    int hit = Move.of(move).hits()[0];
    return ease((t - (hit - 2)) / 3);
  }

  /** How far the body sits in the cut rather than in guard: wind-up in, recovery out. */
  private static float presence(int move, float t) {
    var m = Move.of(move);
    int hit = m.hits()[0];
    float in = ease(t / Math.max(1, hit - 2));
    float out = 1 - ease((t - (hit + 3)) / Math.max(1, m.length() - hit - 3));
    return Math.min(in, out);
  }

  static void thirdPerson(
      HumanoidModel<?> model, ModelPart main, ModelPart off, float sign, int move, float t) {
    int k = move - Move.SOLO_1;
    float s = stroke(move, t), p = presence(move, t);
    float[] cut = new float[8];
    for (int i = 0; i < 8; i++) {
      float during = START[k][i] + (END[k][i] - START[k][i]) * s;
      cut[i] = GUARD[i] + (during - GUARD[i]) * p;
    }
    main.xRot = cut[0];
    main.yRot = sign * cut[1];
    main.zRot = sign * cut[2];
    off.xRot = cut[3];
    off.yRot = sign * cut[4];
    off.zRot = sign * cut[5];
    model.body.yRot = sign * cut[6];
    model.body.xRot = cut[7];
    // Arms are not children of the body: carry the shoulders round with its twist, as vanilla's
    // own attack swing does.
    float yaw = model.body.yRot;
    model.rightArm.x = -(float) Math.cos(yaw) * 5;
    model.rightArm.z = (float) Math.sin(yaw) * 5;
    model.leftArm.x = (float) Math.cos(yaw) * 5;
    model.leftArm.z = -(float) Math.sin(yaw) * 5;
    // The finisher lunges; the other cuts keep a light forward stance.
    float lunge = move == Move.SOLO_3 ? p * s : 0;
    model.rightLeg.xRot = -.22f - .35f * lunge;
    model.leftLeg.xRot = .27f + .3f * lunge;
    model.rightLeg.zRot = .07f;
    model.leftLeg.zRot = -.07f;
  }

  /** First person: translate x/y/z, then rotate z, y, x (degrees), for start and end of a cut. */
  // Rotating about +Z turns the tip left, about +Y swings it left, about +X tips it back.
  private static final float[][] FP_START = {
    {.08f, .24f, .04f, -22, 0, 18},
    {-.36f, -.16f, -.14f, 60, 0, -20},
    {.24f, .02f, .06f, -45, -35, 0}
  };

  private static final float[][] FP_END = {
    {-.44f, -.2f, -.22f, 70, 0, -26},
    {.14f, .26f, -.18f, -35, 0, 10},
    {-.5f, 0, -.3f, 70, 40, 0}
  };

  static void firstPerson(PoseStack pose, float side, int move, float t) {
    int k = move - Move.SOLO_1;
    float s = stroke(move, t), p = presence(move, t);
    float[] v = new float[6];
    for (int i = 0; i < 6; i++) v[i] = (FP_START[k][i] + (FP_END[k][i] - FP_START[k][i]) * s) * p;
    pose.translate(side * v[0], v[1], v[2]);
    ClientCompat.rotate(pose, Axis.ZP, side * v[3]);
    ClientCompat.rotate(pose, Axis.YP, side * v[4]);
    ClientCompat.rotate(pose, Axis.XP, v[5]);
  }

  private SoloPose() {}
}

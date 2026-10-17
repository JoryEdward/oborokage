package oborokage.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

/**
 * Minecraft 26.1, 26.1.1 and 26.1.2 client bindings for APIs whose shape differs across the 26.x
 * line.
 */
public final class ClientCompat {
  public static boolean screenOpen(Minecraft c) {
    return c.screen != null;
  }

  public static InputConstants.Type keyboard() {
    return InputConstants.Type.KEYSYM;
  }

  public static void rotate(PoseStack pose, Axis axis, float degrees) {
    pose.mulPose(axis.rotationDegrees(degrees));
  }

  public static float partialTick(LevelRenderContext context) {
    return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
  }

  /** Full-bright, untextured vertex for the trail and technique geometry. */
  public static void vertex(
      VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, int color) {
    v.addVertex(pose, x, y, z)
        .setColor(color)
        .setUv(.5f, .5f)
        .setLight(0x00f000f0)
        .setOverlay(0)
        .setNormal(pose, 0, 1, 0);
  }

  private ClientCompat() {}
}

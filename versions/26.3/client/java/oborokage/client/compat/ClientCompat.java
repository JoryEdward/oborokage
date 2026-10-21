package oborokage.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

/** Minecraft 26.3 client bindings for APIs whose shape differs across the 26.x line. */
public final class ClientCompat {
  public static void openScreen(Minecraft c, net.minecraft.client.gui.screens.Screen screen) {
    c.gui.setScreen(screen);
  }

  /** Whether the GUI is hidden (F1); name tags and the post's readout hide with it. */
  public static boolean hudHidden(Minecraft c) {
    return c.gui.hud.isHidden();
  }

  public static boolean screenOpen(Minecraft c) {
    return c.gui.screen() != null;
  }

  public static InputConstants.Type keyboard() {
    return InputConstants.Type.KEYBOARD;
  }

  public static void rotate(PoseStack pose, Axis axis, float degrees) {
    pose.rotateDegrees(axis, degrees);
  }

  public static float partialTick(LevelRenderContext context) {
    return context.levelState().worldPartialTicks;
  }

  /** Full-bright, untextured vertex for the trail and technique geometry. */
  public static void vertex(
      VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, int color) {
    v.addVertex(pose, x, y, z)
        .setColor(color)
        .setUv(.5f, .5f)
        .setLight(0x00f000f0)
        // NO_OVERLAY, not 0: overlay row 0 is the red hurt flash and tinted every effect red.
        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
        .setUv3(0, 0)
        .setNormal(pose, 0, 1, 0);
  }

  private ClientCompat() {}
}

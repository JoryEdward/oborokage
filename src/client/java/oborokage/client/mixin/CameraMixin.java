package oborokage.client.mixin;

import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
  @Shadow
  protected abstract void setRotation(float y, float x);

  @Shadow
  protected abstract void setPosition(Vec3 p);

  @Shadow
  protected abstract void move(float forward, float up, float side);

  @Shadow
  private float getMaxZoom(float distance) {
    throw new AssertionError();
  }

  @Shadow
  public abstract float yRot();

  @Shadow
  public abstract float xRot();

  // Before Camera.update builds view/culling matrices, after vanilla has positioned the camera.
  @Inject(method = "alignWithEntity", at = @At("TAIL"))
  private void frameUltimate(float partial, CallbackInfo ci) {
    var c = Minecraft.getInstance();
    if (!OborokageClient.cameraActive() || c.player == null) return;
    float t = OborokageClient.localAge + partial;
    float yaw =
        OborokageClient.cinematicYaw
            + (t < 12
                ? 120 - 45 * t / 12
                : t < 20 ? 75 - 32 * (t - 12) / 8 : 43 + 55 * Math.min(1, (t - 20) / 28));
    float distance = t < 12 ? 4.6f : t < 20 ? 5.8f : 5.8f - 1.2f * Math.min(1, (t - 20) / 28);
    if (OborokageClient.dualUltimate) {
      yaw =
          OborokageClient.cinematicYaw
              + (t < 7
                  ? 115 - 30 * t / 7
                  : t < 24 ? 85 - 95 * (t - 7) / 17 : -10 + 75 * Math.min(1, (t - 24) / 24));
      distance = t < 7 ? 3.7f : t < 24 ? 5.2f : 5.2f - 1.0f * Math.min(1, (t - 24) / 24);
    }
    setPosition(c.player.getPosition(partial).add(0, 1.25, 0));
    setRotation(yaw, t < 12 ? 8 : 12);
    move(-getMaxZoom(distance), 0, 0);
    if (OborokageClient.screenShake && t >= 12 && t < 19) {
      float a = .4f * (19 - t) / 7;
      setRotation(yRot() + (float) Math.sin(t * 3) * a, xRot() + (float) Math.cos(t * 2.5) * a);
    }
  }
}

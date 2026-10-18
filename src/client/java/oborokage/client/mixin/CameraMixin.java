package oborokage.client.mixin;

import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import oborokage.client.Cinematics;
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
    if (c.player == null) return;
    if (!Cinematics.cameraActive()) {
      float[] kick = Cinematics.kickOffset(partial);
      if (kick != null) setRotation(yRot() + kick[0], xRot() + kick[1]);
      return;
    }
    float[] shot = Cinematics.shot(partial);
    setPosition(c.player.getPosition(partial).add(0, shot[3], 0));
    setRotation(shot[0], shot[1]);
    move(-getMaxZoom(shot[2]), 0, 0);
  }
}

package oborokage.check.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.*;
import oborokage.check.RenderObservations;
import oborokage.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(HumanoidModel.class)
public class ObservedPose {
  @Inject(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
      at = @At("TAIL"))
  private void inspect(HumanoidRenderState state, CallbackInfo ci) {
    if (!(state instanceof AvatarRenderState avatar)) return;
    HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
    if (Math.abs(model.rightArm.yRot) + Math.abs(model.leftArm.yRot) > .1) {
      if (OborokageClient.sceneAge(avatar.id) >= 0) RenderObservations.iaido++;
      if (DualClient.scene(avatar.id) != null) RenderObservations.dual++;
    }
  }
}

package oborokage.check.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.*;
import oborokage.Move;
import oborokage.check.MaintenanceObservations;
import oborokage.client.DualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(HumanoidModel.class)
public class ObservedSinglePose {
  @Inject(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
      at = @At("TAIL"),
      order = 1500)
  private void inspect(HumanoidRenderState state, CallbackInfo ci) {
    if (!(state instanceof AvatarRenderState avatar)) return;
    var c = Minecraft.getInstance();
    if (c.player == null || avatar.id != c.player.getId()) return;
    var s = DualClient.scene(avatar.id);
    if (s == null || s.event.move() < Move.STORM || s.event.move() > Move.TERRAIN) return;
    var m = (HumanoidModel<?>) (Object) this;
    var off =
        c.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
            ? m.leftArm
            : m.rightArm;
    MaintenanceObservations.offX = off.xRot;
    MaintenanceObservations.offY = off.yRot;
    MaintenanceObservations.offZ = off.zRot;
    MaintenanceObservations.singlePoses++;
  }
}

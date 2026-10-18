package oborokage.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.*;
import oborokage.Oborokage;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class IaidoPoseMixin {
  @Shadow @Final public ModelPart body, rightArm, leftArm, rightLeg, leftLeg;

  @Inject(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
      at = @At("TAIL"))
  private void iaido(HumanoidRenderState state, CallbackInfo ci) {
    if (!(state instanceof AvatarRenderState avatar)) return;
    var level = Minecraft.getInstance().level;
    if (level == null) return;
    var e = level.getEntity(avatar.id);
    if (!(e instanceof net.minecraft.world.entity.player.Player p)) return;
    if (oborokage.client.DualClient.pose(
        avatar.id,
        (HumanoidModel<?>) (Object) this,
        state.ageInTicks - (int) state.ageInTicks,
        p.getMainArm())) return;
    boolean charging = p.isUsingItem() && Oborokage.mainBlade(p.getUseItem());
    int age = OborokageClient.sceneAge(avatar.id);
    if (!charging && age < 0) return;
    float t = age + (state.ageInTicks - (int) state.ageInTicks);
    int delay = OborokageClient.sceneDelay(avatar.id);
    boolean full = delay > 0;
    float cut =
        charging || t < delay
            ? 0
            : t < delay + 4
                ? Math.min(1, (t - delay) / 4)
                : full
                    // Held out in stillness, flicked clean (chiburi), then slid home by the click.
                    ? t < 31 ? 1 : Math.max(0, 1 - (t - 31) / 3)
                    : t < 29 ? 1 : Math.max(0, 1 - (t - 29) / 9);
    float flick = full && t >= 28 && t < 32 ? (float) Math.sin((t - 28) / 4 * Math.PI) : 0;
    // Ready: right hand on the hilt in front of the belly, scabbard back past the left hip, the
    // free hand steadying it there. The draw then sweeps the shoulder into one horizontal cut.
    body.xRot = .1f + .1f * cut;
    body.yRot = -.16f + .36f * cut;
    ModelPart sword =
        p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? rightArm : leftArm;
    ModelPart support = sword == rightArm ? leftArm : rightArm;
    float sign = sword == rightArm ? 1 : -1;
    sword.xRot = -.42f + (-1.33f + .42f) * cut + .55f * flick;
    sword.yRot = sign * (-.3f + 1.55f * cut + .35f * flick);
    sword.zRot = sign * (-.48f * cut - .3f * flick);
    support.xRot = -.22f - .2f * cut;
    support.yRot = sign * (.32f - .1f * cut);
    support.zRot = sign * (.05f + .1f * cut);
    rightLeg.xRot = -.22f;
    leftLeg.xRot = .27f;
    rightLeg.zRot = .06f;
    leftLeg.zRot = -.06f;
    if (charging) {
      // Sinking into the stance as the charge builds: a forward lean and a wider footing.
      float q = Math.min(1, p.getTicksUsingItem() / (float) OborokageClient.fullChargeTicks(p));
      body.xRot += .1f * q;
      rightLeg.xRot -= .12f * q;
      leftLeg.xRot += .14f * q;
      rightLeg.zRot += .07f * q;
      leftLeg.zRot -= .07f * q;
    }
  }
}

package dev.codex.oborokage.client.mixin;
import dev.codex.oborokage.Oborokage;
import dev.codex.oborokage.Combat;
import dev.codex.oborokage.client.OborokageClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class IaidoPoseMixin {
 @Shadow @Final public ModelPart body,rightArm,leftArm,rightLeg,leftLeg;
 @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
 private void iaido(HumanoidRenderState state,CallbackInfo ci){
  if(!(state instanceof AvatarRenderState avatar))return;
  var level=Minecraft.getInstance().level;if(level==null)return;var e=level.getEntity(avatar.id);
  if(!(e instanceof net.minecraft.world.entity.player.Player p))return;
  if(dev.codex.oborokage.client.DualClient.pose(avatar.id,(HumanoidModel<?>)(Object)this,state.ageInTicks-(int)state.ageInTicks,p.getMainArm()))return;
  boolean charging=p.isUsingItem()&&p.getUseItem().is(Oborokage.TACHI);
  int age=OborokageClient.sceneAge(avatar.id);if(!charging&&age<0)return;
  OborokageClient.poseApplications++;
  float t=age+(state.ageInTicks-(int)state.ageInTicks);
  int delay=OborokageClient.sceneDelay(avatar.id);
  float cut=charging||t<delay?0:t<delay+4?Math.min(1,(t-delay)/4):t<29?1:Math.max(0,1-(t-29)/9);
  // Grip stays at the hand; the shoulder drives a single cross-body draw and horizontal cut.
  body.xRot=.13f+.08f*cut;body.yRot=-.18f+.38f*cut;
  ModelPart sword=p.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?rightArm:leftArm;
  ModelPart support=sword==rightArm?leftArm:rightArm;float sign=sword==rightArm?1:-1;
  sword.xRot=-.58f-.75f*cut;sword.yRot=sign*(-.65f+1.9f*cut);sword.zRot=sign*(-.16f-.32f*cut);
  support.xRot=-.65f;support.yRot=sign*.55f;support.zRot=sign*.18f;
  rightLeg.xRot=-.22f;leftLeg.xRot=.27f;rightLeg.zRot=.06f;leftLeg.zRot=-.06f;
 }
}

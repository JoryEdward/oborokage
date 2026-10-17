package oborokage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import oborokage.Oborokage;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class HandsMixin {
  @Inject(method = "submitArmWithItem", at = @At("HEAD"))
  private void pose(
      PlayerRenderState player,
      FirstPersonHandsAndItemsRenderState state,
      float pt,
      float pitch,
      InteractionHand hand,
      float swing,
      ItemStack item,
      float height,
      PoseStack pose,
      SubmitNodeCollector nodes,
      int light,
      CallbackInfo ci) {
    if (!Oborokage.mainBlade(item) && !item.is(Oborokage.WAKIZASHI)) return;
    pose.pushPose();
    oborokage.client.DualClient.itemPose(pose, hand, pt);
    var p = Minecraft.getInstance().player;
    if (p != null && p.isUsingItem() && p.getUsedItemHand() == hand) {
      float q = Math.min(1, p.getTicksUsingItem() / 40f);
      pose.translate(-.18 * q, -.13 * q, .08 * q);
      pose.rotate(Axis.ZP.rotationDegrees(-12 * q));
    }
    int age = OborokageClient.localAge;
    if (age >= 0 && age < 26) {
      int delay = OborokageClient.localFull ? oborokage.Combat.FULL_WINDUP : 0;
      float a = age + pt - delay;
      float cut = a < 0 ? 0 : a < 3 ? a / 3 : a < 9 ? 1 : Math.max(0, 1 - (a - 9) / 12);
      pose.translate(-.48 * cut, .15 * cut, -.12 * cut);
      pose.rotate(Axis.ZP.rotationDegrees(-72 * cut));
      pose.rotate(Axis.YP.rotationDegrees(-22 * cut));
    }
  }

  @Inject(method = "submitArmWithItem", at = @At("RETURN"))
  private void restore(
      PlayerRenderState player,
      FirstPersonHandsAndItemsRenderState state,
      float pt,
      float pitch,
      InteractionHand hand,
      float swing,
      ItemStack item,
      float height,
      PoseStack pose,
      SubmitNodeCollector nodes,
      int light,
      CallbackInfo ci) {
    if (Oborokage.mainBlade(item) || item.is(Oborokage.WAKIZASHI)) pose.popPose();
  }
}

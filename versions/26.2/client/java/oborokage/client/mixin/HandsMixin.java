package oborokage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import oborokage.client.FirstPersonPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2: first-person arms are submitted by ItemInHandRenderer.submitArmWithItem. */
@Mixin(ItemInHandRenderer.class)
public class HandsMixin {
  @Inject(method = "submitArmWithItem", at = @At("HEAD"))
  private void pose(
      AbstractClientPlayer player,
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
    if (FirstPersonPose.applies(item)) FirstPersonPose.push(pose, hand, pt);
  }

  @Inject(method = "submitArmWithItem", at = @At("RETURN"))
  private void restore(
      AbstractClientPlayer player,
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
    if (FirstPersonPose.applies(item)) pose.popPose();
  }
}

package oborokage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import oborokage.Oborokage;
import oborokage.client.compat.ClientCompat;

/** First-person blade pose shared by every version's arm-render mixin. */
public final class FirstPersonPose {
  public static boolean applies(ItemStack item) {
    return Oborokage.mainBlade(item) || item.is(Oborokage.WAKIZASHI);
  }

  /** Pushes one pose level; the caller pops it after the arm and item are submitted. */
  public static void push(PoseStack pose, InteractionHand hand, float pt) {
    pose.pushPose();
    DualClient.itemPose(pose, hand, pt);
    var p = Minecraft.getInstance().player;
    if (p != null && p.isUsingItem() && p.getUsedItemHand() == hand) {
      float q = Math.min(1, p.getTicksUsingItem() / (float) OborokageClient.fullChargeTicks(p));
      pose.translate(-.18 * q, -.13 * q, .08 * q);
      ClientCompat.rotate(pose, Axis.ZP, -12 * q);
    }
    // The iaido draw belongs to the main hand; the dual ultimate has its own scene-driven pose.
    int age = OborokageClient.localAge;
    if (hand == InteractionHand.MAIN_HAND
        && !OborokageClient.dualUltimate
        && age >= 0
        && age < 26) {
      int delay = OborokageClient.localFull ? oborokage.Combat.FULL_WINDUP : 0;
      float a = age + pt - delay;
      float cut = a < 0 ? 0 : a < 3 ? a / 3 : a < 9 ? 1 : Math.max(0, 1 - (a - 9) / 12);
      pose.translate(-.48 * cut, .15 * cut, -.12 * cut);
      ClientCompat.rotate(pose, Axis.ZP, -72 * cut);
      ClientCompat.rotate(pose, Axis.YP, -22 * cut);
    }
  }

  private FirstPersonPose() {}
}

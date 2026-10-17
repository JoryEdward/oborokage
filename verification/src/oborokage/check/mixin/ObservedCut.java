package oborokage.check.mixin;

import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import oborokage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TachiItem.class)
public class ObservedCut {
  @Inject(method = "use", at = @At("HEAD"))
  private void using(
      Level level,
      net.minecraft.world.entity.player.Player p,
      net.minecraft.world.InteractionHand h,
      CallbackInfoReturnable<net.minecraft.world.InteractionResult> ci) {
    System.out.println(
        "CUTQA use "
            + (p instanceof ServerPlayer ? "server" : "client")
            + " main="
            + p.getMainHandItem()
            + " active="
            + Combat.active(p.getUUID())
            + " loaded="
            + (p instanceof ServerPlayer server ? server.connection.hasClientLoaded() : true));
  }

  @Inject(method = "releaseUsing", at = @At("HEAD"))
  private void observe(
      ItemStack stack,
      Level level,
      LivingEntity entity,
      int remaining,
      CallbackInfoReturnable<Boolean> ci) {
    if (entity instanceof ServerPlayer p)
      System.out.println(
          "CUTQA release server chargeTicks="
              + (72000 - remaining)
              + " shift="
              + p.isShiftKeyDown()
              + " eye="
              + p.getEyePosition()
              + " blade="
              + stack);
  }
}

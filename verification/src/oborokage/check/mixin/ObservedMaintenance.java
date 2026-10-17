package oborokage.check.mixin;

import net.minecraft.client.Minecraft;
import oborokage.ActionEvent;
import oborokage.Move;
import oborokage.check.MaintenanceObservations;
import oborokage.client.DualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(DualClient.class)
public class ObservedMaintenance {
  @Inject(method = "predict", at = @At("HEAD"))
  private static void predicted(Minecraft c, int move, int seq, CallbackInfo ci) {
    if (move == Move.GUARD) MaintenanceObservations.guardPredictions++;
  }

  @Inject(method = "receive", at = @At("HEAD"))
  private static void event(ActionEvent e, CallbackInfo ci) {
    if (e.kind() == ActionEvent.CANCEL) MaintenanceObservations.cancels++;
  }

  @Inject(method = "interceptAttack", at = @At("RETURN"))
  private static void attack(CallbackInfoReturnable<Boolean> ci) {
    if (!MaintenanceObservations.watchingMining) return;
    if (ci.getReturnValue()) MaintenanceObservations.intercepted++;
    else MaintenanceObservations.vanillaMining++;
  }
}

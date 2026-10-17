package oborokage.check.mixin;

import net.minecraft.world.phys.Vec3;
import oborokage.check.MaintenanceObservations;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(OborokageClient.class)
public class ObservedCharge {
  @Inject(method = "dust", at = @At("HEAD"))
  private static void aura(Vec3 pos, int color, float size, CallbackInfo ci) {
    if (MaintenanceObservations.watchingCharge && (color == 0xe4dbff || color == 0x8171c4))
      MaintenanceObservations.aura++;
  }
}

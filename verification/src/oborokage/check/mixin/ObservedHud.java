package oborokage.check.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import oborokage.check.MaintenanceObservations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GuiGraphicsExtractor.class)
public class ObservedHud {
  @Inject(method = "fill(IIIII)V", at = @At("HEAD"))
  private void fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
    if (MaintenanceObservations.watchingCharge
        && y2 - y1 == 2
        && (color == 0xfff1ddff || color == 0xffa79cff)) {
      MaintenanceObservations.hudFrames++;
      MaintenanceObservations.hudWidth = x2 - x1;
    }
  }
}

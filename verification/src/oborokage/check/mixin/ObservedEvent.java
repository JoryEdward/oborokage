package oborokage.check.mixin;

import net.minecraft.client.renderer.entity.state.*;
import oborokage.check.RenderObservations;
import oborokage.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(DualClient.class)
public class ObservedEvent {
  @Inject(method = "receive", at = @At("HEAD"))
  private static void receive(oborokage.ActionEvent e, CallbackInfo ci) {
    RenderObservations.events++;
  }
}

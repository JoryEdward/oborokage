package oborokage.check.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import oborokage.check.MaintenanceObservations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChatComponent.class)
public class ObservedChat {
  @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
  private void message(Component msg, CallbackInfo ci) {
    if (MaintenanceObservations.watchingChat && MaintenanceObservations.advancement(msg))
      MaintenanceObservations.clientBroadcasts++;
  }
}

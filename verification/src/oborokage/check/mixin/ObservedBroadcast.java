package oborokage.check.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import oborokage.check.MaintenanceObservations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PlayerList.class)
public class ObservedBroadcast {
  @Inject(
      method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
      at = @At("HEAD"))
  private void broadcast(Component msg, boolean overlay, CallbackInfo ci) {
    if (MaintenanceObservations.watchingChat && MaintenanceObservations.advancement(msg))
      MaintenanceObservations.serverBroadcasts++;
  }
}

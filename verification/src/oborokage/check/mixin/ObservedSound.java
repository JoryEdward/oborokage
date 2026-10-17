package oborokage.check.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Entity;
import oborokage.check.MaintenanceObservations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerLevel.class)
public class ObservedSound {
  @Inject(
      method =
          "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
      at = @At("HEAD"))
  private void sound(
      Entity source,
      double x,
      double y,
      double z,
      Holder<SoundEvent> sound,
      SoundSource category,
      float volume,
      float pitch,
      long seed,
      CallbackInfo ci) {
    if (MaintenanceObservations.watchingCharge)
      for (var p : ((ServerLevel) (Object) this).players())
        if (p.isUsingItem()) {
          if (sound.value() == SoundEvents.AMETHYST_BLOCK_CHIME)
            MaintenanceObservations.chimes.add(p.getTicksUsingItem());
          if (sound.value() == SoundEvents.AMETHYST_BLOCK_RESONATE)
            MaintenanceObservations.resonates.add(p.getTicksUsingItem());
        }
  }
}

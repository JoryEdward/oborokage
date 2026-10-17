package oborokage.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import oborokage.DualCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public class GuardDamageMixin {
  // A single-argument rewrite: unlike @ModifyArgs it allocates nothing on every entity's damage.
  @ModifyArg(
      method = "hurtServer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"),
      index = 2)
  private float guard(ServerLevel level, DamageSource source, float amount) {
    return DualCombat.guardedAmount((LivingEntity) (Object) this, source, amount);
  }
}

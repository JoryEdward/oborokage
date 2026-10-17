package oborokage.mixin;

import net.minecraft.world.entity.LivingEntity;
import oborokage.DualCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public class GuardDamageMixin {
  @ModifyArgs(
      method = "hurtServer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"))
  private void guard(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
    args.set(2, DualCombat.guardedAmount((LivingEntity) (Object) this, args.get(1), args.get(2)));
  }
}

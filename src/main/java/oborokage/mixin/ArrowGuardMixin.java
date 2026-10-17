package oborokage.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import oborokage.DualCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public class ArrowGuardMixin {
  @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
  private void reflect(EntityHitResult hit, CallbackInfo ci) {
    var arrow = (AbstractArrow) (Object) this;
    if (arrow.entityTags().contains("oborokage_reflected")
        && arrow.getOwner() instanceof ServerPlayer owner
        && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target
        && !oborokage.TargetRules.allowed(owner, target)) {
      arrow.discard();
      ci.cancel();
      return;
    }
    if (hit.getEntity() instanceof ServerPlayer p
        && DualCombat.reflectArrow((AbstractArrow) (Object) this, p)) ci.cancel();
  }
}

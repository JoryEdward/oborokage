package oborokage.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import oborokage.Oborokage;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The storm model's 5/10/15/20 stages also cover a forty-tick terrain charge. */
@Mixin(UseDuration.class)
public class ChargeModelMixin {
  @Shadow @Final private boolean remaining;

  @Inject(method = "get", at = @At("RETURN"), cancellable = true)
  private void charge(
      ItemStack stack,
      ClientLevel level,
      ItemOwner owner,
      int seed,
      CallbackInfoReturnable<Float> ci) {
    if (!remaining
        && stack.is(Oborokage.RAIKIRI)
        && owner != null
        && owner.asLivingEntity() instanceof Player player) {
      ci.setReturnValue(ci.getReturnValue() * 20f / OborokageClient.fullChargeTicks(player));
    }
  }
}

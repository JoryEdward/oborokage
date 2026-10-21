package oborokage.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import oborokage.TombGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Items taken out of a chest (by click, shift-click or drag) may wake a blade tomb. */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class TombChestMixin {
  @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
  private void oborokage$taken(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
    var self = (RandomizableContainerBlockEntity) (Object) this;
    if (!cir.getReturnValue().isEmpty() && self.getLevel() instanceof ServerLevel level)
      TombGuard.taken(level, self.getBlockPos(), self, slot);
  }
}

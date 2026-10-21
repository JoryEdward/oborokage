package oborokage.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import oborokage.TombGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opening a blade tomb's chest: it remembers its own contents (the loot is unpacked by then). */
@Mixin(ChestBlockEntity.class)
public abstract class TombOpenMixin {
  @Inject(method = "startOpen", at = @At("HEAD"))
  private void oborokage$opened(ContainerUser user, CallbackInfo ci) {
    var self = (ChestBlockEntity) (Object) this;
    if (self.getLevel() instanceof ServerLevel level) TombGuard.opened(level, self);
  }
}

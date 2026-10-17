package oborokage.mixin;

import net.minecraft.advancements.*;
import net.minecraft.server.PlayerAdvancements;
import oborokage.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerAdvancements.class)
public class AdvancementChatMixin {
  @Inject(method = "lambda$award$0", at = @At("HEAD"), cancellable = true, require = 0)
  private void chat(AdvancementHolder advancement, DisplayInfo display, CallbackInfo ci) {
    if (advancement.id().getNamespace().equals("oborokage")
        && !ServerConfig.get().announceAdvancements) ci.cancel();
  }
}

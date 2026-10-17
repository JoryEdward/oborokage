package oborokage.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.advancements.*;
import net.minecraft.server.PlayerAdvancements;
import oborokage.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerAdvancements.class)
public class AdvancementChatMixin {
  // In 26.3 this consumer only announces the completed advancement. Progress, rewards,
  // visibility and toast synchronization remain in award's native execution path.
  @WrapOperation(
      method = "award",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
  private void chat(
      Optional<DisplayInfo> display,
      Consumer<DisplayInfo> announcement,
      Operation<Void> original,
      @Local(argsOnly = true) AdvancementHolder advancement) {
    if (!advancement.id().getNamespace().equals("oborokage")
        || ServerConfig.get().announceAdvancements) original.call(display, announcement);
  }
}

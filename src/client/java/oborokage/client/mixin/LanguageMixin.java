package oborokage.client.mixin;

import net.minecraft.locale.Language;
import oborokage.ModLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every language the game installs is wrapped, so the mod's own language setting applies. */
@Mixin(Language.class)
public abstract class LanguageMixin {
  @ModifyVariable(method = "inject", at = @At("HEAD"), argsOnly = true)
  private static Language oborokage$wrap(Language language) {
    return ModLanguage.wrap(language);
  }
}

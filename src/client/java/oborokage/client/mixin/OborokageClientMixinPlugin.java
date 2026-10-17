package oborokage.client.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

/**
 * Ensures mixin compatibility across Minecraft 1.26+ (26.x) releases.
 *
 * <p>Safely conditionally loads first-person render mixins only when the corresponding renderer
 * class is present in the runtime environment.
 */
public class OborokageClientMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (mixinClassName.endsWith("HandsMixin")) {
      try {
        Class.forName(
            "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer",
            false,
            getClass().getClassLoader());
        return true;
      } catch (ClassNotFoundException e) {
        return false;
      }
    }
    return true;
  }
}

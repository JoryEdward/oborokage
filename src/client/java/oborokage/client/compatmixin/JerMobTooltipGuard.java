package oborokage.client.compatmixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guard for Just Enough Resources (tested against 1.11.0.46 for 26.3). JER names each mob-drop slot
 * by its index into the mob's drop list, but replaces that list when it reads the world's loot
 * tables; a recipe page JEI laid out before then keeps slots past the end of the new list, and
 * hovering one threw IndexOutOfBoundsException and crashed the game. A stale slot now simply gets
 * no JER tooltip. Applied only when JER is installed.
 */
@Pseudo
@Mixin(targets = "jeresources.jei.mob.MobTooltip", remap = false)
public abstract class JerMobTooltipGuard {
  @Inject(
      method =
          "onRichTooltip(Lmezz/jei/api/gui/ingredient/IRecipeSlotView;"
              + "Lmezz/jei/api/gui/builder/ITooltipBuilder;)V",
      at = @At("HEAD"),
      cancellable = true,
      remap = false,
      require = 0)
  private void oborokage$skipStaleSlot(
      @Coerce Object slot, @Coerce Object tooltip, CallbackInfo ci) {
    try {
      Field entryField = getClass().getDeclaredField("entry");
      entryField.setAccessible(true);
      Object entry = entryField.get(this);
      Method dropsMethod = entry.getClass().getMethod("getDrops");
      List<?> drops = (List<?>) dropsMethod.invoke(entry);
      Method nameMethod =
          Class.forName(
                  "mezz.jei.api.gui.ingredient.IRecipeSlotView", false, getClass().getClassLoader())
              .getMethod("getSlotName");
      Optional<?> slotName = (Optional<?>) nameMethod.invoke(slot);
      Object name = slotName.isPresent() ? slotName.get() : "0";
      int index = Integer.parseInt(String.valueOf(name));
      if (drops == null || index < 0 || index >= drops.size()) ci.cancel();
    } catch (ReflectiveOperationException | RuntimeException e) {
      // If JER's layout differs from what this guard expects, leave its tooltip alone.
    }
  }
}

package oborokage.client.mixin;

import net.minecraft.client.renderer.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import oborokage.*;
import oborokage.client.OborokageClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class SheathModelMixin {
  @Shadow
  public abstract void updateForTopItem(
      ItemStackRenderState state,
      ItemStack item,
      ItemDisplayContext context,
      Level level,
      ItemOwner owner,
      int seed);

  @Inject(method = "updateForLiving", at = @At("HEAD"), cancellable = true)
  private void sheath(
      ItemStackRenderState state,
      ItemStack item,
      ItemDisplayContext context,
      LivingEntity owner,
      CallbackInfo ci) {
    if ((!Oborokage.mainBlade(item) && !item.is(Oborokage.WAKIZASHI))
        || context == ItemDisplayContext.GUI) return;
    var dual = oborokage.client.DualClient.scene(owner.getId());
    if (dual != null && dual.event.move() == Move.ULTIMATE && (dual.age < 7 || dual.age >= 34)) {
      ItemStack render = item.copy();
      render.set(
          DataComponents.ITEM_MODEL,
          Oborokage.id(
              item.is(Oborokage.WAKIZASHI)
                  ? "waki_sheathed"
                  : item.is(Oborokage.TACHI)
                      ? "sheathed"
                      : net.minecraft.core.registries.BuiltInRegistries.ITEM
                              .getKey(item.getItem())
                              .getPath()
                          + "_sheathed"));
      updateForTopItem(
          state, render, context, owner.level(), owner, owner.getId() + context.ordinal());
      ci.cancel();
      return;
    }
    // SlashPayload describes the original iaido only. Style techniques use DualClient scenes.
    if (!item.is(Oborokage.TACHI)) return;
    int age = OborokageClient.sceneAge(owner.getId());
    int delay = OborokageClient.sceneDelay(owner.getId());
    if (age >= 0 && (age < delay || age >= 34)) {
      ItemStack render = item.copy();
      render.set(DataComponents.ITEM_MODEL, Oborokage.id(age < delay ? "charged15" : "sheathed"));
      updateForTopItem(
          state, render, context, owner.level(), owner, owner.getId() + context.ordinal());
      ci.cancel();
    }
  }
}

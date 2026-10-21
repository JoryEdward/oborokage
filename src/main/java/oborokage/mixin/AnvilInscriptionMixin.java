package oborokage.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import oborokage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A blade and an inscription stone in the anvil: the stone's inscription is cut into the blade. An
 * ancient blade keeps its 古铭 (and the patina that goes with it): the anvil offers nothing.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilInscriptionMixin extends ItemCombinerMenu {
  @Shadow @Final private DataSlot cost;
  @Shadow private int repairItemCountCost;
  @Shadow private String itemName;

  private AnvilInscriptionMixin(
      MenuType<?> type,
      int id,
      net.minecraft.world.entity.player.Inventory inventory,
      ContainerLevelAccess access,
      ItemCombinerMenuSlotDefinition slots) {
    super(type, id, inventory, access, slots);
  }

  @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
  private void oborokage$inscribe(CallbackInfo ci) {
    ItemStack blade = inputSlots.getItem(0), stone = inputSlots.getItem(1);
    if (BladeStyle.of(blade) == null || !(stone.getItem() instanceof InscriptionStoneItem s))
      return;
    ci.cancel();
    if (Inscription.has(blade, s.inscription) || Inscription.has(blade, Inscription.ANCIENT)) {
      resultSlots.setItem(0, ItemStack.EMPTY);
      cost.set(0);
      repairItemCountCost = 0;
      broadcastChanges();
      return;
    }
    ItemStack result = blade.copy();
    result.set(Components.INSCRIPTION, s.inscription.id);
    if (itemName != null
        && !StringUtil.isBlank(itemName)
        && !itemName.equals(blade.getHoverName().getString()))
      result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
    resultSlots.setItem(0, result);
    cost.set(s.inscription.levels);
    repairItemCountCost = 1;
    broadcastChanges();
  }
}

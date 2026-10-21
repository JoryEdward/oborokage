package oborokage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

/** 铭石: put a blade and this stone in an anvil to cut the inscription into the blade. */
public final class InscriptionStoneItem extends Item {
  public final Inscription inscription;

  public InscriptionStoneItem(Properties properties, Inscription inscription) {
    super(properties);
    this.inscription = inscription;
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(
        Component.translatable("inscription.oborokage." + inscription.id + ".desc")
            .withStyle(ChatFormatting.AQUA));
    out.accept(
        Component.translatable("tooltip.oborokage.inscription_stone", inscription.levels)
            .withStyle(ChatFormatting.GRAY));
  }
}

package oborokage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

/** 残刀: a broken blade from a blade tomb, restored at the crafting table into an ancient blade. */
public final class RelicItem extends Item {
  public RelicItem(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(Component.translatable("tooltip.oborokage.relic").withStyle(ChatFormatting.GRAY));
  }
}

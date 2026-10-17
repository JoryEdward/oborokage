package oborokage;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

public final class WakizashiItem extends Item {
  public WakizashiItem(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(
        Component.translatable("tooltip.oborokage.wakizashi")
            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    out.accept(
        Component.translatable("tooltip.oborokage.controls")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
  }
}

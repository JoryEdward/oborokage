package oborokage.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import oborokage.*;

/** Per-stack lines on a blade's tooltip: its moon, inscription and whetstone edge. */
final class BladeTooltip {
  static void append(ItemStack stack, List<Component> lines) {
    if (BladeStyle.of(stack) == null) return;
    if (stack.is(Oborokage.TACHI)) {
      int phase = Moon.phase(stack);
      lines.add(
          Component.translatable(
                  "tooltip.oborokage.moon_phase",
                  Component.translatable("moon.oborokage." + phase),
                  phase,
                  Moon.FULL)
              .withStyle(phase >= Moon.FULL ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
    }
    Inscription inscription = Inscription.of(stack);
    if (inscription != null)
      lines.add(
          Component.translatable(
                  "tooltip.oborokage.inscribed",
                  Component.translatable("inscription.oborokage." + inscription.id),
                  Component.translatable("inscription.oborokage." + inscription.id + ".desc"))
              .withStyle(ChatFormatting.AQUA));
    var level = Minecraft.getInstance().level;
    if (level != null) {
      long left = Hone.remaining(stack, level.getGameTime());
      if (left > 0)
        lines.add(
            Component.translatable(
                    "tooltip.oborokage.honed", left / 1200, String.format("%02d", left / 20 % 60))
                .withStyle(ChatFormatting.YELLOW));
    }
  }

  private BladeTooltip() {}
}

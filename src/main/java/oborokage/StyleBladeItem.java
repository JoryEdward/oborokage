package oborokage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Same input grammar; each blade supplies a distinct charged technique. */
public final class StyleBladeItem extends TachiItem {
  public enum Style {
    STORM,
    FROST,
    VOID
  }

  private final Style style;

  public StyleBladeItem(Properties properties, Style style) {
    super(properties);
    this.style = style;
  }

  @Override
  public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
    if (!(user instanceof ServerPlayer p) || 72000 - remaining < 4) return false;
    float charge = Math.min(1, (72000 - remaining) / (style == Style.STORM ? 20f : 40f));
    if (p.isShiftKeyDown()) return super.releaseUsing(stack, level, user, remaining);
    var result = StyleTechniques.release(p, stack, style, charge);
    if (!result.fired()) return false;
    // 赤霆 · 雷返: a full-charge assault that fells an enemy is ready again almost at once.
    boolean recall =
        style == Style.STORM && charge >= 1 && result.kills() > 0 && BladeArts.enabled();
    p.getCooldowns()
        .addCooldown(
            stack, recall ? 4 : style == Style.STORM ? 14 : style == Style.FROST ? 30 : 36);
    if (recall)
      level.playSound(
          null,
          p.blockPosition(),
          net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
          net.minecraft.sounds.SoundSource.PLAYERS,
          .5f,
          2f);
    stack.hurtAndBreak(
        charge >= 1 ? 6 : 2,
        p,
        p.getUsedItemHand() == InteractionHand.MAIN_HAND
            ? EquipmentSlot.MAINHAND
            : EquipmentSlot.OFFHAND);
    return true;
  }

  @Override
  public void onUseTick(Level l, LivingEntity e, ItemStack s, int remaining) {
    if (style != Style.STORM || e.isShiftKeyDown()) {
      super.onUseTick(l, e, s, remaining);
      return;
    }
    int t = 72000 - remaining;
    if (l instanceof net.minecraft.server.level.ServerLevel sl && (t == 1 || t == 10 || t == 20)) {
      sl.playSound(
          null,
          e.blockPosition(),
          t == 20
              ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME
              : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
          net.minecraft.sounds.SoundSource.PLAYERS,
          .65f,
          t == 20 ? 1.7f : .65f + t * .036f);
    }
  }

  @Override
  public void appendHoverText(
      ItemStack stack,
      Item.TooltipContext context,
      net.minecraft.world.item.component.TooltipDisplay display,
      java.util.function.Consumer<net.minecraft.network.chat.Component> out,
      TooltipFlag flag) {
    out.accept(
        net.minecraft.network.chat.Component.translatable(
                "tooltip.oborokage." + style.name().toLowerCase(java.util.Locale.ROOT))
            .withStyle(net.minecraft.ChatFormatting.AQUA));
    out.accept(
        net.minecraft.network.chat.Component.translatable(
                "tooltip.oborokage.art." + style.name().toLowerCase(java.util.Locale.ROOT))
            .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
    out.accept(
        net.minecraft.network.chat.Component.translatable("tooltip.oborokage.terrain")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
  }
}

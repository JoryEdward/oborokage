package oborokage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 数打太刀: the plainest tachi, forged by the dozen. It cuts the single-blade chain like any blade of
 * the mod but has no art, technique or ultimate of its own; it is the blank every named blade is
 * forged from, and what swordsmen carry.
 */
public final class KazuuchiItem extends Item {
  public KazuuchiItem(Properties properties) {
    super(properties);
  }

  /**
   * Sneak-use on a whetstone: the grind into a wakizashi (the block itself is skipped when
   * sneaking).
   */
  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    var pos = context.getClickedPos();
    if (player == null
        || !player.isSecondaryUseActive()
        || !context.getLevel().getBlockState(pos).is(Content.WHETSTONE))
      return super.useOn(context);
    if (!(context.getLevel() instanceof ServerLevel server)) return InteractionResult.SUCCESS;
    return WhetstoneBlock.grind(server, pos, player, context.getHand());
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(Component.translatable("tooltip.oborokage.kazuuchi").withStyle(ChatFormatting.GRAY));
  }
}

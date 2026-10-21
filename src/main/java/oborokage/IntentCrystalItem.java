package oborokage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** 剑意结晶: dropped by swordsmen. Use it to gather 25 intent at once, or cut it into a stone. */
public final class IntentCrystalItem extends Item {
  public static final int INTENT = 25;

  public IntentCrystalItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
    if (DualCombat.state(p).intent() >= 100) return InteractionResult.FAIL;
    DualCombat.addIntent(p, INTENT);
    ItemStack stack = p.getItemInHand(hand);
    if (!p.getAbilities().instabuild) stack.shrink(1);
    p.getCooldowns().addCooldown(stack, 40);
    level.playSound(
        null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .8f, 1.4f);
    return InteractionResult.CONSUME;
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(
        Component.translatable("tooltip.oborokage.intent_crystal", INTENT)
            .withStyle(ChatFormatting.GRAY));
  }
}

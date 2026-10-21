package oborokage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 磨刀石. Use it with a blade in hand and a flint in the inventory: the flint is spent and the blade
 * is honed for five minutes (configurable), cutting 5% harder with brighter light. Sneak and use it
 * with a kazuuchi in hand and six 剑魄 in the inventory to grind the plain blade down into a
 * wakizashi.
 */
public final class WhetstoneBlock extends Block {
  public static final int SOULS_PER_WAKIZASHI = 6;

  private static final VoxelShape SHAPE = Block.box(1, 0, 3, 15, 11, 13);

  public WhetstoneBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state,
      net.minecraft.world.level.BlockGetter level,
      BlockPos pos,
      CollisionContext context) {
    return SHAPE;
  }

  @Override
  protected InteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (BladeStyle.of(stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
    if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
    if (!player.getAbilities().instabuild) {
      int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.FLINT));
      if (slot < 0) {
        player.sendOverlayMessage(
            net.minecraft.network.chat.Component.translatable(
                "ui.oborokage.whetstone_needs_flint"));
        return InteractionResult.FAIL;
      }
      player.getInventory().removeItem(slot, 1);
    }
    Hone.apply(stack, server.getGameTime());
    server.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1, 1.2f);
    server.sendParticles(
        ParticleTypes.ELECTRIC_SPARK,
        pos.getX() + .5,
        pos.getY() + .9,
        pos.getZ() + .5,
        14,
        .25,
        .05,
        .25,
        .08);
    return InteractionResult.CONSUME;
  }

  /**
   * Six 剑魄 grind a kazuuchi down into a wakizashi (creative players need none). Reached from {@link
   * KazuuchiItem#useOn}: a sneaking player holding an item never reaches the block's own use.
   */
  static InteractionResult grind(
      ServerLevel server, BlockPos pos, Player player, InteractionHand hand) {
    var inventory = player.getInventory();
    if (!player.getAbilities().instabuild) {
      int have = inventory.countItem(Content.BLADE_SOUL);
      if (have < SOULS_PER_WAKIZASHI) {
        player.sendOverlayMessage(
            net.minecraft.network.chat.Component.translatable(
                "ui.oborokage.whetstone_needs_souls", have, SOULS_PER_WAKIZASHI));
        return InteractionResult.FAIL;
      }
      int left = SOULS_PER_WAKIZASHI;
      for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
        ItemStack s = inventory.getItem(i);
        if (!s.is(Content.BLADE_SOUL)) continue;
        int take = Math.min(left, s.getCount());
        s.shrink(take);
        left -= take;
      }
    }
    player.setItemInHand(hand, new ItemStack(Oborokage.WAKIZASHI));
    server.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1, .7f);
    server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1, 1.2f);
    server.sendParticles(
        ParticleTypes.SOUL, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 16, .3, .2, .3, .03);
    return InteractionResult.CONSUME;
  }
}

package oborokage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Plants a training stake on the clicked face, turned toward the player. It stands on the surface
 * it was set on (a slab, snow, a carpet) and never on thin air: it has no AI, so it would not fall.
 */
public final class TrainingDummyItem extends Item {
  public TrainingDummyItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
    Direction face = context.getClickedFace();
    BlockPos pos = context.getClickedPos().relative(face);
    if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
    double y;
    if (face == Direction.UP) y = context.getClickLocation().y;
    else {
      BlockPos under = pos.below();
      var floor = level.getBlockState(under).getCollisionShape(level, under);
      if (floor.isEmpty()) return InteractionResult.FAIL;
      y = under.getY() + floor.max(Direction.Axis.Y);
    }
    TrainingDummy dummy = Content.TRAINING_DUMMY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
    if (dummy == null) return InteractionResult.FAIL;
    float yaw = context.getPlayer() == null ? 0 : context.getPlayer().getYRot() + 180;
    dummy.snapTo(pos.getX() + .5, y, pos.getZ() + .5, yaw, 0);
    dummy.setYHeadRot(yaw);
    dummy.setYBodyRot(yaw);
    if (!level.noCollision(dummy)
        || !level
            .getEntities(
                dummy, dummy.getBoundingBox(), e -> e instanceof LivingEntity && !e.isSpectator())
            .isEmpty()) return InteractionResult.FAIL;
    level.addFreshEntity(dummy);
    if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)
      context.getItemInHand().shrink(1);
    return InteractionResult.CONSUME;
  }

  @Override
  public void appendHoverText(
      ItemStack s, TooltipContext c, TooltipDisplay d, Consumer<Component> out, TooltipFlag f) {
    out.accept(
        Component.translatable("tooltip.oborokage.training_dummy").withStyle(ChatFormatting.GRAY));
  }
}

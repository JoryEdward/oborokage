package oborokage;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * A living swordsman with nothing to fight sometimes goes to the nearest training post on its
 * ground and practises on it for a while: a cut every second and a half or so, the post swaying and
 * reading out each blow.
 */
public class PracticeGoal extends Goal {
  private final Swordsman mob;
  private TrainingDummy post;
  private int left, swing;

  public PracticeGoal(Swordsman mob) {
    this.mob = mob;
    setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    if (mob.getTarget() != null || mob.getRandom().nextInt(reducedTickDelay(60)) != 0) return false;
    // The posts on its ground (it may have wandered to the far side of it), or near it.
    AABB area =
        mob.hasHome()
            ? new AABB(mob.getHomePosition())
                .inflate(mob.getHomeRadius() + 1, 3, mob.getHomeRadius() + 1)
                .minmax(mob.getBoundingBox().inflate(6, 3, 6))
            : mob.getBoundingBox().inflate(12, 3, 12);
    post =
        mob.level().getEntitiesOfClass(TrainingDummy.class, area).stream()
            .filter(
                p ->
                    !mob.hasHome()
                        || mob.isWithinHome(p.blockPosition())
                        || mob.distanceToSqr(p) < 36)
            .min((a, b) -> Double.compare(mob.distanceToSqr(a), mob.distanceToSqr(b)))
            .orElse(null);
    return post != null;
  }

  @Override
  public boolean canContinueToUse() {
    return post != null && post.isAlive() && mob.getTarget() == null && left > 0;
  }

  @Override
  public void start() {
    left = 160 + mob.getRandom().nextInt(160);
    swing = 20;
  }

  @Override
  public void stop() {
    post = null;
    mob.getNavigation().stop();
  }

  @Override
  public boolean requiresUpdateEveryTick() {
    return true;
  }

  @Override
  public void tick() {
    left--;
    mob.getLookControl().setLookAt(post, 30, 30);
    if (mob.distanceToSqr(post) > 2.6 * 2.6) {
      if (mob.getNavigation().isDone()) mob.getNavigation().moveTo(post, .8);
      return;
    }
    mob.getNavigation().stop();
    if (--swing <= 0 && mob.level() instanceof ServerLevel level) {
      swing = 26 + mob.getRandom().nextInt(14);
      oborokage.compat.Compat.swing(mob, InteractionHand.MAIN_HAND);
      mob.doHurtTarget(level, post);
    }
  }
}

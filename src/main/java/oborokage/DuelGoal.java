package oborokage;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * How the swordsmen fight, in place of vanilla's walk-up-and-swing. Closes in and cuts when its
 * swing is ready; between swings it keeps a sword's length and circles its foe, switching sides now
 * and then; it rushes a player who stands charging a draw, edges round a raised shield, and backs
 * off when badly hurt. The 樱下鬼 does not circle: it drifts straight in.
 */
public class DuelGoal extends Goal {
  private final Swordsman mob;
  private final double speed;
  private int cooldown, repath, side = 1, sideTicks;
  private double lastX, lastY, lastZ;

  public DuelGoal(Swordsman mob, double speed) {
    this.mob = mob;
    this.speed = speed;
    setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  private LivingEntity target() {
    LivingEntity t = mob.getTarget();
    return t != null
            && t.isAlive()
            && !(t instanceof Player p && (p.isSpectator() || p.isCreative()))
        ? t
        : null;
  }

  /**
   * Whether `t` is gathering a draw: a player charging one of the mod's blades (not raising a
   * shield, which the swordsman edges round instead, nor eating).
   */
  public static boolean drawing(LivingEntity t) {
    return t instanceof Player p && p.isUsingItem() && Oborokage.mainBlade(p.getUseItem());
  }

  @Override
  public boolean canUse() {
    return target() != null;
  }

  @Override
  public boolean canContinueToUse() {
    LivingEntity t = target();
    return t != null && mob.distanceToSqr(t) < 32 * 32;
  }

  @Override
  public void start() {
    mob.setAggressive(true);
    repath = 0;
  }

  @Override
  public void stop() {
    mob.setAggressive(false);
    mob.getNavigation().stop();
    mob.getMoveControl().strafe(0, 0);
  }

  @Override
  public boolean requiresUpdateEveryTick() {
    return true;
  }

  @Override
  public void tick() {
    LivingEntity t = target();
    if (t == null || !(mob.level() instanceof ServerLevel level)) return;
    mob.getLookControl().setLookAt(t, 30, 30);
    if (mob.busy()) {
      mob.getNavigation().stop();
      mob.getMoveControl().strafe(0, 0);
      return;
    }
    if (cooldown > 0) cooldown--;
    double d = mob.distanceToSqr(t);
    boolean see = mob.getSensing().hasLineOfSight(t);
    boolean ready = cooldown <= 0;
    // A player gathering a draw is open: close in before it lands.
    boolean charging = drawing(t) && mob.strafes();
    boolean hurt = mob.getHealth() < mob.getMaxHealth() * .3f && mob.strafes();
    if (ready && mob.isWithinMeleeAttackRange(t) && see) {
      mob.getNavigation().stop();
      mob.getMoveControl().strafe(0, 0);
      oborokage.compat.Compat.swing(mob, InteractionHand.MAIN_HAND);
      mob.doHurtTarget(level, t);
      cooldown = 18 + mob.getRandom().nextInt(10);
      mob.afterCut(level, t);
      return;
    }
    if (!mob.strafes() || ready || charging || !see || d > 5.5 * 5.5) {
      // In to cut (or to find the foe again). Like vanilla, a new path only when the foe has moved
      // or the old one ran out, less often from afar, and not for a while after a failed one.
      if (--repath <= 0
          && (mob.getNavigation().isDone()
              || t.distanceToSqr(lastX, lastY, lastZ) >= 1
              || mob.getRandom().nextFloat() < .05f)) {
        lastX = t.getX();
        lastY = t.getY();
        lastZ = t.getZ();
        repath = 4 + mob.getRandom().nextInt(7) + (d > 32 * 32 ? 10 : d > 16 * 16 ? 5 : 0);
        if (!mob.getNavigation().moveTo(t, charging ? speed * 1.3 : speed)) repath += 15;
      }
      return;
    }
    // Between swings: hold a sword's length and circle, switching sides now and then.
    mob.getNavigation().stop();
    repath = 0;
    if (--sideTicks <= 0 || mob.horizontalCollision) {
      side = mob.getRandom().nextBoolean() ? 1 : -1;
      sideTicks = 20 + mob.getRandom().nextInt(40);
    }
    float forward = hurt ? -.6f : d < 2.4 * 2.4 ? -.45f : d > 4 * 4 ? .35f : 0;
    float sideways = side * (t.isBlocking() ? .7f : .45f);
    mob.getMoveControl().strafe(forward, sideways);
  }
}

package oborokage;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A 樱下鬼's place in a 百鬼夜行 ({@link NightParade}). The 鬼将 leading it walks from stop to stop, each 64
 * to 96 blocks on from the last; the others keep their places in a column behind it, {@link
 * #SPACING} blocks apart. Marching, they take no foe: a blow at any of them, or a player within
 * {@link #WAKE_RANGE} blocks, turns the whole parade to fight. At dawn those not fighting go out
 * like soul fire. Saved with the 樱下鬼.
 */
public final class ParadeRole {
  static final double SPACING = 2.5, WAKE_RANGE = 8;
  private final BlossomOni oni;

  /** The 鬼将 it follows (null for the 鬼将 itself, and outside a parade) and its place behind. */
  private UUID leader;

  private int rank;

  /** The 鬼将's next stop (null for the others). */
  private BlockPos waypoint;

  /** Whether its parade has turned to fight. */
  private boolean provoked;

  ParadeRole(BlossomOni oni) {
    this.oni = oni;
  }

  public boolean member() {
    return leader != null || waypoint != null;
  }

  /** Whether it leads a parade (its 鬼将). */
  public boolean leads() {
    return waypoint != null;
  }

  /** On the march: in a parade that has not turned to fight. */
  public boolean marching() {
    return member() && !provoked;
  }

  public boolean provoked() {
    return provoked;
  }

  public BlockPos waypoint() {
    return waypoint;
  }

  /** The parade's mark: its 鬼将's id. */
  public UUID mark() {
    return leads() ? oni.getUUID() : leader;
  }

  void lead(BlockPos first) {
    waypoint = first;
  }

  void follow(BlossomOni leader, int rank) {
    this.leader = leader.getUUID();
    this.rank = rank;
  }

  void save(ValueOutput out) {
    out.storeNullable("ParadeLeader", UUIDUtil.CODEC, leader);
    out.putInt("ParadeRank", rank);
    out.storeNullable("ParadeStop", BlockPos.CODEC, waypoint);
    out.putBoolean("ParadeProvoked", provoked);
  }

  void load(ValueInput in) {
    leader = in.read("ParadeLeader", UUIDUtil.CODEC).orElse(null);
    rank = in.getIntOr("ParadeRank", 0);
    waypoint = in.read("ParadeStop", BlockPos.CODEC).orElse(null);
    provoked = in.getBooleanOr("ParadeProvoked", false);
  }

  /** The 鬼将 it follows, if alive and near. */
  private BlossomOni leaderNow(ServerLevel level) {
    return leader != null
            && level.getEntity(leader) instanceof BlossomOni l
            && l.isAlive()
            && l.distanceToSqr(oni) < 64 * 64
        ? l
        : null;
  }

  /** Turns the whole parade on `foe`. */
  public void provoke(ServerLevel level, LivingEntity foe) {
    UUID mark = mark();
    if (mark == null) return;
    for (BlossomOni o :
        level.getEntitiesOfClass(
            BlossomOni.class,
            oni.getBoundingBox().inflate(48, 16, 48),
            o -> o.isAlive() && mark.equals(o.parade().mark()))) {
      o.parade().provoked = true;
      if (o.getTarget() == null) o.setTarget(foe);
    }
  }

  /** One server tick: dawn, and a player coming too near the march. */
  void tick(ServerLevel level) {
    if (!member()) return;
    if (oni.tickCount % 20 == 0 && level.isBrightOutside() && oni.getTarget() == null) {
      oni.dismiss(level);
      return;
    }
    if (!marching() || oni.tickCount % 10 != 0) return;
    for (ServerPlayer p : level.players())
      if (p.isAlive()
          && !p.isSpectator()
          && !p.isCreative()
          && oni.distanceToSqr(p) < WAKE_RANGE * WAKE_RANGE) {
        provoke(level, p);
        return;
      }
  }

  /** The march itself, as a goal above the 樱下鬼's wandering. */
  static final class MarchGoal extends Goal {
    private final BlossomOni oni;

    MarchGoal(BlossomOni oni) {
      this.oni = oni;
      setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
      return oni.parade() != null && oni.parade().marching() && oni.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
      return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
      return true;
    }

    @Override
    public void tick() {
      if (oni.level() instanceof ServerLevel level) oni.parade().march(level);
    }
  }

  private void march(ServerLevel level) {
    var nav = oni.getNavigation();
    if (leads()) {
      Vec3 goal = Vec3.atBottomCenterOf(waypoint);
      if (Math.hypot(goal.x - oni.getX(), goal.z - oni.getZ()) < 4) {
        // Arrived: on to the next stop, turning up to 60° either way.
        float yaw = oni.getYRot() + (oni.getRandom().nextFloat() - .5f) * 120;
        waypoint =
            NightParade.waypoint(oni.blockPosition(), Math.toRadians(yaw + 90), oni.getRandom());
        return;
      }
      if (nav.isDone() && oni.tickCount % 10 == 0) {
        // Paths reach only so far: ten blocks of the way at a time, straight on; round whatever
        // is in the way (a narrow random step) when that cannot be walked.
        Vec3 ahead =
            oni.position()
                .add(goal.subtract(oni.position()).multiply(1, 0, 1).normalize().scale(10));
        int x = net.minecraft.util.Mth.floor(ahead.x), z = net.minecraft.util.Mth.floor(ahead.z);
        int y =
            level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!nav.moveTo(x + .5, y, z + .5, .9)) {
          Vec3 step = DefaultRandomPos.getPosTowards(oni, 10, 6, goal, Math.PI / 6);
          if (step != null) nav.moveTo(step.x, step.y, step.z, .9);
        }
      }
      return;
    }
    BlossomOni lead = leaderNow(level);
    if (lead == null) return;
    Vec3 back = Vec3.directionFromRotation(0, lead.getYRot()).scale(-SPACING * (rank + 1));
    Vec3 spot = lead.position().add(back.x, 0, back.z);
    double d = oni.distanceToSqr(spot);
    if (d > 2 * 2 && oni.tickCount % 10 == rank % 10)
      nav.moveTo(spot.x, spot.y, spot.z, d > 12 * 12 ? 1.15 : .85);
  }
}

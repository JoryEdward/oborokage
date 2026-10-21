package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * The mobs' AI and details, one phase after another in a closed room so they never see each other:
 * allies rally to the attacked, the master rallies its students and collects itself out of a fight,
 * the 樱下鬼 fades and comes back behind its attacker, does not fall, drown or freeze, and never rises
 * in a cave under the grove; zombified piglins keep out of the feud; an idle swordsman practises on
 * a post; a swordsman and a 樱下鬼 left to themselves fight it out.
 */
final class AiChecks {
  private static final int X0 = -30, X1 = -6, Z0 = 10, Z1 = 36;
  private static final int X = -18, Z = 23;
  private final ServerChecks t;
  private final List<Entity> phase = new ArrayList<>();
  private int age, practiceFrom, duelFrom;
  private BlossomOni faded;
  private Mob fadeFrom, duelA, duelB;
  private TrainingDummy post;
  private PetalLord resting;
  private boolean circled;
  private Mob practiser;
  private BlockPos cave, open, canopy;

  AiChecks(ServerChecks t) {
    this.t = t;
  }

  private <T extends Mob> T spawn(EntityType<T> type, double x, double z, boolean ai) {
    ServerLevel level = t.level;
    T mob = type.create(level, EntitySpawnReason.COMMAND);
    mob.setPos(x, 100, z);
    mob.finalizeSpawn(
        level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
    mob.setNoAi(!ai);
    level.addFreshEntity(mob);
    phase.add(mob);
    return mob;
  }

  private void clear() {
    for (Entity e : phase) e.discard();
    phase.clear();
  }

  /** Whether the mob was given the undead's goal of hunting swordsmen. */
  private static boolean huntsSwordsmen(Mob mob) throws Exception {
    var type = NearestAttackableTargetGoal.class.getDeclaredField("targetType");
    type.setAccessible(true);
    for (var g : ((oborokage.mixin.MobGoalsAccess) mob).oborokage$targets().getAvailableGoals())
      if (g.getGoal() instanceof NearestAttackableTargetGoal<?> n && type.get(n) == Swordsman.class)
        return true;
    return false;
  }

  /** Sets the time of day by command (26.3 replaced the day time with world clocks). */
  private void time(String ticks) {
    t.server
        .getCommands()
        .performPrefixedCommand(t.server.createCommandSourceStack(), "time set " + ticks);
  }

  private boolean rule(BlockPos pos, int seed) {
    return SpawnPlacements.checkSpawnRules(
        Content.BLOSSOM_ONI, t.level, EntitySpawnReason.NATURAL, pos, RandomSource.create(seed));
  }

  private boolean vanilla(BlockPos pos, int seed) {
    return Monster.checkMonsterSpawnRules(
        Content.BLOSSOM_ONI, t.level, EntitySpawnReason.NATURAL, pos, RandomSource.create(seed));
  }

  /** What a mob is doing: its running goals, ground, path and distance to a thing. */
  private static String state(Mob mob, Entity to) throws Exception {
    var goals = Mob.class.getDeclaredField("goalSelector");
    goals.setAccessible(true);
    var running = new ArrayList<String>();
    for (var g :
        ((net.minecraft.world.entity.ai.goal.GoalSelector) goals.get(mob)).getAvailableGoals())
      if (g.isRunning()) running.add(g.getGoal().getClass().getSimpleName());
    return "running "
        + running
        + " at "
        + mob.blockPosition().toShortString()
        + " home "
        + (mob.hasHome()
            ? mob.getHomePosition().toShortString() + " r" + mob.getHomeRadius()
            : "none")
        + " withinHome "
        + mob.isWithinHome()
        + " distance "
        + f(mob.distanceTo(to))
        + " pathDone "
        + mob.getNavigation().isDone()
        + " noAi "
        + mob.isNoAi();
  }

  /** The mobs in the room, by type: what the test placed and anything else that got in. */
  private static String room(ServerLevel level) {
    var counts = new TreeMap<String, Integer>();
    for (Mob m :
        level.getEntitiesOfClass(
            Mob.class, new net.minecraft.world.phys.AABB(X0, 99, Z0, X1 + 1, 105, Z1 + 1)))
      counts.merge(m.getType().toShortString(), 1, Integer::sum);
    return counts.toString();
  }

  private static String f(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  /** In a newly created test world the force-loaded chunks take a few ticks to start ticking. */
  private boolean ready(ServerLevel level) {
    for (int cx = X0 >> 4; cx <= 44 >> 4; cx++)
      for (int cz = Z0 >> 4; cz <= Z1 >> 4; cz++)
        if (!level.areEntitiesActuallyLoadedAndTicking(
            new net.minecraft.world.level.ChunkPos(cx, cz))) return false;
    return true;
  }

  boolean tick() throws Exception {
    ServerLevel level = t.level;
    if (age == 0 && !ready(level)) return false;
    age++;
    if (age == 1) {
      // A failed run stops before tidying up and the world keeps what it left: clear the room.
      for (Mob m :
          level.getEntitiesOfClass(
              Mob.class, new net.minecraft.world.phys.AABB(X0, 99, Z0, X1 + 1, 105, Z1 + 1)))
        m.discard();
      for (int x = X0; x <= X1; x++)
        for (int z = Z0; z <= Z1; z++) {
          boolean edge = x == X0 || x == X1 || z == Z0 || z == Z1;
          level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
          for (int y = 100; y < 104; y++)
            level.setBlock(
                new BlockPos(x, y, z), (edge ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
          level.setBlock(new BlockPos(x, 104, z), Blocks.STONE.defaultBlockState(), 2);
        }
      // Rallies: the living to the living, the dead to the dead. Struck from behind, so no parry.
      var a = spawn(Content.SWORDSMAN, X - 9.5, Z - 8.5, false);
      var b = spawn(Content.SWORDSMAN, X - 7.5, Z - 8.5, false);
      var zombie = spawn(EntityTypes.ZOMBIE, X - 8.5, Z - 11.5, false);
      a.setYRot(0);
      boolean struck = a.hurtServer(level, level.damageSources().mobAttack(zombie), 1);
      t.check(
          b.getTarget() == zombie,
          "Struck, a swordsman's companions turn on the attacker (struck="
              + struck
              + " health="
              + a.getHealth()
              + " target="
              + b.getTarget()
              + " zombie="
              + zombie
              + ")");
      var oa = spawn(Content.BLOSSOM_ONI, X - 4.5, Z - 8.5, false);
      var ob = spawn(Content.BLOSSOM_ONI, X - 2.5, Z - 8.5, false);
      oa.setYRot(-90);
      oa.hurtServer(level, level.damageSources().mobAttack(a), 1);
      t.check(
          ob.getTarget() == a && b.getTarget() == zombie,
          "Struck, a 樱下鬼 calls its own kind, not the living");
      var lord = spawn(Content.PETAL_LORD, X + 2.5, Z - 8.5, false);
      var student = spawn(Content.SWORDSMAN, X + 5.5, Z - 8.5, false);
      lord.setTarget(zombie);
      t.check(student.getTarget() == zombie, "The master's students turn on the foe it takes");
      // Zombies join the feud against the living; zombified piglins keep to themselves.
      var piglin = spawn(EntityTypes.ZOMBIFIED_PIGLIN, X + 8.5, Z - 8.5, false);
      t.check(
          huntsSwordsmen(zombie) && !huntsSwordsmen(piglin),
          "Zombies hunt swordsmen; zombified piglins are left out of it");
      // The ghost does not fall, drown or freeze, and walks over powder snow.
      var ghost = spawn(Content.BLOSSOM_ONI, X + 8.5, Z - 4.5, false);
      boolean fell = ghost.causeFallDamage(20, 1, level.damageSources().fall());
      t.check(
          !fell
              && ghost.getHealth() == ghost.getMaxHealth()
              && ghost.canBreatheUnderwater()
              && !ghost.canFreeze()
              && net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(ghost),
          "The 樱下鬼 takes no fall damage, does not drown or freeze, and walks over powder snow");
      clear();
      // Where it may rise: a dark pocket under stone (a cave), open ground under the sky and ground
      // under a cherry canopy, checked at night once the light has settled (below, at age 30).
      cave = new BlockPos(X + 8, 96, Z + 8);
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          for (int y = 94; y <= 99; y++)
            level.setBlock(cave.offset(dx, y - 96, dz), Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(cave, Blocks.AIR.defaultBlockState(), 2);
      level.setBlock(cave.above(), Blocks.AIR.defaultBlockState(), 2);
      open = new BlockPos(40, 120, 40);
      canopy = new BlockPos(44, 120, 40);
      var leaves =
          Blocks.CHERRY_LEAVES
              .defaultBlockState()
              .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          level.setBlock(open.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
          level.setBlock(canopy.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
          level.setBlock(canopy.offset(dx, 3, dz), leaves, 2);
        }
      time("18000");
      // The fade: gone for a moment, back behind the attacker.
      // (Kept more than 16 blocks from the master below, so the cut it lands rallies no one.)
      fadeFrom = spawn(Content.SWORDSMAN, X + .5, Z0 + 3.5, false);
      fadeFrom.setYRot(0);
      faded = spawn(Content.BLOSSOM_ONI, X + .5, Z0 + 5.5, false);
      var fade = BlossomOni.class.getDeclaredMethod("fade", ServerLevel.class, LivingEntity.class);
      fade.setAccessible(true);
      fade.invoke(faded, level, fadeFrom);
      t.check(faded.hasEffect(MobEffects.INVISIBILITY), "Struck, the 樱下鬼 can fade from sight");
      // Collecting itself: the master, alone, hurt and long out of a fight.
      resting = spawn(Content.PETAL_LORD, X - 8.5, Z1 - 2.5, false);
      resting.setHealth(120);
      var last = PetalLord.class.getDeclaredField("lastFight");
      last.setAccessible(true);
      last.setLong(resting, level.getGameTime() - 1000);
      return false;
    }
    if (age == 30) {
      // Natural spawning asks SpawnPlacements, which runs the rule the mod registered. Each spot is
      // tried with 64 seeds against vanilla's monster rule (light and darkness are random draws).
      int caveOurs = 0, caveVanilla = 0, openOurs = 0, canopyOurs = 0, differ = 0;
      for (int seed = 0; seed < 64; seed++) {
        if (rule(cave, seed)) caveOurs++;
        if (vanilla(cave, seed)) caveVanilla++;
        boolean o = rule(open, seed), c = rule(canopy, seed);
        if (o) openOurs++;
        if (c) canopyOurs++;
        if (o != vanilla(open, seed) || c != vanilla(canopy, seed)) differ++;
      }
      t.check(
          caveOurs == 0 && caveVanilla > 0,
          "No 樱下鬼 rises naturally in a cave under the grove: "
              + caveOurs
              + "/64 (vanilla monster rule: "
              + caveVanilla
              + "/64)");
      t.check(
          differ == 0 && openOurs > 0 && canopyOurs > 0,
          "On the surface at night the 樱下鬼 still rises, open ground and under cherry leaves alike:"
              + " open "
              + openOurs
              + "/64, canopy "
              + canopyOurs
              + "/64, differing from vanilla "
              + differ);
      boolean spawner =
          SpawnPlacements.checkSpawnRules(
              Content.BLOSSOM_ONI, level, EntitySpawnReason.SPAWNER, cave, RandomSource.create(1));
      t.check(
          spawner, "A spawner in a cave still raises the 樱下鬼 (only natural spawning is limited)");
      time("1000");
    }
    if (age == 1 + 14 + 4) { // the fade lasts 14 ticks
      double behind = faded.getZ() - fadeFrom.getZ();
      t.check(
          !faded.hasEffect(MobEffects.INVISIBILITY)
              && faded.distanceTo(fadeFrom) < 2.6
              && behind < 0,
          "The faded 樱下鬼 comes back behind its attacker: d="
              + f(faded.distanceTo(fadeFrom))
              + " behind="
              + f(behind));
    }
    if (age == 120) {
      t.check(
          resting.getHealth() > 120,
          "Left alone, the master collects itself: " + resting.getHealth());
      clear();
      // Practice: an idle swordsman and a post.
      post = Content.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
      post.setPos(X + .5, 100, Z + 4.5);
      level.addFreshEntity(post);
      phase.add(post);
      practiser = spawn(Content.SWORDSMAN, X + .5, Z - 1.5, true);
      practiceFrom = age;
      return false;
    }
    if (practiceFrom > 0 && duelFrom == 0) {
      boolean hit = !post.readout().getString().isEmpty();
      if (!hit && age - practiceFrom < 900) return false;
      t.check(
          hit,
          "An idle swordsman practises on a training post nearby ("
              + (age - practiceFrom)
              + " ticks; its target "
              + practiser.getTarget()
              + "; in the room "
              + room(level)
              + "; "
              + state(practiser, post)
              + ")");
      clear();
      // A duel left to itself.
      duelA = spawn(Content.SWORDSMAN, X - 3.5, Z + .5, true);
      duelB = spawn(Content.BLOSSOM_ONI, X + 3.5, Z + .5, true);
      duelFrom = age;
      return false;
    }
    if (duelFrom > 0) {
      if (duelA.xxa != 0) circled = true;
      boolean over = !duelA.isAlive() || !duelB.isAlive();
      if (!over && age - duelFrom < 1200) return false;
      t.check(
          over,
          "A swordsman and a 樱下鬼 left to themselves fight it out ("
              + (age - duelFrom)
              + " ticks): swordsman "
              + duelA.getHealth()
              + ", 樱下鬼 "
              + duelB.getHealth());
      t.check(circled, "Between cuts the swordsman circles its foe");
      clear();
      return true;
    }
    return false;
  }
}

package oborokage;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 百鬼夜行 (the Night Parade): on full-moon nights a column of 樱下鬼 led by a 鬼将 walks the cherry groves
 * ({@link ParadeRole}). Every 200 ticks each player on a grove's open ground, with no parade
 * marching within 128 blocks and none met in the last day, has a chance (nightParadeChance, 20%) of
 * seeing one come out 32 to 48 blocks off. A parade is a 鬼将 and six to nine 樱下鬼, two more for each
 * other player near, nightParadeMax (12) in all. Never in peaceful, nor off the overworld; off with
 * nightParade=false.
 */
public final class NightParade {
  /** The game time a player last met a parade (saved, kept through death). */
  public static final AttachmentType<Long> MET =
      AttachmentRegistry.<Long>create(
          Oborokage.id("parade_met"), b -> b.persistent(Codec.LONG).copyOnDeath());

  private NightParade() {}

  static void init() {
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(
        NightParade::tick);
  }

  /** Whether it is a full-moon night at `pos`. */
  public static boolean fullMoonNight(ServerLevel level, BlockPos pos) {
    return level.isDarkOutside()
        && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos)
            == MoonPhase.FULL_MOON;
  }

  /** Whether `player` has met a parade within the last day (one a night at most). */
  public static boolean metLately(ServerLevel level, ServerPlayer player) {
    Long met = player.getAttached(MET);
    return met != null && level.getGameTime() - met < 24000;
  }

  private static void tick(ServerLevel level) {
    if (level.getGameTime() % 200 != 0 || level.dimension() != Level.OVERWORLD) return;
    var config = ServerConfig.get();
    if (!config.nightParade || level.getDifficulty() == Difficulty.PEACEFUL) return;
    for (ServerPlayer p : List.copyOf(level.players())) {
      BlockPos at = p.blockPosition();
      if (!p.isAlive()
          || p.isSpectator()
          || !fullMoonNight(level, at)
          || !level.getBiome(at).is(Biomes.CHERRY_GROVE)
          || at.getY() + 1
              < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ())
          || metLately(level, p)
          || marching(level, at)
          || level.getRandom().nextDouble() >= config.nightParadeChance) continue;
      if (start(level, p.position()) != null) p.setAttached(MET, level.getGameTime());
    }
  }

  /** Whether a parade is on the march within 128 blocks of `at`. */
  public static boolean marching(ServerLevel level, BlockPos at) {
    return !level
        .getEntitiesOfClass(
            BlossomOni.class,
            new AABB(at).inflate(128, 64, 128),
            o -> o.isAlive() && o.parade().leads() && o.parade().marching())
        .isEmpty();
  }

  /** A stop 64 to 96 blocks from `from` toward `angle` (radians, x = cos, z = sin). */
  static BlockPos waypoint(BlockPos from, double angle, RandomSource random) {
    double d = 64 + random.nextDouble() * 32;
    return BlockPos.containing(
        from.getX() + Math.cos(angle) * d, from.getY(), from.getZ() + Math.sin(angle) * d);
  }

  /**
   * Brings a parade out 32 to 48 blocks from `near`, on open ground, marching across the way;
   * returns its 鬼将, or null if no ground was found.
   */
  public static BlossomOni start(ServerLevel level, Vec3 near) {
    RandomSource r = level.getRandom();
    for (int attempt = 0; attempt < 16; attempt++) {
      double a = r.nextDouble() * Math.PI * 2, d = 32 + r.nextDouble() * 16;
      int x = Mth.floor(near.x + Math.cos(a) * d), z = Mth.floor(near.z + Math.sin(a) * d);
      BlockPos at =
          new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
      if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at).isEmpty())
        continue;
      BlossomOni leader = Content.BLOSSOM_ONI.create(level, EntitySpawnReason.EVENT);
      if (leader == null) return null;
      double across = a + (r.nextBoolean() ? 1 : -1) * Math.PI / 2;
      leader.snapTo(x + .5, at.getY(), z + .5, (float) Math.toDegrees(across) - 90, 0);
      if (!level.noCollision(leader)) continue;
      leader.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
      leader.makeElite();
      leader.parade().lead(waypoint(at, across, r));
      level.addFreshEntity(leader);
      int players = 0;
      for (ServerPlayer p : level.players())
        if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(near) < 64 * 64) players++;
      int n =
          Math.min(
              6 + r.nextInt(4) + 2 * Math.max(0, players - 1),
              ServerConfig.get().nightParadeMax - 1);
      Vec3 back = Vec3.directionFromRotation(0, leader.getYRot()).scale(-1);
      for (int i = 0; i < n; i++) {
        BlossomOni oni = Content.BLOSSOM_ONI.create(level, EntitySpawnReason.EVENT);
        if (oni == null) break;
        Vec3 spot =
            leader
                .position()
                .add(back.scale(1.5 + i * .6))
                .add(r.nextDouble() - .5, 0, r.nextDouble() - .5);
        int y =
            level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(spot.x), Mth.floor(spot.z));
        oni.snapTo(spot.x, y, spot.z, leader.getYRot(), 0);
        if (!level.noCollision(oni))
          oni.snapTo(leader.getX(), leader.getY(), leader.getZ(), leader.getYRot(), 0);
        oni.finalizeSpawn(
            level,
            level.getCurrentDifficultyAt(oni.blockPosition()),
            EntitySpawnReason.EVENT,
            null);
        oni.parade().follow(leader, i);
        level.addFreshEntity(oni);
        level.sendParticles(
            ParticleTypes.SOUL, oni.getX(), oni.getY() + 1, oni.getZ(), 8, .3, .5, .3, .02);
      }
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME,
          leader.getX(),
          leader.getY() + 1,
          leader.getZ(),
          24,
          .4,
          .8,
          .4,
          .03);
      return leader;
    }
    return null;
  }
}

package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * Server cost of the mod's mobs: average tick time with crowds in a walled, roofed arena, each
 * phase 160 ticks, against an empty arena, the same number of vanilla zombies and a vanilla fight
 * of the same size (vindicators against iron golems). Informational: every phase passes and reports
 * its milliseconds per tick.
 */
final class PerfChecks {
  private static final int X0 = -30, Z0 = -30, SIZE = 26, PHASE = 160, WARM = 40;
  private final ServerChecks t;
  private int age, phase = -1;
  private final List<Mob> crowd = new ArrayList<>();
  private final List<Long> samples = new ArrayList<>();
  private static final String[] NAMES = {
    "empty arena",
    "40 zombies",
    "40 swordsmen",
    "40 樱下鬼",
    "20 swordsmen + 20 樱下鬼 fighting",
    "3 落英剑主 + 24 樱下鬼 fighting",
    "20 vindicators + 20 iron golems fighting (vanilla)"
  };

  PerfChecks(ServerChecks t) {
    this.t = t;
  }

  private void spawn(EntityType<? extends Mob> type, int n, Random r) {
    var level = t.level;
    for (int i = 0; i < n; i++) {
      Mob m = type.create(level, EntitySpawnReason.COMMAND);
      m.setPos(X0 + 2 + r.nextInt(SIZE - 3) + .5, 100, Z0 + 2 + r.nextInt(SIZE - 3) + .5);
      m.finalizeSpawn(
          level, level.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.COMMAND, null);
      level.addFreshEntity(m);
      crowd.add(m);
    }
  }

  private void build() {
    var level = t.level;
    for (int x = X0; x <= X0 + SIZE; x++)
      for (int z = Z0; z <= Z0 + SIZE; z++) {
        boolean edge = x == X0 || z == Z0 || x == X0 + SIZE || z == Z0 + SIZE;
        level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int y = 100; y < 104; y++)
          level.setBlock(
              new BlockPos(x, y, z), (edge ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, 104, z), Blocks.STONE.defaultBlockState(), 2);
      }
  }

  boolean tick() throws Exception {
    if (phase < 0) {
      build();
      phase = 0;
      age = 0;
    }
    age++;
    if (age > WARM) {
      // This tick's time is written after this callback: read the previous tick's.
      long[] times = t.server.getTickTimesNanos();
      samples.add(times[(t.server.getTickCount() - 1 + times.length) % times.length]);
    }
    if (age < PHASE) return false;
    double ms = samples.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
    t.check(true, String.format(Locale.ROOT, "perf: %s: %.2f ms per tick", NAMES[phase], ms));
    crowd.forEach(Mob::discard);
    crowd.clear();
    samples.clear();
    age = 0;
    phase++;
    Random r = new Random(7);
    switch (phase) {
      case 1 -> spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 40, r);
      case 2 -> spawn(Content.SWORDSMAN, 40, r);
      case 3 -> spawn(Content.BLOSSOM_ONI, 40, r);
      case 4 -> {
        spawn(Content.SWORDSMAN, 20, r);
        spawn(Content.BLOSSOM_ONI, 20, r);
      }
      case 5 -> {
        spawn(Content.PETAL_LORD, 3, r);
        spawn(Content.BLOSSOM_ONI, 24, r);
      }
      case 6 -> {
        spawn(net.minecraft.world.entity.EntityTypes.VINDICATOR, 20, r);
        spawn(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, 20, r);
      }
      default -> {
        return true;
      }
    }
    return false;
  }
}

package oborokage;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Bounded, explicit harvesting action. Every block passes the native player break path. */
public final class TerrainCut {
  private static final class Job {
    final ServerPlayer owner;
    final ServerLevel level;
    final ItemStack blade;
    final InteractionHand hand;
    final BlockPos origin;
    final float hardness;
    final ArrayDeque<BlockPos> queue;
    int logCount;

    Job(
        ServerPlayer owner,
        ServerLevel level,
        ItemStack blade,
        InteractionHand hand,
        BlockPos origin,
        float hardness,
        int budget,
        ArrayDeque<BlockPos> queue) {
      this.owner = owner;
      this.level = level;
      this.blade = blade;
      this.hand = hand;
      this.origin = origin;
      this.hardness = hardness;
      this.queue = queue;
    }
  }

  private static final ThreadLocal<UUID> BREAKING = new ThreadLocal<>();

  public static boolean breaking(net.minecraft.world.entity.player.Player p, ItemStack stack) {
    return p.getUUID().equals(BREAKING.get())
        && Oborokage.mainBlade(stack)
        && stack == p.getMainHandItem();
  }

  private static final Map<UUID, Job> jobs = new HashMap<>();

  public static boolean start(ServerPlayer player, float charge) {
    var config = ServerConfig.get();
    if (player.getUsedItemHand() != InteractionHand.MAIN_HAND
        || !Oborokage.mainBlade(player.getMainHandItem())
        || !config.terrainCutEnabled
        || jobs.containsKey(player.getUUID())
        || player.isSpectator()) return false;
    ServerLevel level = (ServerLevel) player.level();
    Vec3 eye = player.getEyePosition();
    var hit =
        level.clip(
            new ClipContext(
                eye,
                eye.add(player.getLookAngle().scale(config.terrainReach)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
    if (hit.getType() != HitResult.Type.BLOCK) return false;
    BlockPos origin = hit.getBlockPos();
    float q = Math.clamp(charge, 0, 1);
    int count = Math.max(1, (int) Math.floor(1 + (config.terrainMaxBlocks - 1) * q * q));
    float hardness =
        (float) Math.min(config.terrainMaxHardness, 2 + (config.terrainMaxHardness - 2) * q);
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    if (level.getBlockState(origin).is(BlockTags.LOGS)) {
      // Discover a finite tree section. No leaves, no chunk loads, no unbounded forest flood.
      ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
      Set<BlockPos> visited = new HashSet<>();
      frontier.add(origin);
      visited.add(origin);
      while (!frontier.isEmpty()
          && queue.size() < count
          && visited.size() < config.terrainMaxBlocks * 27) {
        BlockPos pos = frontier.removeFirst();
        if (!level.hasChunkAt(pos)
            || !level.getBlockState(pos).is(BlockTags.LOGS)
            || !eligible(player, level, pos, hardness)) continue;
        queue.add(pos);
        for (int y = 0; y <= 1; y++)
          for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
              BlockPos next = pos.offset(x, y, z);
              if (Math.abs(next.getX() - origin.getX()) <= 4
                  && Math.abs(next.getZ() - origin.getZ()) <= 4
                  && next.getY() - origin.getY() <= config.terrainMaxBlocks
                  && visited.add(next)) frontier.addLast(next);
            }
      }
    } else {
      // A shallow cut facing the hit surface; never tunnel through an intact obstacle.
      int radius = Math.min(3, (int) Math.floor(q * 3));
      var normal = hit.getDirection().getAxis();
      List<BlockPos> candidates = new ArrayList<>();
      for (int a = -radius; a <= radius; a++)
        for (int b = -radius; b <= radius; b++) {
          if (a * a + b * b > radius * radius) continue;
          candidates.add(
              switch (normal) {
                case X -> origin.offset(0, a, b);
                case Y -> origin.offset(a, 0, b);
                case Z -> origin.offset(a, b, 0);
              });
        }
      candidates.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
      for (BlockPos pos : candidates) {
        if (queue.size() >= count) break;
        if (!level.hasChunkAt(pos) || !eligible(player, level, pos, hardness)) continue;
        var visible =
            level.clip(
                new ClipContext(
                    eye,
                    Vec3.atCenterOf(pos),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player));
        if (visible.getType() == HitResult.Type.BLOCK && visible.getBlockPos().equals(pos))
          queue.add(pos);
      }
    }
    if (queue.isEmpty()) return false;
    jobs.put(
        player.getUUID(),
        new Job(
            player,
            level,
            player.getItemInHand(player.getUsedItemHand()),
            player.getUsedItemHand(),
            origin,
            hardness,
            count,
            queue));
    TechniqueEvent.send(player, 3, eye, hit.getLocation(), charge);
    oborokage.compat.Compat.swing(player, player.getUsedItemHand());
    return true;
  }

  private static boolean eligible(ServerPlayer p, ServerLevel level, BlockPos pos, float hardness) {
    if (!level.hasChunkAt(pos)
        || !level.getWorldBorder().isWithinBounds(pos)
        || !level.mayInteract(p, pos)) return false;
    var state = level.getBlockState(pos);
    float strength = state.getDestroySpeed(level, pos);
    return !state.isAir()
        && !state.hasBlockEntity()
        && state.getFluidState().isEmpty()
        && strength >= 0
        && strength <= hardness;
  }

  public static void tick(MinecraftServer server) {
    var it = jobs.entrySet().iterator();
    while (it.hasNext()) {
      Job j = it.next().getValue();
      ServerPlayer p = j.owner;
      if (p.isRemoved()
          || !p.isAlive()
          || p.level() != j.level
          || !validBlade(j)
          || !ServerConfig.get().terrainCutEnabled
          || p.distanceToSqr(Vec3.atCenterOf(j.origin)) > 144) {
        it.remove();
        continue;
      }
      for (int n = 0; n < ServerConfig.get().terrainBlocksPerTick && !j.queue.isEmpty(); n++) {
        // Native mining may empty the same ItemStack object midway through this batch.
        if (!validBlade(j)) {
          j.queue.clear();
          break;
        }
        BlockPos pos = j.queue.removeFirst();
        if (!eligible(p, j.level, pos, j.hardness)) continue;
        // Fabric BEFORE/AFTER break callbacks, adventure rules and actual drops run here.
        // Stop at a denied block rather than chopping above a protected trunk.
        boolean log = j.level.getBlockState(pos).is(BlockTags.LOGS);
        boolean destroyed;
        BREAKING.set(p.getUUID());
        try {
          destroyed = p.gameMode.destroyBlock(pos);
        } finally {
          BREAKING.remove();
        }
        if (destroyed && log && ++j.logCount == 16) Feats.award(p, "one_breath_forest");
        if (!destroyed) {
          j.queue.clear();
          break;
        }
      }
      if (j.queue.isEmpty()) it.remove();
    }
  }

  private static boolean validBlade(Job job) {
    return !job.blade.isEmpty()
        && Oborokage.mainBlade(job.blade)
        && job.owner.getItemInHand(job.hand) == job.blade;
  }

  public static void clear() {
    jobs.clear();
  }

  private TerrainCut() {}
}

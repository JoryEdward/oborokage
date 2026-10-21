package oborokage;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * A hidden feat, 浮名留冢: light every candle before three different blade tombs without taking
 * anything from their chests (a robbed tomb, one that has woken, does not count). Each tomb a
 * player honours is remembered on the player, through death; the third earns the advancement.
 * Lighting is noticed when the player lights a candle by hand (flint and steel, a fire charge).
 */
public final class TombCandles {
  public static final String FEAT = "three_tombs_lit";
  public static final int NEEDED = 3;

  /** The tombs this player has honoured, as "dimension|packed chest position". */
  public static final AttachmentType<List<String>> HONOURED =
      AttachmentRegistry.create(
          Oborokage.id("tombs_honoured"),
          b -> b.persistent(Codec.STRING.listOf()).copyOnDeath().initializer(List::of));

  private record Lit(ServerPlayer player, BlockPos pos) {}

  private static final List<Lit> pending = new ArrayList<>();

  static void init() {
    // The candle is lit by the item's use, after this callback: look at it at the end of the tick.
    UseBlockCallback.EVENT.register(
        (player, level, hand, hit) -> {
          if (player instanceof ServerPlayer p
              && level.getBlockState(hit.getBlockPos()).is(BlockTags.CANDLES)
              && pending.size() < 64) pending.add(new Lit(p, hit.getBlockPos()));
          return InteractionResult.PASS;
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (pending.isEmpty()) return;
          for (Lit lit : List.copyOf(pending))
            if (lit.player.level() instanceof ServerLevel level) check(level, lit.player, lit.pos);
          pending.clear();
        });
  }

  /** After a candle near a tomb is lit: is the tomb untouched and every candle before it lit? */
  public static boolean check(ServerLevel level, ServerPlayer player, BlockPos candle) {
    BlockPos chest = tombChest(level, candle);
    if (chest == null
        || !(level.getBlockEntity(chest) instanceof ChestBlockEntity be)
        || Boolean.TRUE.equals(be.getAttached(TombGuard.AWAKENED))) return false;
    int candles = 0;
    for (BlockPos p : BlockPos.betweenClosed(chest.offset(-5, -1, -5), chest.offset(5, 2, 5))) {
      var state = level.getBlockState(p);
      if (!state.is(BlockTags.CANDLES)) continue;
      if (!state.getValue(CandleBlock.LIT)) return false;
      candles++;
    }
    if (candles < 4) return false;
    String key = level.dimension().identifier() + "|" + chest.asLong();
    List<String> honoured = player.getAttachedOrCreate(HONOURED);
    if (honoured.contains(key)) return false;
    List<String> next = new ArrayList<>(honoured);
    next.add(key);
    player.setAttached(HONOURED, List.copyOf(next));
    level.playSound(null, chest, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, .6f);
    level.sendParticles(
        ParticleTypes.SOUL,
        chest.getX() + .5,
        chest.getY() + 1,
        chest.getZ() + .5,
        24,
        2,
        .4,
        2,
        .02);
    if (next.size() >= NEEDED) Feats.award(player, FEAT);
    return true;
  }

  /** The chest of the blade tomb a candle belongs to, or null. */
  private static BlockPos tombChest(ServerLevel level, BlockPos candle) {
    for (BlockPos p : BlockPos.betweenClosed(candle.offset(-6, -2, -6), candle.offset(6, 2, 6)))
      if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CHEST)
          && TombGuard.rod(level, p.immutable()) != null) return p.immutable();
    return null;
  }

  private TombCandles() {}
}

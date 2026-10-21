package oborokage.check;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CandleBlock;
import oborokage.*;

/**
 * 浮名留冢: lighting every candle of an untouched blade tomb honours it; three earn the hidden
 * advancement. Three of four candles, a robbed tomb, and the same tomb twice do not count.
 */
final class CandleChecks {
  private final ServerChecks t;

  CandleChecks(ServerChecks t) {
    this.t = t;
  }

  private BlockPos tomb(int x, int z) throws Exception {
    BlockPos at = new BlockPos(x, 100, z);
    t.server
        .getStructureTemplateManager()
        .get(Oborokage.id("blade_tomb/tomb"))
        .orElseThrow()
        .placeInWorld(
            t.level,
            at,
            at,
            new net.minecraft.world.level.levelgen.structure.templatesystem
                .StructurePlaceSettings(),
            t.level.getRandom(),
            2);
    return at;
  }

  /** Lights the tomb's candles, all or all but one; returns the last candle lit. */
  private BlockPos light(BlockPos at, boolean all) {
    BlockPos last = null;
    int n = 0;
    for (BlockPos p : BlockPos.betweenClosed(at, at.offset(8, 2, 8))) {
      var s = t.level.getBlockState(p);
      if (!(s.getBlock() instanceof CandleBlock)) continue;
      if (!all && n++ == 0) continue;
      t.level.setBlock(p, s.setValue(CandleBlock.LIT, true), 3);
      last = p.immutable();
    }
    return last;
  }

  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) p)
        .setAttached(TombCandles.HONOURED, java.util.List.of());
    BlockPos a = tomb(40, -10), b = tomb(40, 2), c = tomb(52, -10), robbed = tomb(52, 2);
    t.check(
        !TombCandles.check(level, p, light(a, false)),
        "Three of a tomb's four candles are not enough");
    t.check(
        TombCandles.check(level, p, light(a, true)),
        "Every candle of an untouched tomb lit: it is honoured");
    t.check(!TombCandles.check(level, p, light(a, true)), "The same tomb is honoured only once");
    var chest = level.getBlockEntity(robbed.offset(4, 1, 2));
    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) chest)
        .setAttached(TombGuard.AWAKENED, true);
    t.check(!TombCandles.check(level, p, light(robbed, true)), "A robbed tomb does not count");
    TombCandles.check(level, p, light(b, true));
    t.check(!Feats.has(p, TombCandles.FEAT), "Two tombs are not yet the feat");
    TombCandles.check(level, p, light(c, true));
    t.check(
        Feats.has(p, TombCandles.FEAT), "Three untouched tombs with every candle lit earn 浮名留冢");
    return true;
  }
}

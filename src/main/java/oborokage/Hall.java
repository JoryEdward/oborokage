package oborokage;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Keeps a dojo's hall clear of the grove. A cherry tree growing beside the hall can push its leaves
 * (and its bee nest) in under the roof, where the template has air: in the 1.1.0 survey one dojo in
 * ten had some inside. The master clears them once, the first time it wakes: natural leaves and bee
 * nests where its dojo's template has air under a roof. Leaves a player placed are persistent and
 * stay, and so does everything of the template's own.
 */
final class Hall {
  /** The dojos: 落英剑主's and the ruined one. */
  static final TagKey<Structure> DOJOS = TagKey.create(Registries.STRUCTURE, Oborokage.id("dojos"));

  private Hall() {}

  /** Grown in from the grove: natural leaves or a bee nest. */
  private static boolean grownIn(BlockState s) {
    return s.is(BlockTags.LEAVES)
            && s.hasProperty(LeavesBlock.PERSISTENT)
            && !s.getValue(LeavesBlock.PERSISTENT)
        || s.is(Blocks.BEE_NEST);
  }

  /** Clears what grew in under the roof of the dojo at `at`; returns how many blocks it cleared. */
  static int tidy(ServerLevel level, BlockPos at) {
    StructureStart start = level.structureManager().getStructureWithPieceAt(at, DOJOS);
    if (!start.isValid()) return 0;
    int cleared = 0;
    for (StructurePiece piece : start.getPieces()) {
      if (!(piece instanceof PoolElementStructurePiece p)
          || !(p.getElement() instanceof SinglePoolElement element)) continue;
      var template = oborokage.compat.Compat.templates(level).get(element.getTemplateLocation());
      if (template.isEmpty()) continue;
      Set<BlockPos> air = new HashSet<>();
      for (var info :
          template
              .get()
              .filterBlocks(
                  p.getPosition(),
                  new StructurePlaceSettings().setRotation(p.getRotation()),
                  Blocks.AIR)) air.add(info.pos());
      // Down each column: once past a block of the template's own (the roof), air is the hall's.
      BoundingBox box = piece.getBoundingBox();
      for (int x = box.minX(); x <= box.maxX(); x++)
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
          boolean roofed = false;
          for (int y = box.maxY(); y > box.minY(); y--) {
            BlockPos q = new BlockPos(x, y, z);
            if (!air.contains(q)) roofed = true;
            else if (roofed && grownIn(level.getBlockState(q))) {
              level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
              cleared++;
            }
          }
        }
    }
    return cleared;
  }
}

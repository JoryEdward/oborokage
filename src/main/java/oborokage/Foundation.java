package oborokage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * 石垣, the stone base a structure stands on. At a slope's edge or a cliff the ground can fall away
 * from under the edge of a structure's lowest layer: the terrain adaptation fills only a few blocks
 * down (in the 1.1.0 survey one dojo in five had part of its yard in the air, by up to 24 blocks).
 * This lays stone under each such column of the lowest layer down to solid ground, the way a castle
 * or a temple stands on its stone base. It is the last step of placing a template (the structure
 * processor {@code oborokage:foundation}) and works on the part of it in the chunk being placed.
 */
public final class Foundation {
  private Foundation() {}

  /** Ground a base can stand on: solid, and not a tree's leaves or wood. */
  static boolean ground(BlockState s) {
    return s.isSolid() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS);
  }

  /**
   * The template's blocks with the stone base added under its lowest layer. Each stone is one of
   * `stones`, picked by position so a column always gets the same mix. A column with no ground
   * within `depth` blocks is left as it is: a pillar hanging in the air would look worse.
   */
  public static List<StructureBlockInfo> lay(
      ServerLevelAccessor level,
      List<StructureBlockInfo> blocks,
      StructurePlaceSettings settings,
      List<Block> stones,
      int depth) {
    if (blocks.isEmpty() || stones.isEmpty()) return blocks;
    BoundingBox box = settings.getBoundingBox();
    int floor = Integer.MAX_VALUE;
    for (StructureBlockInfo b : blocks) floor = Math.min(floor, b.pos().getY());
    List<StructureBlockInfo> out = new ArrayList<>(blocks);
    for (StructureBlockInfo b : blocks) {
      BlockPos p = b.pos();
      if (p.getY() != floor || b.state().isAir() || box != null && !box.isInside(p)) continue;
      int gap = 0;
      while (gap < depth
          && p.getY() - gap - 1 >= level.getMinY()
          && !ground(level.getBlockState(p.below(gap + 1)))) gap++;
      if (gap == 0 || gap == depth || p.getY() - gap - 1 < level.getMinY()) continue;
      for (int d = 1; d <= gap; d++) {
        BlockPos at = p.below(d);
        Block stone = stones.get(Math.floorMod(Mth.getSeed(at) >> 16, stones.size()));
        out.add(new StructureBlockInfo(at, stone.defaultBlockState(), null));
      }
    }
    return out;
  }
}

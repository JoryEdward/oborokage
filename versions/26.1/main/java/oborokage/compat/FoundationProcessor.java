package oborokage.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import oborokage.Foundation;
import oborokage.Oborokage;

/**
 * The structure processor {@code oborokage:foundation} ({@link Foundation}): {@code stones}, the
 * blocks of the base; {@code max_depth}, how far down it reaches for ground. 26.1: processors are
 * classes with a registered processor type.
 */
public final class FoundationProcessor extends StructureProcessor {
  public static final MapCodec<FoundationProcessor> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      BuiltInRegistries.BLOCK
                          .byNameCodec()
                          .listOf()
                          .fieldOf("stones")
                          .forGetter(p -> p.stones),
                      Codec.intRange(1, 128)
                          .optionalFieldOf("max_depth", 48)
                          .forGetter(p -> p.depth))
                  .apply(i, FoundationProcessor::new));

  private static StructureProcessorType<FoundationProcessor> type;
  private final List<Block> stones;
  private final int depth;

  public FoundationProcessor(List<Block> stones, int depth) {
    this.stones = stones;
    this.depth = depth;
  }

  public static void register() {
    type =
        Registry.register(
            BuiltInRegistries.STRUCTURE_PROCESSOR, Oborokage.id("foundation"), () -> CODEC);
  }

  @Override
  protected StructureProcessorType<?> getType() {
    return type;
  }

  @Override
  public List<StructureBlockInfo> finalizeProcessing(
      ServerLevelAccessor level,
      BlockPos offset,
      BlockPos pivot,
      List<StructureBlockInfo> original,
      List<StructureBlockInfo> processed,
      StructurePlaceSettings settings) {
    return Foundation.lay(level, processed, settings, stones, depth);
  }
}

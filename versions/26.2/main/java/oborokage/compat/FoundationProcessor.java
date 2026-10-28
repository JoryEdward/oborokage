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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import oborokage.Foundation;
import oborokage.Oborokage;

/**
 * The structure processor {@code oborokage:foundation} ({@link Foundation}): {@code stones}, the
 * blocks of the base; {@code max_depth}, how far down it reaches for ground. 26.2 and 26.3:
 * processors are an interface with a codec.
 */
public record FoundationProcessor(List<Block> stones, int depth) implements StructureProcessor {
  public static final MapCodec<FoundationProcessor> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      BuiltInRegistries.BLOCK
                          .byNameCodec()
                          .listOf()
                          .fieldOf("stones")
                          .forGetter(FoundationProcessor::stones),
                      Codec.intRange(1, 128)
                          .optionalFieldOf("max_depth", 48)
                          .forGetter(FoundationProcessor::depth))
                  .apply(i, FoundationProcessor::new));

  public static void register() {
    Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, Oborokage.id("foundation"), CODEC);
  }

  @Override
  public MapCodec<? extends StructureProcessor> codec() {
    return CODEC;
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

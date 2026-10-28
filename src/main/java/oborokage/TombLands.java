package oborokage;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 「四方刀冢」: the blade tomb has five kinds, one to each land (the plains' and forests', the snow's,
 * the mountains', the cherry groves', the badlands'); a player who has stood by four of them earns
 * the feat. Looked at every 40 ticks per player; the kinds seen are kept (saved, and through
 * death).
 */
public final class TombLands {
  public static final String FEAT = "four_tombs";
  public static final int NEEDED = 4;

  /** The five tombs. */
  static final TagKey<Structure> TOMBS = TagKey.create(Registries.STRUCTURE, Oborokage.id("tombs"));

  /** The kinds of tomb a player has stood by. */
  public static final AttachmentType<List<String>> SEEN =
      AttachmentRegistry.create(
          Oborokage.id("tomb_kinds_seen"),
          b -> b.persistent(Codec.STRING.listOf()).copyOnDeath().initializer(List::of));

  private TombLands() {}

  static void init() {
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(
        TombLands::tick);
  }

  private static void tick(ServerLevel level) {
    if (level.getGameTime() % 40 != 7 || level.players().isEmpty()) return;
    var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    var tombs = structures.getOrThrow(TOMBS);
    for (ServerPlayer p : level.players()) {
      if (p.isSpectator()) continue;
      for (var tomb : tombs)
        if (level.structureManager().getStructureAt(p.blockPosition(), tomb.value()).isValid()) {
          see(p, structures.getKey(tomb.value()).getPath());
          break;
        }
    }
  }

  /** `player` stands by a tomb of `kind`; returns whether it was a new kind to them. */
  public static boolean see(ServerPlayer player, String kind) {
    List<String> seen = player.getAttachedOrElse(SEEN, List.of());
    if (seen.contains(kind)) return false;
    List<String> next = new ArrayList<>(seen);
    next.add(kind);
    player.setAttached(SEEN, List.copyOf(next));
    if (next.size() >= NEEDED) Feats.award(player, FEAT);
    return true;
  }
}

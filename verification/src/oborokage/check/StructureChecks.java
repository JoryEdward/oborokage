package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * 1.1.0's structures: the dojos' set holds the dojo and the ruined dojo three to one at the chosen
 * spacing; the ruined dojo's template (soul lanterns, a candle before each of three graves,
 * cobwebs, a roof two fifths gone, its own chest loot, the 朽英剑主 in the hall); only 樱下鬼 spawn in the
 * ruin; the dojos' structure tags; the stone base (oborokage:foundation) fills under a dojo placed
 * above the ground; the ruin's chest (剑魄 3-6, 剑意结晶 1-3, now and then a relic).
 */
final class StructureChecks {
  static final int SPACING = 8, SEPARATION = 4;
  private static final int X = 130, Z = 130;
  private final ServerChecks t;

  StructureChecks(ServerChecks t) {
    this.t = t;
  }

  private static int count(StructureTemplate template, Block block) {
    return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
  }

  boolean tick() throws Exception {
    var level = t.level;
    var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    Structure dojo = structures.getValue(Oborokage.id("dojo"));
    Structure ruin = structures.getValue(Oborokage.id("ruined_dojo"));
    var set =
        level
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE_SET)
            .getValue(Oborokage.id("dojo"));
    int wDojo = 0, wRuin = 0;
    for (var e : set.structures()) {
      if (e.structure().value() == dojo) wDojo = e.weight();
      if (e.structure().value() == ruin) wRuin = e.weight();
    }
    var spread = (RandomSpreadStructurePlacement) set.placement();
    t.check(
        ruin != null
            && wDojo == 3
            && wRuin == 1
            && spread.spacing() == SPACING
            && spread.separation() == SEPARATION,
        "The dojos' set holds the dojo and the ruined dojo 3 : 1 at "
            + spread.spacing()
            + " / "
            + spread.separation()
            + " (weights "
            + wDojo
            + " : "
            + wRuin
            + ")");
    var override = ruin.spawnOverrides().get(MobCategory.MONSTER);
    var kinds =
        override == null
            ? List.<String>of()
            : override.spawns().unwrap().stream()
                .map(
                    w ->
                        String.valueOf(w.value().type().builtInRegistryHolder().key().identifier()))
                .toList();
    t.check(
        kinds.equals(List.of("oborokage:blossom_oni")),
        "Only 樱下鬼 spawn in the ruined dojo: " + kinds);
    var dojos = TagKey.create(Registries.STRUCTURE, Oborokage.id("dojos"));
    var ruins = TagKey.create(Registries.STRUCTURE, Oborokage.id("ruined_dojo"));
    t.check(
        structures.wrapAsHolder(dojo).is(dojos)
            && structures.wrapAsHolder(ruin).is(dojos)
            && structures.wrapAsHolder(ruin).is(ruins)
            && !structures.wrapAsHolder(dojo).is(ruins),
        "The structure tags: dojos holds both, ruined_dojo the ruin alone");

    // The ruin's template.
    var templates = t.server.getStructureTemplateManager();
    StructureTemplate ruinT = templates.get(Oborokage.id("ruined_dojo/ruined_dojo")).orElseThrow();
    StructureTemplate dojoT = templates.get(Oborokage.id("dojo/dojo")).orElseThrow();
    int roof = count(ruinT, Blocks.DEEPSLATE_TILE_STAIRS) + count(ruinT, Blocks.DEEPSLATE_TILES);
    int roof0 = count(dojoT, Blocks.DEEPSLATE_TILE_STAIRS) + count(dojoT, Blocks.DEEPSLATE_TILES);
    var chests = ruinT.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
    String loot =
        chests.size() == 1 && chests.get(0).nbt() != null
            ? chests.get(0).nbt().getStringOr("LootTable", "")
            : "";
    t.check(
        count(ruinT, Blocks.SOUL_LANTERN) == 6
            && count(ruinT, Blocks.CANDLE) == 3
            && count(ruinT, Blocks.COBWEB) >= 8
            && count(ruinT, Blocks.LANTERN) == 0
            && roof < roof0 * .7
            && roof > roof0 * .45
            && loot.equals("oborokage:chests/ruined_dojo"),
        "The ruin: 6 soul lanterns, 3 candles, "
            + count(ruinT, Blocks.COBWEB)
            + " cobwebs, roof "
            + roof
            + " of "
            + roof0
            + ", chest "
            + loot);
    BlockPos at = new BlockPos(X, 100, Z);
    ruinT.placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2);
    var box = new AABB(X, 99, Z, X + 17, 112, Z + 17);
    var lords = level.getEntitiesOfClass(WitheredLord.class, box);
    t.check(
        lords.size() == 1
            && level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box).size() == 1,
        "The ruin places the 朽英剑主 in its hall and no one else: " + lords.size());
    level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box).forEach(e -> e.discard());

    // The stone base: a dojo placed six blocks above a floor stands on stone all the way down.
    int bx = X + 40, bz = Z;
    for (int x = bx - 1; x <= bx + 17; x++)
      for (int z = bz - 1; z <= bz + 17; z++) {
        level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int y = 100; y < 120; y++)
          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    var settings = new StructurePlaceSettings().setIgnoreEntities(true);
    level
        .registryAccess()
        .lookupOrThrow(Registries.PROCESSOR_LIST)
        .getValue(Oborokage.id("dojo_foundation"))
        .list()
        .forEach(settings::addProcessor);
    BlockPos base = new BlockPos(bx, 106, bz);
    dojoT.placeInWorld(level, base, base, settings, level.getRandom(), 2);
    Set<Block> stones =
        Set.of(Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE);
    int columns = 0, standing = 0;
    for (int x = bx; x < bx + 17; x++)
      for (int z = bz; z < bz + 17; z++) {
        columns++;
        boolean ok = true;
        for (int y = 100; y < 106; y++)
          ok &= stones.contains(level.getBlockState(new BlockPos(x, y, z)).getBlock());
        if (ok) standing++;
      }
    t.check(
        standing == columns,
        "A dojo placed six blocks above the ground stands on a stone base: "
            + standing
            + " of "
            + columns
            + " columns");

    // The ruin's chest.
    var table =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, Oborokage.id("chests/ruined_dojo")));
    LootParams params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
            .create(LootContextParamSets.CHEST);
    Set<Item> relics = new HashSet<>(Content.RELICS.values());
    int lo = 99, hi = 0, crystals = 0, relicCount = 0, stoneChests = 0;
    for (int i = 0; i < 400; i++) {
      int souls = 0;
      boolean stone = false;
      for (ItemStack s : table.getRandomItems(params)) {
        if (s.is(Content.BLADE_SOUL)) souls += s.getCount();
        if (s.is(Content.INTENT_CRYSTAL)) crystals += s.getCount();
        if (relics.contains(s.getItem())) relicCount++;
        if (Content.STONES.containsValue(s.getItem())) stone = true;
      }
      lo = Math.min(lo, souls);
      hi = Math.max(hi, souls);
      if (stone) stoneChests++;
    }
    t.check(
        lo >= 3
            && hi <= 6
            && crystals >= 400
            && crystals <= 1200
            && relicCount >= 8
            && relicCount <= 36
            && stoneChests > 160,
        "The ruin's chest: 剑魄 "
            + lo
            + "-"
            + hi
            + ", 剑意结晶 "
            + crystals
            + " in 400, relics "
            + relicCount
            + " in 400 (about 5%), an inscription stone in "
            + stoneChests);

    // The blade tombs: five kinds in one set, each in lands of its own.
    var tombSet =
        level
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE_SET)
            .getValue(Oborokage.id("blade_tomb"));
    String[] tombKinds = {
      "blade_tomb",
      "blade_tomb_snow",
      "blade_tomb_mountain",
      "blade_tomb_cherry",
      "blade_tomb_badlands"
    };
    List<Structure> tombs = new ArrayList<>();
    for (String k : tombKinds) tombs.add(structures.getValue(Oborokage.id(k)));
    boolean inSet =
        tombSet.structures().size() == 5
            && tombs.stream()
                .allMatch(
                    st -> tombSet.structures().stream().anyMatch(e -> e.structure().value() == st));
    int shared = 0;
    for (int i = 0; i < tombs.size(); i++)
      for (int j = i + 1; j < tombs.size(); j++)
        for (var b : tombs.get(i).biomes()) if (tombs.get(j).biomes().contains(b)) shared++;
    t.check(
        inSet && shared == 0,
        "The tomb's set holds the five kinds, no two in the same lands: " + shared + " shared");
    var rod =
        Class.forName("oborokage.TombGuard")
            .getDeclaredMethod("rod", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
    rod.setAccessible(true);
    String[] favoured = {null, "frostmoon", "raikiri", "tachi wakizashi", "voidstring"};
    for (int i = 1; i < tombKinds.length; i++) {
      String k = tombKinds[i];
      StructureTemplate tomb = templates.get(Oborokage.id(k + "/tomb")).orElseThrow();
      BlockPos origin = new BlockPos(X + 80 + i * 14, 100, Z);
      for (int x = -1; x < 10; x++)
        for (int z = -1; z < 10; z++) {
          level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
          for (int y = 0; y < 7; y++)
            level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
      tomb.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
      BlockPos chest = origin.offset(4, 1, 2);
      int candles = 0;
      for (BlockPos q : BlockPos.betweenClosed(origin, origin.offset(8, 5, 8)))
        if (level.getBlockState(q).is(net.minecraft.tags.BlockTags.CANDLES)) candles++;
      var be = level.getBlockEntity(chest);
      String lootId =
          be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c
                  && c.getLootTable() != null
              ? c.getLootTable().identifier().toString()
              : "";
      var chestLoot =
          level
              .getServer()
              .reloadableRegistries()
              .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Oborokage.id("chests/" + k)));
      int fav = 0, all = 0;
      for (int n = 0; n < 400; n++)
        for (ItemStack st : chestLoot.getRandomItems(params))
          if (relics.contains(st.getItem())) {
            all++;
            String path =
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(st.getItem())
                    .getPath()
                    .replace("broken_", "");
            if (favoured[i].contains(path)) fav++;
          }
      t.check(
          rod.invoke(null, level, chest) != null
              && candles >= 4
              && lootId.equals("oborokage:chests/" + k)
              && all == 400
              && fav >= 160
              && fav <= 240,
          k
              + ": known as a tomb by its layout, "
              + candles
              + " candles, its own chest ("
              + lootId
              + "), its land's blade "
              + fav
              + " of "
              + all
              + " relics");
    }
    // 「四方刀冢」: four kinds stood by.
    var p = t.player;
    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) p).removeAttached(TombLands.SEEN);
    boolean early = false;
    for (int i = 0; i < 3; i++) TombLands.see(p, tombKinds[i]);
    early = Feats.has(p, TombLands.FEAT);
    boolean again = TombLands.see(p, tombKinds[0]);
    TombLands.see(p, tombKinds[3]);
    t.check(
        !early && !again && Feats.has(p, TombLands.FEAT),
        "「四方刀冢」 comes with the fourth kind of tomb, not before; a kind counts once");
    return true;
  }
}

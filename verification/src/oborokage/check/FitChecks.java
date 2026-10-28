package oborokage.check;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import oborokage.Oborokage;

/**
 * Terrain fit on a real world (run with --world=normal --seed=N). For each structure, the starts
 * its structure set predicts nearest the origin are generated in full, and each is measured against
 * the terrain around it:
 *
 * <ul>
 *   <li>floating: footprint columns with no ground right under the floor, and how deep the gap
 *       goes;
 *   <li>intrusion: terrain (or trees, or water) where the template has air;
 *   <li>wall and drop: in a ring three blocks wide around the footprint, ground 4 or more blocks
 *       above the floor (the structure is cut into a slope) or below it (it stands over a drop);
 *   <li>roofed: footprint columns with terrain above the structure.
 * </ul>
 *
 * <p>A start fits when under 5% of its floor floats, no terrain is inside, under a third of the
 * ring is a wall, nothing roofs it and no tree has grown into it (under its roof and walled in on
 * all four sides: inside the hall; under the eaves outside its walls is only reported). Ground
 * falling away beside it (a ledge) is reported but is not a fault. Results go to fit.json in the
 * run folder.
 *
 * <p>Properties: oborokage.fit.structures ("dojo,blade_tomb"), .samples (per structure, 10),
 * .radius (blocks, 4096).
 */
final class FitChecks {
  private static final int RING = 3, STEP = 4, GAP_LIMIT = 24, ROOF_LIMIT = 24;
  private final ServerChecks t;
  private final List<String> names =
      List.of(System.getProperty("oborokage.fit.structures", "dojo,blade_tomb").split(","));
  private final int samples = Integer.getInteger("oborokage.fit.samples", 10);
  private final int radius = Integer.getInteger("oborokage.fit.radius", 4096);
  private final JsonObject out = new JsonObject();
  private final Deque<Object[]> todo = new ArrayDeque<>();
  private final Map<String, JsonArray> measured = new LinkedHashMap<>();
  private boolean planned;
  private int nightPhase;
  private final List<Object[]> ruins = new ArrayList<>();
  private final List<Object[]> verdicts = new ArrayList<>();

  private void verdict(boolean ok, String message) {
    verdicts.add(new Object[] {ok, message});
  }

  FitChecks(ServerChecks t) {
    this.t = t;
  }

  /** Predicted starts of `structure`, nearest the origin first (some hold another of its set). */
  private List<ChunkPos> nearest(Structure structure) {
    var level = t.level;
    var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
    var set =
        sets.stream()
            .filter(s -> s.structures().stream().anyMatch(e -> e.structure().value() == structure))
            .findFirst()
            .orElseThrow();
    var spread = (RandomSpreadStructurePlacement) set.placement();
    int spacing = spread.spacing(), chunks = radius >> 4;
    List<ChunkPos> found = new ArrayList<>();
    for (int rz = Math.floorDiv(-chunks, spacing); rz <= Math.floorDiv(chunks, spacing); rz++)
      for (int rx = Math.floorDiv(-chunks, spacing); rx <= Math.floorDiv(chunks, spacing); rx++) {
        ChunkPos c = spread.getPotentialStructureChunk(level.getSeed(), rx * spacing, rz * spacing);
        if (Math.abs(c.getMinBlockX()) > radius || Math.abs(c.getMinBlockZ()) > radius) continue;
        if (level.structureManager().checkStructurePresence(c, structure, spread, false)
            != StructureCheckResult.START_NOT_PRESENT) found.add(c);
      }
    found.sort(
        Comparator.comparingLong(
            c ->
                (long) c.getMinBlockX() * c.getMinBlockX()
                    + (long) c.getMinBlockZ() * c.getMinBlockZ()));
    return found;
  }

  /** Trees and huge mushrooms: they grow after the structure is placed. */
  private static boolean tree(BlockState s) {
    return s.is(BlockTags.LEAVES)
        || s.is(BlockTags.LOGS)
        || s.is(Blocks.MUSHROOM_STEM)
        || s.is(Blocks.RED_MUSHROOM_BLOCK)
        || s.is(Blocks.BROWN_MUSHROOM_BLOCK);
  }

  /** Ground: anything solid, except trees. */
  private static boolean ground(BlockState s) {
    return s.isSolid() && !tree(s);
  }

  /** The top block of the ground in a column, below trees and plants. */
  private int groundTop(int x, int z) {
    var level = t.level;
    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
    while (p.getY() > level.getMinY() && !ground(level.getBlockState(p))) p.move(0, -1, 0);
    return p.getY();
  }

  private JsonObject measure(String name, Structure structure, ChunkPos at) throws Exception {
    var level = t.level;
    JsonObject r = new JsonObject();
    r.addProperty("chunk", at.x() + "," + at.z());
    StructureStart start =
        level
            .getChunk(
                at.x(),
                at.z(),
                net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS,
                true)
            .getStartForStructure(structure);
    if (start == null || !start.isValid()) {
      r.addProperty("generated", false);
      return r;
    }
    var piece = (PoolElementStructurePiece) start.getPieces().get(0);
    BoundingBox box = piece.getBoundingBox();
    // Generate every chunk the structure and the ring around it touch, in full.
    for (int cx = (box.minX() - RING) >> 4; cx <= (box.maxX() + RING) >> 4; cx++)
      for (int cz = (box.minZ() - RING) >> 4; cz <= (box.maxZ() + RING) >> 4; cz++)
        level.getChunk(cx, cz);
    int floor = box.minY();
    r.addProperty("generated", true);
    r.addProperty("at", box.minX() + " " + floor + " " + box.minZ());
    r.addProperty(
        "biome",
        level.getBiome(box.getCenter()).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
    // Floating: the floor's columns with no ground right under them.
    int columns = 0, floating = 0, deepest = 0, roofed = 0;
    for (int x = box.minX(); x <= box.maxX(); x++)
      for (int z = box.minZ(); z <= box.maxZ(); z++) {
        columns++;
        int gap = 0;
        while (gap < GAP_LIMIT && !ground(level.getBlockState(new BlockPos(x, floor - 1 - gap, z))))
          gap++;
        if (gap > 0) floating++;
        deepest = Math.max(deepest, gap);
        for (int y = box.maxY() + 1; y <= box.maxY() + ROOF_LIMIT; y++)
          if (ground(level.getBlockState(new BlockPos(x, y, z)))) {
            roofed++;
            break;
          }
      }
    // Intrusion: where the template has air.
    var template =
        t.server
            .getStructureTemplateManager()
            .get(
                ((net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement)
                        piece.getElement())
                    .getTemplateLocation())
            .orElseThrow();
    var air =
        template.filterBlocks(
            piece.getPosition(),
            new StructurePlaceSettings().setRotation(piece.getRotation()),
            Blocks.AIR);
    // Under a roof: air with a block of the template above it in the same column (the hall).
    Set<BlockPos> airAt = new HashSet<>();
    for (var info : air) airAt.add(info.pos());
    Map<Long, Integer> roofTop = new HashMap<>();
    for (BlockPos p :
        BlockPos.betweenClosed(
            box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
      if (!airAt.contains(p))
        roofTop.merge(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL), p.getY(), Math::max);
    int terrain = 0, trees = 0, eaveTrees = 0, hallTrees = 0, water = 0;
    JsonArray where = new JsonArray(), terrainAt = new JsonArray();
    for (BlockPos p : airAt) {
      BlockState s = level.getBlockState(p);
      if (s.isAir()) continue;
      if (tree(s)) {
        trees++;
        Integer top = roofTop.get(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL));
        if (top == null || top <= p.getY()) continue;
        // Inside: the template closes it in on all four sides too (the hall); else under the eaves.
        boolean enclosed = true;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
          BlockPos q = p.relative(dir);
          while (box.isInside(q) && airAt.contains(q)) q = q.relative(dir);
          if (!box.isInside(q)) enclosed = false;
        }
        if (enclosed) {
          hallTrees++;
          if (where.size() < 6)
            where.add(
                p.getX() + " " + p.getY() + " " + p.getZ() + " " + s.getBlock().getDescriptionId());
        } else eaveTrees++;
      } else if (!s.getFluidState().isEmpty()) water++;
      else if (s.isSolid()) {
        terrain++;
        if (terrainAt.size() < 6)
          terrainAt.add(
              p.getX() + " " + p.getY() + " " + p.getZ() + " " + s.getBlock().getDescriptionId());
      }
    }
    // The ring around the footprint: the ground's height against the floor.
    int ring = 0, walls = 0, drops = 0, highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE;
    for (int x = box.minX() - RING; x <= box.maxX() + RING; x++)
      for (int z = box.minZ() - RING; z <= box.maxZ() + RING; z++) {
        if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) continue;
        ring++;
        int rise = groundTop(x, z) - floor;
        if (rise >= STEP) walls++;
        if (rise <= -STEP) drops++;
        highest = Math.max(highest, rise);
        lowest = Math.min(lowest, rise);
      }
    double floatShare = floating / (double) columns,
        wallShare = walls / (double) ring,
        dropShare = drops / (double) ring;
    // A ledge (ground falling away beside it) is scenery, not a fault: it is reported only.
    boolean fits =
        floatShare < .05 && terrain == 0 && wallShare < 1 / 3.0 && roofed == 0 && hallTrees == 0;
    r.addProperty("floating_pct", Math.round(1000 * floatShare) / 10.0);
    r.addProperty("deepest_gap", deepest);
    r.addProperty("terrain_inside", terrain);
    r.addProperty("trees_inside", trees);
    r.addProperty("trees_under_eaves", eaveTrees);
    r.addProperty("trees_under_roof", hallTrees);
    if (!where.isEmpty()) r.add("trees_under_roof_at", where);
    if (!terrainAt.isEmpty()) r.add("terrain_at", terrainAt);
    r.addProperty("water_inside", water);
    r.addProperty("wall_pct", Math.round(1000 * wallShare) / 10.0);
    r.addProperty("drop_pct", Math.round(1000 * dropShare) / 10.0);
    r.addProperty("ring_highest", highest);
    r.addProperty("ring_lowest", lowest);
    r.addProperty("roofed_columns", roofed);
    r.addProperty("fits", fits);
    if (name.contains("dojo")) {
      // The master clears what grew in under the roof (Hall.tidy, as on its first tick).
      var tidy =
          Class.forName("oborokage.Hall")
              .getDeclaredMethod(
                  "tidy", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
      tidy.setAccessible(true);
      int cleared = (int) tidy.invoke(null, level, box.getCenter());
      int left = 0;
      for (BlockPos p : airAt) {
        BlockState s = level.getBlockState(p);
        if (!tree(s) || s.is(BlockTags.LOGS)) continue;
        Integer top = roofTop.get(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL));
        if (top == null || top <= p.getY()) continue;
        boolean enclosed = true;
        for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
          BlockPos q = p.relative(dir);
          while (box.isInside(q) && airAt.contains(q)) q = q.relative(dir);
          if (!box.isInside(q)) enclosed = false;
        }
        if (enclosed) left++;
      }
      r.addProperty("tidied", cleared);
      r.addProperty("hall_trees_after_tidy", left);
    }
    // The night rule in a ruin is read after all are measured (the sky turns a tick after the
    // time).
    if (name.equals("ruined_dojo")) ruins.add(new Object[] {box.getCenter(), r});
    return r;
  }

  private void time(String ticks) {
    t.server
        .getCommands()
        .performPrefixedCommand(t.server.createCommandSourceStack(), "time set " + ticks);
  }

  boolean tick() throws Exception {
    var structures = t.level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    if (!planned) {
      planned = true;
      for (String name : names) {
        Structure s = structures.getValue(Oborokage.id(name));
        measured.put(name, new JsonArray());
        for (ChunkPos c : nearest(s)) todo.add(new Object[] {name, s, c});
      }
      return false;
    }
    while (!todo.isEmpty()) {
      // One structure a tick: each needs a few chunks generated in full. A candidate holding the
      // other structure of its set is passed over, until `samples` of this one are measured.
      Object[] next = todo.poll();
      JsonArray done = measured.get((String) next[0]);
      if (done.size() >= samples) continue;
      JsonObject r = measure((String) next[0], (Structure) next[1], (ChunkPos) next[2]);
      if (r.get("generated").getAsBoolean()) done.add(r);
      return false;
    }
    if (!ruins.isEmpty() && nightPhase < 2) {
      if (nightPhase == 0) time("18000");
      else
        for (Object[] ruin : ruins) {
          BlockPos in = (BlockPos) ruin[0];
          ((JsonObject) ruin[1])
              .addProperty(
                  "night",
                  oborokage.BlossomOni.inRuinAtNight(t.level, in)
                      && !oborokage.BlossomOni.inRuinAtNight(t.level, in.offset(40, 0, 40)));
        }
      if (nightPhase == 1) time("6000");
      nightPhase++;
      return false;
    }
    if (nightPhase == 2) {
      for (Object[] ruin : ruins) {
        JsonObject r = (JsonObject) ruin[1];
        r.addProperty(
            "night_spawns",
            r.get("night").getAsBoolean()
                && !oborokage.BlossomOni.inRuinAtNight(t.level, (BlockPos) ruin[0]));
      }
      nightPhase++;
    }
    out.addProperty("seed", t.level.getSeed());
    for (var e : measured.entrySet()) {
      JsonArray list = e.getValue();
      int generated = 0,
          fit = 0,
          floating = 0,
          terrain = 0,
          walls = 0,
          drops = 0,
          roofed = 0,
          hall = 0,
          afterTidy = 0,
          nightOk = 0,
          ruins = 0;
      for (var el : list) {
        JsonObject r = el.getAsJsonObject();
        if (!r.get("generated").getAsBoolean()) continue;
        generated++;
        if (r.get("fits").getAsBoolean()) fit++;
        if (r.get("floating_pct").getAsDouble() >= 5) floating++;
        if (r.get("terrain_inside").getAsInt() > 0) terrain++;
        if (r.get("wall_pct").getAsDouble() >= 100 / 3.0) walls++;
        if (r.get("drop_pct").getAsDouble() >= 100 / 3.0) drops++;
        if (r.get("roofed_columns").getAsInt() > 0) roofed++;
        if (r.get("trees_under_roof").getAsInt() > 0) hall++;
        if (r.has("hall_trees_after_tidy")) afterTidy += r.get("hall_trees_after_tidy").getAsInt();
        if (r.has("night_spawns")) {
          ruins++;
          if (r.get("night_spawns").getAsBoolean()) nightOk++;
        }
      }
      if (e.getKey().contains("dojo"))
        verdict(
            afterTidy == 0,
            e.getKey() + ": after the master's tidying no leaves are left in a hall: " + afterTidy);
      if (ruins > 0)
        verdict(
            nightOk == ruins,
            "In a ruined dojo the 樱下鬼 may rise under its roof by night, not by day nor outside: "
                + nightOk
                + " of "
                + ruins);
      JsonObject summary = new JsonObject();
      summary.addProperty("sampled", list.size());
      summary.addProperty("generated", generated);
      summary.addProperty("fit", fit);
      summary.addProperty("floating", floating);
      summary.addProperty("terrain_inside", terrain);
      summary.addProperty("walled", walls);
      summary.addProperty("over_drop", drops);
      summary.addProperty("roofed", roofed);
      summary.addProperty("trees_under_roof", hall);
      summary.add("starts", list);
      out.add(e.getKey(), summary);
      // None within the survey (a land not on this seed's map) is not a fault.
      verdict(
          true,
          String.format(
              Locale.ROOT,
              "fit %s: %d of %d generated fit; floating %d, terrain inside %d, walled %d, on a"
                  + " ledge %d, roofed %d, trees under its roof %d",
              e.getKey(),
              fit,
              generated,
              floating,
              terrain,
              walls,
              drops,
              roofed,
              hall));
    }
    Files.writeString(
        Path.of("fit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out));
    // The file first, so a failed check still leaves the numbers.
    for (Object[] v : verdicts) t.check((boolean) v[0], (String) v[1]);
    return true;
  }
}

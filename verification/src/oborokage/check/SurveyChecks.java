package oborokage.check;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import oborokage.Oborokage;

/**
 * Structure density survey on a real world (run with --world=normal --seed=N), without generating
 * chunks. The surface biome is sampled on a grid and the cherry groves are joined into groves;
 * then, for each spacing tried, every candidate chunk of the dojo's placement is asked whether a
 * dojo would start there (the same check /locate makes: biome and height at the start). Reports,
 * per spacing, how many groves of a size hold a dojo, the dojo density, the closest pair, and how
 * many tombs fall within two chunks of a dojo. Results go to survey.json in the run folder.
 *
 * <p>Properties: oborokage.survey.radius (blocks, 4096), .step (grid, 32), .options
 * ("spacing:separation,...").
 */
final class SurveyChecks {
  private static final double BUDGET_NANOS = 40e6;
  private final ServerChecks t;
  private final int radius = Integer.getInteger("oborokage.survey.radius", 4096);
  private final int step = Integer.getInteger("oborokage.survey.step", 32);
  private final String options =
      System.getProperty("oborokage.survey.options", "32:12,24:9,20:8,18:7,16:6,14:5,12:4");
  private int phase, row, n;
  private boolean[] cherry;
  private int[] grove;
  private final List<Integer> groveCells = new ArrayList<>();
  private final JsonObject out = new JsonObject();
  private long started;

  SurveyChecks(ServerChecks t) {
    this.t = t;
  }

  private static int salt(String set) throws Exception {
    try (var in =
        SurveyChecks.class.getResourceAsStream(
            "/data/oborokage/worldgen/structure_set/" + set + ".json")) {
      var json = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
      return json.getAsJsonObject("placement").get("salt").getAsInt();
    }
  }

  private int cell(int blockX, int blockZ) {
    int cx = Math.floorDiv(blockX + radius, step), cz = Math.floorDiv(blockZ + radius, step);
    if (cx < 0 || cz < 0 || cx >= n || cz >= n) return -1;
    return cz * n + cx;
  }

  /** The grove a structure start lies in: its own grid cell, or a cherry cell next to it. */
  private int groveAt(int blockX, int blockZ) {
    int c = cell(blockX, blockZ);
    if (c < 0) return -1;
    if (grove[c] >= 0) return grove[c];
    int cx = c % n, cz = c / n;
    for (int dz = -1; dz <= 1; dz++)
      for (int dx = -1; dx <= 1; dx++) {
        int x = cx + dx, z = cz + dz;
        if (x >= 0 && z >= 0 && x < n && z < n && grove[z * n + x] >= 0) return grove[z * n + x];
      }
    return -1;
  }

  /** Starts of `structure` that would generate under `placement`, within the survey square. */
  private List<ChunkPos> starts(Structure structure, StructurePlacement placement, int spacing) {
    var level = t.level;
    long seed = level.getSeed();
    int chunks = radius >> 4;
    List<ChunkPos> found = new ArrayList<>();
    var spread = (RandomSpreadStructurePlacement) placement;
    for (int rz = Math.floorDiv(-chunks, spacing); rz <= Math.floorDiv(chunks, spacing); rz++)
      for (int rx = Math.floorDiv(-chunks, spacing); rx <= Math.floorDiv(chunks, spacing); rx++) {
        ChunkPos c = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
        if (Math.abs(c.getMinBlockX()) > radius || Math.abs(c.getMinBlockZ()) > radius) continue;
        StructureCheckResult r =
            level.structureManager().checkStructurePresence(c, structure, placement, false);
        if (r != StructureCheckResult.START_NOT_PRESENT) found.add(c);
      }
    return found;
  }

  boolean tick() throws Exception {
    var level = t.level;
    if (phase == 0) {
      started = System.nanoTime();
      n = 2 * radius / step;
      cherry = new boolean[n * n];
      grove = new int[n * n];
      Arrays.fill(grove, -1);
      phase = 1;
      return false;
    }
    if (phase == 1) {
      // Surface biome on the grid, a few rows a tick.
      var source = level.getChunkSource();
      var generator = source.getGenerator();
      var random = source.randomState();
      long until = System.nanoTime() + (long) BUDGET_NANOS;
      while (row < n && System.nanoTime() < until) {
        int z = -radius + row * step + step / 2;
        for (int col = 0; col < n; col++) {
          int x = -radius + col * step + step / 2;
          int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
          cherry[row * n + col] =
              level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).is(Biomes.CHERRY_GROVE);
        }
        row++;
      }
      if (row < n) return false;
      phase = 2;
      return false;
    }
    // Groves: cherry cells joined side by side.
    int id = 0;
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    for (int c = 0; c < n * n; c++) {
      if (!cherry[c] || grove[c] >= 0) continue;
      int size = 0;
      grove[c] = id;
      queue.add(c);
      while (!queue.isEmpty()) {
        int q = queue.poll();
        size++;
        int qx = q % n, qz = q / n;
        int[][] near = {{qx - 1, qz}, {qx + 1, qz}, {qx, qz - 1}, {qx, qz + 1}};
        for (int[] p : near)
          if (p[0] >= 0 && p[1] >= 0 && p[0] < n && p[1] < n) {
            int o = p[1] * n + p[0];
            if (cherry[o] && grove[o] < 0) {
              grove[o] = id;
              queue.add(o);
            }
          }
      }
      groveCells.add(size);
      id++;
    }
    double cellArea = step * (double) step;
    double cherryArea = groveCells.stream().mapToInt(i -> i).sum() * cellArea;
    int[] bounds = {10_000, 40_000, 90_000, 250_000};
    JsonObject sizes = new JsonObject();
    sizes.addProperty("under_10k", groveCells.stream().filter(s -> s * cellArea < 10_000).count());
    for (int b = 0; b < bounds.length; b++) {
      int lo = bounds[b], hi = b + 1 < bounds.length ? bounds[b + 1] : Integer.MAX_VALUE;
      sizes.addProperty(
          lo / 1000 + "k_up",
          groveCells.stream().filter(s -> s * cellArea >= lo && s * cellArea < hi).count());
    }
    out.addProperty("seed", level.getSeed());
    out.addProperty("radius", radius);
    out.addProperty("step", step);
    out.addProperty("cherry_area_m2", cherryArea);
    out.addProperty("cherry_share_pct", 100 * cherryArea / (4.0 * radius * radius));
    out.addProperty("groves", groveCells.size());
    out.add("grove_sizes", sizes);

    var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    Structure dojo = structures.getValue(Oborokage.id("dojo"));
    Structure tomb = structures.getValue(Oborokage.id("blade_tomb"));
    int dojoSalt = salt("dojo"), tombSalt = salt("blade_tomb");
    var tombSet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
    var tombPlacement = tombSet.getValue(Oborokage.id("blade_tomb")).placement();
    List<ChunkPos> tombs =
        starts(tomb, tombPlacement, ((RandomSpreadStructurePlacement) tombPlacement).spacing());
    out.addProperty("tombs", tombs.size());
    out.addProperty("tomb_salt", tombSalt);
    JsonArray results = new JsonArray();
    // First the placement the world really uses (from the registered structure set), then the
    // trials.
    var configured =
        (RandomSpreadStructurePlacement) tombSet.getValue(Oborokage.id("dojo")).placement();
    List<String> tried = new ArrayList<>(List.of("configured"));
    tried.addAll(List.of(options.split(",")));
    for (String option : tried) {
      boolean real = option.equals("configured");
      String[] part = real ? null : option.split(":");
      int spacing = real ? configured.spacing() : Integer.parseInt(part[0]);
      int separation = real ? configured.separation() : Integer.parseInt(part[1]);
      StructurePlacement placement =
          real
              ? configured
              : new RandomSpreadStructurePlacement(
                  spacing, separation, RandomSpreadType.LINEAR, dojoSalt);
      List<ChunkPos> dojos = starts(dojo, placement, spacing);
      int verify = Integer.getInteger("oborokage.survey.verify", 0);
      if (real && verify > 0) {
        // Generate the start chunk of a few predicted dojos: the dojo must really start there.
        int sampled = 0, started = 0;
        for (ChunkPos c : dojos) {
          if (sampled++ >= verify) break;
          var chunk =
              level.getChunk(
                  c.getMinBlockX() >> 4,
                  c.getMinBlockZ() >> 4,
                  net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS,
                  true);
          var start = chunk.getStartForStructure(dojo);
          if (start != null && start.isValid()) started++;
        }
        int checked = Math.min(verify, dojos.size());
        out.addProperty("verified", started + "/" + checked);
        t.check(
            started == checked,
            "Generated, " + started + " of " + checked + " predicted dojo starts hold a dojo");
      }
      Map<Integer, Integer> perGrove = new HashMap<>();
      int outside = 0;
      for (ChunkPos c : dojos) {
        int g = groveAt(c.getMinBlockX(), c.getMinBlockZ());
        if (g < 0) outside++;
        else perGrove.merge(g, 1, Integer::sum);
      }
      double closest = Double.MAX_VALUE;
      for (int i = 0; i < dojos.size(); i++)
        for (int j = i + 1; j < dojos.size(); j++) {
          double dx = dojos.get(i).getMinBlockX() - dojos.get(j).getMinBlockX();
          double dz = dojos.get(i).getMinBlockZ() - dojos.get(j).getMinBlockZ();
          closest = Math.min(closest, Math.sqrt(dx * dx + dz * dz));
        }
      int nearTomb = 0;
      for (ChunkPos tc : tombs)
        for (ChunkPos dc : dojos)
          if (Math.max(
                  Math.abs(tc.getMinBlockX() - dc.getMinBlockX()),
                  Math.abs(tc.getMinBlockZ() - dc.getMinBlockZ()))
              <= 2 * 16) {
            nearTomb++;
            break;
          }
      JsonObject r = new JsonObject();
      r.addProperty("configured", real);
      r.addProperty("spacing", spacing);
      r.addProperty("separation", separation);
      r.addProperty("dojos", dojos.size());
      r.addProperty("dojos_per_km2_cherry", dojos.size() / (cherryArea / 1e6));
      r.addProperty("outside_grid_groves", outside);
      for (int lo : new int[] {2_500, 10_000, 40_000}) {
        int total = 0, covered = 0;
        for (int g = 0; g < groveCells.size(); g++)
          if (groveCells.get(g) * cellArea >= lo) {
            total++;
            if (perGrove.containsKey(g)) covered++;
          }
        r.addProperty("groves_" + lo / 1000 + "k_up", total);
        r.addProperty("covered_" + lo / 1000 + "k_up", covered);
      }
      r.addProperty(
          "max_in_one_grove", perGrove.values().stream().mapToInt(i -> i).max().orElse(0));
      r.addProperty("closest_pair_blocks", closest == Double.MAX_VALUE ? -1 : Math.round(closest));
      r.addProperty("tombs_within_2_chunks", nearTomb);
      results.add(r);
      t.check(
          true,
          String.format(
              Locale.ROOT,
              "survey %s%d:%d: %d dojos; groves >=10k: %d of %d hold one; closest %s; tombs"
                  + " beside: %d",
              real ? "(configured) " : "",
              spacing,
              separation,
              dojos.size(),
              r.get("covered_10k_up").getAsInt(),
              r.get("groves_10k_up").getAsInt(),
              r.get("closest_pair_blocks"),
              nearTomb));
    }
    out.add("options", results);
    out.addProperty("seconds", (System.nanoTime() - started) / 1e9);
    Files.writeString(
        Path.of("survey.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out));
    return true;
  }
}

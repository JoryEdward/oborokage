package oborokage.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import oborokage.Move;

/**
 * 招式图鉴: what each blade can do, and which of it this player has already done. The seen list is kept
 * per client in config/oborokage-codex.txt.
 */
public final class Codex {
  /** One codex page: a tab key and the entries on it. */
  public record Page(String key, int color, List<String> entries) {}

  public static final List<Page> PAGES =
      List.of(
          new Page(
              "common",
              0xd8c8ff,
              List.of(
                  "dual_chain",
                  "guard",
                  "step",
                  "plunge",
                  "mounted",
                  "intent",
                  "vigor",
                  "terrain",
                  "whetstone",
                  "inscription")),
          new Page(
              "world",
              0xffc0d8,
              List.of(
                  "kazuuchi",
                  "swordsman",
                  "blossom_oni",
                  "petal_lord",
                  "withered_lord",
                  "dojo",
                  "ruined_dojo",
                  "night_parade",
                  "tomb",
                  "dummy")),
          new Page(
              "tachi", 0xbca6f4, List.of("tachi_solo", "tachi_iaido", "tachi_moon", "tachi_ult")),
          new Page(
              "moon", 0xd9cdff, List.of("moon_solo", "moon_bloom", "moon_offhand", "moon_ult")),
          new Page(
              "storm", 0xff7a55, List.of("storm_solo", "storm_tech", "storm_mark", "storm_ult")),
          new Page(
              "frost", 0x7fe6ff, List.of("frost_solo", "frost_tech", "frost_ice", "frost_ult")),
          new Page("void", 0xc49bff, List.of("void_solo", "void_tech", "void_string", "void_ult")));

  private static final Set<String> seen = new HashSet<>();
  private static boolean loaded;

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("oborokage-codex.txt");
  }

  private static void load() {
    if (loaded) return;
    loaded = true;
    try {
      if (Files.exists(file()))
        for (String line : Files.readAllLines(file())) if (!line.isBlank()) seen.add(line.trim());
    } catch (Exception ignored) {
      // A damaged list only means some entries show as unseen again.
    }
  }

  public static boolean seen(String entry) {
    load();
    return seen.contains(entry);
  }

  public static void see(String entry) {
    load();
    if (!seen.add(entry)) return;
    try {
      Files.write(file(), new TreeSet<>(seen));
    } catch (Exception ignored) {
      // Not being able to write the list never affects play.
    }
  }

  /** Marks what a move of the local player shows: called when its scene starts. */
  static void sawMove(int move, String blade) {
    switch (move) {
      case Move.MAIN -> see("dual_chain");
      case Move.OFF, Move.CROSS -> {
        see("dual_chain");
        see("moon_offhand");
      }
      case Move.COUNTER, Move.GUARD -> see("guard");
      case Move.DODGE -> see("step");
      case Move.PLUNGE -> see("plunge");
      case Move.STRING_DASH -> see("void_string");
      case Move.SOLO_1, Move.SOLO_2, Move.SOLO_3 -> see(blade + "_solo");
      case Move.STORM -> see("storm_tech");
      case Move.FROST -> see("frost_tech");
      case Move.VOID -> see("void_tech");
      case Move.TERRAIN -> see("terrain");
      default -> {
        if (Move.ultimate(move)) see(blade + "_ult");
      }
    }
  }

  private Codex() {}
}

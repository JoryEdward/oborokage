package oborokage.check;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * 1.0.0-beta crafting grids, mirrored from data/oborokage/recipe: every long blade is forged from a
 * kazuuchi; 胧影 also takes a nether star, two diamond blocks, two netherite ingots, two amethyst
 * blocks and a blaze rod; the themed blades three diamonds, a netherite ingot and two themes.
 */
final class RecipeInputs {
  static CraftingInput of(String blade) {
    String[] rows =
        switch (blade) {
          case "tachi" -> new String[] {"DSD", "NKN", "ABA"};
          case "wakizashi" -> new String[] {"  d", " d ", "NA "};
          case "raikiri", "frostmoon", "voidstring" -> new String[] {"XdY", "dKd", " N "};
          default -> throw new IllegalArgumentException(blade);
        };
    String x =
        switch (blade) {
          case "raikiri" -> "redstone_block";
          case "frostmoon" -> "blue_ice";
          default -> "ender_eye";
        };
    String y =
        switch (blade) {
          case "raikiri" -> "lightning_rod";
          case "frostmoon" -> "prismarine_crystals";
          default -> "shulker_shell";
        };
    List<ItemStack> cells = new ArrayList<>();
    for (String row : rows)
      for (char c : row.toCharArray())
        cells.add(
            switch (c) {
              case 'D' -> item("diamond_block");
              case 'd' -> item("diamond");
              case 'S' -> item("nether_star");
              case 'N' -> item("netherite_ingot");
              case 'K' -> new ItemStack(oborokage.Oborokage.KAZUUCHI);
              case 'A' -> item("amethyst_block");
              case 'B' -> item("blaze_rod");
              case 'X' -> item(x);
              case 'Y' -> item(y);
              default -> ItemStack.EMPTY;
            });
    return CraftingInput.of(3, 3, cells);
  }

  /** Looked up by id: some Items fields became copper-variant collections in later 26.x. */
  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.Identifier.withDefaultNamespace(id)));
  }

  private RecipeInputs() {}
}

package oborokage.check;

import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;

/** 0.5.0 crafting grids (netherite + diamond blocks), mirrored from data/oborokage/recipe. */
final class RecipeInputs {
  static CraftingInput of(String blade) {
    List<ItemStack> cells =
        switch (blade) {
          case "tachi" ->
              List.of(
                  ItemStack.EMPTY,
                  ItemStack.EMPTY,
                  item("diamond_block"),
                  ItemStack.EMPTY,
                  item("netherite_ingot"),
                  item("diamond_block"),
                  item("blaze_rod"),
                  item("amethyst_block"),
                  ItemStack.EMPTY);
          case "wakizashi" ->
              List.of(
                  ItemStack.EMPTY,
                  ItemStack.EMPTY,
                  item("netherite_ingot"),
                  ItemStack.EMPTY,
                  item("diamond_block"),
                  item("amethyst_block"),
                  item("blaze_rod"),
                  ItemStack.EMPTY,
                  ItemStack.EMPTY);
          case "raikiri" ->
              List.of(
                  ItemStack.EMPTY,
                  item("redstone_block"),
                  item("diamond_block"),
                  ItemStack.EMPTY,
                  item("netherite_ingot"),
                  item("diamond_block"),
                  item("blaze_rod"),
                  item("lightning_rod"),
                  ItemStack.EMPTY);
          case "frostmoon" ->
              List.of(
                  ItemStack.EMPTY,
                  item("blue_ice"),
                  item("diamond_block"),
                  ItemStack.EMPTY,
                  item("netherite_ingot"),
                  item("diamond_block"),
                  item("blaze_rod"),
                  item("prismarine_crystals"),
                  ItemStack.EMPTY);
          case "voidstring" ->
              List.of(
                  ItemStack.EMPTY,
                  item("ender_eye"),
                  item("diamond_block"),
                  ItemStack.EMPTY,
                  item("netherite_ingot"),
                  item("diamond_block"),
                  item("blaze_rod"),
                  item("shulker_shell"),
                  ItemStack.EMPTY);
          default -> throw new IllegalArgumentException(blade);
        };
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

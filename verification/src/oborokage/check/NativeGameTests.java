package oborokage.check;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import oborokage.*;

/** Native Fabric GameTest migration: real entity execution and real recipe registry. */
public final class NativeGameTests {
  @GameTest
  public void fullExecution(GameTestHelper helper) {
    var player = helper.makeMockServerPlayerInLevel();
    var target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
    target.setNoAi(true);
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
    target.setHealth(1024);
    target.setAbsorptionAmount(300);
    target.setPermanentlyInvulnerable(true);
    Combat.strikeTarget(helper.getLevel(), player, target, 1);
    helper.assertTrue(
        !target.isAlive(), "Full tachi executes1024HP with absorption and invulnerability");
    helper.succeed();
  }

  @GameTest
  public void overworldRecipes(GameTestHelper helper) {
    Item[] metals = {Items.COPPER_INGOT, Items.PACKED_ICE, Items.ENDER_PEARL};
    Item[] gems = {Items.REDSTONE, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD};
    Item[] blades = {Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING};
    for (int i = 0; i < 3; i++) {
      var input =
          CraftingInput.of(
              3,
              3,
              List.of(
                  ItemStack.EMPTY,
                  ItemStack.EMPTY,
                  new ItemStack(Items.DIAMOND),
                  ItemStack.EMPTY,
                  new ItemStack(metals[i]),
                  new ItemStack(Items.DIAMOND),
                  new ItemStack(Items.STICK),
                  new ItemStack(gems[i]),
                  ItemStack.EMPTY));
      var recipe =
          helper
              .getLevel()
              .getServer()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
      helper.assertTrue(
          recipe.isPresent() && recipe.get().value().assemble(input).is(blades[i]),
          "New blade Overworld recipe");
    }
    helper.succeed();
  }
}

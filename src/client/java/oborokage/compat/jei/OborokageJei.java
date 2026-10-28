package oborokage.compat.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import oborokage.BladeStyle;
import oborokage.Components;
import oborokage.Content;
import oborokage.Oborokage;

/**
 * JEI, when it is installed (entrypoint {@code jei_mod_plugin}; nothing here loads without it): the
 * anvil's inscriptions (each blade with each inscription stone) and a page for each of the mod's
 * things that no recipe explains: the whetstone, 剑魄, the broken blades, the intent crystal, the
 * kazuuchi, the inscription stones and the training post.
 */
public final class OborokageJei implements IModPlugin {
  /** What it has registered, and whether JEI's runtime came up (read by the checks). */
  public static int anvilRecipes, infoPages;

  public static boolean runtime;

  @Override
  public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jei) {
    runtime = true;
  }

  @Override
  public Identifier getPluginUid() {
    return Oborokage.id("jei");
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    var factory = registration.getVanillaRecipeFactory();
    List<IJeiAnvilRecipe> anvil = new ArrayList<>();
    Item[] blades = {
      Oborokage.TACHI,
      Oborokage.WAKIZASHI,
      Oborokage.RAIKIRI,
      Oborokage.FROSTMOON,
      Oborokage.VOIDSTRING,
      Oborokage.KAZUUCHI
    };
    for (var stone : Content.STONES.entrySet())
      for (Item blade : blades) {
        if (BladeStyle.of(new ItemStack(blade)) == null) continue;
        ItemStack inscribed = new ItemStack(blade);
        inscribed.set(Components.INSCRIPTION, stone.getKey().id);
        anvil.add(
            factory.createAnvilRecipe(
                new ItemStack(blade),
                List.of(new ItemStack(stone.getValue())),
                List.of(inscribed),
                Oborokage.id(
                    "inscribe/"
                        + stone.getKey().id
                        + "/"
                        + BuiltInRegistries.ITEM.getKey(blade).getPath())));
      }
    registration.addRecipes(RecipeTypes.ANVIL, anvil);
    anvilRecipes = anvil.size();
    registration.addIngredientInfo(
        Content.WHETSTONE_ITEM, Component.translatable("codex.oborokage.whetstone.desc"));
    registration.addIngredientInfo(
        Content.BLADE_SOUL, Component.translatable("jei.oborokage.blade_soul"));
    for (Item relic : Content.RELICS.values())
      registration.addIngredientInfo(relic, Component.translatable("jei.oborokage.relic"));
    registration.addIngredientInfo(
        Content.INTENT_CRYSTAL, Component.translatable("codex.oborokage.intent.desc"));
    registration.addIngredientInfo(
        Oborokage.KAZUUCHI, Component.translatable("codex.oborokage.kazuuchi.desc"));
    for (Item stone : Content.STONES.values())
      registration.addIngredientInfo(
          stone, Component.translatable("codex.oborokage.inscription.desc"));
    registration.addIngredientInfo(
        Content.TRAINING_DUMMY_ITEM, Component.translatable("codex.oborokage.dummy.desc"));
    infoPages = 5 + Content.RELICS.size() + Content.STONES.size();
  }
}

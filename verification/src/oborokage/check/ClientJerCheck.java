package oborokage.check;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Run with --fullpack (the player's own mods, JEI and Just Enough Resources among them). Feeds
 * JER's mob-drop tooltip the stale slot that crashed the game on 2026-10-18 (slot 8 of a six-drop
 * list) and a live slot, through reflection and proxies so nothing here compiles against JER.
 */
public final class ClientJerCheck implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private int ticks;
  private boolean done;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null || ++ticks < 40) return;
          done = true;
          try {
            run();
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
          }
          try {
            Files.writeString(Path.of("client-results.txt"), String.join("\n", results) + "\n");
          } catch (Exception e) {
            e.printStackTrace();
          }
          c.disconnectWithSavingScreen();
          c.stop();
        });
  }

  private void check(boolean ok, String what) {
    results.add((ok ? "PASS " : "FAIL ") + what);
  }

  private void run() throws Exception {
    check(FabricLoader.getInstance().isModLoaded("jeresources"), "Just Enough Resources is loaded");
    ClassLoader loader = getClass().getClassLoader();
    Class<?> dropType = Class.forName("jeresources.api.drop.LootDrop", true, loader);
    Class<?> entryType = Class.forName("jeresources.entry.MobEntry", true, loader);
    Class<?> tooltipType = Class.forName("jeresources.jei.mob.MobTooltip", true, loader);
    Class<?> slotType = Class.forName("mezz.jei.api.gui.ingredient.IRecipeSlotView", true, loader);
    Class<?> builderType = Class.forName("mezz.jei.api.gui.builder.ITooltipBuilder", true, loader);
    Class<?> typedType = Class.forName("mezz.jei.api.ingredients.ITypedIngredient", true, loader);
    check(
        Arrays.stream(tooltipType.getDeclaredMethods())
            .anyMatch(m -> m.getName().contains("skipStaleSlot")),
        "Oborokage's guard is applied to JER's mob tooltip");

    Item[] items = {Items.STICK, Items.BONE, Items.STRING, Items.FEATHER, Items.FLINT, Items.COAL};
    Object drops = java.lang.reflect.Array.newInstance(dropType, items.length);
    for (int i = 0; i < items.length; i++)
      java.lang.reflect.Array.set(
          drops, i, dropType.getConstructor(ItemStack.class).newInstance(new ItemStack(items[i])));
    Supplier<Object> mob = () -> null;
    Object entry =
        entryType.getMethod("create", Supplier.class, drops.getClass()).invoke(null, mob, drops);
    Object tooltip = tooltipType.getConstructor(entryType).newInstance(entry);
    var method = tooltipType.getMethod("onRichTooltip", slotType, builderType);

    List<Object> lines = new ArrayList<>();
    Object builder =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {builderType},
            (p, m, a) -> {
              if (m.getName().startsWith("add") && a != null) lines.add(a[0]);
              return null;
            });
    Object stale = slot(loader, slotType, typedType, "8", new ItemStack(Items.STICK));
    boolean crashed = false;
    try {
      method.invoke(tooltip, stale, builder);
    } catch (java.lang.reflect.InvocationTargetException e) {
      crashed = true;
      e.getCause().printStackTrace();
    }
    check(
        !crashed && lines.isEmpty(),
        "A stale JER drop slot (8 of 6) no longer crashes and shows no tooltip");
    Object live = slot(loader, slotType, typedType, "2", new ItemStack(Items.STRING));
    method.invoke(tooltip, live, builder);
    check(!lines.isEmpty(), "A live JER drop slot still gets its tooltip: " + lines.size());
  }

  private static Object slot(
      ClassLoader loader, Class<?> slotType, Class<?> typedType, String name, ItemStack stack) {
    Object typed =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {typedType},
            (p, m, a) -> m.getName().equals("getIngredient") ? stack : null);
    return Proxy.newProxyInstance(
        loader,
        new Class<?>[] {slotType},
        (p, m, a) ->
            switch (m.getName()) {
              case "getSlotName" -> Optional.of(name);
              case "getDisplayedIngredient" -> Optional.of(typed);
              default -> null;
            });
  }
}

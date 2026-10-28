package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Run with --fullpack (the player's own mods, JEI and Jade among them): JEI has taken the mod's
 * plugin (its anvil inscriptions and item pages) and come up; Jade has taken both parts of its
 * plugin. Read through reflection, so nothing here compiles against either.
 */
public final class ClientCompatCheck implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private int ticks;
  private boolean done;

  private void check(boolean ok, String what) {
    results.add((ok ? "PASS " : "FAIL ") + what);
  }

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null || ++ticks < 100) return;
          done = true;
          try {
            Class<?> jei = Class.forName("oborokage.compat.jei.OborokageJei");
            int anvil = jei.getField("anvilRecipes").getInt(null);
            int pages = jei.getField("infoPages").getInt(null);
            boolean runtime = jei.getField("runtime").getBoolean(null);
            check(
                anvil >= 25 && pages >= 15 && runtime,
                "JEI takes the plugin: "
                    + anvil
                    + " anvil inscriptions, "
                    + pages
                    + " item pages, runtime "
                    + runtime);
            Class<?> jade = Class.forName("oborokage.compat.jade.OborokageJade");
            boolean common = jade.getField("common").getBoolean(null);
            boolean client = jade.getField("client").getBoolean(null);
            check(
                common && client,
                "Jade takes the plugin: server part " + common + ", client part " + client);
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
}

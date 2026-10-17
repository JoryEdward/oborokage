package oborokage.check;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;

/** Compiled against 26.1.2 for a bootstrap probe of the preserved Gemini jar. */
public final class CompatClientProbe implements ClientModInitializer {
  int ticks;
  boolean done;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null) return;
          if (++ticks == 60) {
            done = true;
            Screenshot.grab(
                c.gameDirectory, "compatibility-world.png", c.getMainRenderTarget(), 1, m -> {});
            try {
              Files.writeString(
                  Path.of("client-probe-result.txt"),
                  "PASS Isolated26.1.2 client and generated world loaded with Gemini jar\n");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            c.disconnectWithSavingScreen();
            c.stop();
          }
        });
  }
}

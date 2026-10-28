package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import oborokage.client.*;
import oborokage.client.compat.ClientCompat;

/**
 * 1.1.0's settings screen: all fifteen settings are on it; each of the seven switches and seven
 * sliders takes effect at once (the cinematic camera, shake and third-person ultimate too, which
 * are kept apart from the file); closing writes the file; reset puts every setting back.
 */
public final class ClientSettingsCheck implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private int ticks;
  private boolean done;

  private void check(boolean ok, String what) {
    results.add((ok ? "PASS " : "FAIL ") + what);
  }

  /** Presses a button whichever way this line's onPress takes its input. */
  private static void press(Button b) throws Exception {
    for (var m : Button.class.getMethods())
      if (m.getName().equals("onPress") && m.getParameterCount() <= 1) {
        m.invoke(b, m.getParameterCount() == 0 ? new Object[0] : new Object[] {null});
        return;
      }
    throw new AssertionError("No onPress on this line");
  }

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null) return;
          ticks++;
          try {
            if (ticks == 40) step(c);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            ticks = 1000;
          }
          if (ticks >= 60) finish(c);
        });
  }

  private void step(Minecraft c) throws Exception {
    ClientConfig.reset();
    var screen = new SettingsScreen(null);
    ClientCompat.openScreen(c, screen);
    List<Button> buttons = new ArrayList<>();
    List<AbstractSliderButton> sliders = new ArrayList<>();
    for (var w : screen.children()) {
      if (w instanceof AbstractSliderButton s) sliders.add(s);
      else if (w instanceof Button b) buttons.add(b);
    }
    // The language first, then the switches; reset and done last.
    List<Button> switches = buttons.subList(1, buttons.size() - 2);
    check(
        buttons.size() + sliders.size() - 2 == 15 && switches.size() == 7 && sliders.size() == 7,
        "The settings screen holds all fifteen settings: "
            + (buttons.size() + sliders.size() - 2)
            + " ("
            + switches.size()
            + " switches, "
            + sliders.size()
            + " sliders)");
    for (Button b : switches) press(b);
    var value = AbstractSliderButton.class.getDeclaredField("value");
    value.setAccessible(true);
    var apply = AbstractSliderButton.class.getDeclaredMethod("applyValue");
    apply.setAccessible(true);
    for (AbstractSliderButton s : sliders) {
      value.setDouble(s, 1);
      apply.invoke(s);
    }
    var v = ClientConfig.get();
    boolean off =
        !v.cinematic
            && !v.thirdPersonUltimate
            && !v.screenShake
            && !v.tutorial
            && !v.keyConflictWarning
            && !v.trailShaderCompat
            && !v.localPrediction;
    boolean kept =
        !OborokageClient.cinematic
            && !OborokageClient.screenShake
            && !OborokageClient.thirdPersonUltimate;
    boolean max =
        v.hudScale == 2
            && v.hudOffsetX == 300
            && v.hudOffsetY == 300
            && v.trailMaxPlayers == 32
            && v.trailSegments == 64
            && v.trailVertexBudget == 65536
            && v.trailDistance == 96;
    check(
        off && kept && max,
        "Each switch and slider takes effect at once: switches "
            + off
            + ", kept "
            + kept
            + ", sliders "
            + max);
    screen.onClose();
    Path file = FabricLoader.getInstance().getConfigDir().resolve("oborokage-client.properties");
    String text = Files.readString(file);
    check(
        text.contains("cinematic=false")
            && text.contains("trailDistance=96")
            && text.contains("hudScale=2.0")
            && !text.contains("Restart the client"),
        "Closing the screen writes them to the file");
    var again = new SettingsScreen(null);
    ClientCompat.openScreen(c, again);
    Button reset =
        again.children().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(
                b ->
                    b.getMessage()
                        .getString()
                        .equals(
                            net.minecraft.client.resources.language.I18n.get(
                                "ui.oborokage.settings.reset")))
            .findFirst()
            .orElseThrow();
    press(reset);
    again.onClose();
    var d = ClientConfig.get();
    check(
        d.cinematic
            && d.screenShake
            && d.trailDistance == 48
            && d.hudScale == 1
            && OborokageClient.cinematic
            && Files.readString(file).contains("trailDistance=48"),
        "Reset puts every setting back to its default, in the file too");
  }

  private void finish(Minecraft c) {
    done = true;
    try {
      Files.writeString(Path.of("client-results.txt"), String.join("\n", results) + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
    c.disconnectWithSavingScreen();
    c.stop();
  }
}

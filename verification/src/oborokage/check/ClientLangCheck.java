package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import oborokage.ModLanguage;
import oborokage.client.ClientConfig;
import oborokage.client.CodexScreen;
import oborokage.client.SettingsScreen;
import oborokage.client.compat.ClientCompat;

/**
 * Run with --langcheck (add --fullpack for Mod Menu). The game is in Chinese; the mod's own
 * language setting is switched in a real client: the wrapper is installed, I18n and components
 * follow at once while the game's strings stay Chinese, the codex has the language button, the
 * settings button cycles the languages, and Mod Menu's settings button opens the same screen.
 * Screenshots: lang-codex-ja, lang-settings-ja, lang-settings-en (and lang-modmenu).
 */
public final class ClientLangCheck implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private int ticks, step = -1;
  private CodexScreen codex;
  private SettingsScreen settings;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (step == 99 || c.player == null || c.level == null || ++ticks < 40) return;
          try {
            if (ticks % 20 == 0) run(c, ++step);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            step = 98;
          }
          if (step >= 98) {
            step = 99;
            ClientConfig.setLanguage("auto");
            try {
              Files.writeString(Path.of("client-results.txt"), String.join("\n", results) + "\n");
            } catch (Exception e) {
              e.printStackTrace();
            }
            c.disconnectWithSavingScreen();
            c.stop();
          }
        });
  }

  private void check(boolean ok, String what) {
    results.add((ok ? "PASS " : "FAIL ") + what);
  }

  private static void press(Button b) throws Exception {
    for (var m : Button.class.getMethods())
      if (m.getName().equals("onPress") && m.getParameterCount() <= 1) {
        m.invoke(b, m.getParameterCount() == 0 ? new Object[0] : new Object[] {null});
        return;
      }
    throw new AssertionError("No onPress on this line");
  }

  /** The open screen: a field up to 26.2, the GUI's screen() from 26.3. */
  private static Screen current(Minecraft c) {
    try {
      return (Screen) Minecraft.class.getField("screen").get(c);
    } catch (ReflectiveOperationException e) {
      try {
        Object gui = Minecraft.class.getField("gui").get(c);
        return (Screen) gui.getClass().getMethod("screen").invoke(gui);
      } catch (ReflectiveOperationException e2) {
        return null;
      }
    }
  }

  private static Button button(Screen screen, int index) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .toList()
        .get(index);
  }

  private void run(Minecraft c, int at) throws Exception {
    switch (at) {
      case 0 -> {
        check(
            !(Language.getInstance() instanceof ModLanguage.Wrapped),
            "Following the game, the game's language object is left alone");
        Component cached = Component.translatable("item.oborokage.tachi");
        String before = cached.getString();
        check(
            before.equals("胧影太刀") && I18n.get("item.oborokage.tachi").equals("胧影太刀"),
            "Auto follows the game's Chinese: " + before);
        ClientConfig.setLanguage("ja_jp");
        check(
            Language.getInstance() instanceof ModLanguage.Wrapped,
            "Once a language is chosen the mod's wrapper is installed (mixin applied)");
        check(
            cached.getString().equals("朧影太刀")
                && I18n.get("item.oborokage.tachi").equals("朧影太刀")
                && I18n.get("tooltip.oborokage.moon_phase", "満月", 8, 8).equals("月相：満月（8 / 8）"),
            "Switched to Japanese at once, cached text included: "
                + cached.getString()
                + " / "
                + I18n.get("tooltip.oborokage.moon_phase", "満月", 8, 8));
        check(
            I18n.get("block.minecraft.stone").equals("石头"),
            "The game's own text stays Chinese: " + I18n.get("block.minecraft.stone"));
        check(
            Files.readString(
                    FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve("oborokage-client.properties"))
                .contains("language=ja_jp"),
            "The choice is saved in oborokage-client.properties");
        codex = CodexScreen.open();
        ClientCompat.openScreen(c, codex);
      }
      case 1 -> {
        screenshot(c, "lang-codex-ja");
        Button language = button(codex, 0);
        check(
            language.getMessage().getString().equals("设置 · Settings · 設定"),
            "The codex has the settings button: " + language.getMessage().getString());
        press(language);
      }
      case 2 -> {
        check(
            current(c) instanceof SettingsScreen,
            "The codex's language button opens the settings: " + current(c));
        settings = (SettingsScreen) current(c);
        screenshot(c, "lang-settings-ja");
        Button cycle = button(settings, 0);
        check(
            cycle.getMessage().getString().equals("言語：日本語"),
            "Settings show the language: " + cycle.getMessage().getString());
        press(cycle);
        check(
            ModLanguage.choice().equals("auto") && cycle.getMessage().getString().equals("语言：跟随游戏"),
            "Next: follow the game: " + cycle.getMessage().getString());
        press(cycle);
        press(cycle);
        check(
            ModLanguage.choice().equals("en_us")
                && cycle.getMessage().getString().equals("Language: English")
                && I18n.get("entity.oborokage.blossom_oni").equals("Oni Beneath the Blossoms"),
            "Then Chinese, then English: " + cycle.getMessage().getString());
      }
      case 3 -> {
        screenshot(c, "lang-settings-en");
        press(
            settings.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(
                    b ->
                        b.getMessage()
                            .getString()
                            .equals(
                                net.minecraft.network.chat.CommonComponents.GUI_DONE.getString()))
                .findFirst()
                .orElseThrow());
        check(current(c) == codex, "Done goes back to the codex: " + current(c));
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
          results.add("NOTE Mod Menu not installed in this run; its entry not checked");
          ClientCompat.openScreen(c, null);
          step = 98;
          return;
        }
        Object api =
            FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
                .filter(e -> e.getProvider().getMetadata().getId().equals("oborokage"))
                .findFirst()
                .orElseThrow()
                .getEntrypoint();
        Object factory = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
        Object screen =
            Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory")
                .getMethod("create", Screen.class)
                .invoke(factory, (Screen) null);
        check(screen instanceof SettingsScreen, "Mod Menu's settings button opens them: " + screen);
        ClientCompat.openScreen(c, (Screen) screen);
      }
      case 4 -> {
        screenshot(c, "lang-modmenu");
        ClientCompat.openScreen(c, null);
        step = 98;
      }
      default -> step = 98;
    }
  }

  /** Screenshot API moved between lines; take whichever form this line offers. */
  private void screenshot(Minecraft c, String name) {
    results.add("SHOT " + name);
    try {
      net.minecraft.client.Screenshot.class
          .getMethod("grab", Minecraft.class, boolean.class)
          .invoke(null, c, false);
      return;
    } catch (ReflectiveOperationException ignored) {
      // Older lines take a file name.
    }
    try {
      var target = Minecraft.class.getMethod("getMainRenderTarget").invoke(c);
      net.minecraft.client.Screenshot.class
          .getMethod(
              "grab",
              java.io.File.class,
              String.class,
              com.mojang.blaze3d.pipeline.RenderTarget.class,
              int.class,
              java.util.function.Consumer.class)
          .invoke(
              null,
              c.gameDirectory,
              name + ".png",
              target,
              1,
              (java.util.function.Consumer<Object>) m -> {});
    } catch (ReflectiveOperationException e) {
      results.add("NOTE screenshot unavailable on this line");
    }
  }
}

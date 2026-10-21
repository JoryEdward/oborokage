package oborokage.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import oborokage.ModLanguage;
import oborokage.client.compat.ClientCompat;

/**
 * The mod's settings: the language of its text. Opened from the move codex and from Mod Menu; each
 * click moves to the next language and takes effect at once.
 */
public final class SettingsScreen extends Screen {
  private final Screen parent;

  public SettingsScreen(Screen parent) {
    super(Component.translatable("ui.oborokage.settings"));
    this.parent = parent;
  }

  /** A language's own name, so each can be found whatever language the screen is in. */
  static Component name(String choice) {
    return switch (choice) {
      case "zh_cn" -> Component.literal("简体中文");
      case "en_us" -> Component.literal("English");
      case "ja_jp" -> Component.literal("日本語");
      default -> Component.translatable("ui.oborokage.language.auto");
    };
  }

  @Override
  protected void init() {
    addRenderableWidget(
        Button.builder(
                Component.translatable("ui.oborokage.language", name(ModLanguage.choice())),
                b -> {
                  var all = ModLanguage.CHOICES;
                  String next = all.get((all.indexOf(ModLanguage.choice()) + 1) % all.size());
                  ClientConfig.setLanguage(next);
                  b.setMessage(Component.translatable("ui.oborokage.language", name(next)));
                })
            .bounds(width / 2 - 100, height / 2 - 24, 200, 20)
            .build());
    addRenderableWidget(
        Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 - 100, height / 2 + 40, 200, 20)
            .build());
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    super.extractRenderState(g, mouseX, mouseY, partial);
    g.centeredText(font, title, width / 2, height / 2 - 56, 0xfff2e8ff);
    int y = height / 2 + 4;
    for (FormattedCharSequence line :
        font.split(Component.translatable("ui.oborokage.language.hint"), 260)) {
      g.centeredText(font, line, width / 2, y, 0xffa8a1bf);
      y += 10;
    }
  }

  @Override
  public void onClose() {
    ClientCompat.openScreen(minecraft, parent);
  }
}

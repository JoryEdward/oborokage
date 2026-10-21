package oborokage.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The move codex: one tab of general arts, one per blade; unseen entries are marked 未见. */
public final class CodexScreen extends Screen {
  private static int lastPage;
  private int page;
  private int scroll;

  public CodexScreen(int page) {
    super(Component.translatable("codex.oborokage.title"));
    this.page = page;
  }

  public static CodexScreen open() {
    return new CodexScreen(lastPage);
  }

  @Override
  protected void init() {
    Component language = Component.literal("语言 · Language · 言語");
    addRenderableWidget(
        Button.builder(
                language,
                b ->
                    oborokage.client.compat.ClientCompat.openScreen(
                        minecraft, new SettingsScreen(this)))
            .bounds(width - font.width(language) - 18, 6, font.width(language) + 12, 16)
            .build());
    int n = Codex.PAGES.size(), w = Math.min(64, (width - 40) / n), x0 = (width - n * (w + 4)) / 2;
    for (int i = 0; i < n; i++) {
      int index = i;
      addRenderableWidget(
          Button.builder(
                  Component.translatable("codex.oborokage.tab." + Codex.PAGES.get(i).key()),
                  b -> {
                    page = index;
                    lastPage = index;
                    scroll = 0;
                  })
              .bounds(x0 + i * (w + 4), 28, w, 18)
              .build());
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("codex.oborokage.up"), b -> scroll = Math.max(0, scroll - 1))
            .bounds(width - 30, 56, 20, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("codex.oborokage.down"), b -> scroll++)
            .bounds(width - 30, height - 36, 20, 20)
            .build());
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    super.extractRenderState(g, mouseX, mouseY, partial);
    Codex.Page p = Codex.PAGES.get(page);
    g.centeredText(font, title, width / 2, 10, 0xfff2e8ff);
    int x = Math.max(16, width / 2 - 170), right = Math.min(width - 40, width / 2 + 170);
    int wrap = right - x, y = 56, bottom = height - 16;
    // A dark page behind the text, so it reads over any world or blur.
    g.fill(x - 12, 50, right + 8, bottom + 4, 0xd0100e1e);
    List<String> entries = p.entries();
    int first = Math.min(scroll, Math.max(0, entries.size() - 1));
    for (int i = first; i < entries.size() && y < bottom - 12; i++) {
      String e = entries.get(i);
      boolean seen = Codex.seen(e);
      Component name = Component.translatable("codex.oborokage." + e);
      int accent = 0xff000000 | p.color();
      g.fill(x - 6, y + 1, x - 3, y + 8, seen ? accent : 0xff5d5873);
      g.text(font, name, x, y, seen ? accent : 0xffa8a1bf);
      if (!seen)
        g.text(
            font,
            Component.translatable("codex.oborokage.unseen"),
            x + font.width(name) + 8,
            y,
            0xff8a84a0);
      y += 11;
      // How to use it: wrapped too, as some languages need more than one line for it.
      for (FormattedCharSequence line :
          font.split(Component.translatable("codex.oborokage." + e + ".input"), wrap)) {
        g.text(font, line, x, y, 0xffd2c9e8);
        y += 10;
      }
      for (FormattedCharSequence line :
          font.split(Component.translatable("codex.oborokage." + e + ".desc"), wrap)) {
        if (y > bottom - 10) break;
        g.text(font, line, x, y, seen ? 0xffece8f6 : 0xffa8a1bf);
        y += 10;
      }
      y += 7;
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}

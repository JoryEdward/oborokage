package oborokage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import oborokage.ModLanguage;
import oborokage.client.compat.ClientCompat;

/**
 * 胧影 · 设置, from the move codex (K) and from Mod Menu: all fifteen client settings. Language; the
 * cinematic camera, one's own ultimates in third person, screen shake; the HUD's size and place,
 * the beginner's hints, the key-conflict warning; the blade trails (shader compatibility, how many
 * players, segments, vertex budget, distance); local prediction. Every change takes effect at once
 * and the file is written when the screen closes. Two columns (one on a narrow screen), scrolled
 * with the wheel when they do not fit; "reset" puts every setting back to its default.
 */
public final class SettingsScreen extends Screen {
  private static final int ROW = 24, W = 150, GAP = 8;
  private final Screen parent;
  private int scroll;

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

  private static Component label(String key) {
    return Component.translatable("ui.oborokage.settings." + key);
  }

  /** "name: on/off", in the mod's own language (the game's on/off would follow the game's). */
  private static Component switchLabel(String key, boolean on) {
    return Component.translatable(
        "ui.oborokage.settings.value",
        label(key),
        Component.translatable(on ? "ui.oborokage.settings.on" : "ui.oborokage.settings.off"));
  }

  private static Tooltip hint(String key) {
    return Tooltip.create(Component.translatable("ui.oborokage.settings." + key + ".hint"));
  }

  /** An on/off setting. */
  private static AbstractWidget toggle(
      String key,
      Predicate<ClientConfig.Values> get,
      java.util.function.BiConsumer<ClientConfig.Values, Boolean> set,
      int x,
      int y) {
    Button b =
        Button.builder(
                switchLabel(key, get.test(ClientConfig.get())),
                button -> {
                  boolean next = !get.test(ClientConfig.get());
                  ClientConfig.update(v -> set.accept(v, next));
                  button.setMessage(switchLabel(key, next));
                })
            .bounds(x, y, W, 20)
            .build();
    b.setTooltip(hint(key));
    return b;
  }

  /** A setting with a range, stepped. */
  private static AbstractWidget slider(
      String key,
      double min,
      double max,
      double step,
      ToDoubleFunction<ClientConfig.Values> get,
      java.util.function.BiConsumer<ClientConfig.Values, Double> set,
      Function<Double, String> show,
      int x,
      int y) {
    double start = (get.applyAsDouble(ClientConfig.get()) - min) / (max - min);
    AbstractSliderButton s =
        new AbstractSliderButton(x, y, W, 20, Component.empty(), start) {
          {
            updateMessage();
          }

          private double now() {
            return Math.round((min + value * (max - min)) / step) * step;
          }

          @Override
          protected void updateMessage() {
            setMessage(
                Component.translatable(
                    "ui.oborokage.settings.value", label(key), show.apply(now())));
          }

          @Override
          protected void applyValue() {
            double v = now();
            ClientConfig.update(c -> set.accept(c, v));
          }
        };
    s.setTooltip(hint(key));
    return s;
  }

  /** The settings in order, placed by `at` (index to x, y). */
  private List<AbstractWidget> options(java.util.function.IntFunction<int[]> at) {
    List<AbstractWidget> all = new ArrayList<>();
    Consumer<Function<int[], AbstractWidget>> add = f -> all.add(f.apply(at.apply(all.size())));
    add.accept(
        p ->
            Button.builder(
                    Component.translatable("ui.oborokage.language", name(ModLanguage.choice())),
                    b -> {
                      var choices = ModLanguage.CHOICES;
                      String next =
                          choices.get((choices.indexOf(ModLanguage.choice()) + 1) % choices.size());
                      ClientConfig.update(v -> v.language = next);
                      b.setMessage(Component.translatable("ui.oborokage.language", name(next)));
                    })
                .bounds(p[0], p[1], W, 20)
                .tooltip(Tooltip.create(Component.translatable("ui.oborokage.language.hint")))
                .build());
    add.accept(p -> toggle("cinematic", v -> v.cinematic, (v, b) -> v.cinematic = b, p[0], p[1]));
    add.accept(
        p ->
            toggle(
                "thirdPersonUltimate",
                v -> v.thirdPersonUltimate,
                (v, b) -> v.thirdPersonUltimate = b,
                p[0],
                p[1]));
    add.accept(
        p -> toggle("screenShake", v -> v.screenShake, (v, b) -> v.screenShake = b, p[0], p[1]));
    add.accept(
        p ->
            slider(
                "hudScale",
                .5,
                2,
                .05,
                v -> v.hudScale,
                (v, d) -> v.hudScale = d,
                d -> String.format(java.util.Locale.ROOT, "%.2f", d),
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "hudOffsetX",
                -300,
                300,
                1,
                v -> v.hudOffsetX,
                (v, d) -> v.hudOffsetX = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "hudOffsetY",
                -300,
                300,
                1,
                v -> v.hudOffsetY,
                (v, d) -> v.hudOffsetY = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(p -> toggle("tutorial", v -> v.tutorial, (v, b) -> v.tutorial = b, p[0], p[1]));
    add.accept(
        p ->
            toggle(
                "keyConflictWarning",
                v -> v.keyConflictWarning,
                (v, b) -> v.keyConflictWarning = b,
                p[0],
                p[1]));
    add.accept(
        p ->
            toggle(
                "trailShaderCompat",
                v -> v.trailShaderCompat,
                (v, b) -> v.trailShaderCompat = b,
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "trailMaxPlayers",
                1,
                32,
                1,
                v -> v.trailMaxPlayers,
                (v, d) -> v.trailMaxPlayers = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "trailSegments",
                8,
                64,
                1,
                v -> v.trailSegments,
                (v, d) -> v.trailSegments = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "trailVertexBudget",
                512,
                65536,
                512,
                v -> v.trailVertexBudget,
                (v, d) -> v.trailVertexBudget = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(
        p ->
            slider(
                "trailDistance",
                8,
                96,
                1,
                v -> v.trailDistance,
                (v, d) -> v.trailDistance = (int) Math.round(d),
                d -> String.valueOf((int) Math.round(d)),
                p[0],
                p[1]));
    add.accept(
        p ->
            toggle(
                "localPrediction",
                v -> v.localPrediction,
                (v, b) -> v.localPrediction = b,
                p[0],
                p[1]));
    return all;
  }

  @Override
  protected void init() {
    int columns = width >= 2 * W + GAP + 20 ? 2 : 1;
    int top = 34, bottom = height - 34, visible = Math.max(1, (bottom - top) / ROW);
    int rows = (15 + columns - 1) / columns;
    scroll = Math.max(0, Math.min(scroll, rows - visible));
    int x0 = width / 2 - (columns * W + (columns - 1) * GAP) / 2;
    List<AbstractWidget> all =
        options(i -> new int[] {x0 + i % columns * (W + GAP), top + (i / columns - scroll) * ROW});
    for (int i = 0; i < all.size(); i++) {
      int row = i / columns - scroll;
      if (row >= 0 && row < visible) addRenderableWidget(all.get(i));
    }
    int bw = Math.min(W, (width - 30) / 2);
    addRenderableWidget(
        Button.builder(
                Component.translatable("ui.oborokage.settings.reset"),
                b -> {
                  ClientConfig.reset();
                  rebuildWidgets();
                })
            .bounds(width / 2 - bw - GAP / 2, height - 26, bw, 20)
            .build());
    addRenderableWidget(
        Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 + GAP / 2, height - 26, bw, 20)
            .build());
  }

  @Override
  public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
    int before = scroll;
    scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
    if (scroll != before) rebuildWidgets();
    return true;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    super.extractRenderState(g, mouseX, mouseY, partial);
    g.centeredText(font, title, width / 2, 14, 0xfff2e8ff);
  }

  @Override
  public void onClose() {
    ClientConfig.save();
    ClientCompat.openScreen(minecraft, parent);
  }
}

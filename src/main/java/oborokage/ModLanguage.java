package oborokage;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * The mod's own language setting: follow the game, or show the mod's text in Chinese, English or
 * Japanese whatever language the game is in. Only while a language is chosen is the game's language
 * wrapped (on the client, each time the game installs one) so that the mod's keys come from that
 * language, read straight from the JAR, and every other key from the game; following the game
 * leaves the game's own language object in place, so other mods see exactly what they would without
 * this one. Switching installs the game's language again, which wraps it anew (or not) and so drops
 * every translation cached against the old one.
 */
public final class ModLanguage {
  /** "auto" follows the game; the others are the languages the mod ships. */
  public static final List<String> CHOICES = List.of("auto", "zh_cn", "en_us", "ja_jp");

  private static volatile String choice = "auto";
  private static volatile Map<String, String> table = Map.of();
  private static volatile Language base;

  public static String choice() {
    return choice;
  }

  /** Picks the mod's language and puts it into effect at once (an unknown one means "auto"). */
  public static void choose(String next) {
    if (!CHOICES.contains(next)) next = "auto";
    table = next.equals("auto") ? Map.of() : load(next);
    choice = next;
    Language game = base;
    if (game != null) Language.inject(game);
  }

  /** The mod's strings for one language, from the JAR (resource packs do not change them). */
  static Map<String, String> load(String code) {
    Map<String, String> strings = new HashMap<>();
    try (InputStream in =
        ModLanguage.class.getResourceAsStream("/assets/oborokage/lang/" + code + ".json")) {
      if (in != null) Language.loadFromJson(in, strings::put);
    } catch (Exception e) {
      ConfigIO.LOG.warn("Cannot read the {} strings: {}", code, e.toString());
    }
    return Map.copyOf(strings);
  }

  /**
   * The language to install in place of the one the game is installing: wrapped while a language is
   * chosen, the game's own otherwise. Called from the client's mixin.
   */
  public static Language wrap(Language language) {
    if (language instanceof Wrapped w) language = w.game;
    base = language;
    Language wrapped = choice.equals("auto") ? language : new Wrapped(language);
    // Up to 26.1 the client's I18n keeps a language of its own, set just before this one.
    try {
      var i18n =
          Class.forName(
              "net.minecraft.client.resources.language.I18n",
              false,
              ModLanguage.class.getClassLoader());
      var field = i18n.getDeclaredField("language");
      field.setAccessible(true);
      field.set(null, wrapped);
    } catch (ReflectiveOperationException | LinkageError ignored) {
      // A dedicated server, or a version whose I18n reads the shared language.
    }
    return wrapped;
  }

  /** The game's language with the mod's keys taken from the chosen one. */
  public static final class Wrapped extends Language {
    final Language game;

    Wrapped(Language game) {
      this.game = game;
    }

    @Override
    public String getOrDefault(String key, String fallback) {
      if (!choice.equals("auto")) {
        String own = table.get(key);
        if (own != null) return own;
      }
      return game.getOrDefault(key, fallback);
    }

    @Override
    public boolean has(String key) {
      return !choice.equals("auto") && table.containsKey(key) || game.has(key);
    }

    @Override
    public boolean isDefaultRightToLeft() {
      return game.isDefaultRightToLeft();
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText text) {
      return game.getVisualOrder(text);
    }
  }

  private ModLanguage() {}
}

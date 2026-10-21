package oborokage.check;

import java.util.*;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import oborokage.ModLanguage;

/**
 * The mod's own language setting: each choice gives every mod string from its own file, the game's
 * strings are untouched, "auto" follows the game, arguments still fill in, and a bad value falls
 * back to "auto". (The client wraps the game's language through a mixin; here it is wrapped by
 * hand, which is the same call.)
 */
final class LangChecks {
  private final ServerChecks t;

  LangChecks(ServerChecks t) {
    this.t = t;
  }

  private static Map<String, String> file(String code) throws Exception {
    Map<String, String> strings = new LinkedHashMap<>();
    try (var in =
        ModLanguage.class.getResourceAsStream("/assets/oborokage/lang/" + code + ".json")) {
      Language.loadFromJson(in, strings::put);
    }
    return strings;
  }

  boolean tick() throws Exception {
    Language game = Language.getInstance();
    Language wrapped = ModLanguage.wrap(game);
    try {
      Set<String> keys = file("zh_cn").keySet();
      for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
        Map<String, String> own = file(code);
        ModLanguage.choose(code);
        int wrong = 0;
        for (String key : keys)
          if (!Objects.equals(wrapped.getOrDefault(key), own.get(key))) wrong++;
        t.check(
            own.keySet().equals(keys) && wrong == 0 && ModLanguage.choice().equals(code),
            "Language "
                + code
                + ": all "
                + keys.size()
                + " strings from its own file ("
                + wrapped.getOrDefault("entity.oborokage.blossom_oni")
                + ")");
      }
      ModLanguage.choose("ja_jp");
      t.check(
          wrapped
                  .getOrDefault("block.minecraft.stone")
                  .equals(game.getOrDefault("block.minecraft.stone"))
              && wrapped.has("item.oborokage.kazuuchi"),
          "The game's own strings are untouched: " + wrapped.getOrDefault("block.minecraft.stone"));
      Language.inject(wrapped);
      String phase = Component.translatable("tooltip.oborokage.moon_phase", "満月", 8, 8).getString();
      Language.inject(game);
      t.check(phase.equals("月相：満月（8 / 8）"), "Arguments still fill in: " + phase);
      ModLanguage.choose("auto");
      t.check(
          wrapped
              .getOrDefault("item.oborokage.tachi")
              .equals(game.getOrDefault("item.oborokage.tachi")),
          "Auto follows the game: " + wrapped.getOrDefault("item.oborokage.tachi"));
      ModLanguage.choose("xx_yy");
      t.check(ModLanguage.choice().equals("auto"), "An unknown language falls back to auto");
    } finally {
      ModLanguage.choose("auto");
      Language.inject(game);
    }
    return true;
  }
}

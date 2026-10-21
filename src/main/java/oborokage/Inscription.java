package oborokage;

import net.minecraft.world.item.ItemStack;

/**
 * Inscriptions cut into a blade at the anvil with an inscription stone. One per blade; a new stone
 * replaces the old inscription. 古铭 is never made at the anvil: it comes only on blades restored
 * from a blade tomb.
 */
public enum Inscription {
  /** Zanshin after an ultimate lasts twice as long. */
  ZANSHIN("zanshin", 5),
  /** Intent is gained 15% faster. */
  FOCUS("focus", 5),
  /** Aerial plunge and string dash recover in half the time. */
  FEATHER("feather", 4),
  /** Each enemy the blade fells restores a food point. */
  DEW("dew", 6),
  /** Cosmetic: the blade's light burns crimson and gold. */
  CRIMSON("crimson", 2),
  /** Blade abilities cost 30% less hunger. Tomb blades only. */
  ANCIENT("ancient", 0);

  public final String id;
  public final int levels;

  Inscription(String id, int levels) {
    this.id = id;
    this.levels = levels;
  }

  public static Inscription byId(String id) {
    for (Inscription i : values()) if (i.id.equals(id)) return i;
    return null;
  }

  public static Inscription of(ItemStack stack) {
    String id = stack.get(Components.INSCRIPTION);
    return id == null ? null : byId(id);
  }

  public static boolean has(ItemStack stack, Inscription inscription) {
    return of(stack) == inscription;
  }
}

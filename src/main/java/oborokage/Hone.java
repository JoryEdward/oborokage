package oborokage;

import net.minecraft.world.item.ItemStack;

/** A whetstone's edge: 5% more blade damage and brighter light until it wears off. */
public final class Hone {
  public static final float BONUS = 1.05f;

  public static void apply(ItemStack blade, long now) {
    blade.set(Components.HONED_UNTIL, now + ServerConfig.get().honeTicks);
  }

  /** Remaining ticks of the edge at the given game time, or 0. */
  public static long remaining(ItemStack blade, long now) {
    Long until = blade.get(Components.HONED_UNTIL);
    return until == null ? 0 : Math.max(0, until - now);
  }

  public static boolean honed(ItemStack blade, long now) {
    return remaining(blade, now) > 0;
  }

  private Hone() {}
}

package oborokage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 胧影 · 月相. Every swing of the tachi that lands on an enemy waxes the blade's moon by one phase. At
 * the full moon the next iaido is drawn at full charge however briefly it was held, and the moon
 * returns to new.
 */
public final class Moon {
  public static final int FULL = 8;

  public static int phase(ItemStack stack) {
    Integer v = stack.get(Components.MOON_PHASE);
    return v == null ? 0 : v;
  }

  public static boolean full(ItemStack stack) {
    return stack.is(Oborokage.TACHI) && phase(stack) >= FULL;
  }

  /** One phase for a landed swing; rings once when the moon comes full. */
  public static void wax(ServerPlayer p, ItemStack blade) {
    if (!blade.is(Oborokage.TACHI) || !BladeArts.enabled(p)) return;
    int phase = phase(blade);
    if (phase >= FULL) return;
    blade.set(Components.MOON_PHASE, phase + 1);
    if (phase + 1 == FULL)
      ((ServerLevel) p.level())
          .playSound(
              null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, .8f, 1.6f);
  }

  public static void spend(ItemStack blade) {
    blade.set(Components.MOON_PHASE, 0);
  }

  private Moon() {}
}

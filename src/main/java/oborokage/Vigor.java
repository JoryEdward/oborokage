package oborokage;

import net.minecraft.world.entity.player.Player;

/**
 * The 1.0 balance: blade abilities are paid for in hunger (food exhaustion), and a wielder whose
 * food has run down to the fatigue level loses the blade's arts until they eat. Four exhaustion is
 * one food point (half a drumstick) once saturation is spent; a vanilla sword swing costs 0.1.
 */
public final class Vigor {
  public static final float SWING = .1f,
      PLUNGE = 1.2f,
      STRING_DASH = 1.5f,
      THUNDER = .4f,
      BLOOM_HEAL = .3f,
      ULTIMATE = 6f;

  public static float technique(float charge) {
    return .5f + 1.5f * charge;
  }

  public static float iaido(float charge) {
    return .5f + 2.5f * charge;
  }

  /** Charges the wielder; creative players and a zero cost setting pay nothing. */
  public static void spend(Player p, float exhaustion) {
    double k = ServerConfig.get().hungerCost;
    if (k <= 0 || p.getAbilities().instabuild || exhaustion <= 0) return;
    if (Inscription.of(p.getMainHandItem()) == Inscription.ANCIENT) k *= .7;
    p.causeFoodExhaustion((float) (exhaustion * k));
  }

  /** At or below the fatigue food level the blade's arts fall silent until the wielder eats. */
  public static boolean fatigued(Player p) {
    if (p.getAbilities().instabuild) return false;
    int level = ServerConfig.get().fatigueFoodLevel;
    return level >= 0 && p.getFoodData().getFoodLevel() <= level;
  }

  private Vigor() {}
}

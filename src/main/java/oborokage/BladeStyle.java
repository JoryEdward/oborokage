package oborokage;

import net.minecraft.world.item.ItemStack;

/**
 * Per-blade identity shared by server and client: technique power, ultimate and palette. The
 * Oborokage tachi keeps its original power of 1.0; the four other blades hit 30% harder.
 */
public enum BladeStyle {
  TACHI(1.0f, Move.ULTIMATE, 0xbca6f4, 0x7d66b6, 0xf3e8ff, "tachi"),
  WAKIZASHI(1.3f, Move.ULT_MOON, 0xd9cdff, 0x9f86e8, 0xfffaff, "moon"),
  RAIKIRI(1.3f, Move.ULT_STORM, 0xff5a2c, 0xffc35a, 0xfff2d6, "storm"),
  FROSTMOON(1.3f, Move.ULT_FROST, 0x7fe6ff, 0x2f86c9, 0xf4fdff, "frost"),
  VOIDSTRING(1.3f, Move.ULT_VOID, 0xa66bff, 0x36186e, 0xf2e6ff, "void"),
  /** 数打太刀: plain steel, no art, no technique and no ultimate ({@link #ultimate} is -1). */
  PLAIN(.8f, -1, 0xd6dae2, 0x7a808c, 0xf6f8fb, "plain");

  public final float power;
  public final int ultimate, primary, secondary, accent;
  public final String key;

  BladeStyle(float power, int ultimate, int primary, int secondary, int accent, String key) {
    this.power = power;
    this.ultimate = ultimate;
    this.primary = primary;
    this.secondary = secondary;
    this.accent = accent;
    this.key = key;
  }

  /** The style of a blade stack, or null for anything else. */
  public static BladeStyle of(ItemStack stack) {
    if (stack.is(Oborokage.TACHI)) return TACHI;
    if (stack.is(Oborokage.WAKIZASHI)) return WAKIZASHI;
    if (stack.is(Oborokage.RAIKIRI)) return RAIKIRI;
    if (stack.is(Oborokage.FROSTMOON)) return FROSTMOON;
    if (stack.is(Oborokage.VOIDSTRING)) return VOIDSTRING;
    if (stack.is(Oborokage.KAZUUCHI)) return PLAIN;
    return null;
  }

  /** Whether the blade has an ultimate (every named blade does; the kazuuchi does not). */
  public boolean hasUltimate() {
    return ultimate >= 0;
  }

  public static BladeStyle ofUltimate(int move) {
    for (BladeStyle s : values()) if (s.ultimate == move && s.hasUltimate()) return s;
    return null;
  }

  public static float power(ItemStack stack) {
    BladeStyle s = of(stack);
    return s == null ? 1 : s.power;
  }
}

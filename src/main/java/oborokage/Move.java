package oborokage;

/** Shared animation / hit-window timeline, in server ticks. */
public record Move(
    int id,
    int length,
    int[] hits,
    int[] hands,
    float damage,
    float reach,
    float arc,
    float[] weights) {
  public static final int MAIN = 1,
      OFF = 2,
      CROSS = 3,
      COUNTER = 4,
      DODGE = 5,
      ULTIMATE = 6,
      GUARD = 7,
      STORM = 8,
      FROST = 9,
      VOID = 10,
      TERRAIN = 11,
      ULT_MOON = 12,
      ULT_STORM = 13,
      ULT_FROST = 14,
      ULT_VOID = 15,
      SOLO_1 = 16,
      SOLO_2 = 17,
      SOLO_3 = 18;

  public Move(int id, int length, int[] hits, int[] hands, float damage, float reach, float arc) {
    this(id, length, hits, hands, damage, reach, arc, ones(hits.length));
  }

  private static float[] ones(int n) {
    float[] w = new float[n];
    java.util.Arrays.fill(w, 1);
    return w;
  }

  private static final Move NONE = new Move(0, 0, new int[0], new int[0], 0, 0, 0);
  private static final Move[] MOVES = {
    NONE,
    new Move(MAIN, 10, new int[] {3}, new int[] {0}, 18, 3.8f, 105),
    new Move(OFF, 9, new int[] {2}, new int[] {1}, 14, 3.1f, 115),
    new Move(CROSS, 16, new int[] {4, 7}, new int[] {0, 1}, 26, 4.2f, 130),
    new Move(COUNTER, 15, new int[] {3, 5}, new int[] {1, 0}, 42, 4, 130),
    new Move(DODGE, 6, new int[0], new int[0], 0, 0, 0),
    // 胧影 · 双月归寂: two crossing moons, then the 1000-point execution.
    new Move(ULTIMATE, 48, new int[] {9, 13, 23}, new int[] {0, 1, 0}, 85, 6.5f, 210),
    new Move(GUARD, 600, new int[0], new int[0], 0, 0, 0),
    new Move(STORM, 12, new int[] {3}, new int[] {0}, 0, 3, 110),
    new Move(FROST, 16, new int[] {4}, new int[] {0}, 0, 6, 360),
    new Move(VOID, 16, new int[] {3}, new int[] {0}, 0, 3, 100),
    new Move(TERRAIN, 12, new int[] {3}, new int[] {0}, 0, 3, 110),
    // 胧月 · 百花缭乱: eight whirling cuts all around, then the blossoming ninth.
    new Move(
        ULT_MOON,
        46,
        new int[] {8, 10, 12, 14, 16, 18, 20, 22, 32},
        new int[9],
        22,
        5.5f,
        360,
        new float[] {1, 1, 1, 1, 1, 1, 1, 1, 4.5f}),
    // 雷切 · 万雷天降: blink strikes and the closing thunder are scripted in Ultimates.
    new Move(ULT_STORM, 56, new int[0], new int[0], 90, 16, 360),
    // 霜天 · 镜月冰河: freeze at 18 (scripted), shatter everything in the ring at 38.
    new Move(ULT_FROST, 58, new int[] {38}, new int[] {0}, 95, 12, 360),
    // 空弦 · 断界: the realm-severing slash and its aftershock are scripted in Ultimates.
    new Move(ULT_VOID, 62, new int[0], new int[0], 190, 48, 30),
    // One blade drawn: a falling diagonal cut, a rising cut, then a stepping full-width finisher.
    new Move(SOLO_1, 10, new int[] {3}, new int[] {0}, 16, 3.9f, 120),
    new Move(SOLO_2, 10, new int[] {3}, new int[] {0}, 18, 3.9f, 130),
    new Move(SOLO_3, 16, new int[] {6}, new int[] {0}, 28, 4.4f, 160)
  };

  public static Move of(int id) {
    return id > 0 && id < MOVES.length ? MOVES[id] : NONE;
  }

  public static boolean known(int id) {
    return id >= 0 && id < MOVES.length;
  }

  public static boolean ultimate(int id) {
    return id == ULTIMATE || id >= ULT_MOON && id <= ULT_VOID;
  }

  /** The single-blade chain. */
  public static boolean solo(int id) {
    return id >= SOLO_1 && id <= SOLO_3;
  }

  /** The chain's closing cut: bigger trail, stronger feedback, the blade's own rider. */
  public static boolean finisher(int id) {
    return id == CROSS || id == COUNTER || id == SOLO_3;
  }

  /** The chain step that follows a finished move. */
  public static int next(int id) {
    return switch (id) {
      case MAIN, DODGE -> OFF;
      case OFF -> CROSS;
      case SOLO_1 -> SOLO_2;
      case SOLO_2 -> SOLO_3;
      default -> MAIN;
    };
  }

  /** A pending chain step, restarted when the player switched between one and two blades. */
  public static int follow(int next, boolean dual) {
    if (solo(next) == dual) return dual ? MAIN : SOLO_1;
    return next;
  }
}

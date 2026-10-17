package oborokage;

/** Shared animation / hit-window timeline, in server ticks. */
public record Move(
    int id, int length, int[] hits, int[] hands, float damage, float reach, float arc) {
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
      TERRAIN = 11;
  private static final Move NONE = new Move(0, 0, new int[0], new int[0], 0, 0, 0);
  private static final Move[] MOVES = {
    NONE,
    new Move(MAIN, 10, new int[] {3}, new int[] {0}, 18, 3.8f, 105),
    new Move(OFF, 9, new int[] {2}, new int[] {1}, 14, 3.1f, 115),
    new Move(CROSS, 16, new int[] {4, 7}, new int[] {0, 1}, 26, 4.2f, 130),
    new Move(COUNTER, 15, new int[] {3, 5}, new int[] {1, 0}, 42, 4, 130),
    new Move(DODGE, 6, new int[0], new int[0], 0, 0, 0),
    new Move(ULTIMATE, 48, new int[] {9, 13, 23}, new int[] {0, 1, 0}, 65, 6.5f, 210),
    new Move(GUARD, 600, new int[0], new int[0], 0, 0, 0),
    new Move(STORM, 12, new int[] {3}, new int[] {0}, 0, 3, 110),
    new Move(FROST, 16, new int[] {4}, new int[] {0}, 0, 6, 360),
    new Move(VOID, 16, new int[] {3}, new int[] {0}, 0, 3, 100),
    new Move(TERRAIN, 12, new int[] {3}, new int[] {0}, 0, 3, 110)
  };

  public static Move of(int id) {
    return id > 0 && id < MOVES.length ? MOVES[id] : NONE;
  }

  public static boolean known(int id) {
    return id >= 0 && id < MOVES.length;
  }
}

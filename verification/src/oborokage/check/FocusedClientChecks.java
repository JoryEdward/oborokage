package oborokage.check;

import net.minecraft.client.Minecraft;

public final class FocusedClientChecks extends ClientChecks {
  @Override
  void run(Minecraft c) throws Exception {
    if (++wallTick > 2400) throw new AssertionError("Focused client timed out");
    if (c.level == null || c.player == null || c.gameMode == null) return;
    if (expansionFailure != null) throw new AssertionError(expansionFailure);
    if (++tick > 80) FocusedChecks.tick(this, c, tick - 81);
  }
}

package oborokage.check;

import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import oborokage.*;
import oborokage.client.*;

/** Runs only against the preserved actual Gemini jar in a separate generated world. */
public final class BaselineAuditChecks extends ClientChecks {
  @Override
  void run(Minecraft c) throws Exception {
    if (++wallTick > 1500) throw new AssertionError("Baseline client timed out");
    if (c.level == null || c.player == null || c.gameMode == null) return;
    if (expansionFailure != null) throw new AssertionError(expansionFailure);
    if (++tick <= 80) return;
    int age = tick - 81, phase = age / 90, at = age % 90;
    if (phase >= 3) {
      finish(c);
      return;
    }
    Item blade =
        phase == 0 ? Oborokage.RAIKIRI : phase == 1 ? Oborokage.FROSTMOON : Oborokage.VOIDSTRING;
    if (at == 1)
      onServer(
          c,
          p -> {
            FocusedChecks.setup(p, false, 0, GameType.SURVIVAL);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
            p.inventoryMenu.broadcastChanges();
          });
    c.player.setYRot(0);
    c.player.setXRot(0);
    if (at == 15) FocusedChecks.use(c, true);
    if (at == 57) shot(c, "baseline-" + phase + "-charge");
    if (at == 59) c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    if (at == 62) FocusedChecks.use(c, false);
    if (at == 65) shot(c, "baseline-" + phase + "-single-technique");
    if (at == 68) {
      check(
          OborokageClient.sceneAge(c.player.getId()) < 0,
          "New skill does not create legacy SlashPayload scene: " + blade);
      check(
          DualClient.scene(c.player.getId()) != null, "New skill uses DualClient scene: " + blade);
    }
    if (at == 79) {
      shot(c, "baseline-" + phase + "-recovery");
      c.options.setCameraType(CameraType.FIRST_PERSON);
    }
  }
}

package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import oborokage.*;
import oborokage.client.Cinematics;
import oborokage.client.DualClient;

/**
 * Run with --bugcheck. The 1.0.1 player reports, in a real client:
 *
 * <p>1. Keys pressed while a full-charge draw plays (a jump, a turn, attack, J, the step) must not
 * cut its cinematic short and drop the camera to first person; nor during a J ultimate.
 *
 * <p>3. The 霜轮 ultimate cast in mid-air (as after a Warden's blow) lays its ice ring on the ground
 * below, not at the height the player was flung to.
 *
 * <p>Also: a screen opened during an ultimate must not leave the camera in third person after it.
 *
 * <p>Every check is recorded (PASS / FAIL) so one run shows all of them.
 */
public final class ClientBugCheck implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private int ticks = -1, step;
  private double groundY;
  private boolean done;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null || c.getSingleplayerServer() == null)
            return;
          try {
            run(c);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            finish(c);
          }
        });
  }

  private void check(boolean ok, String what) {
    results.add((ok ? "PASS " : "FAIL ") + what);
    System.out.println("TACHI_CHECK " + (ok ? "PASS " : "FAIL ") + what);
  }

  private void onServer(Minecraft c, java.util.function.Consumer<ServerPlayer> work) {
    var server = c.getSingleplayerServer();
    server.execute(() -> work.accept(server.getPlayerList().getPlayer(c.player.getUUID())));
  }

  private static void hold(ServerPlayer p, Item main, boolean dual) {
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
    p.setItemInHand(
        InteractionHand.OFF_HAND, dual ? new ItemStack(Oborokage.WAKIZASHI) : ItemStack.EMPTY);
  }

  private static void fullIntent(ServerPlayer p) {
    try {
      var f = DualCombat.State.class.getDeclaredField("intent");
      f.setAccessible(true);
      f.setInt(DualCombat.state(p), 100);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /** What a player does with their hands full: jumps, swings the view, mashes the keys. */
  private static void mash(Minecraft c) {
    c.player.jumpFromGround();
    c.player.setYRot(c.player.getYRot() + 35);
    c.player.setXRot(c.player.getXRot() - 10);
    DualClient.intent = 100;
    DualClient.request(0);
    DualClient.request(4);
    DualClient.request(3);
  }

  private static boolean steering(Minecraft c, int kind) {
    return Cinematics.cameraActive()
        && Cinematics.kind == kind
        && c.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
  }

  /** The ground plane the ultimate's ring is drawn on: its floor if it has one, else its origin. */
  private static double ringY(int actor) throws Exception {
    Class<?> fx = Class.forName("oborokage.client.UltimateFx");
    var states = fx.getDeclaredField("states");
    states.setAccessible(true);
    Object s = ((Map<?, ?>) states.get(null)).get(actor);
    if (s == null) return Double.NaN;
    Class<?> type = s.getClass();
    java.lang.reflect.Field f;
    try {
      f = type.getDeclaredField("floor");
    } catch (NoSuchFieldException e) {
      f = type.getDeclaredField("origin");
    }
    f.setAccessible(true);
    return ((net.minecraft.world.phys.Vec3) f.get(s)).y;
  }

  private void run(Minecraft c) throws Exception {
    if (ticks < 0) {
      // Start on the ground, in the default first-person view.
      if (!c.player.onGround()) return;
      c.options.setCameraType(CameraType.FIRST_PERSON);
      ticks = 0;
    }
    ticks++;
    int id = c.player.getId();
    switch (ticks) {
      // 1a. A full-charge draw, then keys while it plays.
      case 40 ->
          onServer(
              c,
              p -> {
                hold(p, Oborokage.TACHI, true);
                Combat.start(p, 1);
              });
      case 46 -> {
        check(steering(c, Cinematics.IAIDO), "A full-charge draw plays its cinematic");
        mash(c);
      }
      case 58 -> {
        check(
            steering(c, Cinematics.IAIDO),
            "Jumping, turning and pressing attack, J and the step during a full-charge draw keep"
                + " its cinematic (camera "
                + c.options.getCameraType()
                + ", kind "
                + Cinematics.kind
                + ")");
        screenshot(c, "bug1-draw-after-keys");
      }
      // 1b. A J ultimate, then keys while it plays.
      case 130 ->
          onServer(
              c,
              p -> {
                hold(p, Oborokage.TACHI, true);
                fullIntent(p);
              });
      case 133 -> {
        DualClient.intent = 100;
        DualClient.request(4);
      }
      case 138 -> {
        check(
            Cinematics.cameraActive(), "A J ultimate plays its cinematic: kind " + Cinematics.kind);
        step = Cinematics.kind;
        mash(c);
      }
      case 150 ->
          check(
              steering(c, step),
              "Jumping, turning and pressing keys during a J ultimate keep its cinematic (camera "
                  + c.options.getCameraType()
                  + ", kind "
                  + Cinematics.kind
                  + ")");
      // 3. The 霜轮 ultimate cast four blocks up.
      case 260 -> {
        groundY = c.player.getY();
        onServer(
            c,
            p -> {
              hold(p, Oborokage.FROSTMOON, false);
              fullIntent(p);
              p.teleportTo(p.getX(), p.getY() + 4, p.getZ());
            });
      }
      case 263 -> {
        DualClient.intent = 100;
        DualClient.request(4);
      }
      case 272 -> {
        double y = ringY(id);
        check(
            !Double.isNaN(y) && Math.abs(y - groundY) < .3,
            String.format(
                Locale.ROOT,
                "The 霜轮 ultimate cast in mid-air lays its ice ring on the ground: ring y %.2f,"
                    + " ground y %.2f, player y %.2f",
                y,
                groundY,
                c.player.getY()));
      }
      case 290 -> screenshot(c, "bug3-frost-airborne");
      // A screen opened during an ultimate: the view the player had comes back at the end.
      case 380 ->
          onServer(
              c,
              p -> {
                hold(p, Oborokage.FROSTMOON, false);
                fullIntent(p);
              });
      case 383 -> {
        DualClient.intent = 100;
        DualClient.request(4);
      }
      case 390 ->
          oborokage.client.compat.ClientCompat.openScreen(
              c, new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player));
      case 396 -> oborokage.client.compat.ClientCompat.openScreen(c, null);
      case 470 ->
          check(
              !Cinematics.playing() && c.options.getCameraType() == CameraType.FIRST_PERSON,
              "After an ultimate during which the inventory was opened, the view is first person"
                  + " again: "
                  + c.options.getCameraType());
      case 480 -> finish(c);
      default -> {}
    }
  }

  private void finish(Minecraft c) {
    done = true;
    try {
      Files.writeString(Path.of("client-results.txt"), String.join("\n", results) + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
    c.disconnectWithSavingScreen();
    c.stop();
  }

  /** Screenshot API moved between lines; take whichever form this line offers. */
  private void screenshot(Minecraft c, String name) {
    results.add("SHOT " + name);
    try {
      net.minecraft.client.Screenshot.class
          .getMethod("grab", Minecraft.class, boolean.class)
          .invoke(null, c, false);
      return;
    } catch (ReflectiveOperationException ignored) {
      // Older lines take a file name.
    }
    try {
      var target = Minecraft.class.getMethod("getMainRenderTarget").invoke(c);
      net.minecraft.client.Screenshot.class
          .getMethod(
              "grab",
              java.io.File.class,
              String.class,
              com.mojang.blaze3d.pipeline.RenderTarget.class,
              int.class,
              java.util.function.Consumer.class)
          .invoke(
              null,
              c.gameDirectory,
              name + ".png",
              target,
              1,
              (java.util.function.Consumer<Object>) m -> {});
    } catch (ReflectiveOperationException e) {
      results.add("NOTE screenshot unavailable on this line");
    }
  }
}

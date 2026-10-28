package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import oborokage.*;
import oborokage.client.*;

public final class NetClientChecks implements ClientModInitializer {
  int ticks, ready;
  boolean joined, finished, sawMain, sawUltimate, sawCounter;
  int idleCancels;
  List<String> results = new ArrayList<>();
  boolean actor = Boolean.getBoolean("oborokage.qa.actor");

  void check(boolean ok, String text) {
    if (!ok) throw new AssertionError(text);
    results.add("PASS " + text);
  }

  /** The screenshot API moved between lines; take whichever form this line offers. */
  void shot(Minecraft c, String name) {
    try {
      net.minecraft.client.Screenshot.class
          .getMethod("grab", Minecraft.class, boolean.class)
          .invoke(null, c, false);
      return;
    } catch (ReflectiveOperationException ignored) {
      // Older lines take a file name.
    }
    try {
      Object target;
      try {
        target = Minecraft.class.getMethod("getMainRenderTarget").invoke(c);
      } catch (NoSuchMethodException e) {
        target = c.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(c.gameRenderer);
      }
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

  public void onInitializeClient() {
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
        .register(NetSignal.TYPE, NetSignal.CODEC);
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (finished) return;
          try {
            run(c);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            finish(c);
          }
        });
  }

  void run(Minecraft c) {
    if (++ticks > 2400) throw new AssertionError("Network client timed out");
    if (c.player == null || c.level == null) return;
    if (c.level.players().size() >= 2) joined = true;
    if (!joined) return;
    ready++;
    c.player.setYRot(actor ? 0 : 110);
    c.player.setXRot(5);
    for (var scene : DualClient.scenes.values())
      if (scene.event.actor() != c.player.getId()) {
        if (scene.event.move() == Move.MAIN) sawMain = true;
        if (scene.event.move() == Move.COUNTER) sawCounter = true;
        if (scene.event.move() == Move.ULTIMATE) sawUltimate = true;
      }
    if (actor) {
      if (ready == 40) {
        check(DualCombat.equipped(c.player), "Server equipment reaches attacker client");
        DualClient.request(0);
        check(
            DualClient.scene(c.player.getId()) != null
                && DualClient.scene(c.player.getId()).predicted,
            "Own input predicts swing before delayed response");
      }
      if (ready == 70)
        check(
            DualClient.intent == 100, "Confirmed server hits synchronize full intent over network");
      if (ready == 95) DualClient.request(4);
      if (ready == 111) {
        check(Cinematics.cameraActive(), "Attacker receives own ultimate camera");
        shot(c, "network-attacker-ultimate");
      }
      if (ready == 160)
        check(
            c.options.getCameraType() == CameraType.FIRST_PERSON,
            "Attacker camera restores after networked ultimate");
    } else {
      if (ready == 113) shot(c, "network-observer-dual-ultimate");
      if (ready == 165) {
        check(
            sawMain && sawUltimate, "Observer receives ordered remote combo and ultimate actions");
        check(RenderObservations.dual > 0, "Observer renders remote dual arm animation");
        check(
            !Cinematics.cameraActive() && c.options.getCameraType() == CameraType.FIRST_PERSON,
            "Observer camera is never taken over");
      }
    }
    if (ready == 185) idleCancels = RenderObservations.cancels;
    if (actor && ready == 190) {
      c.options.keyUse.setDown(true);
      c.gameMode.useItem(c.player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    if (actor && ready == 191) {
      c.options.keyUse.setDown(false);
      c.gameMode.releaseUsingItem(c.player);
    }
    if (ready == 202)
      check(
          RenderObservations.cancels == idleCancels,
          "Idle right-click sends no stale cancel to " + (actor ? "owner" : "observer"));
    if (actor && ready == 210) {
      DualClient.request(1);
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NetSignal(1));
    }
    if (actor && ready == 217) {
      DualClient.request(0);
      var scene = DualClient.scene(c.player.getId());
      check(
          scene != null && scene.predicted && scene.event.move() == Move.COUNTER,
          "Delayed parry predicts counter immediately before acknowledgement");
    }
    if (actor && ready == 224) {
      var scene = DualClient.scene(c.player.getId());
      check(
          scene != null && !scene.predicted && scene.event.move() == Move.COUNTER,
          "Server acknowledgement preserves the predicted counter");
      shot(c, "network-owner-counter");
    }
    if (!actor && ready == 240)
      check(sawCounter, "Observer receives and renders the real counter after idle cancel");
    if (ready == 250)
      check(c.getConnection() != null, "Client remains connected after dual actions");
    // 1.1.0: a master between the two players and a parade near the observer (NetServerChecks).
    if (actor && ready == 255)
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NetSignal(2));
    if (!actor && ready == 340) {
      // The parade walks the ground (y -60 in this flat world), far below the players' platform.
      var near = c.player.getBoundingBox().inflate(96, 256, 96);
      var oni = c.level.getEntitiesOfClass(oborokage.BlossomOni.class, near);
      long generals = oni.stream().filter(oborokage.BlossomOni::elite).count();
      check(
          generals == 1 && oni.size() >= 7,
          "The observer sees the Night Parade and its 鬼将 (elite flag synced): "
              + generals
              + " general, "
              + oni.size()
              + " in all; entities "
              + c.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, near).size()
              + " at "
              + c.player.blockPosition().toShortString());
      shot(c, "network-observer-parade");
    }
    if (ready == 380) finish(c);
  }

  void finish(Minecraft c) {
    finished = true;
    try {
      Files.writeString(Path.of("network-client-results.txt"), String.join("\n", results) + "\n");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    c.disconnectWithSavingScreen();
    c.stop();
  }
}

package oborokage.check;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import oborokage.*;
import oborokage.client.*;

/**
 * Visual review of every ultimate: real key path, real server, three photographed beats each, then
 * the full-charge iaido. Labels go to showcase-labels.txt in shot order.
 */
public final class ClientUltimateShowcase implements ClientModInitializer {
  private record Act(String name, Item blade, int[] beats, double[][] foes) {}

  private static final Act[] ACTS = {
    new Act(
        "tachi-twin-moons",
        Oborokage.TACHI,
        new int[] {7, 15, 26},
        new double[][] {{0, 4}, {2, 5}, {-2, 5}}),
    new Act(
        "waki-hundred-flowers",
        Oborokage.WAKIZASHI,
        new int[] {13, 21, 33},
        new double[][] {{0, 3}, {3, 1}, {-3, 0}}),
    new Act(
        "raikiri-thunders",
        Oborokage.RAIKIRI,
        new int[] {6, 19, 46},
        new double[][] {{0, 6}, {4, 10}, {-4, 13}}),
    new Act(
        "frostmoon-mirror-moon",
        Oborokage.FROSTMOON,
        new int[] {12, 26, 39},
        new double[][] {{0, 4}, {5, 6}, {-6, 3}}),
    new Act(
        "voidstring-severing",
        Oborokage.VOIDSTRING,
        new int[] {20, 30, 41},
        new double[][] {{0, 8}, {1, 20}, {-1, 30}})
  };

  private final List<String> labels = new ArrayList<>();
  private final List<Entity> foes = new ArrayList<>();
  private int ticks, act = -1, local;
  private boolean done, iaido;
  private int combat, solo;
  private static final Item[] SOLO = {
    Oborokage.TACHI,
    Oborokage.WAKIZASHI,
    Oborokage.RAIKIRI,
    Oborokage.FROSTMOON,
    Oborokage.VOIDSTRING
  };
  private double bx, by, bz;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null || c.getSingleplayerServer() == null)
            return;
          try {
            tick(c);
          } catch (Throwable t) {
            t.printStackTrace();
            labels.add("FAIL " + t);
            finish(c);
          }
        });
  }

  private ServerPlayer server(Minecraft c) {
    return c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
  }

  private void onServer(Minecraft c, Consumer<ServerPlayer> work) {
    c.getSingleplayerServer().execute(() -> work.accept(server(c)));
  }

  private void setup(Minecraft c, Act a) {
    onServer(
        c,
        p -> {
          for (Entity e : foes) e.discard();
          foes.clear();
          // The reused server-test world leaves drops, orbs and mobs near spawn; clear them.
          for (Entity e :
              p.level()
                  .getEntities(
                      p,
                      p.getBoundingBox().inflate(64),
                      e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
          p.teleportTo(bx, by, bz);
          p.setYRot(0);
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(a.blade));
          p.setItemInHand(
              InteractionHand.OFF_HAND,
              a.blade == Oborokage.TACHI ? new ItemStack(Oborokage.WAKIZASHI) : ItemStack.EMPTY);
          for (double[] at : a.foes) {
            var h = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
            h.setPos(bx + at[0], by, bz + at[1]);
            h.setNoAi(true);
            h.setPersistenceRequired();
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            h.setHealth(2000);
            p.level().addFreshEntity(h);
            foes.add(h);
          }
          try {
            var f = DualCombat.State.class.getDeclaredField("intent");
            f.setAccessible(true);
            f.setInt(DualCombat.state(p), 100);
          } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
          }
        });
    c.player.setYRot(0);
    c.player.setXRot(5);
  }

  private void tick(Minecraft c) throws Exception {
    if (++ticks < 40) return;
    if (ticks == 40) {
      bx = Math.floor(c.player.getX()) + .5;
      by = c.player.getY();
      bz = Math.floor(c.player.getZ()) + .5;
    }
    local++;
    if (act < 0 || local > (iaido ? 140 : 110)) {
      iaido = false;
      combat = 0;
      solo = 0;
      act++;
      local = 0;
      int s0 = ACTS.length + 5;
      if (act == s0 + SOLO.length + 1) {
        // 0.6.1: 空弦 looking up; its cuts tip with the eyes.
        solo = -2;
        setup(c, new Act("solo", Oborokage.VOIDSTRING, new int[0], new double[][] {{1.3, 2.4}}));
        onServer(c, p -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        c.player.setXRot(-40);
        return;
      }
      if (act >= s0 && act <= s0 + SOLO.length) {
        // 0.6.0: the single-blade chain of every blade, then the tachi's in first person.
        solo = act - s0 + 1;
        boolean fp = solo > SOLO.length;
        Item blade = fp ? Oborokage.TACHI : SOLO[solo - 1];
        setup(c, new Act("solo", blade, new int[0], new double[][] {{1.3, 2.4}}));
        onServer(c, p -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        c.options.setCameraType(
            fp
                ? net.minecraft.client.CameraType.FIRST_PERSON
                : net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if (fp) solo = -1;
        return;
      }
      if (act > s0 + SOLO.length + 1) {
        labels.add("PASS ultimate showcase captured " + labels.size() + " beats");
        finish(c);
        return;
      }
      if (act < ACTS.length) setup(c, ACTS[act]);
      else if (act == ACTS.length + 1) {
        combat = 1;
        setup(c, new Act("dual-chain", Oborokage.TACHI, new int[0], new double[][] {{2.2, 1.6}}));
        c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
      } else if (act >= ACTS.length + 2 && act <= ACTS.length + 4) {
        combat = act - ACTS.length;
        Item[] blades = {Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING};
        Item blade = blades[act - ACTS.length - 2];
        setup(c, new Act("technique", blade, new int[0], new double[][] {{3, 6}}));
        onServer(c, p -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
      } else if (act == ACTS.length) {
        iaido = true;
        setup(
            c,
            new Act(
                "tachi-iaido",
                Oborokage.TACHI,
                new int[0],
                new double[][] {{0, 5}, {1.4, 8}, {-1.6, 10.5}, {1, 13}}));
        onServer(c, p -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
      }
    }
    if (solo != 0) {
      String name =
          solo == -2
              ? "solo-void-looking-up"
              : solo < 0
                  ? "solo-first-person"
                  : "solo-" + SOLO[solo - 1].toString().replace(':', '-');
      if (local == 30 || local == 42 || local == 54) DualClient.interceptAttack();
      if (local == 33) shot(c, name + "-1");
      if (local == 45) shot(c, name + "-2");
      if (local == 59) shot(c, name + "-3");
      if (local == 62) shot(c, name + "-3b");
      return;
    }
    if (combat == 1) {
      // Dual chain: main, off, cross, then a held guard; one shot mid-swing of each.
      if (local == 30 || local == 42 || local == 54) DualClient.interceptAttack();
      if (local == 33) shot(c, "dual-main-swing");
      if (local == 44) shot(c, "dual-off-swing");
      if (local == 59) shot(c, "dual-cross");
      if (local == 80) DualClient.request(1);
      if (local == 86) shot(c, "dual-guard");
      if (local == 90) DualClient.request(2);
      return;
    }
    if (combat >= 2) {
      // Full-charge technique: hold use for 42 ticks, release, photograph the stroke.
      if (local == 25) {
        c.options.keyUse.setDown(true);
        c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
      }
      if (local == 50) shot(c, "technique-charging-" + combat);
      if (local == 68) {
        c.options.keyUse.setDown(false);
        c.gameMode.releaseUsingItem(c.player);
      }
      if (local == 71) shot(c, "technique-release-" + combat);
      if (local == 76) shot(c, "technique-after-" + combat);
      return;
    }
    if (iaido) {
      // Charge the tachi for 42 ticks with the use key held, then release into the full draw.
      if (local == 25) {
        c.options.keyUse.setDown(true);
        c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
      }
      // The four charge steps of the build-up.
      for (int q : new int[] {12, 22, 32, 42}) if (local == 25 + q) shot(c, "charge-" + q);
      if (local == 68) {
        c.options.keyUse.setDown(false);
        c.gameMode.releaseUsingItem(c.player);
      }
      for (int beat : new int[] {6, 14, 24, 33, 36, 40, 52})
        if (local == 68 + beat) shot(c, "tachi-iaido-t" + beat);
      return;
    }
    Act a = ACTS[act];
    if (local == 30) DualClient.request(4);
    for (int beat : a.beats) if (local == 30 + beat) shot(c, a.name + "-t" + beat);
  }

  private void shot(Minecraft c, String label) throws Exception {
    labels.add(String.format(Locale.ROOT, "%02d %s", labels.size(), label));
    try {
      net.minecraft.client.Screenshot.class
          .getMethod("grab", Minecraft.class, boolean.class)
          .invoke(null, c, false);
    } catch (NoSuchMethodException e) {
      var target = Minecraft.class.getMethod("getMainRenderTarget").invoke(c);
      net.minecraft.client.Screenshot.class
          .getMethod(
              "grab",
              java.io.File.class,
              String.class,
              com.mojang.blaze3d.pipeline.RenderTarget.class,
              int.class,
              Consumer.class)
          .invoke(null, c.gameDirectory, label + ".png", target, 1, (Consumer<Object>) m -> {});
    }
  }

  private void finish(Minecraft c) {
    done = true;
    try {
      Files.writeString(Path.of("showcase-labels.txt"), String.join("\n", labels) + "\n");
      StringBuilder results = new StringBuilder();
      for (String l : labels)
        if (l.startsWith("PASS") || l.startsWith("FAIL")) results.append(l).append('\n');
      Files.writeString(Path.of("client-results.txt"), results.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
    c.disconnectWithSavingScreen();
    c.stop();
  }
}

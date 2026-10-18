package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import oborokage.*;
import oborokage.client.*;

/**
 * Version-neutral client smoke run: loads a world on any 26.x line and drives the real client paths
 * (prediction, trails, poses, sheaths, camera, technique FX, iaido scene) end to end. Any mixin
 * target missing on this line would fail class loading before the world appears.
 */
public final class ClientSmokeChecks implements ClientModInitializer {
  private final List<String> results = new ArrayList<>();
  private final List<Zombie> zombies = Collections.synchronizedList(new ArrayList<>());
  private volatile float zombieDamage = -1;
  private int ticks;
  private boolean done, sawThirdPerson;
  private CameraType before;

  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done || c.player == null || c.level == null || c.getSingleplayerServer() == null)
            return;
          try {
            tick(c);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            finish(c);
          }
        });
  }

  private void check(boolean ok, String what) {
    if (!ok) throw new AssertionError(what);
    results.add("PASS " + what);
    System.out.println("TACHI_CHECK PASS " + what);
  }

  private ServerPlayer server(Minecraft c) {
    return c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
  }

  private void onServer(Minecraft c, java.util.function.Consumer<ServerPlayer> work) {
    var server = c.getSingleplayerServer();
    server.execute(() -> work.accept(server.getPlayerList().getPlayer(c.player.getUUID())));
  }

  private volatile net.minecraft.world.entity.LivingEntity soloTarget;
  private volatile float soloDamage;

  private void hold(ServerPlayer p, net.minecraft.world.item.Item main) {
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
    p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
  }

  private void tick(Minecraft c) throws Exception {
    ticks++;
    int id = c.player.getId();
    if (ticks == 20) {
      check(true, Oborokage.ID + " client reached a loaded world");
      before = c.options.getCameraType();
      onServer(
          c,
          p -> {
            p.setYRot(0);
            p.setXRot(0);
            hold(p, Oborokage.TACHI);
            for (int i = 0; i < 3; i++) {
              var z = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
              z.setPos(p.getX() + (i - 1) * .9, p.getY(), p.getZ() + 2.4);
              z.setNoAi(true);
              z.setPersistenceRequired();
              z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
              z.setHealth(1000);
              p.level().addFreshEntity(z);
              zombies.add(z);
            }
          });
    }
    if (ticks == 30) {
      c.player.setYRot(0);
      c.player.setXRot(0);
    }
    if (ticks == 40 || ticks == 52 || ticks == 64) {
      check(DualClient.interceptAttack(), "Dual chain click intercepted at tick " + ticks);
      check(
          DualClient.scene(id) != null || predictionOff(),
          "Local prediction shows the cut immediately");
    }
    if (ticks == 90) {
      onServer(
          c,
          p -> {
            float total = 0;
            for (var z : zombies) total += z.getMaxHealth() - z.getHealth();
            zombieDamage = total;
          });
    }
    if (ticks == 94) {
      check(zombieDamage > 0, "Server-confirmed chain damage reached the targets: " + zombieDamage);
      check(
          DualClient.intent > 0,
          "Owner status packets carry intent to the HUD: " + DualClient.intent);
      DualClient.request(3);
    }
    if (ticks == 96) check(DualClient.scene(id) != null, "Shadow step scene plays");
    if (ticks == 110) DualClient.request(1);
    if (ticks == 114)
      check(
          DualClient.scene(id) != null && DualClient.scene(id).event.move() == Move.GUARD,
          "Cross guard pose is shown");
    if (ticks == 118) DualClient.request(2);
    if (ticks == 125)
      onServer(
          c,
          p -> {
            try {
              var f = DualCombat.State.class.getDeclaredField("intent");
              f.setAccessible(true);
              f.setInt(DualCombat.state(p), 100);
            } catch (ReflectiveOperationException e) {
              throw new RuntimeException(e);
            }
          });
    if (ticks == 135) DualClient.request(4);
    if (ticks > 135 && ticks < 175 && c.options.getCameraType() == CameraType.THIRD_PERSON_BACK)
      sawThirdPerson = true;
    if (ticks == 160) screenshot(c, "smoke-ultimate");
    if (ticks == 200) {
      check(
          sawThirdPerson || !OborokageClient.cinematic,
          "Dual ultimate frames the third-person camera");
      check(c.options.getCameraType() == before, "Camera returns to the player's own view");
      onServer(
          c,
          p -> {
            hold(p, Oborokage.FROSTMOON);
            StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.FROST, 1);
          });
    }
    if (ticks == 204)
      check(
          DualClient.scene(id) != null && DualClient.scene(id).event.move() == Move.FROST,
          "Frost technique event reaches the client");
    if (ticks == 225)
      onServer(
          c,
          p -> {
            hold(p, Oborokage.VOIDSTRING);
            StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.VOID, 1);
          });
    if (ticks == 229)
      check(
          DualClient.scene(id) != null && DualClient.scene(id).event.move() == Move.VOID,
          "Void technique event reaches the client");
    if (ticks == 231) screenshot(c, "smoke-void");
    if (ticks == 250)
      onServer(
          c,
          p -> {
            hold(p, Oborokage.RAIKIRI);
            StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.STORM, 1);
          });
    if (ticks == 270)
      onServer(
          c,
          p -> {
            hold(p, Oborokage.TACHI);
            Combat.start(p, 1);
          });
    if (ticks == 274)
      check(OborokageClient.sceneAge(id) >= 0, "Original iaido scene plays on this version");
    if (ticks == 290) screenshot(c, "smoke-iaido");
    if (ticks == 330)
      onServer(
          c,
          p -> {
            // A drawn Frostmoon alone unleashes its own ultimate on every 26.x line.
            hold(p, Oborokage.FROSTMOON);
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            try {
              var f = DualCombat.State.class.getDeclaredField("intent");
              f.setAccessible(true);
              f.setInt(DualCombat.state(p), 100);
            } catch (ReflectiveOperationException e) {
              throw new RuntimeException(e);
            }
          });
    if (ticks == 345) DualClient.request(4);
    if (ticks == 350)
      check(
          DualClient.scene(id) != null
              && DualClient.scene(id).event.move() == Move.ULT_FROST
              && Cinematics.playing(),
          "Solo Frostmoon ultimate plays with its cinematic");
    if (ticks == 372) screenshot(c, "smoke-frost-ultimate");
    if (ticks == 420) {
      check(c.options.getCameraType() == before, "Camera returns after the solo ultimate");
      check(
          !Cinematics.playing() && Cinematics.titleShowing(),
          "The ultimate's name lingers after its cinematic");
      c.player.setYRot(0);
      c.player.setXRot(0);
      onServer(
          c,
          p -> {
            // 0.6.0: one blade alone has its own chain on the attack key.
            hold(p, Oborokage.RAIKIRI);
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            p.setYRot(0);
            p.setXRot(0);
            var z = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
            z.setPos(p.getX(), p.getY(), p.getZ() + 2.3);
            z.setNoAi(true);
            z.setPersistenceRequired();
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            z.setHealth(1000);
            p.level().addFreshEntity(z);
            soloTarget = z;
          });
    }
    if (ticks == 430 || ticks == 442 || ticks == 454) {
      check(DualClient.interceptAttack(), "Single-blade click intercepted at tick " + ticks);
      int expected = ticks == 430 ? Move.SOLO_1 : ticks == 442 ? Move.SOLO_2 : Move.SOLO_3;
      check(
          predictionOff()
              || DualClient.scene(id) != null && DualClient.scene(id).event.move() == expected,
          "Single-blade chain step " + (expected - Move.SOLO_1 + 1) + " shows at once");
    }
    if (ticks == 461) screenshot(c, "smoke-solo");
    if (ticks == 480)
      onServer(c, p -> soloDamage = soloTarget.getMaxHealth() - soloTarget.getHealth());
    if (ticks == 484) {
      check(soloDamage > 40, "The single-blade chain damaged its target: " + soloDamage);
      check(
          !oborokage.client.compat.ClientCompat.screenOpen(c),
          "No error screen interrupted the run");
      finish(c);
    }
  }

  private static boolean predictionOff() {
    return !ClientConfig.get().localPrediction;
  }

  /** Screenshot API moved between lines; take whichever form this line offers. */
  private void screenshot(Minecraft c, String name) {
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
      try {
        net.minecraft.client.Screenshot.class
            .getMethod("grab", Minecraft.class, boolean.class)
            .invoke(null, c, false);
      } catch (ReflectiveOperationException ignored) {
        System.out.println("TACHI_NOTE screenshot unavailable on this line");
      }
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
}

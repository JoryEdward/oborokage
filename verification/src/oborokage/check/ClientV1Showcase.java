package oborokage.check;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import oborokage.*;
import oborokage.client.*;

/**
 * Visual review of the 1.0 content: the swordsman, the training post and the whetstone; the new
 * items on the hotbar; the codex; the aerial plunge; the petal field, ice floor and string; an
 * ancient blade in hand; the moon and fatigue lines on the HUD; the post's readout and its sway
 * away from blows from the side and from the front.
 */
public final class ClientV1Showcase implements ClientModInitializer {
  private final List<String> labels = new ArrayList<>();
  private final List<Entity> spawned = new ArrayList<>();
  private int ticks;
  private boolean done;
  private double bx, by, bz;
  private Swordsman swordsman, striker;
  private TrainingDummy post;

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

  private void tick(Minecraft c) throws Exception {
    ticks++;
    if (ticks < 40) return;
    if (ticks == 40) {
      // The scene is laid out on the test platform (x -4..4, z -3..19, standing at y 100), not
      // where the player was saved: the client world is kept between runs.
      bx = .5;
      by = 100;
      bz = .5;
      onServer(
          c,
          p -> {
            for (Entity e :
                p.level()
                    .getEntities(
                        p,
                        p.getBoundingBox().inflate(64),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
            p.teleportTo(bx, by, bz);
            p.setYRot(0);
            var level = (net.minecraft.server.level.ServerLevel) p.level();
            swordsman = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
            swordsman.setPos(bx - 1.5, by, bz + 4);
            swordsman.setYRot(180);
            swordsman.setYHeadRot(180);
            swordsman.setYBodyRot(180);
            swordsman.setNoAi(true);
            swordsman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.RAIKIRI));
            level.addFreshEntity(swordsman);
            var dummy = Content.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
            dummy.setPos(bx + 1.5, by, bz + 4);
            dummy.setYRot(180);
            dummy.setYHeadRot(180);
            dummy.setYBodyRot(180);
            level.addFreshEntity(dummy);
            post = dummy;
            dummy.hurtServer(level, level.damageSources().playerAttack(p), 23.4f);
            level.setBlock(
                BlockPos.containing(bx, by, bz + 2.5), Content.WHETSTONE.defaultBlockState(), 3);
            p.getInventory().clearContent();
            Item[] bar = {
              Oborokage.TACHI,
              Content.WHETSTONE_ITEM,
              Content.INTENT_CRYSTAL,
              Content.STONES.get(Inscription.ZANSHIN),
              Content.STONES.get(Inscription.CRIMSON),
              Content.RELICS.get(BladeStyle.RAIKIRI),
              Content.TRAINING_DUMMY_ITEM,
              Content.SWORDSMAN_SPAWN_EGG,
              Oborokage.FROSTMOON
            };
            for (int i = 0; i < bar.length; i++) p.getInventory().setItem(i, new ItemStack(bar[i]));
            ItemStack ancient = new ItemStack(Oborokage.TACHI);
            ancient.set(Components.INSCRIPTION, "ancient");
            ancient.set(
                net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                    List.of(), List.of(), List.of("ancient"), List.of()));
            ancient.set(Components.MOON_PHASE, 5);
            p.getInventory().setItem(0, ancient);
            p.getInventory().setSelectedSlot(0);
          });
      c.player.setYRot(0);
      c.player.setXRot(10);
      c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }
    if (ticks == 70) shot(c, "swordsman-dummy-whetstone-front");
    if (ticks == 72) c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    if (ticks == 80) shot(c, "ancient-tachi-moon-hud-back");
    if (ticks == 82) {
      c.options.setCameraType(CameraType.FIRST_PERSON);
      c.player.setXRot(0);
    }
    if (ticks == 90) shot(c, "first-person-ancient-hotbar");
    if (ticks == 92) oborokage.client.compat.ClientCompat.openScreen(c, new CodexScreen(0));
    if (ticks == 98) shot(c, "codex-general");
    if (ticks == 100) oborokage.client.compat.ClientCompat.openScreen(c, new CodexScreen(2));
    if (ticks == 106) shot(c, "codex-moon");
    if (ticks == 108) {
      oborokage.client.compat.ClientCompat.openScreen(c, null);
      onServer(c, p -> p.getFoodData().setFoodLevel(4));
    }
    if (ticks == 116) shot(c, "fatigue-hud");
    if (ticks == 118)
      onServer(
          c,
          p -> {
            p.getFoodData().setFoodLevel(20);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.RAIKIRI));
            p.teleportTo(bx, by + 5, bz);
          });
    if (ticks == 120) c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    if (ticks == 123) DualClient.interceptAttack();
    if (ticks == 125) shot(c, "plunge-falling");
    for (int at : new int[] {130, 134}) if (ticks == at) shot(c, "plunge-landing-" + (at - 123));
    if (ticks == 150)
      onServer(
          c,
          p -> {
            p.teleportTo(bx, by, bz);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.WAKIZASHI));
            BladeArts.bloom(p, p.position());
          });
    if (ticks == 166) shot(c, "petal-field");
    if (ticks == 175)
      onServer(
          c,
          p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.FROSTMOON));
            BladeArts.iceFloor(p, p.position(), 5);
          });
    if (ticks == 186) shot(c, "ice-floor");
    if (ticks == 240)
      onServer(
          c,
          p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.VOIDSTRING));
            var target =
                p
                    .level()
                    .getEntitiesOfClass(Swordsman.class, p.getBoundingBox().inflate(16))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (target != null) {
              target.setPos(bx - 4, by, bz + 9);
              BladeArts.tether(p, target);
            }
          });
    if (ticks == 250) shot(c, "void-string");
    if (ticks == 252) DualClient.request(3);
    if (ticks == 256) shot(c, "string-dash");
    // The post: its readout up close, then a blow from its side and one from its front.
    if (ticks == 272)
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            BladeArts.untether(p);
            if (swordsman != null) swordsman.discard();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.teleportTo(post.getX(), by, post.getZ() - 2.6);
            striker = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
            striker.setNoAi(true);
            striker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.TACHI));
            face(striker, post.getX() + 2, post.getZ(), 90);
            level.addFreshEntity(striker);
          });
    if (ticks == 274) {
      c.options.setCameraType(CameraType.FIRST_PERSON);
      c.player.setYRot(0);
      c.player.setXRot(8);
      onServer(
          c,
          p ->
              post.hurtServer(
                  (ServerLevel) p.level(), p.level().damageSources().playerAttack(p), 7.5f));
    }
    if (ticks == 290) {
      var seen = clientPost(c);
      String text = seen == null ? "missing" : seen.readout().getString();
      labels.add(
          (seen != null && !text.isEmpty() ? "PASS" : "FAIL")
              + " the post reads out a blow on the client: "
              + text);
      shot(c, "post-front-readout");
    }
    if (ticks == 292) strike(c);
    if (ticks == 296) lean(c, -90, "its east side");
    if (ticks == 297) shot(c, "post-struck-from-east-leans-west");
    if (ticks == 300)
      onServer(
          c,
          p -> {
            p.teleportTo(post.getX() - 2.6, by, post.getZ());
            face(striker, post.getX(), post.getZ() - 2, 0);
          });
    if (ticks == 302) {
      c.player.setYRot(-90);
      c.player.setXRot(8);
    }
    if (ticks == 306) strike(c);
    if (ticks == 310) lean(c, 180, "its front");
    if (ticks == 311) shot(c, "post-struck-from-front-side-view-leans-south");
    if (ticks == 320) {
      labels.add("PASS v1 showcase captured " + labels.size() + " shots");
      finish(c);
    }
  }

  private void face(Swordsman e, double x, double z, float yaw) {
    e.setPos(x, by, z);
    e.setYRot(yaw);
    e.setYHeadRot(yaw);
    e.setYBodyRot(yaw);
  }

  /** The striker hits the post on the server; the client hears the blow through its event. */
  private void strike(Minecraft c) {
    onServer(
        c,
        p ->
            post.hurtServer(
                (ServerLevel) p.level(), p.level().damageSources().mobAttack(striker), 6));
  }

  private TrainingDummy clientPost(Minecraft c) {
    return post != null && c.level.getEntity(post.getId()) instanceof TrainingDummy d ? d : null;
  }

  /**
   * Checks the client knows where the blow came from, then holds the post near its deepest lean.
   */
  private void lean(Minecraft c, float yaw, String side) {
    var seen = clientPost(c);
    boolean ok = seen != null && Math.abs(Mth.wrapDegrees(seen.blowYaw - yaw)) < 5;
    labels.add(
        (ok ? "PASS" : "FAIL")
            + " the post knows the blow came from "
            + side
            + ": blowYaw="
            + (seen == null ? "missing" : seen.blowYaw));
    if (seen != null) seen.hurtTime = 9;
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

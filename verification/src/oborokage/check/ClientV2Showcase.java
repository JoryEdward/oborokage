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
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import oborokage.*;
import oborokage.client.*;

/**
 * Visual review of the 1.0.0-beta play-test round: the dojo from outside, its yard and hall (the
 * master and its boss bar), the swordsman, the 樱下鬼 and the 落英剑主 side by side, the kazuuchi in hand
 * and mid-cut, the new items, the master's flash-step cut on a training post, and the codex's world
 * page.
 */
public final class ClientV2Showcase implements ClientModInitializer {
  private static final BlockPos DOJO = new BlockPos(-8, 99, 22);
  private final List<String> labels = new ArrayList<>();
  private int ticks;
  private boolean done;
  private PetalLord lord;
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

  /** Puts the camera at a world position looking along yaw/pitch, hovering in creative flight. */
  private void view(Minecraft c, double x, double y, double z, float yaw, float pitch) {
    onServer(c, p -> p.teleportTo(x, y, z));
    c.player.getAbilities().flying = true;
    c.player.setYRot(yaw);
    c.player.setXRot(pitch);
  }

  private static <T extends Mob> T place(
      ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw) {
    T mob = type.create(level, EntitySpawnReason.COMMAND);
    mob.finalizeSpawn(
        level,
        level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
        EntitySpawnReason.COMMAND,
        null);
    mob.setPos(x, y, z);
    mob.setYRot(yaw);
    mob.setYHeadRot(yaw);
    mob.setYBodyRot(yaw);
    mob.setNoAi(true);
    level.addFreshEntity(mob);
    return mob;
  }

  private void tick(Minecraft c) throws Exception {
    ticks++;
    if (ticks < 40) return;
    if (ticks == 40) {
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            var area =
                new net.minecraft.world.phys.AABB(DOJO).expandTowards(17, 12, 17).inflate(12);
            for (Entity e :
                level.getEntities(
                    p, area, e -> !(e instanceof net.minecraft.world.entity.player.Player)))
              e.discard();
            var template =
                level.getServer().getStructureTemplateManager().get(Oborokage.id("dojo/dojo"));
            var settings =
                new net.minecraft.world.level.levelgen.structure.templatesystem
                        .StructurePlaceSettings()
                    .setFinalizeEntities(true);
            template.get().placeInWorld(level, DOJO, DOJO, settings, level.getRandom(), 2);
            for (var e : level.getEntitiesOfClass(Mob.class, area)) e.setNoAi(true);
            p.getInventory().clearContent();
            Item[] bar = {
              Oborokage.KAZUUCHI,
              Content.BLADE_SOUL,
              Content.SWORDSMAN_SPAWN_EGG,
              Content.BLOSSOM_ONI_SPAWN_EGG,
              Content.PETAL_LORD_SPAWN_EGG,
              Oborokage.TACHI,
              Oborokage.RAIKIRI,
              Oborokage.FROSTMOON,
              Oborokage.VOIDSTRING
            };
            for (int i = 0; i < bar.length; i++) p.getInventory().setItem(i, new ItemStack(bar[i]));
            p.getInventory().setSelectedSlot(0);
          });
      c.options.setCameraType(CameraType.FIRST_PERSON);
    }
    if (ticks == 45) view(c, .5, 108, 13, 0, 22);
    if (ticks == 70) shot(c, "dojo-from-outside");
    if (ticks == 72) view(c, .5, 100.2, 23.5, 0, 4);
    if (ticks == 95) shot(c, "dojo-yard");
    if (ticks == 97) {
      // The swordsman and the 樱下鬼 beside the master, in the hall where no sun reaches.
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            place(level, Content.SWORDSMAN, -2.5, 101, 34.5, 180);
            place(level, Content.BLOSSOM_ONI, 3.5, 101, 34.5, 180);
          });
      view(c, .5, 101.2, 30.6, 0, 6);
    }
    if (ticks == 125) shot(c, "hall-swordsman-lord-oni-bossbar");
    if (ticks == 127) {
      view(c, .5, 100, 6, 0, 10);
      c.player.getAbilities().flying = false;
    }
    if (ticks == 140) shot(c, "kazuuchi-first-person-hotbar");
    if (ticks == 142) c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    if (ticks == 150) DualClient.interceptAttack();
    if (ticks == 153) shot(c, "kazuuchi-cut-third-person");
    if (ticks == 170)
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            post = Content.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
            post.setPos(.5, 100, 15);
            post.setYRot(180);
            level.addFreshEntity(post);
            lord = place(level, Content.PETAL_LORD, .5, 100, 7, 0);
            lord.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.RAIKIRI));
            lord.setTarget(post);
            p.teleportTo(-5.5, 101, 11);
          });
    if (ticks == 172) {
      c.options.setCameraType(CameraType.FIRST_PERSON);
      c.player.getAbilities().flying = true;
      c.player.setYRot(-90);
      c.player.setXRot(8);
    }
    if (ticks == 180)
      onServer(
          c,
          p -> {
            try {
              var f = PetalLord.class.getDeclaredField("artCooldown");
              f.setAccessible(true);
              f.setInt(lord, 0);
            } catch (ReflectiveOperationException e) {
              throw new RuntimeException(e);
            }
          });
    if (ticks == 187) shot(c, "lord-gathers-itself");
    if (ticks == 194) shot(c, "lord-flash-step-cut");
    if (ticks == 196) {
      boolean moved = lord != null && lord.getZ() > 12;
      labels.add(
          (moved ? "PASS" : "FAIL")
              + " the master flash-stepped to the post: z="
              + (lord == null ? "none" : String.format(Locale.ROOT, "%.2f", lord.getZ())));
    }
    if (ticks == 200) oborokage.client.compat.ClientCompat.openScreen(c, new CodexScreen(1));
    if (ticks == 206) shot(c, "codex-world");
    // The 樱下鬼 close up: translucent, legless, one-armed, a broken blade in hand.
    if (ticks == 208) {
      oborokage.client.compat.ClientCompat.openScreen(c, null);
      view(c, 3.5, 101.4, 32.2, 0, 8);
    }
    if (ticks == 222) shot(c, "oni-close-front");
    if (ticks == 240) {
      labels.add("PASS v2 showcase captured " + labels.size() + " shots");
      finish(c);
    }
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

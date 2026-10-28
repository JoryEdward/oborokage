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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * Visual review of 1.1.0 on the flat test world's ground: the ruined dojo (outside, its yard, its
 * hall), the 朽英剑主 in meditation by day and standing by night, its 「百鬼」 gathering and the 樱下鬼 it
 * calls up, a 百鬼夜行 with its 鬼将, the five blade tombs side by side, and the figures side by side
 * (swordsman, 樱下鬼, 鬼将, 落英剑主, 朽英剑主).
 */
public final class ClientV3Showcase implements ClientModInitializer {
  private static final int G = -61;
  private static final BlockPos RUIN = new BlockPos(40, G, 40);
  private final List<String> labels = new ArrayList<>();
  private int ticks;
  private boolean done;
  private WitheredLord lord;
  private BlossomOni general;

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

  private void command(Minecraft c, String command) {
    onServer(
        c,
        p ->
            p.level()
                .getServer()
                .getCommands()
                .performPrefixedCommand(
                    p.level().getServer().createCommandSourceStack().withSuppressedOutput(),
                    command));
  }

  /** The camera at a point, looking at another. */
  private void look(Minecraft c, double x, double y, double z, double tx, double ty, double tz) {
    double dx = tx - x, dy = ty - y, dz = tz - z;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    onServer(c, p -> p.teleportTo(x, y, z));
    c.player.getAbilities().flying = true;
    c.player.setYRot(yaw);
    c.player.setXRot(pitch);
  }

  private static <T extends Mob> T place(
      ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw, boolean ai) {
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
    mob.setNoAi(!ai);
    level.addFreshEntity(mob);
    return mob;
  }

  private static void template(ServerLevel level, String id, BlockPos at) {
    level
        .getServer()
        .getStructureTemplateManager()
        .get(Oborokage.id(id))
        .orElseThrow()
        .placeInWorld(
            level,
            at,
            at,
            new StructurePlaceSettings().setFinalizeEntities(true),
            level.getRandom(),
            2);
  }

  private static void clear(ServerLevel level, AABB area) {
    for (Entity e :
        level.getEntities(
            (Entity) null, area, e -> !(e instanceof net.minecraft.world.entity.player.Player)))
      e.discard();
  }

  private void tick(Minecraft c) throws Exception {
    ticks++;
    if (ticks < 40) return;
    double lx = RUIN.getX() + 8.5, ly = G + 2, lz = RUIN.getZ() + 12.5;
    if (ticks == 40) {
      command(c, "time set 1000");
      command(c, "gamerule doDaylightCycle false");
      command(c, "gamerule doMobSpawning false");
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            clear(level, new AABB(-120, G - 5, -80, 120, G + 40, 120));
            template(level, "ruined_dojo/ruined_dojo", RUIN);
            lord = level.getEntitiesOfClass(WitheredLord.class, new AABB(RUIN).inflate(20)).get(0);
            String[] tombs = {
              "blade_tomb/tomb",
              "blade_tomb_snow/tomb",
              "blade_tomb_mountain/tomb",
              "blade_tomb_cherry/tomb",
              "blade_tomb_badlands/tomb"
            };
            for (int i = 0; i < tombs.length; i++)
              template(level, tombs[i], new BlockPos(-70 + i * 14, G, -40));
            place(level, Content.SWORDSMAN, -40.5, G + 1, 10.5, 180, false);
            place(level, Content.BLOSSOM_ONI, -38.5, G + 1, 10.5, 180, false);
            BlossomOni elite = place(level, Content.BLOSSOM_ONI, -36, G + 1, 10.5, 180, false);
            elite.makeElite();
            place(level, Content.PETAL_LORD, -33.5, G + 1, 10.5, 180, false);
            place(level, Content.WITHERED_LORD, -31, G + 1, 10.5, 180, false);
          });
      c.options.setCameraType(CameraType.FIRST_PERSON);
    }
    if (ticks == 60) look(c, lx, G + 14, RUIN.getZ() - 14, lx, G + 3, lz - 2);
    if (ticks == 90) shot(c, "ruin-from-outside");
    if (ticks == 92)
      look(
          c, RUIN.getX() + 15, G + 3.5, RUIN.getZ() + 1, RUIN.getX() + 4, G + 1.5, RUIN.getZ() + 7);
    if (ticks == 120) shot(c, "ruin-yard");
    if (ticks == 122) look(c, lx, G + 2.6, lz - 5, lx, G + 1.8, lz);
    if (ticks == 150) shot(c, "withered-lord-meditating-day");
    if (ticks == 152) look(c, -36, G + 2.5, 3, -36, G + 1.5, 10.5);
    if (ticks == 180) shot(c, "figures-side-by-side-day");
    if (ticks == 182) {
      command(c, "time set 18000");
    }
    if (ticks == 230) look(c, lx, G + 2.6, lz - 5, lx, G + 2, lz);
    if (ticks == 255) shot(c, "withered-lord-standing-night");
    if (ticks == 257) look(c, lx - 1, G + 8, lz - 9, lx, G + 9, lz + 30);
    if (ticks == 280) shot(c, "ruin-hall-roof-night");
    if (ticks == 282) {
      // A foe for the 朽英剑主: by night it gathers and calls up the dead.
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            Mob foe = place(level, Content.SWORDSMAN, lx, ly, lz - 4, 0, false);
            foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(1000);
            foe.setHealth(1000);
            lord.setTarget(foe);
            try {
              var f = WitheredLord.class.getDeclaredField("summonCooldown");
              f.setAccessible(true);
              f.setInt(lord, 0);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      look(c, lx + 4, G + 4, lz - 7, lx, G + 1, lz - 1);
    }
    if (ticks == 300) shot(c, "hundred-ghosts-gathering");
    if (ticks == 330) shot(c, "hundred-ghosts-called");
    if (ticks == 332) {
      onServer(
          c,
          p -> {
            var level = (ServerLevel) p.level();
            general = NightParade.start(level, new Vec3(0, G + 1, 80));
          });
    }
    if (ticks == 400 && general != null) {
      Vec3 g = general.position();
      Vec3 side = Vec3.directionFromRotation(0, general.getYRot() + 90).scale(9);
      look(c, g.x + side.x, g.y + 3, g.z + side.z, g.x, g.y + 1, g.z);
    }
    if (ticks == 425) shot(c, "night-parade-column");
    if (ticks == 427 && general != null) {
      Vec3 g = general.position();
      Vec3 front = Vec3.directionFromRotation(0, general.getYRot()).scale(5);
      look(c, g.x + front.x, g.y + 2.2, g.z + front.z, g.x, g.y + 1.6, g.z);
    }
    if (ticks == 450) shot(c, "night-parade-general");
    if (ticks == 452) command(c, "time set 6000");
    for (int i = 0; i < 5; i++) {
      int x = -70 + i * 14;
      if (ticks == 480 + i * 30) look(c, x + 4.5, G + 7, -40 - 6, x + 4.5, G + 1, -40 + 4);
      if (ticks == 505 + i * 30) shot(c, "tomb-" + i);
    }
    if (ticks == 640) finish(c);
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

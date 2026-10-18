package oborokage.check;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.*;
import oborokage.*;

/**
 * Visual review rig. A row of mannequins holds every loadout and is photographed facing the camera,
 * side-on, from behind and from above; then the player is photographed in first person and while
 * charging. Shots are spaced so screenshot names sort in order; showcase-labels.txt lists them.
 */
public final class ClientShowcase implements ClientModInitializer {
  private record Shot(String label, Consumer<Minecraft> setup) {}

  private static final Item[][] LOADOUTS = {
    {Oborokage.TACHI, Oborokage.WAKIZASHI},
    {Oborokage.WAKIZASHI, Items.AIR},
    {Oborokage.RAIKIRI, Oborokage.WAKIZASHI},
    {Oborokage.FROSTMOON, Items.AIR},
    {Oborokage.VOIDSTRING, Items.AIR}
  };

  private final List<Shot> shots = new ArrayList<>();
  private final List<String> labels = new ArrayList<>();
  private final List<Mannequin> row = new ArrayList<>();
  private double baseX, baseY, baseZ;
  private int ticks, index = -1, phase;
  private boolean done, charging;

  public void onInitializeClient() {
    // Camera stations around the diagonal row: {dx, dz from the row centre, camera yaw}.
    double[][] views = {{0, -9, 0}, {9.5, 0, 90}, {0, 9, 180}, {7, -7, 45}};
    String[] names = {"row-front", "row-right-side", "row-back", "row-three-quarter"};
    for (int v = 0; v < views.length; v++) {
      double[] view = views[v];
      shots.add(new Shot(names[v], c -> rowView(c, view[0], view[1], (float) view[2])));
    }
    for (Item[] l : LOADOUTS)
      shots.add(
          new Shot(
              name(l[0]) + (l[1] == Items.AIR ? "" : "+waki") + "-first_person",
              c -> playerView(c, l[0], l[1], CameraType.FIRST_PERSON, false)));
    for (Item blade : new Item[] {Oborokage.TACHI, Oborokage.RAIKIRI, Oborokage.FROSTMOON})
      for (CameraType camera :
          new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT})
        shots.add(
            new Shot(
                name(blade) + "-charging-" + camera.name().toLowerCase(Locale.ROOT),
                c -> playerView(c, blade, Items.AIR, camera, true)));
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

  private static String name(Item item) {
    return item.toString().replace("oborokage:", "");
  }

  private ServerPlayer server(Minecraft c) {
    return c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
  }

  private void onServer(Minecraft c, Consumer<ServerPlayer> work) {
    c.getSingleplayerServer().execute(() -> work.accept(server(c)));
  }

  private void rowView(Minecraft c, double dx, double dz, float yaw) {
    c.options.setCameraType(CameraType.FIRST_PERSON);
    double cx = baseX + .5 + 2 * 2.2, cz = baseZ + .5 - 2 * 2.2;
    onServer(
        c,
        p -> {
          p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
          p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
          p.teleportTo(cx + dx, baseY + .3, cz + dz);
        });
    c.player.setYRot(yaw);
    c.player.setXRot(9);
  }

  private void playerView(Minecraft c, Item main, Item off, CameraType camera, boolean charge) {
    charging = charge;
    c.options.setCameraType(camera);
    onServer(
        c,
        p -> {
          p.teleportTo(baseX + 4.4, baseY, baseZ - 9);
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
          p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(off));
        });
    c.player.setYRot(180);
    c.player.setXRot(camera == CameraType.FIRST_PERSON ? 8 : 4);
  }

  private void tick(Minecraft c) throws Exception {
    ++ticks;
    if (ticks == 20)
      // Clean ground away from the regression area (its platform and drops sit near spawn).
      onServer(
          c,
          p -> {
            int x = (int) Math.floor(p.getX()) + 48, z = (int) Math.floor(p.getZ()) + 48;
            int y =
                p.level()
                    .getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            p.teleportTo(x + .5, y, z + .5);
          });
    if (ticks < 40) return;
    if (ticks == 40) {
      baseX = Math.floor(c.player.getX());
      baseY = c.player.getY();
      baseZ = Math.floor(c.player.getZ());
      onServer(
          c,
          p -> {
            for (int i = 0; i < LOADOUTS.length; i++) {
              var m = EntityTypes.MANNEQUIN.create(p.level(), EntitySpawnReason.COMMAND);
              // A diagonal line never overlaps from the front, the side or behind.
              double x = baseX + .5 + i * 2.2, z = baseZ + .5 - i * 2.2;
              m.setPos(x, baseY, z);
              m.setYRot(180);
              m.setYBodyRot(180);
              m.setYHeadRot(180);
              p.level()
                  .setBlock(
                      net.minecraft.core.BlockPos.containing(x, baseY, z - 2.2),
                      net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),
                      3);
              m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LOADOUTS[i][0]));
              m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(LOADOUTS[i][1]));
              p.level().addFreshEntity(m);
              row.add(m);
            }
          });
      return;
    }
    phase++;
    if (index < 0 || phase == 34) {
      if (charging) {
        c.options.keyUse.setDown(false);
        c.gameMode.releaseUsingItem(c.player);
      }
      charging = false;
      index++;
      phase = 0;
      if (index >= shots.size()) {
        labels.add("PASS showcase captured " + shots.size() + " shots");
        finish(c);
        return;
      }
      shots.get(index).setup.accept(c);
    }
    // Vanilla stops using an item as soon as the use key is up, so hold it for the charge.
    if (charging && phase == 4) {
      c.options.keyUse.setDown(true);
      c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
    }
    if (phase == 30) {
      labels.add(String.format(Locale.ROOT, "%02d %s", index, shots.get(index).label));
      screenshot(c, String.format(Locale.ROOT, "%02d-%s", index, shots.get(index).label));
    }
  }

  private static void screenshot(Minecraft c, String name) throws Exception {
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
          .invoke(null, c.gameDirectory, name + ".png", target, 1, (Consumer<Object>) m -> {});
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

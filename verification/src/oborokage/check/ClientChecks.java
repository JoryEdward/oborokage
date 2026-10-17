package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;
import oborokage.client.OborokageClient;

public class ClientChecks implements ClientModInitializer {
  int tick, wallTick;
  boolean done, sawAnimation;
  volatile String expansionFailure;
  volatile boolean comboOK, guardOK, normalGuardOK, ultimateOK, craftOK;
  volatile ClientProbe practice;
  volatile LivingEntity victim;
  volatile boolean ready, killed, challenge;
  List<String> results = new ArrayList<>();

  void check(boolean yes, String s) {
    if (!yes) throw new AssertionError(s);
    results.add("PASS " + s);
    System.out.println("TACHI_CLIENT PASS " + s);
  }

  void shot(Minecraft c, String name) {
    Screenshot.grab(
        c.gameDirectory,
        name + ".png",
        c.gameRenderer.mainRenderTarget(),
        1,
        msg -> System.out.println("TACHI_SCREEN " + name));
  }

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          if (done) return;
          try {
            run(c);
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            finish(c);
          }
        });
  }

  void run(Minecraft c) throws Exception {
    if (++wallTick > 2400) throw new AssertionError("Client world load timed out");
    if (c.level == null || c.player == null || c.gameMode == null) return;
    tick++;
    if (tick == 30)
      onServer(
          c,
          p ->
              checkServer(
                  p.level().getServer().getAdvancements().get(Oborokage.id("moon_still_hangs"))
                      != null,
                  "legacy advancement remains registered"));
    if (tick == 40) {
      check(c.getSingleplayerServer() != null, "Independent temporary integrated world loaded");
      var id = c.player.getUUID();
      c.getSingleplayerServer()
          .execute(
              () -> {
                var s = c.getSingleplayerServer();
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                ServerLevel l = (ServerLevel) p.level();
                p.setGameMode(GameType.CREATIVE);
                for (int x = 34; x < 48; x++)
                  for (int z = -8; z < 25; z++) {
                    l.getChunkAt(new BlockPos(x, 99, z));
                    l.setBlock(
                        new BlockPos(x, 99, z),
                        ((x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES)
                            .defaultBlockState(),
                        3);
                    for (int y = 100; y < 104; y++)
                      l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                  }
                p.connection.teleport(40.5, 100, .5, 0, 5);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
                p.inventoryMenu.broadcastChanges();
                var z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
                z.setPos(40.5, 100, 7);
                z.setNoAi(true);
                z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
                z.setHealth(1024);
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                l.addFreshEntity(z);
                victim = z;
                var a = s.getAdvancements().get(Oborokage.id("moon_still_hangs"));
                if (a != null) p.getAdvancements().revoke(a, "flawless_iaido");
                for (int distance : new int[] {4, 10}) {
                  var mob = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
                  mob.setPos(40.5, 100, distance);
                  mob.setNoAi(true);
                  l.addFreshEntity(mob);
                }
                ready = true;
              });
    }
    if (tick == 80) {
      check(ready, "Legacy advancement registered and isolated stage prepared");
      c.player.getInventory().setSelectedSlot(0);
      c.player.getInventory().setItem(0, new ItemStack(Oborokage.TACHI));
      c.gameMode.handleCreativeModeItemAdd(new ItemStack(Oborokage.TACHI), 36);
      c.player.setYRot(0);
      c.player.setXRot(5);
    }
    if (tick == 95) shot(c, "01-drawn-blade");
    if (tick == 110) {
      c.options.keyUse.setDown(true);
      c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
    }
    if (tick == 154) {
      check(c.player.isUsingItem(), "Actual client right-click holds charged tachi");
      shot(c, "02-charged-saya");
    }
    if (tick == 160) {
      c.options.keyUse.setDown(false);
      c.gameMode.releaseUsingItem(c.player);
    }
    if (OborokageClient.localAge >= 0) sawAnimation = true;
    if (tick == 167) shot(c, "03-ultimate-cut");
    if (tick == 176) {
      check(
          RenderObservations.iaido > 0,
          "Third-person player skeleton receives authored iaido pose");
      check(
          OborokageClient.cameraActive()
              && c.options.getCameraType() == CameraType.THIRD_PERSON_BACK,
          "Ultimate uses a third-person cinematic camera");
      shot(c, "04-moon-arcs");
    }
    if (tick == 190) shot(c, "04b-finisher");
    if (tick == 208)
      c.getSingleplayerServer()
          .execute(
              () -> {
                var p = c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
                var a =
                    c.getSingleplayerServer()
                        .getAdvancements()
                        .get(Oborokage.id("moon_still_hangs"));
                challenge = a != null && p.getAdvancements().getOrStartProgress(a).isDone();
              });
    if (tick == 213)
      check(
          c.options.getCameraType() == CameraType.FIRST_PERSON,
          "Cinematic restores original first-person camera");
    if (tick == 200) {
      check(sawAnimation, "Server slash payload triggers client ultimate animation");
      c.getSingleplayerServer().execute(() -> killed = victim != null && !victim.isAlive());
    }
    if (tick == 215) {
      check(challenge, "Real three-target iaido awards the creative challenge");
      shot(c, "04c-challenge-toast");
      c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
      check(killed, "Full charge through real use/release packets kills 1024 HP target");
      check(c.player.getZ() > 8, "Server collision-aware dash moved client forward");
    }
    if (tick == 222) shot(c, "05-third-person");
    if (tick == 230) {
      c.options.setCameraType(CameraType.FIRST_PERSON);
      CreativeModeTabs.tryRebuildTabContents(
          c.level.enabledFeatures(), true, c.level.registryAccess());
      var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Oborokage.id("equipment"));
      check(
          tab.getDisplayItems().iterator().next().is(Oborokage.TACHI),
          "Client independent Oborokage tab starts with tachi");
      var screen =
          new CreativeModeInventoryScreen(c.player, c.getConnection().enabledFeatures(), false);
      c.setScreenAndShow(screen);
      ((net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen) screen)
          .setSelectedTab(tab);
    }
    if (tick == 235) {
      var ui =
          (net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen)
              c.gui.screen();
      ui.setSelectedTab(Oborokage.EQUIPMENT);
      check(
          ui.getSelectedTab() == Oborokage.EQUIPMENT
              && ui.getTabsOnPage(ui.getCurrentPage()).contains(Oborokage.EQUIPMENT),
          "Independent tab is selected on its actual visible GUI page");
    }
    if (tick == 240) shot(c, "06-creative-tab");
    if (expansionFailure != null) throw new AssertionError(expansionFailure);
    if (tick >= 270) {
      c.player.setYRot(0);
      c.player.setXRot(5);
    }
    if (tick == 260) {
      check(
          c.getConnection() != null,
          "Client remains connected after rendering attack and inventory");
      c.setScreenAndShow(null);
      onServer(
          c,
          p -> {
            p.connection.teleport(40.5, 100, .5, 0, 5);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
            p.inventoryMenu.broadcastChanges();
            practice = new ClientProbe(p.level());
            practice.setNoAi(true);
            practice.setNoGravity(true);
            practice.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            practice.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
            practice.setHealth(1024);
            practice.setPos(40.5, 100, 3);
            practice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            ((ServerLevel) p.level()).addFreshEntity(practice);
          });
    }
    if (tick == 285) shot(c, "07-dual-first-person");
    if (tick == 295) c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    if (tick == 300 || tick == 305 || tick == 315) {
      c.hitResult =
          net.minecraft.world.phys.BlockHitResult.miss(
              c.player.getEyePosition(), Direction.UP, c.player.blockPosition());
      var attack = Minecraft.class.getDeclaredMethod("startAttack");
      attack.setAccessible(true);
      attack.invoke(c);
    }
    if (tick == 307) shot(c, "08-dual-main-cut");
    if (tick == 325) shot(c, "09-dual-cross-cut");
    if (tick == 348) {
      onServer(
          c,
          p -> {
            comboOK = practice.hits == 4;
          });
    }
    if (tick == 355) {
      check(comboOK, "Actual left-click mixin and C2S packets produce exactly four dual hits");
      check(RenderObservations.dual > 0, "Independent dual arm poses applied");
      c.options.setCameraType(CameraType.FIRST_PERSON);
      onServer(
          c,
          p -> {
            p.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(20);
            practice.setPos(40.5, 100, 2);
          });
    }
    if (tick == 360) KeyMapping.get("key.oborokage.guard").setDown(true);
    if (tick == 362)
      onServer(
          c,
          p -> {
            float health = p.getHealth();
            boolean damaged =
                p.hurtServer(
                    (ServerLevel) p.level(), p.level().damageSources().mobAttack(practice), 6);
            guardOK = p.getHealth() == health && !damaged;
          });
    if (tick == 366) shot(c, "10-perfect-guard");
    if (tick == 368) KeyMapping.get("key.oborokage.guard").setDown(false);
    if (tick == 373) check(guardOK, "Real player front melee perfect guard prevents health damage");
    if (tick == 395) KeyMapping.get("key.oborokage.guard").setDown(true);
    if (tick == 403)
      onServer(
          c,
          p -> {
            float health = p.getHealth();
            oborokage.compat.Compat.resetHurtCooldown(p);
            p.hurtServer((ServerLevel) p.level(), p.level().damageSources().mobAttack(practice), 6);
            normalGuardOK = Math.abs(health - p.getHealth() - 2.1f) < .15;
            System.out.println(
                "GUARD_DIAGNOSTIC before="
                    + health
                    + " after="
                    + p.getHealth()
                    + " armor="
                    + p.getArmorValue()
                    + " snapshot="
                    + DualCombat.snapshot(p)
                    + " invulnerable="
                    + p.getAbilities().invulnerable);
          });
    if (tick == 408) KeyMapping.get("key.oborokage.guard").setDown(false);
    if (tick == 415) {
      check(normalGuardOK, "Real player sustained guard reduces incoming six damage to 2.1");
    }
    if (tick == 420)
      KeyMapping.click(
          oborokage.client.compat.ClientCompat.keyboard()
              .getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_V));
    if (tick == 430)
      check(
          c.player.getZ() < -1.5, "Client keybinding triggers server collision-aware shadow step");
    if (tick == 440) {
      onServer(
          c,
          p -> {
            p.connection.teleport(40.5, 100, .5, 0, 5);
            p.setHealth(20);
          });
    }
    if (tick == 450 || tick == 494 || tick == 538 || tick == 582)
      KeyMapping.get("key.oborokage.guard").setDown(true);
    if (tick == 453 || tick == 497 || tick == 541 || tick == 585)
      onServer(
          c,
          p ->
              p.hurtServer(
                  (ServerLevel) p.level(), p.level().damageSources().mobAttack(practice), 6));
    if (tick == 456 || tick == 500 || tick == 544 || tick == 588)
      KeyMapping.get("key.oborokage.guard").setDown(false);
    if (tick == 600) {
      check(oborokage.client.DualClient.intent == 100, "Server intent state reaches client HUD");
      onServer(c, p -> practice.setPos(40.5, 100, 6.5));
    }
    if (tick == 610)
      KeyMapping.click(
          oborokage.client.compat.ClientCompat.keyboard()
              .getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_J));
    if (tick == 617) shot(c, "11-dual-ultimate-draw");
    if (tick == 624) {
      check(
          OborokageClient.dualUltimate && OborokageClient.cameraActive(),
          "Dual ultimate uses local cinematic camera");
      shot(c, "12-dual-ultimate-twin-arcs");
    }
    if (tick == 635) shot(c, "13-dual-ultimate-finish");
    if (tick == 641) shot(c, "13b-dual-ultimate-after-cut");
    if (tick == 660)
      onServer(
          c,
          p -> {
            ultimateOK = !practice.isAlive() && Feats.has(p, "twin_moon_ultimate");
          });
    if (tick == 670) {
      check(
          ultimateOK,
          "Networked dual ultimate final cut kills high-health hostile and grants feat");
      check(
          c.options.getCameraType() == CameraType.FIRST_PERSON,
          "Dual ultimate restores previous first-person camera");
    }
    if (tick == 690) onServer(c, p -> openCraft(p, false));
    if (tick == 700) shot(c, "14-tachi-overworld-recipe");
    if (tick == 710)
      onServer(
          c,
          p -> {
            var menu = p.containerMenu;
            checkServer(menu.getSlot(0).getItem().is(Oborokage.TACHI), "tachi result slot");
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p);
            checkServer(
                menu.getCarried().is(Oborokage.TACHI) && menu.getCarried().getMaxDamage() == 4096,
                "tachi crafted original properties");
            for (int i = 1; i <= 9; i++)
              checkServer(menu.getSlot(i).getItem().isEmpty(), "tachi material consumed");
            p.closeContainer();
          });
    if (tick == 730) onServer(c, p -> openCraft(p, true));
    if (tick == 740) shot(c, "15-wakizashi-overworld-recipe");
    if (tick == 750)
      onServer(
          c,
          p -> {
            var menu = p.containerMenu;
            checkServer(menu.getSlot(0).getItem().is(Oborokage.WAKIZASHI), "wakizashi result slot");
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, p);
            for (int i = 1; i <= 9; i++)
              checkServer(
                  menu.getSlot(i).getItem().isEmpty(),
                  "wakizashi material consumed by shift craft");
            craftOK = true;
            p.closeContainer();
          });
    if (tick == 760) {
      check(
          craftOK, "Real overworld workbench take and shift craft consume both recipes correctly");
      c.setScreenAndShow(
          new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(
              c.getConnection().getAdvancements()));
    }
    if (tick == 773) shot(c, "16-chronicle-advancements");
    if (tick == 790) {
      check(
          RenderObservations.events > 10, "Action lifecycle and resource status packets received");
    }
    if (tick >= 790) FocusedChecks.tick(this, c, tick - 790);
  }

  void onServer(Minecraft c, java.util.function.Consumer<ServerPlayer> job) {
    var id = c.player.getUUID();
    c.getSingleplayerServer()
        .execute(
            () -> {
              try {
                job.accept(c.getSingleplayerServer().getPlayerList().getPlayer(id));
              } catch (Throwable t) {
                t.printStackTrace();
                expansionFailure = t.toString();
              }
            });
  }

  void checkServer(boolean yes, String msg) {
    if (!yes) throw new AssertionError(msg);
  }

  void openCraft(ServerPlayer p, boolean shortBlade) {
    ServerLevel l = (ServerLevel) p.level();
    BlockPos at = new BlockPos(40, 100, 0);
    l.setBlock(at, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
    p.connection.teleport(40.5, 100, -1.5, 0, 0);
    p.openMenu(
        new net.minecraft.world.SimpleMenuProvider(
            (id, inv, who) ->
                new net.minecraft.world.inventory.CraftingMenu(
                    id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(l, at)),
            net.minecraft.network.chat.Component.literal("胧影 · 主世界铸刀")));
    var menu = p.containerMenu;
    Item[] slots =
        shortBlade
            ? new Item[] {
              null,
              null,
              Items.IRON_INGOT,
              null,
              Items.AMETHYST_SHARD,
              Items.IRON_INGOT,
              null,
              Items.STICK,
              null
            }
            : new Item[] {
              null,
              null,
              Items.DIAMOND,
              null,
              Items.IRON_BLOCK,
              Items.DIAMOND,
              Items.STICK,
              Items.AMETHYST_SHARD,
              null
            };
    for (int i = 0; i < 9; i++)
      menu.getSlot(i + 1).set(slots[i] == null ? ItemStack.EMPTY : new ItemStack(slots[i]));
    menu.broadcastChanges();
  }

  static class ClientProbe extends net.minecraft.world.entity.monster.zombie.Zombie {
    int hits;

    ClientProbe(net.minecraft.world.level.Level l) {
      super(l);
    }

    @Override
    public boolean hurtServer(
        ServerLevel l, net.minecraft.world.damagesource.DamageSource s, float d) {
      if (s.getEntity() instanceof ServerPlayer) hits++;
      return super.hurtServer(l, s, d);
    }
  }

  void finish(Minecraft c) {
    done = true;
    c.options.keyUse.setDown(false);
    try {
      Files.writeString(Path.of("client-results.txt"), String.join("\n", results) + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
    c.disconnectWithSavingScreen();
    c.stop();
  }
}

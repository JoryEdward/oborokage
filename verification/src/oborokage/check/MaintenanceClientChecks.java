package oborokage.check;

import java.util.*;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.*;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import oborokage.*;
import oborokage.client.*;

/** Real client inputs, connected integrated player, native mining and advancement broadcasts. */
public final class MaintenanceClientChecks extends ClientChecks {
  volatile boolean nativeDone, startedJob;
  volatile int remainingLogs;
  ItemStack worn;
  int predictionsAtReject, cancelsBefore;
  boolean sawOff, sawCross;

  public MaintenanceClientChecks() {
    results = Collections.synchronizedList(new ArrayList<>());
  }

  KeyMapping guard() throws Exception {
    var f = DualClient.class.getDeclaredField("guard");
    f.setAccessible(true);
    return (KeyMapping) f.get(null);
  }

  void guardLock(ServerPlayer p, int ticks) throws Exception {
    var state = DualCombat.state(p);
    var f = state.getClass().getDeclaredField("guardRestart");
    f.setAccessible(true);
    f.setLong(state, p.level().getGameTime() + ticks);
  }

  void resetAdvancement(ServerPlayer p, AdvancementHolder a) {
    for (String key : a.value().criteria().keySet()) p.getAdvancements().revoke(a, key);
  }

  void award(ServerPlayer p, AdvancementHolder a) {
    resetAdvancement(p, a);
    for (String key : a.value().criteria().keySet()) p.getAdvancements().award(a, key);
  }

  @Override
  void run(Minecraft c) throws Exception {
    if (++wallTick > 2900) throw new AssertionError("Maintenance client timed out");
    if (c.level == null || c.player == null || c.gameMode == null) return;
    if (expansionFailure != null) throw new AssertionError(expansionFailure);
    if (++tick <= 80) return;
    int age = tick - 81 + Integer.getInteger("oborokage.qa.start", 0) * 100,
        phase = age / 100,
        at = age % 100;
    if (phase >= 15) {
      guard().setDown(false);
      finish(c);
      return;
    }
    c.player.setYRot(0);
    c.player.setXRot(0);
    if (phase <= 3) {
      Item blade =
          phase == 0 || phase == 3
              ? Oborokage.RAIKIRI
              : phase == 1 ? Oborokage.FROSTMOON : Oborokage.VOIDSTRING;
      c.options.keyShift.setDown(phase == 3);
      if (at == 1) {
        c.options.setCameraType(CameraType.FIRST_PERSON);
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, phase == 3, 4, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
              p.inventoryMenu.broadcastChanges();
            });
      }
      if (at == 15) {
        MaintenanceObservations.resetCharge();
        FocusedChecks.use(c, true);
      }
      if (at == 37) {
        check(c.player.isUsingItem(), "Real charge starts: " + blade + " sneaking=" + (phase == 3));
        check(
            OborokageClient.fullChargeTicks(c.player) == (phase == 0 ? 20 : 40),
            "Shared full-charge timing: " + blade + " sneaking=" + (phase == 3));
        check(
            MaintenanceObservations.aura > 20 && MaintenanceObservations.hudFrames > 5,
            "Rendered HUD and live charge aura: " + blade + " sneaking=" + (phase == 3));
        if (phase == 0)
          check(MaintenanceObservations.hudWidth == 98, "Storm HUD full after twenty ticks");
        if (phase == 3) {
          check(
              MaintenanceObservations.hudWidth > 30 && MaintenanceObservations.hudWidth < 70,
              "Sneaking storm HUD is halfway after twenty ticks");
          float modelTicks =
              new UseDuration(false).get(c.player.getUseItem(), c.level, c.player, 0);
          check(
              modelTicks < 15 && modelTicks >= 9,
              "Storm model remains half charged during terrain preparation");
          check(
              MaintenanceObservations.chimes.isEmpty(),
              "No premature full-charge chime at twenty ticks while sneaking");
        }
        shot(c, "maintenance-" + phase + "-charge-20");
      }
      if (at == 58) {
        check(
            MaintenanceObservations.hudWidth == 98,
            "Full charge HUD rendered: " + blade + " sneaking=" + (phase == 3));
        int expected = phase == 0 ? 20 : 40;
        check(
            MaintenanceObservations.chimes.size() == 1
                && MaintenanceObservations.chimes.getFirst() == expected,
            "Native full-charge sound at tick "
                + expected
                + ": "
                + blade
                + " sneaking="
                + (phase == 3)
                + " observed="
                + MaintenanceObservations.chimes);
        check(
            MaintenanceObservations.resonates.equals(List.of(1, phase == 0 ? 10 : 20)),
            "Native preparatory sounds match the charge stages: "
                + blade
                + " sneaking="
                + (phase == 3)
                + " observed="
                + MaintenanceObservations.resonates);
        shot(c, "maintenance-" + phase + "-charge-40");
        MaintenanceObservations.watchingCharge = false;
      }
      if (at == 59) c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
      if (at == 62) FocusedChecks.use(c, false);
      if (at == 66) shot(c, "maintenance-" + phase + "-single-technique");
      if (at == 70) {
        check(
            OborokageClient.sceneAge(c.player.getId()) < 0,
            "New technique has no legacy sheath scene: " + blade + " terrain=" + (phase == 3));
        check(
            MaintenanceObservations.singlePoses > 0
                && Math.abs(MaintenanceObservations.offY) < .01f
                && Math.abs(MaintenanceObservations.offX) < .3f,
            "Single-blade technique leaves free arm relaxed: "
                + blade
                + " terrain="
                + (phase == 3));
      }
      if (at == 80) {
        shot(c, "maintenance-" + phase + "-recovery");
        c.options.setCameraType(CameraType.FIRST_PERSON);
      }
    } else if (phase >= 4 && phase <= 9) {
      int kind = (phase - 4) / 2;
      boolean sneak = phase % 2 == 1;
      c.options.keyShift.setDown(sneak);
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
              p.inventoryMenu.broadcastChanges();
              p.level().setBlock(FocusedChecks.ROOT, Blocks.DIRT.defaultBlockState(), 3);
              var type =
                  kind == 0
                      ? EntityTypes.ZOMBIE
                      : kind == 1 ? EntityTypes.COW : EntityTypes.VILLAGER;
              var mob = type.create(p.level(), EntitySpawnReason.COMMAND);
              mob.setNoAi(true);
              mob.setNoGravity(true);
              mob.setPos(62, 100, 2.5);
              mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
              mob.setHealth(10000);
              mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
              if (kind == 0)
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
              p.level().addFreshEntity(mob);
            });
      // Keep the controlled mining aim tied to the current crouching eye position.
      // Render-frame picking can lag the test's forced teleport/stance by one frame.
      c.options.setCameraType(CameraType.FIRST_PERSON);
      c.hitResult =
          c.level.clip(
              new net.minecraft.world.level.ClipContext(
                  c.player.getEyePosition(),
                  c.player.getEyePosition().add(c.player.getLookAngle().scale(4.5)),
                  net.minecraft.world.level.ClipContext.Block.COLLIDER,
                  net.minecraft.world.level.ClipContext.Fluid.NONE,
                  c.player));
      if (at == 18) {
        System.out.println(
            "MINING_AIM pos="
                + c.player.position()
                + " eye="
                + c.player.getEyePosition()
                + " look="
                + c.player.getLookAngle()
                + " hit="
                + c.hitResult
                + " block="
                + c.level.getBlockState(FocusedChecks.ROOT));
        check(
            c.hitResult instanceof BlockHitResult h && h.getBlockPos().equals(FocusedChecks.ROOT),
            "Mining aim is the block; nearby " + kind + " sneaking=" + sneak);
        MaintenanceObservations.intercepted = MaintenanceObservations.vanillaMining = 0;
        MaintenanceObservations.watchingMining = true;
        var start = Minecraft.class.getDeclaredMethod("startAttack");
        start.setAccessible(true);
        start.invoke(c);
      }
      if (at > 18 && at < 58) {
        var hold = Minecraft.class.getDeclaredMethod("continueAttack", boolean.class);
        hold.setAccessible(true);
        hold.invoke(c, true);
      }
      if (at == 58) {
        System.out.println(
            "MININGQA kind="
                + kind
                + " sneak="
                + sneak
                + " intercepted="
                + MaintenanceObservations.intercepted
                + " native="
                + MaintenanceObservations.vanillaMining);
        c.options.keyAttack.setDown(false);
        c.gameMode.stopDestroyBlock();
        MaintenanceObservations.watchingMining = false;
        if (kind == 0 && !sneak)
          check(
              MaintenanceObservations.intercepted > 0,
              "Block-targeted click becomes dual strike near zombie");
        else
          check(
              MaintenanceObservations.intercepted == 0 && MaintenanceObservations.vanillaMining > 0,
              "Block-targeted click stays native near " + kind + " sneaking=" + sneak);
        onServer(
            c,
            p -> {
              if (kind != 0 || sneak)
                check(
                    p.level().getBlockState(FocusedChecks.ROOT).isAir(),
                    "Real held left-click mines dirt near " + kind + " sneaking=" + sneak);
              else
                System.out.println(
                    "MINING_ZOMBIE standing blockRemaining="
                        + !p.level().getBlockState(FocusedChecks.ROOT).isAir());
            });
        shot(c, "maintenance-mining-" + kind + "-sneak-" + sneak);
      }
    } else if (phase == 10) {
      c.options.keyShift.setDown(false);
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, true, 32, GameType.SURVIVAL);
              worn = new ItemStack(Oborokage.TACHI);
              worn.setDamageValue(4095);
              p.setItemInHand(InteractionHand.MAIN_HAND, worn);
              p.inventoryMenu.broadcastChanges();
              p.startUsingItem(InteractionHand.MAIN_HAND);
              startedJob = TerrainCut.start(p, 1);
              p.stopUsingItem();
            });
      if (at == 30)
        onServer(
            c,
            p -> {
              remainingLogs = FocusedChecks.logs(p.level(), 32);
              check(
                  startedJob && worn.isEmpty(),
                  "Native harvesting breaks a blade with one durability remaining");
              check(
                  remainingLogs == 31,
                  "Broken blade stops immediately within the four-block batch; 31 of 32 logs"
                      + " remain");
            });
    } else if (phase == 11) {
      c.options.keyShift.setDown(true);
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, true, 32, GameType.SURVIVAL);
              var blade = new ItemStack(Oborokage.TACHI);
              blade.setDamageValue(4095);
              p.setItemInHand(InteractionHand.MAIN_HAND, blade);
              p.inventoryMenu.broadcastChanges();
            });
      if (at == 15) FocusedChecks.use(c, true);
      if (at == 58) FocusedChecks.use(c, false);
      if (at == 78)
        onServer(
            c,
            p ->
                check(
                    p.getMainHandItem().isEmpty() && FocusedChecks.logs(p.level(), 32) == 32,
                    "Real charged terrain release pays original eight durability and breaks before"
                        + " harvesting"));
    } else if (phase == 12) {
      c.options.keyShift.setDown(false);
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
              p.inventoryMenu.broadcastChanges();
              try {
                guardLock(p, 50);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
      if (at == 15) guard().setDown(true);
      if (at == 26) {
        check(
            DualClient.scene(c.player.getId()) == null,
            "Server rejection removes initial predicted guard");
        predictionsAtReject = MaintenanceObservations.guardPredictions;
      }
      if (at == 43)
        check(
            DualClient.scene(c.player.getId()) == null
                && MaintenanceObservations.guardPredictions == predictionsAtReject,
            "Holding rejected guard does not flash again on heartbeat");
      if (at == 65) {
        var s = DualClient.scene(c.player.getId());
        check(
            s != null && !s.predicted && s.event.move() == Move.GUARD,
            "Server heartbeat starts real guard after cooldown without a new press");
      }
      if (at == 70) guard().setDown(false);
      if (at == 82) {
        int before = MaintenanceObservations.guardPredictions;
        guard().setDown(true);
        // Input is consumed by DualClient on the next END_CLIENT_TICK.
        predictionsAtReject = before;
      }
      if (at == 84)
        check(
            MaintenanceObservations.guardPredictions > predictionsAtReject,
            "A genuine new guard press enables immediate prediction again");
      if (at == 90) guard().setDown(false);
    } else if (phase == 13) {
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
              p.inventoryMenu.broadcastChanges();
              DualCombat.state(p);
            });
      if (at == 12) {
        cancelsBefore = MaintenanceObservations.cancels;
        onServer(c, DualCombat::cancel);
      }
      if (at == 22)
        check(
            MaintenanceObservations.cancels == cancelsBefore,
            "Idle cancel sends no CANCEL event to clients");
      if (at == 30 || at == 35 || at == 46) DualClient.request(0);
      var s = DualClient.scene(c.player.getId());
      if (s != null) {
        sawOff |= s.event.move() == Move.OFF;
        sawCross |= s.event.move() == Move.CROSS;
      }
      if (at == 75)
        check(
            sawOff && sawCross,
            "Combo buffering and prediction reach offhand and cross cuts after idle cancel");
    } else if (phase == 14) {
      if (at == 1)
        onServer(
            c,
            p -> {
              FocusedChecks.setup(p, false, 0, GameType.SURVIVAL);
              p.getInventory().clearContent();
              p.inventoryMenu.broadcastChanges();
              ServerConfig.get().announceAdvancements = false;
            });
      if (at == 10) {
        MaintenanceObservations.serverBroadcasts = MaintenanceObservations.clientBroadcasts = 0;
        MaintenanceObservations.watchingChat = true;
        onServer(
            c,
            p ->
                award(
                    p,
                    p.level().getServer().getAdvancements().get(Oborokage.id("borrowed_blade"))));
      }
      if (at == 27) {
        check(
            MaintenanceObservations.serverBroadcasts == 0
                && MaintenanceObservations.clientBroadcasts == 0,
            "Default actual Oborokage advancement award produces no server or client chat"
                + " broadcast");
        onServer(
            c,
            p ->
                check(
                    Feats.has(p, "borrowed_blade"),
                    "Muted native advancement still completes progress"));
        shot(c, "maintenance-advancement-muted");
      }
      if (at == 35)
        onServer(
            c,
            p -> {
              ServerConfig.get().announceAdvancements = true;
              award(p, p.level().getServer().getAdvancements().get(Oborokage.id("borrowed_blade")));
            });
      if (at == 52) {
        check(
            MaintenanceObservations.serverBroadcasts == 1
                && MaintenanceObservations.clientBroadcasts == 1,
            "announceAdvancements=true restores one real server and received client broadcast");
        shot(c, "maintenance-advancement-announced");
      }
      if (at == 60)
        onServer(
            c,
            p -> {
              ServerConfig.get().announceAdvancements = false;
              award(
                  p,
                  p.level()
                      .getServer()
                      .getAdvancements()
                      .get(
                          net.minecraft.resources.Identifier.withDefaultNamespace(
                              "story/mine_stone")));
            });
      if (at == 78) {
        check(
            MaintenanceObservations.serverBroadcasts == 2
                && MaintenanceObservations.clientBroadcasts == 2,
            "Vanilla advancement broadcasts remain unaffected with mod announcements disabled");
        MaintenanceObservations.watchingChat = false;
      }
    }
  }
}

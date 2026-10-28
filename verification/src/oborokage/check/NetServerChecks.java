package oborokage.check;

import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

public final class NetServerChecks implements ModInitializer {
  private boolean two, kill;

  /** 1.1.0: a master between the two players (its health counts them) and a parade near one. */
  private oborokage.PetalLord lord;

  private int lordAge;
  private LivingEntity victim, counterTarget;
  private boolean counterRecorded;

  private void record(String text) {
    System.out.println("NETQA " + text);
    try {
      Files.writeString(
          Path.of("network-server-results.txt"),
          text + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void onInitialize() {
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
        .register(NetSignal.TYPE, NetSignal.CODEC);
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
        NetSignal.TYPE,
        (signal, context) ->
            context
                .server()
                .execute(
                    () -> {
                      if (signal.phase() == 2) {
                        party(context.server());
                        return;
                      }
                      var p = context.player();
                      var mob = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
                      mob.setNoAi(true);
                      mob.setNoGravity(true);
                      mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
                      mob.setHealth(1024);
                      mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                      mob.setPos(
                          p.position()
                              .add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2)));
                      p.level().addFreshEntity(mob);
                      counterTarget = mob;
                      oborokage.compat.Compat.resetHurtCooldown(p);
                      float health = p.getHealth();
                      p.hurtServer(p.level(), p.level().damageSources().mobAttack(mob), 6);
                      record(
                          p.getHealth() == health
                              ? "PASS Delayed native attack is precisely guarded before counter"
                              : "FAIL Native guard did not protect counter setup");
                    }));
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          var p = handler.player;
          var l = (ServerLevel) p.level();
          for (int x = -5; x < 14; x++)
            for (int z = -6; z < 20; z++) {
              l.getChunkAt(new BlockPos(x, 79, z));
              l.setBlock(new BlockPos(x, 79, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
            }
          boolean actor = p.getName().getString().equals("BladeActor");
          p.setGameMode(actor ? GameType.SURVIVAL : GameType.CREATIVE);
          p.connection.teleport(actor ? .5 : 8.5, 80, actor ? .5 : 4.5, actor ? 0 : 110, 5);
          if (actor) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
            p.inventoryMenu.broadcastChanges();
            for (int i = 0; i < 10; i++) {
              var mob = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
              mob.setNoAi(true);
              mob.setNoGravity(true);
              mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
              mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
              mob.setHealth(400);
              mob.setPos(.1 + (i % 3) * .4, 80, 2 + (i / 3) * .28);
              l.addFreshEntity(mob);
            }
            var mob = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
            mob.setHealth(1024);
            mob.setPos(.5, 80, 6.5);
            l.addFreshEntity(mob);
            victim = mob;
          }
        });
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (!two && s.getPlayerList().getPlayers().size() == 2) {
            two = true;
            record("PASS Two independent clients connected to local dedicated server");
          }
          if (counterTarget != null && counterTarget.getHealth() < 1024 && !counterRecorded) {
            counterRecorded = true;
            record("PASS Networked counter damages the actual parried source");
          }
          if (victim != null && !victim.isAlive() && !kill) {
            kill = true;
            record("PASS Networked dual ultimate executes 1024 HP hostile");
          }
          if (lord != null) {
            lordAge++;
            if (lordAge % 10 == 0)
              lord.hurtServer((ServerLevel) lord.level(), lord.damageSources().generic(), 1);
            if (lordAge == 35) {
              var l = (ServerLevel) lord.level();
              var oni =
                  l.getEntitiesOfClass(
                      oborokage.BlossomOni.class, lord.getBoundingBox().inflate(128, 256, 128));
              record(
                  "NOTE parade after 35 ticks: "
                      + oni.size()
                      + " alive, dark "
                      + l.isDarkOutside()
                      + ", bright "
                      + l.isBrightOutside()
                      + (oni.isEmpty() ? "" : " at " + oni.get(0).blockPosition().toShortString()));
            }
            if (lordAge == 45) {
              record(
                  (lord.party() == 2 && lord.getMaxHealth() == 300 ? "PASS" : "FAIL")
                      + " A master's health counts the two players fighting it over the network: "
                      + lord.party()
                      + " players, "
                      + lord.getMaxHealth());
              lord.discard();
              lord = null;
              for (ServerPlayer p : s.getPlayerList().getPlayers())
                if (!p.getName().getString().equals("BladeActor")) p.setGameMode(GameType.CREATIVE);
            }
          }
        });
  }

  /** Night; the observer fights too (creative players are not counted); a master held still. */
  private void party(net.minecraft.server.MinecraftServer server) {
    server
        .getCommands()
        .performPrefixedCommand(
            server.createCommandSourceStack().withSuppressedOutput(), "time set 18000");
    ServerLevel level = server.overworld();
    ServerPlayer observer = null;
    for (ServerPlayer p : server.getPlayerList().getPlayers())
      if (!p.getName().getString().equals("BladeActor")) observer = p;
    if (observer == null) return;
    observer.setGameMode(GameType.SURVIVAL);
    // Down to the ground (y -60 here): a mob more than 128 blocks from every player despawns, and
    // the parade walks the ground, far below the platform the duel checks use.
    for (ServerPlayer p : server.getPlayerList().getPlayers())
      p.connection.teleport(
          p == observer ? 8.5 : .5, -60, p == observer ? 4.5 : .5, p == observer ? 110 : 0, 5);
    lord = oborokage.Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
    lord.setPos(4.5, -60, -16.5);
    lord.setNoAi(true);
    try {
      for (String f : new String[] {"artCooldown", "ultCooldown", "finalCooldown"}) {
        var field = oborokage.LordArts.class.getDeclaredField(f);
        field.setAccessible(true);
        field.setInt(lord.arts(), 100000);
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    level.addFreshEntity(lord);
    var leader = oborokage.NightParade.start(level, observer.position());
    record(
        (leader != null ? "PASS" : "FAIL")
            + " A parade comes out near the observer on a full-moon night: "
            + (leader == null ? "-" : leader.blockPosition().toShortString())
            + " observer "
            + observer.blockPosition().toShortString()
            + " dark "
            + level.isDarkOutside());
  }
}

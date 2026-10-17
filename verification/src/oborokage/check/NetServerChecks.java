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
                      p.damageCooldownTime = 0;
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
        });
  }
}

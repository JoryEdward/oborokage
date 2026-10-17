package oborokage.check;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import oborokage.*;
import oborokage.client.*;

/** Actual integrated-server players and native use/release, mining and iframe paths. */
public final class FocusedChecks {
  static volatile boolean reloaded;
  static String savedConfig;
  static volatile boolean initialized,
      partial,
      full,
      protectedTree,
      adventure,
      plane,
      creative,
      iframes,
      arrow,
      predicted,
      ack;
  static volatile boolean deny, collection;
  static volatile int countAtFirst, baseStamina;
  static final BlockPos ROOT = new BlockPos(60, 101, 4);
  static ClientChecks t;

  static void setup(ServerPlayer p, boolean tree, int height, GameType mode) {
    Combat.clear();
    ServerLevel l = p.level();
    p.closeContainer();
    p.setGameMode(mode);
    p.setHealth(20);
    oborokage.compat.Compat.resetHurtCooldown(p);
    for (int x = 55; x <= 66; x++)
      for (int z = -3; z <= 10; z++) {
        l.getChunkAt(new BlockPos(x, 99, z));
        l.setBlock(new BlockPos(x, 99, z), Blocks.DEEPSLATE.defaultBlockState(), 3);
        for (int y = 100; y < 142; y++)
          l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
      }
    for (var e :
        l.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(55, 100, -3, 66, 145, 10),
            e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
    for (var e :
        l.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new AABB(55, 100, -3, 66, 145, 10)))
      e.discard();
    p.connection.teleport(60.5, 100, .5, 0, 0);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    p.inventoryMenu.broadcastChanges();
    if (tree)
      for (int y = 0; y < height; y++)
        l.setBlock(ROOT.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
  }

  static int logs(ServerLevel l, int height) {
    int count = 0;
    for (int y = 0; y < height; y++) if (l.getBlockState(ROOT.above(y)).is(Blocks.OAK_LOG)) count++;
    return count;
  }

  static void use(Minecraft c, boolean down) {
    c.options.keyUse.setDown(down);
    if (down) c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
    else c.gameMode.releaseUsingItem(c.player);
  }

  static void tick(ClientChecks checks, Minecraft c, int age) {
    t = checks;
    int phase = age / 80, at = age % 80;
    if (!initialized) {
      initialized = true;
      PlayerBlockBreakEvents.BEFORE.register(
          (l, p, pos, state, entity) -> !deny || !pos.equals(ROOT));
    }
    if (age == 0) c.setScreenAndShow(null);
    if (phase <= 5) {
      c.player.setYRot(0);
      c.player.setXRot(0);
      c.options.keyShift.setDown(true);
      if (at == 1)
        t.onServer(
            c,
            p -> {
              deny = phase == 2;
              setup(
                  p,
                  phase != 4,
                  phase == 0 ? 8 : 40,
                  phase == 3
                      ? GameType.ADVENTURE
                      : phase == 5 ? GameType.CREATIVE : GameType.SURVIVAL);
              if (phase == 4) {
                for (int x = 58; x <= 62; x++)
                  for (int y = 100; y <= 103; y++)
                    p.level().setBlock(new BlockPos(x, y, 4), Blocks.STONE.defaultBlockState(), 3);
                p.level().setBlock(ROOT.above(), Blocks.CHEST.defaultBlockState(), 3);
                p.level().setBlock(ROOT.offset(1, 0, 0), Blocks.BEDROCK.defaultBlockState(), 3);
              }
            });
      if (at == 10)
        t.onServer(
            c,
            p ->
                System.out.println(
                    "CUTQA before use main="
                        + p.getMainHandItem()
                        + " alive="
                        + p.isAlive()
                        + " spectator="
                        + p.isSpectator()
                        + " loaded="
                        + p.connection.hasClientLoaded()));
      if (at == 12) use(c, true);
      if (at == (phase == 0 ? 20 : 55)) {
        System.out.println(
            "CUTQA client shift="
                + c.player.isShiftKeyDown()
                + " ticks="
                + c.player.getTicksUsingItem());
        t.onServer(
            c,
            p ->
                System.out.println(
                    "CUTQA server shift="
                        + p.isShiftKeyDown()
                        + " item="
                        + p.getUseItem()
                        + " pos="
                        + p.position()
                        + " ray="
                        + p.getLookAngle()));
        use(c, false);
      }
      if (at == 69 && phase == 9) c.options.setCameraType(CameraType.FIRST_PERSON);
      if (at == 70)
        t.onServer(
            c,
            p -> {
              int n = logs(p.level(), phase == 0 ? 8 : 40);
              switch (phase) {
                case 0 -> {
                  partial = n >= 5 && n <= 7;
                  t.checkServer(partial, "partial charge cuts 1-3 logs; remaining=" + n);
                }
                case 1 -> {
                  full = n == 8;
                  t.checkServer(full, "full charge capped at 32 of 40 logs; remaining=" + n);
                  var drops =
                      p.level()
                          .getEntitiesOfClass(
                              net.minecraft.world.entity.item.ItemEntity.class,
                              new AABB(55, 99, -3, 66, 145, 10));
                  int wood =
                      drops.stream()
                          .filter(e -> e.getItem().is(Items.OAK_LOG))
                          .mapToInt(e -> e.getItem().getCount())
                          .sum();
                  t.checkServer(wood == 32, "native survival chopping drops all 32 logs once");
                  t.checkServer(
                      Feats.has(p, "one_breath_forest"),
                      "Sixteen-log harvest awards authored achievement");
                }
                case 2 -> {
                  protectedTree = n == 40;
                  t.checkServer(
                      protectedTree, "BEFORE break veto leaves whole connected trunk intact");
                  deny = false;
                }
                case 3 -> {
                  adventure = n == 40;
                  t.checkServer(adventure, "adventure mode prevents unauthorized terrain break");
                }
                case 4 -> {
                  plane =
                      p.level().getBlockState(ROOT).isAir()
                          && p.level().getBlockState(ROOT.above()).is(Blocks.CHEST)
                          && p.level().getBlockState(ROOT.offset(1, 0, 0)).is(Blocks.BEDROCK);
                  t.checkServer(plane, "plane cut removes stone and preserves chest/bedrock");
                }
                case 5 -> {
                  creative = n == 8;
                  t.checkServer(
                      creative,
                      "explicit terrain cut works in creative without normal sword mining"
                          + " exemption");
                }
              }
            });
      if (at == 72) t.shot(c, "terrain-" + phase);
    } else if (phase == 6) {
      c.options.keyShift.setDown(false);
      if (at == 1)
        t.onServer(
            c,
            p -> {
              deny = false;
              setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
              p.inventoryMenu.broadcastChanges();
              var attacker = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
              attacker.setNoAi(true);
              attacker.setPos(60.5, 100, 2);
              p.level().addFreshEntity(attacker);
              practice = attacker;
              DualCombat.request(p, new ActionRequest(100000, 1));
            });
      if (at == 12)
        t.onServer(
            c,
            p -> {
              p.hurtServer(p.level(), p.level().damageSources().mobAttack(practice), 6);
              baseStamina = DualCombat.state(p).stamina();
              float health = p.getHealth();
              boolean again =
                  p.hurtServer(p.level(), p.level().damageSources().mobAttack(practice), 6);
              iframes =
                  !again && p.getHealth() == health && DualCombat.state(p).stamina() == baseStamina;
              t.checkServer(iframes, "ignored iframe hit does not spend guard stamina");
            });
      if (at == 18)
        t.onServer(
            c,
            p -> {
              p.connection.teleport(60.5, 100, .5, 0, 0);
              p.setDeltaMovement(Vec3.ZERO);
              p.connection.send(
                  new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
            });
      if (at == 22)
        t.onServer(
            c,
            p -> {
              Combat.clear();
              DualCombat.request(p, new ActionRequest(100001, 1));
              var projectile =
                  new net.minecraft.world.entity.projectile.arrow.Arrow(
                      p.level(),
                      p.getEyePosition().x,
                      p.getEyePosition().y,
                      p.getEyePosition().z + .9,
                      new ItemStack(Items.ARROW),
                      new ItemStack(Items.BOW));
              projectile.setOwner(practice);
              projectile.setDeltaMovement(0, 0, -.6);
              p.level().addFreshEntity(projectile);
              incoming = projectile;
            });
      if (at == 26)
        t.onServer(
            c,
            p -> {
              arrow = incoming.getOwner() == p && incoming.getDeltaMovement().z > 0;
              System.out.println(
                  "ARROWQA player="
                      + p.position()
                      + " arrow="
                      + incoming.position()
                      + " velocity="
                      + incoming.getDeltaMovement()
                      + " owner="
                      + incoming.getOwner()
                      + " state="
                      + DualCombat.snapshot(p));
              t.checkServer(arrow, "actual arrow collision mixin reflects projectile");
            });
    } else if (phase == 7) {
      if (at == 1)
        t.onServer(
            c,
            p -> {
              setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
              p.inventoryMenu.broadcastChanges();
            });
      if (at == 14) {
        DualClient.request(0);
        var scene = DualClient.scene(c.player.getId());
        predicted = scene != null && scene.predicted;
        t.check(predicted, "Own swing starts immediately before server acknowledgment");
      }
      if (at == 18) {
        var scene = DualClient.scene(c.player.getId());
        ack = scene != null && !scene.predicted && scene.age > 0;
        t.check(ack, "Server acknowledgment retains animation progress");
      }
    } else if (phase >= 8 && phase <= 10) {
      Item item =
          phase == 8 ? Oborokage.RAIKIRI : phase == 9 ? Oborokage.FROSTMOON : Oborokage.VOIDSTRING;
      if (at == 1)
        t.onServer(
            c,
            p -> {
              setup(p, false, 0, GameType.SURVIVAL);
              p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
              p.inventoryMenu.broadcastChanges();
              var mob = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
              mob.setNoAi(true);
              mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
              mob.setHealth(1024);
              mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
              mob.setPos(60.5, 100, phase == 10 ? 18 : 3);
              p.level().addFreshEntity(mob);
              practice = mob;
              if (phase == 9)
                for (int i = 0; i < 2; i++) {
                  var other = EntityTypes.HUSK.create(p.level(), EntitySpawnReason.COMMAND);
                  other.setNoAi(true);
                  other.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
                  other.setHealth(1024);
                  other.setPos(59 + i * 3, 100, 3);
                  p.level().addFreshEntity(other);
                }
            });
      c.player.setYRot(0);
      c.player.setXRot(0);
      if (at == 12) t.shot(c, "style-" + phase + "-idle");
      if (at == 15) use(c, true);
      if (at == 54) t.shot(c, "style-" + phase + "-charge");
      if (at == 57 && phase == 9) c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      if (at == 58) use(c, false);
      if (at == 61) t.shot(c, "style-" + phase + "-release");
      if (at == 69 && phase == 9) c.options.setCameraType(CameraType.FIRST_PERSON);
      if (at == 70)
        t.onServer(
            c,
            p -> {
              t.checkServer(
                  practice.getHealth() < 1024, "new charged blade technique damages actual mob");
              if (phase == 9)
                t.checkServer(
                    Feats.has(p, "frost_truce"), "Three-target frost technique grants feat");
              if (phase == 10)
                t.checkServer(Feats.has(p, "far_bank"), "Sixteen-block sword energy grants feat");
            });
    } else if (phase == 11 && at == 1) {
      t.onServer(
          c,
          p -> {
            int slot = 0;
            for (Item blade :
                List.of(
                    Oborokage.TACHI,
                    Oborokage.WAKIZASHI,
                    Oborokage.RAIKIRI,
                    Oborokage.FROSTMOON,
                    Oborokage.VOIDSTRING)) p.getInventory().setItem(slot++, new ItemStack(blade));
            p.inventoryMenu.broadcastChanges();
          });
    } else if (phase == 11 && at == 25) {
      t.onServer(c, p -> collection = Feats.has(p, "five_paths"));
    } else if (phase == 11 && at == 35) {
      t.check(collection, "Five-weapon collection grants feat");
      t.check(
          partial && full && protectedTree && adventure && plane && creative,
          "Native charge-scaled harvesting, drops and permissions pass");
      t.check(iframes && arrow, "Native iframe and arrow guard paths pass");
      c.options.keyShift.setDown(false);
    } else if (phase == 12 && at == 1) {
      t.onServer(
          c,
          p -> {
            try {
              var file = java.nio.file.Path.of("config/oborokage-server.properties");
              savedConfig = java.nio.file.Files.readString(file);
              java.nio.file.Files.writeString(file, savedConfig + "\nguardWindowTicks=6\n");
              var server = p.level().getServer();
              server
                  .getCommands()
                  .performPrefixedCommand(server.createCommandSourceStack(), "reload");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    } else if (phase == 12 && at == 70) {
      t.onServer(
          c,
          p -> {
            try {
              reloaded = ServerConfig.get().guardWindowTicks == 6;
              java.nio.file.Files.writeString(
                  java.nio.file.Path.of("config/oborokage-server.properties"), savedConfig);
              ServerConfig.load();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    } else if (phase == 13 && at == 5) {
      t.check(reloaded, "Native /reload applies server configuration without restart");
      t.finish(c);
    }
  }

  static LivingEntity practice;
  static net.minecraft.world.entity.projectile.arrow.Arrow incoming;
}

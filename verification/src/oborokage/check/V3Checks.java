package oborokage.check;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * Third round of 1.0.0-beta (after the second play test): blades cut and draw while gliding; the
 * living and the dead hunt each other and vanilla undead join in; mobs keep to their ground; the
 * 樱下鬼 carries a broken blade, cools off in the shade and speaks with its own voice.
 */
final class V3Checks {
  private static final int X = 34, Z = 4;
  private final ServerChecks t;
  private int step, age;
  private double glideY;
  private Swordsman swordsman, dojoSwordsman;
  private BlossomOni oni, shaded;
  private PetalLord lord;
  private Mob zombie;

  V3Checks(ServerChecks t) {
    this.t = t;
  }

  private <T extends Mob> T spawn(EntityType<T> type, double x, double z) {
    var level = t.level;
    T mob = type.create(level, EntitySpawnReason.COMMAND);
    mob.finalizeSpawn(
        level,
        level.getCurrentDifficultyAt(BlockPos.containing(x, 100, z)),
        EntitySpawnReason.COMMAND,
        null);
    mob.setPos(x, 100, z);
    level.addFreshEntity(mob);
    return mob;
  }

  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    age++;
    if (step == 0) {
      Combat.clear();
      // A stage of its own, away from the other checks.
      for (int x = X - 6; x <= X + 8; x++)
        for (int z = Z - 6; z <= Z + 8; z++) {
          level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
          for (int y = 100; y < 104; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
      // Gliding: the chain cuts, the ultimate waits for the ground, the draw dives along the eyes.
      p.setPos(.5, 110, .5);
      p.setOnGround(false);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      p.startFallFlying();
      t.check(
          DualCombat.chainsReady(p), "A gliding wielder's left click belongs to the blade chain");
      t.act(0);
      t.check(
          DualCombat.state(p).move() != 0 && DualCombat.state(p).move() != Move.PLUNGE,
          "Gliding on elytra, a click cuts (not a plunge, not the vanilla swing)");
      Combat.clear();
      var intent = DualCombat.State.class.getDeclaredField("intent");
      intent.setAccessible(true);
      intent.setInt(DualCombat.state(p), 100);
      t.act(4);
      t.check(!DualCombat.inUltimate(p), "The ultimate waits for the wielder's own feet");
      Combat.clear();
      p.setXRot(40);
      p.setYRot(0);
      glideY = p.getY();
      t.check(LegacyIaido.start(p, .6f), "The iaido can be drawn while gliding");
      // The 樱下鬼, a living swordsman and a vanilla zombie meet.
      swordsman = spawn(Content.SWORDSMAN, X + .5, Z + .5);
      oni = spawn(Content.BLOSSOM_ONI, X + 4.5, Z + .5);
      zombie = spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, X + .5, Z + 5.5);
      dojoSwordsman = spawn(Content.SWORDSMAN, X - 4.5, Z - 4.5);
      dojoSwordsman.setPersistenceRequired();
      lord = spawn(Content.PETAL_LORD, X - 4.5, Z + 6.5);
      lord.setNoAi(true);
      // A roofed corner for the shade check.
      level.setBlock(new BlockPos(X + 7, 102, Z + 7), Blocks.STONE.defaultBlockState(), 3);
      shaded = spawn(Content.BLOSSOM_ONI, X + 7.5, Z + 7.5);
      shaded.setNoAi(true);
      shaded.setRemainingFireTicks(160);
      Item held = oni.getMainHandItem().getItem();
      t.check(
          Content.RELICS.values().contains(held)
              && held != Content.RELICS.get(BladeStyle.WAKIZASHI)
              && !oni.getMainHandItem().is(Oborokage.KAZUUCHI),
          "The 樱下鬼 grips a broken long blade, not a new kazuuchi: " + held);
      step = 1;
      age = 0;
      return false;
    }
    if (step == 1 && age == 10) {
      t.check(
          p.getY() < glideY - 3,
          "Drawn in a glide, the dash follows the eyes downward: fell "
              + String.format(Locale.ROOT, "%.1f", glideY - p.getY()));
      p.stopFallFlying();
      p.setXRot(0);
      t.check(
          shaded.getRemainingFireTicks() <= 20,
          "In the shade a burning 樱下鬼's fire soon goes out: " + shaded.getRemainingFireTicks());
      t.check(
          swordsman.hasHome()
              && swordsman.getHomeRadius() == 12
              && dojoSwordsman.getHomeRadius() == 6
              && lord.getHomeRadius() == 4
              && oni.getHomeRadius() == 12
              && swordsman
                  .getHomePosition()
                  .closerThan(BlockPos.containing(X + .5, 100, Z + .5), 2),
          "Swordsmen keep to their ground: wanderers 12, dojo guards 6, the master 4, the 樱下鬼 12");
      var hurt =
          BlossomOni.class.getDeclaredMethod(
              "getHurtSound", net.minecraft.world.damagesource.DamageSource.class);
      hurt.setAccessible(true);
      Object sound = hurt.invoke(oni, level.damageSources().generic());
      String sounds;
      try (InputStream in = Oborokage.class.getResourceAsStream("/assets/oborokage/sounds.json")) {
        sounds = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      boolean files =
          Oborokage.class.getResource("/assets/oborokage/sounds/blossom_oni/hurt1.ogg") != null
              && Oborokage.class.getResource("/assets/oborokage/sounds/blossom_oni/death1.ogg")
                  != null;
      t.check(
          sound == Content.BLOSSOM_ONI_HURT && sounds.contains("entity.blossom_oni.hurt") && files,
          "The 樱下鬼 has its own voice (not a skeleton's): its sound events and Ogg files ship");
    }
    if (step == 1 && age == 60) {
      t.check(
          swordsman.getTarget() instanceof BlossomOni,
          "A living swordsman hunts the 樱下鬼: " + swordsman.getTarget());
      t.check(
          oni.getTarget() instanceof Swordsman target && !(target instanceof BlossomOni),
          "The 樱下鬼 hunts the living swordsmen: " + oni.getTarget());
      t.check(
          zombie.getTarget() instanceof Swordsman z && !(z instanceof BlossomOni),
          "A vanilla zombie attacks the swordsmen too: " + zombie.getTarget());
      for (var e : List.of(swordsman, oni, zombie, dojoSwordsman, lord, shaded)) e.discard();
      level.setBlock(new BlockPos(X + 7, 102, Z + 7), Blocks.AIR.defaultBlockState(), 3);
      Combat.clear();
      return true;
    }
    return false;
  }
}

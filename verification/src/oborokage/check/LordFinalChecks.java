package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * 1.1.0: below half health each master's blade opens its final form, which no blade can parry: 残月追斩
 * (two flash-steps), 雷域 (lightning on all within six blocks), 冰狱 (an ice field that slows and
 * cuts), 弦缚 (a string drawing the target in, then a void cut); its health grows with the players it
 * fights, half again for each after the first, two and a half times at most.
 */
final class LordFinalChecks {
  private static final int[][] AT = {{2, 24}, {30, 24}, {2, 40}, {30, 40}};
  private static final Item[] BLADES = {
    Oborokage.TACHI, Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING
  };
  private static final String[] NAMES = {"残月追斩", "雷域", "冰狱", "弦缚"};
  private static final float[] LEAST = {30, 45, 20, 22};
  private final ServerChecks t;
  private int age;
  private final PetalLord[] lords = new PetalLord[4];
  private final BlossomOni[] foes = new BlossomOni[4];
  private final double[] start = new double[4];

  LordFinalChecks(ServerChecks t) {
    this.t = t;
  }

  private static void set(Object arts, String name, int v) throws Exception {
    var f = LordArts.class.getDeclaredField(name);
    f.setAccessible(true);
    f.setInt(arts, v);
  }

  private static int get(Object arts, String name) throws Exception {
    var f = LordArts.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.getInt(arts);
  }

  boolean tick() throws Exception {
    var level = t.level;
    age++;
    if (age == 1) {
      for (int i = 0; i < 4; i++) {
        int x = AT[i][0], z = AT[i][1];
        for (int dx = -6; dx <= 6; dx++)
          for (int dz = -4; dz <= 8; dz++) {
            level.setBlock(
                new BlockPos(x + dx, 99, z + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 100; y < 104; y++)
              level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
          }
        PetalLord lord = Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
        lord.setPos(x + .5, 100, z + .5);
        lord.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BLADES[i]));
        level.addFreshEntity(lord);
        lord.setHealth(90);
        BlossomOni foe = Content.BLOSSOM_ONI.create(level, EntitySpawnReason.COMMAND);
        foe.setPos(x + .5, 100, z + 5.5);
        foe.setNoAi(true);
        foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        foe.setHealth(1000);
        level.addFreshEntity(foe);
        lords[i] = lord;
        foes[i] = foe;
      }
      return false;
    }
    if (age == 3) {
      for (int i = 0; i < 4; i++) {
        // Only the final form: the ultimate and the flash-step are held back.
        set(lords[i].arts(), "ultCooldown", 999);
        set(lords[i].arts(), "artCooldown", 999);
        set(lords[i].arts(), "finalCooldown", 0);
        lords[i].setTarget(foes[i]);
        start[i] = foes[i].distanceTo(lords[i]);
      }
    }
    if (age > 3 && age < 140)
      for (int i = 0; i < 4; i++) {
        foes[i].setHealth(Math.max(foes[i].getHealth(), 1));
        if (lords[i].getTarget() == null) lords[i].setTarget(foes[i]);
      }
    if (age == 140) {
      for (int i = 0; i < 4; i++) {
        float lost = 1000 - foes[i].getHealth();
        int cooldown = get(lords[i].arts(), "finalCooldown");
        boolean pulled = i != 3 || foes[i].distanceTo(lords[i]) < start[i] - 1;
        t.check(
            lost >= LEAST[i] && cooldown > 0 && cooldown < 300 && pulled,
            "Below half health "
                + BLADES[i]
                + " opens its final form "
                + NAMES[i]
                + " and it cannot be parried: "
                + String.format(Locale.ROOT, "%.1f", lost)
                + " lost, cooldown "
                + cooldown
                + (i == 3
                    ? String.format(
                        Locale.ROOT,
                        ", drawn in %.1f -> %.1f",
                        start[i],
                        foes[i].distanceTo(lords[i]))
                    : "")
                + (i == 2
                    ? ", slowed "
                        + foes[i].hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)
                    : ""));
      }
      PetalLord party = lords[0];
      party.setTarget(null);
      party.setParty(1);
      party.setHealth(100);
      party.setParty(2);
      float two = party.getMaxHealth(), twoHealth = party.getHealth();
      party.setParty(5);
      float many = party.getMaxHealth();
      party.setParty(1);
      t.check(
          two == 300
              && Math.abs(twoHealth - 150) < .01
              && many == 500
              && party.getMaxHealth() == 200
              && Math.abs(party.getHealth() - 100) < .01,
          "A master's health grows with the players it fights: 2 -> "
              + two
              + " (half-hurt "
              + twoHealth
              + "), 5 -> "
              + many
              + ", 1 -> "
              + party.getMaxHealth());
      for (int i = 0; i < 4; i++) {
        lords[i].discard();
        foes[i].discard();
      }
      return true;
    }
    return false;
  }
}

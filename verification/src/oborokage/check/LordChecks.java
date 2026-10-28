package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * The 落英剑主 fights with its blade's arts: 空弦's 断界 cuts a Warden hard; 胧影's 双月归寂 fells a ring of 樱下鬼
 * and spares its own swordsman; its cut sweeps a second foe.
 */
final class LordChecks {
  private static final int X = 30, Z = -18;
  private final ServerChecks t;
  private int age;
  private PetalLord voidLord, moonLord, cutter;
  private Mob warden, guard, beside, struck;
  private final List<BlossomOni> ring = new ArrayList<>();

  LordChecks(ServerChecks t) {
    this.t = t;
  }

  private <T extends Mob> T spawn(EntityType<T> type, double x, double z) {
    var level = t.level;
    T mob = type.create(level, EntitySpawnReason.COMMAND);
    mob.setPos(x, 100, z);
    mob.setNoAi(true);
    level.addFreshEntity(mob);
    return mob;
  }

  /** A field of the master's arts (cooldowns and windups live in {@link LordArts}). */
  private static Object field(PetalLord lord, String name) throws Exception {
    var f = LordArts.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(lord.arts());
  }

  private static void ready(PetalLord lord) throws Exception {
    var f = LordArts.class.getDeclaredField("ultCooldown");
    f.setAccessible(true);
    f.setInt(lord.arts(), 0);
  }

  boolean tick() throws Exception {
    var level = t.level;
    age++;
    if (age == 1) {
      for (int x = X - 14; x <= X + 14; x++)
        for (int z = Z - 14; z <= Z + 14; z++) {
          level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
          for (int y = 100; y < 104; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
      voidLord = spawn(Content.PETAL_LORD, X - 10.5, Z - 8.5);
      voidLord.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.VOIDSTRING));
      warden = spawn(net.minecraft.world.entity.EntityTypes.WARDEN, X - 10.5, Z + .5);
      voidLord.setTarget(warden);
      moonLord = spawn(Content.PETAL_LORD, X + 6.5, Z + 6.5);
      moonLord.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.TACHI));
      for (int i = 0; i < 3; i++) {
        double a = Math.PI * 2 * i / 3;
        BlossomOni oni =
            spawn(Content.BLOSSOM_ONI, X + 6.5 + Math.cos(a) * 3, Z + 6.5 + Math.sin(a) * 3);
        // Facing the master, so each could parry if its ultimate allowed it.
        oni.setYRot((float) Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a))));
        ring.add(oni);
      }
      guard = spawn(Content.SWORDSMAN, X + 6.5, Z + 10);
      moonLord.setTarget(ring.get(0));
      cutter = spawn(Content.PETAL_LORD, X + 6.5, Z - 8.5);
      cutter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Oborokage.RAIKIRI));
      cutter.setYRot(0);
      struck = spawn(Content.BLOSSOM_ONI, X + 6.5, Z - 7);
      beside = spawn(Content.BLOSSOM_ONI, X + 7.5, Z - 7.3);
      return false;
    }
    if (age == 5) {
      ready(voidLord);
      ready(moonLord);
      cutter.doHurtTarget(level, struck);
    }
    if (age == 60) {
      float lost = warden.getMaxHealth() - warden.getHealth();
      t.check(
          lost >= 40,
          "空弦 落英剑主's 断界 cuts a Warden for real: "
              + String.format(Locale.ROOT, "%.1f", lost)
              + " ult="
              + field(voidLord, "ultCooldown")
              + "/"
              + field(voidLord, "ultWindup")
              + " target="
              + voidLord.getTarget()
              + " attack="
              + voidLord.getAttributeValue(
                  net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
              + " moon ult="
              + field(moonLord, "ultCooldown"));
      long felled = ring.stream().filter(e -> !e.isAlive()).count();
      t.check(
          felled == 3 && guard.getHealth() == guard.getMaxHealth(),
          "胧影 落英剑主's 双月归寂 fells a ring of three 樱下鬼 and spares its swordsman: "
              + felled
              + " felled, guard "
              + guard.getHealth());
      t.check(
          beside.getHealth() < beside.getMaxHealth(),
          "The master's cut sweeps the foe beside its target: " + beside.getHealth());
      for (var e : List.<LivingEntity>of(voidLord, moonLord, cutter, warden, guard, beside, struck))
        e.discard();
      ring.forEach(LivingEntity::discard);
      return true;
    }
    return false;
  }
}

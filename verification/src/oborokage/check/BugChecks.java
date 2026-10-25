package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * 1.0.1 player report 2: the 落英剑主 parries a full-charge draw (the UI calls it 奥义 · 胧月一闪) and
 * cancels the whole blow, and when badly hurt it parried more than half of all blows. Now the draw
 * is never parried and the master parries two in five whatever its health. Both are measured on a
 * master facing the player, below three tenths of its health, with its parry made ready before
 * every blow.
 */
final class BugChecks {
  private static final int X = 10, Z = 40;
  private final ServerChecks t;

  BugChecks(ServerChecks t) {
    this.t = t;
  }

  boolean tick() throws Exception {
    var level = t.level;
    var player = t.player;
    for (int x = X - 3; x <= X + 3; x++)
      for (int z = Z - 3; z <= Z + 3; z++) {
        level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int y = 100; y < 103; y++)
          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    PetalLord lord = Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
    lord.setNoAi(true);
    lord.setPos(X + .5, 100, Z + .5);
    level.addFreshEntity(lord);
    var parry = Swordsman.class.getDeclaredField("parryCooldown");
    parry.setAccessible(true);
    double px = player.getX(), py = player.getY(), pz = player.getZ();
    player.setPos(X + .5, 100, Z - 1.5);
    try {
      // Faces the player (who stands to its north), hurt below three tenths.
      Runnable ready =
          () -> {
            lord.setPos(X + .5, 100, Z + .5);
            lord.setYRot(180);
            player.setPos(X + .5, 100, Z - 1.5);
            lord.setHealth(59);
            oborokage.compat.Compat.resetHurtCooldown(lord);
            try {
              parry.setInt(lord, 0);
            } catch (IllegalAccessException e) {
              throw new RuntimeException(e);
            }
          };
      int drawsParried = 0;
      float least = Float.MAX_VALUE;
      for (int i = 0; i < 20; i++) {
        ready.run();
        LegacyIaido.strikeTarget(level, player, lord, 1);
        float dealt = 59 - lord.getHealth();
        if (dealt <= 0) drawsParried++;
        least = Math.min(least, dealt);
      }
      t.check(
          drawsParried == 0,
          "The master never parries a full-charge draw: "
              + drawsParried
              + "/20 parried, least dealt "
              + String.format(Locale.ROOT, "%.1f", least));
      int parried = 0, blows = 300;
      for (int i = 0; i < blows; i++) {
        ready.run();
        lord.hurtServer(level, level.damageSources().playerAttack(player), 6);
        if (lord.getHealth() >= 59) parried++;
      }
      double rate = parried / (double) blows;
      t.check(
          rate > .30 && rate < .50,
          String.format(
              Locale.ROOT,
              "Badly hurt, the master parries about two blows in five, not more: %d/%d (%.0f%%)",
              parried,
              blows,
              rate * 100));
    } finally {
      lord.discard();
      player.setPos(px, py, pz);
    }
    // /kill takes the master at once: the cap on its blows is for blows.
    PetalLord killed = Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
    killed.setNoAi(true);
    killed.setPos(X + .5, 100, Z + 2.5);
    level.addFreshEntity(killed);
    killed.kill(level);
    t.check(!killed.isAlive(), "/kill kills the master at once: health " + killed.getHealth());
    killed.discard();
    // A raised shield (or a meal) is not a draw being gathered; a charging blade is.
    var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
    var held = player.getMainHandItem().copy();
    player.setItemInHand(
        hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
    player.startUsingItem(hand);
    boolean shield = DuelGoal.drawing(player);
    player.stopUsingItem();
    player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(Oborokage.TACHI));
    player.startUsingItem(hand);
    boolean blade = DuelGoal.drawing(player);
    player.stopUsingItem();
    player.setItemInHand(hand, held);
    t.check(
        !shield && blade,
        "Swordsmen rush a player charging a blade, not one raising a shield: shield "
            + shield
            + ", blade "
            + blade);
    // A tomb nobody has opened, emptied from below (as a hopper does): it wakes all the same, so it
    // can no longer count as a tomb from which nothing was taken.
    BlockPos at = new BlockPos(-25, 100, -25);
    var template =
        t.server.getStructureTemplateManager().get(Oborokage.id("blade_tomb/tomb")).orElseThrow();
    template.placeInWorld(
        level,
        at,
        at,
        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
        level.getRandom(),
        2);
    var box =
        (net.minecraft.world.level.block.entity.ChestBlockEntity)
            level.getBlockEntity(at.offset(4, 1, 2));
    box.unpackLootTable(null);
    int slot = -1;
    for (int i = 0; i < box.getContainerSize() && slot < 0; i++)
      if (!box.getItem(i).isEmpty()) slot = i;
    box.removeItem(slot, 1);
    boolean awake =
        Boolean.TRUE.equals(
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) box)
                .getAttached(TombGuard.AWAKENED));
    var area = new net.minecraft.world.phys.AABB(at).expandTowards(9, 6, 9).inflate(4);
    int risen = level.getEntitiesOfClass(BlossomOni.class, area).size();
    for (var e : level.getEntitiesOfClass(BlossomOni.class, area)) e.discard();
    t.check(
        awake && risen >= 2,
        "A tomb emptied from below before anyone opened it, with nobody near, wakes all the same: "
            + risen
            + " 樱下鬼");
    return true;
  }
}

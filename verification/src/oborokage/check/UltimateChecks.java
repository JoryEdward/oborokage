package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/** 0.5.0 checks: every blade's ultimate, solo use, guard, zanshin, feats and blade power. */
final class UltimateChecks {
  private final ServerChecks t;
  private int step, age;
  private final List<ServerChecks.Probe> probes = new ArrayList<>();
  private double startZ;

  UltimateChecks(ServerChecks t) {
    this.t = t;
  }

  private void setIntent(int value) throws Exception {
    var f = DualCombat.State.class.getDeclaredField("intent");
    f.setAccessible(true);
    f.setInt(DualCombat.state(t.player), value);
  }

  private void solo(Item blade) throws Exception {
    Combat.clear();
    var p = t.player;
    p.setPos(.5, 100, .5);
    p.setYRot(0);
    p.setXRot(0);
    p.removeAllEffects();
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    setIntent(100);
    age = 0;
  }

  private ServerChecks.Probe probe(double x, double z) {
    var e = t.probe(z);
    e.setPos(x + .5, 100, z);
    probes.add(e);
    return e;
  }

  private void clearProbes() {
    probes.forEach(ServerChecks.Probe::discard);
    probes.clear();
  }

  /** Returns true when every ultimate check has run. */
  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    age++;
    if (step == 0) {
      ReviewChecks.attachConnection(t);
      // 胧月 · 百花缭乱: a solo wakizashi, eight cuts all around plus the blossom.
      solo(Oborokage.WAKIZASHI);
      t.act(4);
      t.check(
          DualCombat.state(p).move() == Move.ULT_MOON && DualCombat.state(p).intent() == 0,
          "A drawn wakizashi alone starts its own ultimate and spends 100 intent");
      probe(0, 2.5);
      probe(0, -2.5);
      probe(2.5, .5);
      step = 1;
      return false;
    }
    if (step == 1 && age == 6) {
      t.check(
          !DualCombat.allowDamage(p, level.damageSources().mobAttack(probes.get(0)), 8),
          "Attacks cannot interrupt an ultimate in progress");
    }
    if (step == 1 && age == 50) {
      t.check(
          probes.stream().allMatch(e -> e.hits >= 9),
          "Hundred Flowers strikes all around: " + probes.stream().map(e -> e.hits).toList());
      t.check(p.hasEffect(MobEffects.SPEED), "Zanshin follows a completed ultimate");
      clearProbes();
      // 雷切 · 万雷天降: a blink chain through scattered enemies.
      solo(Oborokage.RAIKIRI);
      probe(0, 6);
      probe(4, 10);
      probe(-4, 13);
      startZ = p.getZ();
      t.act(4);
      step = 2;
      return false;
    }
    if (step == 2 && age == 60) {
      t.check(
          probes.stream().allMatch(e -> e.hits >= 1),
          "Ten Thousand Thunders reaches every enemy in its chain: "
              + probes.stream().map(e -> e.hits).toList());
      t.check(
          p.getZ() - startZ > 8,
          "The thunder chain carries the wielder: dz=" + (p.getZ() - startZ));
      boolean fire = false;
      for (BlockPos pos :
          BlockPos.betweenClosed(new BlockPos(-8, 99, -2), new BlockPos(8, 103, 16)))
        fire |= level.getBlockState(pos).is(Blocks.FIRE);
      t.check(!fire, "Ultimate lightning is visual only and starts no fires");
      clearProbes();
      // 霜天 · 镜月冰河: freeze the ring, then shatter it.
      solo(Oborokage.FROSTMOON);
      probe(0, 3.5);
      probe(6, 6);
      probe(-8, 4);
      t.act(4);
      step = 3;
      return false;
    }
    if (step == 3 && age == 21) {
      t.check(
          probes.stream()
              .allMatch(
                  e ->
                      e.getEffect(MobEffects.SLOWNESS) != null
                          && e.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 6),
          "Mirror Moon freezes everything inside the ring");
    }
    if (step == 3 && age == 62) {
      t.check(
          probes.stream().allMatch(e -> e.hits >= 1 && e.lastDamage > 72),
          "The frozen ring shatters with bonus damage: "
              + probes.stream().map(e -> e.lastDamage).toList());
      clearProbes();
      // 空弦 · 断界: the cut passes walls and reaches the far bank, but not the sides.
      solo(Oborokage.VOIDSTRING);
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 104; y++)
          level.setBlock(new BlockPos(x, y, 12), Blocks.OBSIDIAN.defaultBlockState(), 3);
      probe(0, 30);
      probe(0, 6);
      probe(7, 10);
      t.act(4);
      step = 4;
      return false;
    }
    if (step == 4 && age == 66) {
      t.check(
          probes.get(0).hits >= 1 && probes.get(1).hits >= 1,
          "Severing the Realms cuts through a wall to thirty blocks");
      t.check(probes.get(2).hits == 0, "The severing line spares enemies off to the side");
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 104; y++)
          level.setBlock(new BlockPos(x, y, 12), Blocks.AIR.defaultBlockState(), 3);
      clearProbes();
      // 胧影 · 双月归寂 without a wakizashi still executes.
      solo(Oborokage.TACHI);
      var target = probe(0, 3.5);
      target.setHealth(1024);
      t.act(4);
      step = 5;
      return false;
    }
    if (step == 5 && age == 52) {
      t.check(!probes.get(0).isAlive(), "Twin Moons executes even with the tachi alone");
      t.check(
          Feats.has(p, "all_ultimates"),
          "Using all five ultimates completes the five-part advancement");
      clearProbes();
      // Intent now flows from techniques as well as the dual chain.
      solo(Oborokage.FROSTMOON);
      setIntent(0);
      probe(0, 2.5);
      StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.FROST, .5f);
      t.check(
          DualCombat.state(p).intent() == ServerConfig.get().techniqueIntent,
          "A technique hit earns intent: " + DualCombat.state(p).intent());
      clearProbes();
      // Non-blade items cannot request an ultimate.
      Combat.clear();
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
      setIntent(100);
      t.act(4);
      t.check(DualCombat.state(p).move() == 0, "No ultimate without a blade in hand");
      // Blade power: the four reforged blades hit harder than the tachi; the tachi is unchanged.
      t.check(
          BladeStyle.power(new ItemStack(Oborokage.TACHI)) == 1f
              && BladeStyle.power(new ItemStack(Oborokage.RAIKIRI)) > 1f
              && BladeStyle.power(new ItemStack(Oborokage.WAKIZASHI)) > 1f,
          "Only the four reforged blades gain technique power");
      Combat.clear();
      p.removeAllEffects();
      p.setPos(.5, 100, .5);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
      return true;
    }
    return false;
  }
}

package oborokage.check;

import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.*;
import oborokage.*;

/**
 * 0.6.0 checks: the single-blade chain and each blade's finisher, the chain's hand-over between one
 * and two blades, the raised ultimate damage, and the full iaido's mark-then-sever timing.
 */
final class SoloChecks {
  private final ServerChecks t;
  private int step, age;
  private final List<ServerChecks.Probe> probes = new ArrayList<>();
  private net.minecraft.world.entity.monster.Creeper creeper;

  SoloChecks(ServerChecks t) {
    this.t = t;
  }

  private void single(Item blade) {
    Combat.clear();
    var p = t.player;
    p.setPos(.5, 100, .5);
    p.setYRot(0);
    p.setXRot(0);
    p.removeAllEffects();
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
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

  private float expected(float base) {
    var blade = t.player.getMainHandItem();
    return WeaponDamage.scaled(t.player, blade, base) * BladeStyle.power(blade);
  }

  /** Swings the three single-blade cuts back to back; true on the tick the chain has finished. */
  private boolean chain() {
    if (age == 1 || age == 12 || age == 23) t.act(0);
    return age == 50;
  }

  /** Returns true when every check has run. */
  boolean tick() throws Exception {
    var p = t.player;
    age++;
    if (step == 0) {
      ReviewChecks.attachConnection(t);
      single(Oborokage.TACHI);
      probe(0, 2.6);
      t.act(0);
      t.check(
          DualCombat.state(p).move() == Move.SOLO_1,
          "A single drawn tachi answers the attack key with its own chain");
      step = 1;
      return false;
    }
    if (step == 1 && age == 12) {
      var e = probes.get(0);
      t.check(
          e.hits == 1 && Math.abs(e.lastDamage - expected(16)) < .05,
          "The first single-blade cut lands with chain damage: " + e.lastDamage);
      t.check(DualCombat.state(p).intent() > 0, "Single-blade cuts build intent");
      t.act(0);
      t.check(DualCombat.state(p).move() == Move.SOLO_2, "The chain continues to the rising cut");
    }
    if (step == 1 && age == 23) {
      t.check(probes.get(0).hits == 2, "The rising cut lands");
      t.act(0);
      t.check(DualCombat.state(p).move() == Move.SOLO_3, "The chain closes with the finisher");
    }
    if (step == 1 && age == 50) {
      var e = probes.get(0);
      t.check(
          e.hits == 4 && Math.abs(e.lastDamage - expected(28) * .45f) < .05,
          "胧影's finisher is followed by its shadow cut: hits=" + e.hits + " last=" + e.lastDamage);
      clearProbes();
      // 赤霆: lightning leaps from the struck enemy to one beyond the blade's reach.
      single(Oborokage.RAIKIRI);
      probe(0, 3);
      probe(0, 7.1);
      step = 2;
      return false;
    }
    if (step == 2 && chain()) {
      t.check(
          probes.get(1).hits >= 1,
          "赤霆's finisher arcs to an enemy out of reach: " + probes.get(1).hits);
      clearProbes();
      // 胧月: the short blade's finisher is a full turn; the earlier cuts only face forward.
      single(Oborokage.WAKIZASHI);
      probe(0, 2.6);
      probe(0, -1.8);
      step = 3;
      return false;
    }
    if (step == 3 && chain()) {
      t.check(
          probes.get(1).hits == 1,
          "胧月's finisher turns all the way round: behind=" + probes.get(1).hits);
      t.check(p.hasEffect(MobEffects.SPEED), "Landing 胧月's turn lightens the feet");
      clearProbes();
      // 霜轮: frost bites and freezes through the chain.
      single(Oborokage.FROSTMOON);
      probe(0, 2.6);
      step = 4;
      return false;
    }
    if (step == 4 && chain()) {
      var slow = probes.get(0).getEffect(MobEffects.SLOWNESS);
      t.check(
          slow != null && slow.getAmplifier() >= 1, "霜轮's chain leaves the enemy frozen and slow");
      clearProbes();
      // 空弦: the finisher's line reaches the rank behind.
      single(Oborokage.VOIDSTRING);
      probe(0, 2.6);
      probe(0, 6.5);
      step = 5;
      return false;
    }
    if (step == 5 && chain()) {
      t.check(probes.get(1).hits >= 1, "空弦's finisher sends its line to the rank behind");
      clearProbes();
      // Two blades keep the dual chain; changing hands mid-chain restarts the right chain.
      single(Oborokage.TACHI);
      p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
      t.act(0);
      t.check(DualCombat.state(p).move() == Move.MAIN, "Two blades still start the dual chain");
      DualCombat.cancel(p);
      p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      t.act(0);
      t.check(
          DualCombat.state(p).move() == Move.SOLO_1,
          "Putting the wakizashi away mid-chain restarts the single-blade chain");
      DualCombat.cancel(p);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
      t.act(0);
      t.check(DualCombat.state(p).move() == 0, "No blade chain for an ordinary sword");
      // Raised ultimates.
      t.check(
          Move.of(Move.ULTIMATE).damage() == 85
              && Move.of(Move.ULT_MOON).damage() == 22
              && Move.of(Move.ULT_STORM).damage() == 90
              && Move.of(Move.ULT_FROST).damage() == 95
              && Move.of(Move.ULT_VOID).damage() == 190,
          "Every ultimate except the iaido hits harder than in 0.5.0");
      // 胧月一闪: marked during the dash, standing still, all fall together at the sheath.
      single(Oborokage.TACHI);
      probe(0, 4);
      probe(1.2, 8);
      probe(-1.2, 12);
      Combat.start(p, 1);
      step = 6;
      return false;
    }
    if (step == 6 && age == 26) {
      t.check(
          probes.stream().allMatch(e -> e.isAlive() && e.hits == 0),
          "A full draw only marks: everything it passed still stands until the sheath");
    }
    if (step == 6 && age == LegacyIaido.SHEATH + 3) {
      t.check(
          probes.stream().noneMatch(ServerChecks.Probe::isAlive),
          "At the sheath every marked enemy falls at once");
      t.check(p.getZ() > 12, "The full draw dashes farther than before: z=" + p.getZ());
      clearProbes();
      // 0.6.1 空弦: a cut level with the eyes misses an enemy overhead; looking up finds it.
      single(Oborokage.VOIDSTRING);
      probe(0, 1.8).setPos(.5, 103.5, 1.8);
      t.act(0);
      step = 7;
      return false;
    }
    if (step == 7 && age == 12) {
      t.check(probes.get(0).hits == 0, "空弦's level cut leaves an enemy overhead untouched");
      p.setXRot(-60);
      t.act(0);
    }
    if (step == 7 && age == 24) {
      t.check(
          probes.get(0).hits == 1, "空弦 cuts along the line of sight and reaches an enemy overhead");
      clearProbes();
      // 0.6.1 赤霆: the ultimate's thunder is real for what it strikes; a creeper is charged.
      single(Oborokage.RAIKIRI);
      var f = DualCombat.State.class.getDeclaredField("intent");
      f.setAccessible(true);
      f.setInt(DualCombat.state(p), 100);
      creeper =
          EntityTypes.CREEPER.create(t.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
      creeper.setPos(.5, 100, 6);
      creeper.setNoAi(true);
      creeper
          .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
          .setBaseValue(1024);
      creeper.setHealth(1024);
      t.level.addFreshEntity(creeper);
      t.act(4);
      step = 8;
      return false;
    }
    if (step == 8 && age == 62) {
      t.check(
          creeper.isAlive() && creeper.isPowered(),
          "万雷天降's thunder charges a creeper that survives it: alive="
              + creeper.isAlive()
              + " powered="
              + creeper.isPowered());
      t.check(
          p.getHealth() >= p.getMaxHealth() - .01f,
          "The wielder is never struck by their own thunder");
      creeper.discard();
      // 0.6.2: every blade's plain cut takes the view on the tick it lands. Begin facing +Z, turn
      // to face -X during the wind-up: the cut must land on the side, not straight ahead.
      single(Oborokage.TACHI);
      probe(0, 2.6);
      probe(-2.6, .5);
      t.act(0);
      p.setYRot(90);
      step = 9;
      return false;
    }
    if (step == 9 && age == 12) {
      t.check(
          probes.get(1).hits == 1 && probes.get(0).hits == 0,
          "A cut follows a view turned during its wind-up: side="
              + probes.get(1).hits
              + " front="
              + probes.get(0).hits);
      clearProbes();
      single(Oborokage.FROSTMOON);
      probe(0, 1.8).setPos(.5, 103.5, 1.8);
      t.act(0);
      step = 10;
      return false;
    }
    if (step == 10 && age == 12) {
      t.check(probes.get(0).hits == 0, "A level cut leaves an enemy overhead untouched (霜轮)");
      p.setXRot(-60);
      t.act(0);
    }
    if (step == 10 && age == 24) {
      t.check(probes.get(0).hits == 1, "Looking up, 霜轮's cut reaches the enemy overhead too");
      clearProbes();
      // Riding, gliding and spectating keep the vanilla attack instead of a refused chain.
      single(Oborokage.TACHI);
      var zombie = probe(0, 2.6);
      t.check(DualCombat.ownsAttack(p, zombie), "Standing with a blade, the chain owns the click");
      t.check(!DualCombat.ownsAttack(p, null), "Non-living targets keep the vanilla attack");
      p.startFallFlying();
      t.check(!DualCombat.ownsAttack(p, zombie), "Gliding keeps the vanilla attack");
      p.stopFallFlying();
      p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      t.check(!DualCombat.ownsAttack(p, zombie), "Spectating keeps the vanilla click");
      p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      clearProbes();
      Combat.clear();
      p.removeAllEffects();
      p.setXRot(0);
      p.setYRot(0);
      p.setPos(.5, 100, .5);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
      return true;
    }
    return false;
  }
}

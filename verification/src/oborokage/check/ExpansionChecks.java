package oborokage.check;

import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/** 0.4.0 checks: directional step, evasion, riposte, projectile guard and blade arts. */
final class ExpansionChecks {
  private final ServerChecks t;
  private int step, age;
  private ServerChecks.Probe a, b, c;
  private float expectedRiposte;

  ExpansionChecks(ServerChecks t) {
    this.t = t;
  }

  private Object field(Object owner, String name) throws Exception {
    var f = owner.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(owner);
  }

  private void setLong(Object owner, String name, long value) throws Exception {
    var f = owner.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.setLong(owner, value);
  }

  private void dual(Item main) {
    t.player.setPos(.5, 100, .5);
    t.player.setYRot(0);
    t.player.setXRot(0);
    t.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
    t.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
  }

  private void discard() {
    for (var p : new ServerChecks.Probe[] {a, b, c}) if (p != null) p.discard();
    a = b = c = null;
  }

  /** Returns true once every expansion check has run. */
  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    age++;
    if (step == 0) {
      Combat.clear();
      dual(Oborokage.TACHI);
      p.setLastClientInput(new Input(false, false, true, false, false, false, false));
      t.act(3);
      Vec3 left = (Vec3) field(DualCombat.state(p), "direction");
      Combat.clear();
      p.setLastClientInput(new Input(false, false, false, true, false, false, false));
      t.act(3);
      Vec3 right = (Vec3) field(DualCombat.state(p), "direction");
      t.check(
          left.x > .9 && right.x < -.9,
          "Shadow step follows strafe input: left=" + left + " right=" + right);
      p.setLastClientInput(Input.EMPTY);

      // 见切: an attack in the first frames of a step is slipped and opens a riposte.
      Combat.clear();
      a = t.probe(2);
      t.act(3);
      var s = DualCombat.state(p);
      int intent = s.intent();
      float health = p.getHealth();
      boolean slipped = !DualCombat.allowDamage(p, level.damageSources().mobAttack(a), 6);
      t.check(
          slipped
              && s.intent() == intent + ServerConfig.get().perfectDodgeIntent
              && Feats.has(p, "afterimage")
              && (long) field(s, "riposteUntil") > level.getGameTime(),
          "Perfect step slips an attack, grants intent and opens a riposte");
      t.check(p.getHealth() == health, "Slipped attack deals no damage");
      setLong(s, "start", level.getGameTime() - ServerConfig.get().perfectDodgeTicks - 1);
      t.check(
          DualCombat.allowDamage(p, level.damageSources().mobAttack(a), 6),
          "Late step frames do not evade");
      setLong(s, "start", level.getGameTime());
      expectedRiposte =
          WeaponDamage.scaled(p, p.getOffhandItem(), Move.of(Move.OFF).damage())
              * (float) ServerConfig.get().riposteMultiplier;
      step = 1;
      age = 0;
      return false;
    }
    if (step == 1 && age == 8) {
      p.setPos(.5, 100, .5);
      t.act(0); // the step's follow-up is the offhand cut
      step = 2;
      age = 0;
      return false;
    }
    if (step == 2 && age == 6) {
      var s = DualCombat.state(p);
      t.check(
          a.hits == 1 && Math.abs(a.lastDamage - expectedRiposte) < .01f,
          "Riposte empowers the next cut: damage=" + a.lastDamage + " expected=" + expectedRiposte);
      t.check((long) field(s, "riposteUntil") == 0, "Riposte is spent by the cut that lands");
      discard();

      // Durability: a chain swing that meets nothing leaves the blade untouched.
      Combat.clear();
      dual(Oborokage.TACHI);
      t.act(0);
      step = 3;
      age = 0;
      return false;
    }
    if (step == 3 && age == 12) {
      t.check(p.getMainHandItem().getDamageValue() == 0, "Missed chain swing spends no durability");

      // Guard: frontal projectiles are parried, sustained guard reduces them, rear ones pass.
      Combat.clear();
      a = t.probe(4);
      t.act(1);
      var arrow =
          new net.minecraft.world.entity.projectile.arrow.Arrow(
              level, .5, 101, 1.2, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
      arrow.setOwner(a);
      arrow.setDeltaMovement(0, 0, -3);
      var source = level.damageSources().arrow(arrow, a);
      t.check(
          !DualCombat.allowDamage(p, source, 6), "Precise guard parries a frontal projectile hit");
      setLong(DualCombat.state(p), "guardStart", level.getGameTime() - 20);
      t.check(
          DualCombat.allowDamage(p, source, 6)
              && Math.abs(DualCombat.guardedAmount(p, source, 10) - 3.5f) < .001f,
          "Sustained guard reduces a frontal projectile to 35%");
      arrow.setDeltaMovement(0, 0, 3);
      t.check(
          DualCombat.guardedAmount(p, source, 10) == 10, "Projectiles from behind are not guarded");
      arrow.discard();
      discard();
      Combat.clear();

      // Non-living targets keep the vanilla attack path.
      var crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(level, .5, 100, 2);
      a = t.probe(2);
      var callback = net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker();
      t.check(
          callback.interact(
                      p,
                      level,
                      InteractionHand.MAIN_HAND,
                      crystal,
                      new net.minecraft.world.phys.EntityHitResult(crystal))
                  == InteractionResult.PASS
              && callback.interact(
                      p,
                      level,
                      InteractionHand.MAIN_HAND,
                      a,
                      new net.minecraft.world.phys.EntityHitResult(a))
                  == InteractionResult.FAIL,
          "Drawn blades still break end crystals; living targets stay on the dual chain");
      crystal.discard();
      discard();

      // Blade arts.
      a = t.probe(2);
      BladeArts.mark(p, a);
      t.check(
          a.hasEffect(MobEffects.GLOWING)
              && BladeArts.consumeMark(p, a)
              && !BladeArts.consumeMark(p, a),
          "Void mark glows and is consumed exactly once by its owner");
      t.check(
          !BladeArts.frostHit(p, a) && !BladeArts.frostHit(p, a) && BladeArts.frostHit(p, a),
          "Third frost stack freezes");
      t.check(
          a.getEffect(MobEffects.SLOWNESS) != null
              && a.getEffect(MobEffects.SLOWNESS).getAmplifier() == 3,
          "Freeze applies heavy slowness");
      b = t.probe(2);
      b.setPos(2, 100, 2);
      c = t.probe(2);
      c.setPos(-1, 100, 2);
      var struck = new HashSet<UUID>(Set.of(a.getUUID()));
      int arcs = BladeArts.thunderChain(p, new ItemStack(Oborokage.RAIKIRI), a, 8, struck);
      t.check(arcs == 2 && b.hits == 1 && c.hits == 1, "Thunder chain arcs to two enemies");
      discard();
      a = t.probe(3);
      b = t.probe(6.5);
      int edge =
          BladeArts.voidEdge(
              p, new ItemStack(Oborokage.VOIDSTRING), new Vec3(0, 0, 1), 8, new HashSet<>());
      t.check(edge == 2 && a.hits == 1 && b.hits == 1, "Void edge reaches the second rank");
      discard();
      a = t.probe(2);
      BladeArts.schedulePulse(
          p, new ItemStack(Oborokage.FROSTMOON), p.position().add(0, .9, 0), 4, 6, 2);
      step = 4;
      age = 0;
      return false;
    }
    if (step == 4 && age == 4) {
      t.check(a.hits == 1, "Delayed frost echo strikes after its delay");
      discard();

      // A full-charge storm technique reports its kills for the recall cooldown.
      a = t.probe(2.5);
      a.setHealth(1);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.RAIKIRI));
      var result = StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.STORM, 1);
      t.check(result.fired() && result.kills() == 1, "Storm technique reports its kill");
      discard();

      // Idle state of a player who put the blades away is released.
      Combat.clear();
      dual(Oborokage.TACHI);
      DualCombat.state(p);
      p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      step = 5;
      age = 0;
      return false;
    }
    if (step == 5 && age == 3) {
      var states = DualCombat.class.getDeclaredField("states");
      states.setAccessible(true);
      t.check(
          !((Map<?, ?>) states.get(null)).containsKey(p.getUUID())
              && DualCombat.snapshot(p) == null,
          "Idle unequipped dual state is released");
      Combat.clear();
      dual(Oborokage.TACHI);
      return true;
    }
    return false;
  }
}

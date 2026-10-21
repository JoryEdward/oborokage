package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * 1.0.0-beta checks: hunger costs and fatigue, the aerial plunge, the moon phase, thunder marks,
 * the petal field, the ice floor, the string and string dash, inscriptions, the whetstone, the
 * intent crystal, the swordsman, the training dummy and the blade tomb.
 */
final class V1Checks {
  private final ServerChecks t;
  private int step, age;
  private final List<ServerChecks.Probe> probes = new ArrayList<>();
  private double startY;

  V1Checks(ServerChecks t) {
    this.t = t;
  }

  private void single(Item blade) {
    Combat.clear();
    var p = t.player;
    p.setPos(.5, 100, .5);
    p.setYRot(0);
    p.setXRot(0);
    p.setOnGround(true);
    p.resetFallDistance();
    p.removeAllEffects();
    p.setHealth(p.getMaxHealth());
    p.getFoodData().setFoodLevel(20);
    p.getFoodData().setSaturation(0);
    try {
      // The fake player never ticks its food: earlier checks leave exhaustion at its cap.
      var f = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
      f.setAccessible(true);
      f.setFloat(p.getFoodData(), 0);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    age = 0;
  }

  /** The post's readout arguments: last blow, combo, damage per second; empty when idle. */
  private static Object[] readout(TrainingDummy d) {
    return d.readout().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents c
        ? c.getArgs()
        : new Object[0];
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

  private float exhaustion() throws Exception {
    var f = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
    f.setAccessible(true);
    return f.getFloat(t.player.getFoodData());
  }

  private void intent(int value) throws Exception {
    var f = DualCombat.State.class.getDeclaredField("intent");
    f.setAccessible(true);
    f.setInt(DualCombat.state(t.player), value);
  }

  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    age++;
    if (step == 0) {
      ReviewChecks.attachConnection(t);
      // Hunger: a landed swing costs exhaustion; food at the fatigue level silences the arts.
      single(Oborokage.TACHI);
      probe(0, 2.6);
      float before = exhaustion();
      t.act(0);
      startY = before;
      step = 1;
      return false;
    }
    if (step == 1 && age == 12) {
      t.check(exhaustion() > startY, "A landed blade swing costs hunger: " + exhaustion());
      t.check(
          Moon.phase(p.getMainHandItem()) == 1,
          "A tachi swing that lands on an enemy waxes the moon");
      p.getFoodData().setFoodLevel(6);
      t.check(
          Vigor.fatigued(p) && !BladeArts.enabled(p),
          "At six food the wielder is spent and the blade's arts are silent");
      p.getFoodData().setFoodLevel(7);
      t.check(!Vigor.fatigued(p), "Above six food the arts return");
      clearProbes();
      // The aerial plunge: from twenty blocks up, four of them already fallen, onto a ring of
      // enemies. It cushions its own first twelve blocks of dive and nothing else.
      single(Oborokage.FROSTMOON);
      probe(2, .5);
      probe(-2, .5);
      p.setPos(.5, 120, .5);
      p.setOnGround(false);
      p.fallDistance = 4;
      t.act(0);
      t.check(
          DualCombat.state(p).move() == Move.PLUNGE, "A click in mid-air starts the aerial plunge");
      step = 2;
      return false;
    }
    // The fake player has no client to fall for it: apply the velocity the server sent.
    if (step == 2 && age < 30 && DualCombat.state(p).move() == Move.PLUNGE)
      p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement());
    if (step == 2 && age == 30) {
      t.check(
          probes.stream().allMatch(e -> e.hits >= 1),
          "The plunge's landing strikes the enemies around: "
              + probes.stream().map(e -> e.hits).toList());
      double expected = 4 + 20 - DualCombat.PLUNGE_CUSHION;
      t.check(
          Math.abs(p.fallDistance - expected) <= 2.1,
          "The plunge cushions twelve blocks of its own dive; the fall before it and the rest"
              + " count: fallDistance="
              + p.fallDistance
              + " expected about "
              + expected);
      t.check(
          probes.stream().allMatch(e -> e.getEffect(MobEffects.SLOWNESS) != null),
          "霜轮's landing glazes the ground: the enemies on it are slowed");
      clearProbes();
      // 胧影 · 月相: under the full moon a brief draw is a full draw.
      single(Oborokage.TACHI);
      p.getMainHandItem().set(Components.MOON_PHASE, Moon.FULL);
      var target = probe(0, 4);
      var tachi = (TachiItem) Oborokage.TACHI;
      tachi.releaseUsing(p.getMainHandItem(), level, p, 72000 - 6);
      t.check(Moon.phase(p.getMainHandItem()) == 0, "A full moon is spent by the draw");
      step = 3;
      return false;
    }
    if (step == 3 && age == LegacyIaido.SHEATH + 4) {
      t.check(
          !probes.get(0).isAlive(),
          "A six-tick draw under the full moon cuts like a full charge (marked, then felled)");
      clearProbes();
      // 赤霆 · 积雷: the fifth mark calls the lightning.
      single(Oborokage.RAIKIRI);
      var target = probe(0, 3);
      for (int i = 0; i < 4; i++) BladeArts.thunderMark(p, p.getMainHandItem(), target);
      int hitsBefore = target.hits;
      t.check(BladeArts.thunderStacks(target) == 4, "Four blows leave four thunder marks");
      BladeArts.thunderMark(p, p.getMainHandItem(), target);
      t.check(
          target.hits == hitsBefore + 1 && BladeArts.thunderStacks(target) == 0,
          "The fifth mark calls lightning down on the target and clears the marks");
      clearProbes();
      // 胧月 · 花阵: health returns inside the ring of petals.
      single(Oborokage.WAKIZASHI);
      BladeArts.bloom(p, p.position());
      p.setHealth(10);
      step = 4;
      return false;
    }
    if (step == 4 && age == 45) {
      t.check(p.getHealth() >= 11.9f, "The petal field mends the wielder: health=" + p.getHealth());
      var other = new ServerChecks.Fake(t.server, level);
      other.getAbilities().instabuild = false;
      var rules = level.getGameRules();
      var pvpRule = net.minecraft.world.level.gamerules.GameRules.PVP;
      boolean pvpWas = rules.get(pvpRule), peace = BladeArts.mends(p, other), war;
      try {
        Oborokage.allowPlayerDamage = true;
        rules.set(pvpRule, true, t.server);
        war = BladeArts.mends(p, other);
      } finally {
        Oborokage.allowPlayerDamage = false;
        rules.set(pvpRule, pvpWas, t.server);
      }
      t.check(
          peace && !war,
          "The petals mend another player only while the wielder may not cut them: peace="
              + peace
              + " war="
              + war
              + " spectator="
              + other.isSpectator()
              + " alive="
              + other.isAlive());
      // A wielder who runs down to the fatigue level mends no one.
      BladeArts.bloom(p, p.position());
      p.setHealth(10);
      p.getFoodData().setFoodLevel(6);
      step = 41;
      return false;
    }
    if (step == 41 && age == 70) {
      t.check(p.getHealth() == 10, "A spent wielder's petals mend no one: health=" + p.getHealth());
      // 霜轮 · 冰面.
      single(Oborokage.FROSTMOON);
      var target = probe(2, .5);
      BladeArts.iceFloor(p, p.position(), 4);
      step = 5;
      return false;
    }
    if (step == 5 && age == 8) {
      var slow = probes.get(0).getEffect(MobEffects.SLOWNESS);
      t.check(slow != null && slow.getAmplifier() >= 1, "Enemies on the ice floor are slowed");
      t.check(p.hasEffect(MobEffects.SPEED), "The wielder runs faster across the ice");
      clearProbes();
      // 空弦 · 弦线 and the string dash.
      single(Oborokage.VOIDSTRING);
      probe(0, 10);
      StyleTechniques.release(p, p.getMainHandItem(), StyleBladeItem.Style.VOID, 1);
      t.check(
          BladeArts.tethered(p) == probes.get(0),
          "A full void line that strikes leaves a string to its target");
      p.fallDistance = 7;
      t.act(3);
      t.check(
          DualCombat.state(p).move() == Move.STRING_DASH,
          "With a string drawn, the step key is the string dash even with one blade");
      step = 6;
      return false;
    }
    if (step == 6 && age == 4) {
      t.check(
          p.distanceTo(probes.get(0)) < 2.6 && probes.get(0).hits >= 2,
          "The string dash crosses to the target and cuts it: d="
              + p.distanceTo(probes.get(0))
              + " hits="
              + probes.get(0).hits);
      t.check(BladeArts.tethered(p) == null, "The dash spends the string");
      t.check(
          p.fallDistance == 7,
          "The string dash does not erase a fall under way: fallDistance=" + p.fallDistance);
      clearProbes();
      // Inscriptions at the anvil.
      single(Oborokage.RAIKIRI);
      var menu = new AnvilMenu(0, p.getInventory(), ContainerLevelAccess.NULL);
      menu.getSlot(0).set(new ItemStack(Oborokage.RAIKIRI));
      menu.getSlot(1).set(new ItemStack(Content.STONES.get(Inscription.FOCUS)));
      menu.createResult();
      ItemStack out = menu.getSlot(2).getItem();
      t.check(
          Inscription.has(out, Inscription.FOCUS) && menu.getCost() == Inscription.FOCUS.levels,
          "An anvil cuts an inscription stone into a blade for its level cost");
      ItemStack ancient = new ItemStack(Oborokage.RAIKIRI);
      ancient.set(Components.INSCRIPTION, Inscription.ANCIENT.id);
      menu.getSlot(0).set(ancient);
      menu.getSlot(1).set(new ItemStack(Content.STONES.get(Inscription.ZANSHIN)));
      menu.createResult();
      t.check(
          menu.getSlot(2).getItem().isEmpty(),
          "An ancient blade keeps its 古铭: the anvil offers nothing for a stone");
      // 聚意: intent gains 15% faster.
      p.setItemInHand(InteractionHand.MAIN_HAND, out);
      intent(0);
      var e = probe(0, 3);
      DualCombat.reward(p, e, 20);
      t.check(DualCombat.state(p).intent() == 23, "聚意 gathers intent 15% faster");
      clearProbes();
      // The whetstone: a flint for five minutes of a sharper edge.
      single(Oborokage.TACHI);
      BlockPos at = new BlockPos(3, 100, 3);
      level.setBlock(at, Content.WHETSTONE.defaultBlockState(), 3);
      p.getInventory().add(new ItemStack(Items.FLINT));
      float plain = WeaponDamage.scaled(p, p.getMainHandItem(), 20);
      level
          .getBlockState(at)
          .useItemOn(
              p.getMainHandItem(),
              level,
              p,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false));
      float honed = WeaponDamage.scaled(p, p.getMainHandItem(), 20);
      t.check(
          Hone.honed(p.getMainHandItem(), level.getGameTime())
              && Math.abs(honed / plain - 1.05f) < .001f
              && !p.getInventory().contains(new ItemStack(Items.FLINT)),
          "The whetstone spends a flint and hones the blade 5% sharper");
      var whetstone = level.getBlockState(at);
      ItemStack pick = new ItemStack(Items.WOODEN_PICKAXE);
      var drops =
          net.minecraft.world.level.block.Block.getDrops(whetstone, level, at, null, p, pick);
      t.check(
          whetstone.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
              && pick.isCorrectToolForDrops(whetstone)
              && drops.size() == 1
              && drops.get(0).is(Content.WHETSTONE_ITEM),
          "A pickaxe mines the whetstone and it drops itself: " + drops);
      level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
      // The intent crystal.
      intent(0);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.INTENT_CRYSTAL));
      Content.INTENT_CRYSTAL.use(level, p, InteractionHand.MAIN_HAND);
      t.check(
          DualCombat.state(p).intent() == IntentCrystalItem.INTENT,
          "An intent crystal gathers 25 intent at once");
      // The swordsman parries a frontal blow sooner or later.
      var swordsman = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
      swordsman.setPos(.5, 100, 3);
      swordsman.setYRot(180);
      swordsman.setNoAi(true);
      level.addFreshEntity(swordsman);
      int parries = 0;
      for (int i = 0; i < 24 && swordsman.isAlive(); i++) {
        oborokage.compat.Compat.resetHurtCooldown(swordsman);
        if (!swordsman.hurtServer(level, level.damageSources().playerAttack(p), 1)) parries++;
      }
      t.check(parries >= 1, "The swordsman parries frontal blows: parries=" + parries);
      var duelist = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
      duelist.setPos(.5, 100, 3);
      duelist.setYRot(180);
      duelist.setNoAi(true);
      level.addFreshEntity(duelist);
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      intent(100);
      t.act(4);
      boolean ultimate = DualCombat.inUltimate(p);
      int ultParries = 0;
      for (int i = 0; i < 24; i++) {
        oborokage.compat.Compat.resetHurtCooldown(duelist);
        duelist.setHealth(40);
        if (!duelist.hurtServer(level, level.damageSources().playerAttack(p), 1)) ultParries++;
      }
      t.check(
          ultimate && ultParries == 0,
          "An ultimate cannot be parried: ultimate=" + ultimate + " parries=" + ultParries);
      duelist.discard();
      Combat.clear();
      t.check(
          swordsman
                  .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                  .getBaseValue()
              == 40,
          "The swordsman has 40 health");
      swordsman.discard();
      // The training post never falls, reads out only blows from someone, shrugs off the world and
      // is spared the finisher, so a full iaido reads out one plain cut.
      var dummy = Content.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
      dummy.setPos(.5, 100, 3);
      level.addFreshEntity(dummy);
      t.check(
          !dummy.hurtServer(level, level.damageSources().cactus(), 5) && readout(dummy).length == 0,
          "The post shrugs off the world: a cactus neither hurts it nor shows a readout");
      dummy.hurtServer(level, level.damageSources().playerAttack(p), 1500);
      t.check(
          dummy.isAlive()
              && dummy.getHealth() == dummy.getMaxHealth()
              && "1500.0".equals(readout(dummy)[0]),
          "A training post takes any blow, stays standing and reads out even an overkill: "
              + Arrays.toString(readout(dummy)));
      oborokage.compat.Compat.resetHurtCooldown(dummy);
      LegacyIaido.strikeTarget(level, p, dummy, 1);
      t.check(
          dummy.isAlive()
              && "1000.0".equals(readout(dummy)[0])
              && Integer.valueOf(2).equals(readout(dummy)[1]),
          "A full iaido is no finisher on the post: one plain 1000 cut, combo 2: "
              + Arrays.toString(readout(dummy)));
      t.check(!Feats.enemy(dummy), "The post is no enemy: no intent, no moon");
      dummy.kill(level);
      t.check(dummy.isDeadOrDying(), "Only a sourceless kill (/kill) fells the post");
      dummy.discard();
      // The blade tomb: its template, structure, set and loot table all load.
      var template = t.server.getStructureTemplateManager().get(Oborokage.id("blade_tomb/tomb"));
      t.check(
          template.isPresent() && template.get().getSize().getX() == 9,
          "The blade tomb's template loads at 9 x 6 x 9");
      t.check(
          level
              .registryAccess()
              .lookupOrThrow(Registries.STRUCTURE)
              .containsKey(Oborokage.id("blade_tomb")),
          "The blade tomb structure is registered for worldgen");
      t.check(
          level
              .registryAccess()
              .lookupOrThrow(Registries.STRUCTURE_SET)
              .containsKey(Oborokage.id("blade_tomb")),
          "The blade tomb has a placement set");
      template
          .get()
          .placeInWorld(
              level,
              new BlockPos(20, 100, 20),
              new BlockPos(20, 100, 20),
              new net.minecraft.world.level.levelgen.structure.templatesystem
                  .StructurePlaceSettings(),
              level.getRandom(),
              2);
      t.check(
          level.getBlockState(new BlockPos(24, 101, 22)).is(Blocks.CHEST)
              && net.minecraft.core.registries.BuiltInRegistries.BLOCK
                  .getKey(level.getBlockState(new BlockPos(24, 101, 25)).getBlock())
                  .getPath()
                  .equals("lightning_rod"),
          "A placed tomb has its chest before the stele and the blade in its mound");
      // Restoring a relic gives an ancient blade.
      var recipes = level.getServer().getRecipeManager();
      var restore =
          recipes.byKey(
              net.minecraft.resources.ResourceKey.create(
                  Registries.RECIPE, Oborokage.id("restore_raikiri")));
      t.check(restore.isPresent(), "Relics have a restoring recipe");
      return true;
    }
    return false;
  }
}

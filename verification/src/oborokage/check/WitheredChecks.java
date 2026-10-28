package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import oborokage.*;

/**
 * 1.1.0's 朽英剑主: undead, on the side of the dead and not burnt by the sun; by day it sits in
 * meditation, unmoved, and the blow that wakes it lands and turns it on its attacker; its fade
 * recovers in 200 ticks by day and 100 by night; by night 「百鬼」 calls up three 樱下鬼 bound to it (no
 * player near), none once eight stand around it; those drop nothing and give no experience, and
 * fade when it falls while the others stay; relic 15%; its loot, advancement and lines load.
 */
final class WitheredChecks {
  // Inside the chunks the suite keeps loaded (-2..2), clear of the other checks' ground.
  private static final int X = -22, Z = 32;
  private final ServerChecks t;
  private int age;
  private WitheredLord sitter, caller;
  private Mob target;
  private Vec pos;
  private final List<BlossomOni> plain = new ArrayList<>();

  private record Vec(double x, double y, double z) {}

  WitheredChecks(ServerChecks t) {
    this.t = t;
  }

  private void time(String ticks) {
    t.server
        .getCommands()
        .performPrefixedCommand(t.server.createCommandSourceStack(), "time set " + ticks);
  }

  private <T extends Mob> T spawn(EntityType<T> type, double x, double z, boolean ai) {
    T mob = type.create(t.level, EntitySpawnReason.COMMAND);
    mob.setPos(x, 100, z);
    mob.setNoAi(!ai);
    t.level.addFreshEntity(mob);
    return mob;
  }

  private static Object get(Class<?> c, Object o, String name) throws Exception {
    var f = c.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private static void set(Class<?> c, Object o, String name, Object v) throws Exception {
    var f = c.getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, v);
  }

  private static Object call(Class<?> c, Object o, String name, Class<?>[] types, Object... args)
      throws Exception {
    var m = c.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(o, args);
  }

  /** Fades `lord` from `from` now and returns the cooldown it then waits. */
  private int fadeCooldown(WitheredLord lord, LivingEntity from) throws Exception {
    Object ghost = get(WitheredLord.class, lord, "ghost");
    Class<?> g = ghost.getClass();
    call(g, ghost, "fade", new Class<?>[] {ServerLevel.class, LivingEntity.class}, t.level, from);
    int cooldown = (int) get(g, ghost, "fadeCooldown");
    set(g, ghost, "fadeTicks", 0);
    lord.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
    return cooldown;
  }

  private List<BlossomOni> calledBy(WitheredLord lord) {
    return t.level.getEntitiesOfClass(
        BlossomOni.class,
        lord.getBoundingBox().inflate(32),
        o -> o.isAlive() && lord.getUUID().equals(o.summoner()));
  }

  boolean tick() throws Exception {
    ServerLevel level = t.level;
    age++;
    if (age == 1) {
      // Clear what an earlier run left in the kept test world.
      for (var e :
          level.getEntitiesOfClass(
              Swordsman.class,
              new net.minecraft.world.phys.AABB(X - 64, 90, Z - 64, X + 64, 120, Z + 64)))
        e.discard();
      for (int x = X - 12; x <= X + 12; x++)
        for (int z = Z - 12; z <= Z + 12; z++) {
          level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
          for (int y = 100; y < 106; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
      time("1000");
      sitter = spawn(Content.WITHERED_LORD, X - 6.5, Z + .5, true);
      // Its last fight long ago, so it may sit at once.
      set(PetalLord.class, sitter, "lastFight", -10000L);
      var type = Content.WITHERED_LORD;
      t.check(
          sitter.is(
                  TagKey.create(Registries.ENTITY_TYPE, Identifier.withDefaultNamespace("undead")))
              && sitter.is(
                  TagKey.create(
                      Registries.ENTITY_TYPE,
                      Identifier.withDefaultNamespace("freeze_immune_entity_types")))
              && !sitter.is(
                  TagKey.create(
                      Registries.ENTITY_TYPE, Identifier.withDefaultNamespace("burn_in_daylight")))
              && sitter.ofTheDead(),
          "The 朽英剑主 is undead and of the dead, frost-proof and not burnt by the sun");
      Mob zombie = spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, X - 10, Z - 10, false);
      Mob living = spawn(Content.SWORDSMAN, X - 10, Z - 8, false);
      var foe = LordArts.class.getDeclaredMethod("foe", LivingEntity.class);
      foe.setAccessible(true);
      t.check(
          !(boolean) foe.invoke(sitter.arts(), zombie)
              && (boolean) foe.invoke(sitter.arts(), living),
          "Its arts strike the living, not the other undead");
      zombie.discard();
      living.discard();
      t.check(
          (float) call(PetalLord.class, sitter, "relicChance", new Class<?>[0]) == .15f
              && WitheredLord.RELIC_CHANCE == .15f,
          "Its relic chance is 15%");
      t.check(
          t.server.getAdvancements().get(Oborokage.id("withered_rest")) != null,
          "Its advancement 残英 loads");
      return false;
    }
    if (age == 20) {
      t.check(sitter.meditating(), "By day, with nothing to fight, it sits in meditation");
      pos = new Vec(sitter.getX(), sitter.getY(), sitter.getZ());
    }
    if (age == 60) {
      double moved =
          Math.sqrt(Math.pow(sitter.getX() - pos.x(), 2) + Math.pow(sitter.getZ() - pos.z(), 2));
      t.check(
          sitter.meditating() && moved < .05,
          "In meditation it does not move: " + String.format(Locale.ROOT, "%.3f", moved));
      t.check(fadeCooldown(sitter, t.player) == 200, "By day its fade recovers in 200 ticks");
      sitter.setHealth(sitter.getMaxHealth());
      float before = sitter.getHealth();
      Mob striker = spawn(Content.SWORDSMAN, X - 6.5, Z + 2, false);
      sitter.hurtServer(level, level.damageSources().mobAttack(striker), 6);
      t.check(
          !sitter.meditating() && sitter.getHealth() < before && sitter.getTarget() == striker,
          "A blow wakes it, lands (it cannot parry it) and turns it on the attacker: health "
              + sitter.getHealth()
              + " target "
              + sitter.getTarget());
      sitter.discard();
      striker.discard();
      time("18000");
      caller = spawn(Content.WITHERED_LORD, X + 6.5, Z + .5, true);
      target = spawn(Content.SWORDSMAN, X + 6.5, Z + 6.5, false);
      // A foe it can fight (an invulnerable one is no target) that outlasts the test.
      target
          .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
          .setBaseValue(1000);
      target.setHealth(1000);
      caller.setTarget(target);
      set(WitheredLord.class, caller, "summonCooldown", 0);
    }
    if (age == 62) {
      t.check(
          !caller.meditating() && fadeCooldown(caller, target) == 100,
          "By night it is whole: it does not sit, and its fade recovers in 100 ticks");
      caller.setTarget(target);
    }
    if (age == 110) {
      List<BlossomOni> called = calledBy(caller);
      t.check(
          called.size() == 3,
          "By night 「百鬼」 calls up three 樱下鬼 bound to it (no player near): "
              + called.size()
              + " windup "
              + get(WitheredLord.class, caller, "summonWindup")
              + " cooldown "
              + get(WitheredLord.class, caller, "summonCooldown"));
      boolean drops = false, xp = false;
      for (BlossomOni o : called) {
        drops |=
            (boolean)
                call(
                    BlossomOni.class,
                    o,
                    "shouldDropLoot",
                    new Class<?>[] {ServerLevel.class},
                    level);
        xp |=
            (int)
                    call(
                        BlossomOni.class,
                        o,
                        "getBaseExperienceReward",
                        new Class<?>[] {ServerLevel.class},
                        level)
                > 0;
      }
      for (int i = 0; i < 5; i++)
        plain.add(spawn(Content.BLOSSOM_ONI, X + 2.5 + i, Z - 6.5, false));
      boolean plainDrops =
          (boolean)
              call(
                  BlossomOni.class,
                  plain.get(0),
                  "shouldDropLoot",
                  new Class<?>[] {ServerLevel.class},
                  level);
      t.check(
          !drops && !xp && plainDrops,
          "Those it calls up drop nothing and give no experience; a 樱下鬼 risen by itself does");
      set(WitheredLord.class, caller, "summonCooldown", 0);
      caller.setTarget(target);
    }
    if (age == 160) {
      t.check(
          calledBy(caller).size() == 3
              && (int) get(WitheredLord.class, caller, "summonWindup") == 0,
          "With eight 樱下鬼 around it, it calls up no more: " + calledBy(caller).size() + " called");
      caller.kill(level);
    }
    if (age == 170) {
      long left =
          level
              .getEntitiesOfClass(
                  BlossomOni.class,
                  new net.minecraft.world.phys.AABB(X - 20, 95, Z - 20, X + 20, 110, Z + 20),
                  o -> o.isAlive() && o.summoner() != null)
              .size();
      long plainLeft = plain.stream().filter(o -> o.isAlive() && !o.isRemoved()).count();
      t.check(
          left == 0 && plainLeft == plain.size(),
          "When it falls, those it called up fade with it; the others stay: "
              + left
              + " called left, "
              + plainLeft
              + " others");
      // Its loot: 剑魄 4-8 and 剑意结晶 2-4 a kill.
      Mob mob = Content.WITHERED_LORD.create(level, EntitySpawnReason.COMMAND);
      mob.setPos(X, 100, Z);
      var table =
          level
              .getServer()
              .reloadableRegistries()
              .getLootTable(Content.WITHERED_LORD.getDefaultLootTable().orElseThrow());
      LootParams params =
          new LootParams.Builder(level)
              .withParameter(LootContextParams.THIS_ENTITY, mob)
              .withParameter(LootContextParams.ORIGIN, mob.position())
              .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
              .create(LootContextParamSets.ENTITY);
      int souls = 0, crystals = 0, lo = 99, hi = 0;
      for (int i = 0; i < 40; i++) {
        int s = 0;
        for (ItemStack st : table.getRandomItems(params)) {
          if (st.is(Content.BLADE_SOUL)) s += st.getCount();
          if (st.is(Content.INTENT_CRYSTAL)) crystals += st.getCount();
        }
        souls += s;
        lo = Math.min(lo, s);
        hi = Math.max(hi, s);
      }
      t.check(
          lo >= 4 && hi <= 8 && crystals >= 80 && crystals <= 160,
          "Its loot: 剑魄 4-8 a kill (" + lo + "-" + hi + "), 剑意结晶 2-4 (" + crystals + " in 40)");
      plain.forEach(Entity::discard);
      target.discard();
      time("1000");
      return true;
    }
    return false;
  }
}

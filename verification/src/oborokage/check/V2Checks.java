package oborokage.check;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * 1.0.0-beta checks (second round, after play): the kazuuchi and the rebalanced recipes, swordsman
 * drops and damage, the 樱下鬼 (tags, drops), the 落英剑主 (blades, blow cap, finisher exemption), the
 * whetstone's grind into a wakizashi, and the dojo (template, structure, its mobs armed and its
 * chest in place).
 */
final class V2Checks {
  private static final BlockPos DOJO = new BlockPos(-30, 120, -30);
  private final ServerChecks t;
  private int step, age;
  private Swordsman swordsman;

  V2Checks(ServerChecks t) {
    this.t = t;
  }

  private static String resource(String path) throws Exception {
    try (InputStream in = Oborokage.class.getResourceAsStream(path)) {
      return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** Rolls an entity's loot table n times and counts each item it gave. */
  private Map<Item, Integer> roll(EntityType<? extends Mob> type, int n) {
    ServerLevel level = t.level;
    Mob mob = type.create(level, EntitySpawnReason.COMMAND);
    mob.setPos(.5, 100, .5);
    LootTable table =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(type.getDefaultLootTable().orElseThrow());
    LootParams params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, mob)
            .withParameter(LootContextParams.ORIGIN, mob.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
            .create(LootContextParamSets.ENTITY);
    Map<Item, Integer> got = new HashMap<>();
    for (int i = 0; i < n; i++)
      for (ItemStack s : table.getRandomItems(params)) got.merge(s.getItem(), 1, Integer::sum);
    return got;
  }

  boolean tick() throws Exception {
    var p = t.player;
    var level = t.level;
    age++;
    if (step == 0) {
      Combat.clear();
      // The kazuuchi: a blade of the chain with no ultimate.
      t.check(
          BladeStyle.of(new ItemStack(Oborokage.KAZUUCHI)) == BladeStyle.PLAIN
              && !BladeStyle.PLAIN.hasUltimate()
              && Oborokage.mainBlade(new ItemStack(Oborokage.KAZUUCHI)),
          "The kazuuchi is a plain blade of the chain with no ultimate");
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.KAZUUCHI));
      p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      var intent = DualCombat.State.class.getDeclaredField("intent");
      intent.setAccessible(true);
      intent.setInt(DualCombat.state(p), 100);
      t.act(4);
      t.check(
          !DualCombat.inUltimate(p) && DualCombat.state(p).intent() == 100,
          "The ultimate key does nothing with a kazuuchi and keeps the intent");
      Combat.clear();
      // Recipes: every long blade is forged from a kazuuchi; only 胧影 needs a nether star.
      String tachi = resource("/data/oborokage/recipe/tachi.json");
      t.check(
          tachi.contains("minecraft:nether_star") && tachi.contains("oborokage:kazuuchi"),
          "胧影 is forged from a kazuuchi and a nether star");
      boolean cheaper = true;
      for (String blade : List.of("raikiri", "frostmoon", "voidstring")) {
        String r = resource("/data/oborokage/recipe/" + blade + ".json");
        cheaper &= r.contains("oborokage:kazuuchi") && !r.contains("diamond_block");
      }
      t.check(cheaper, "赤霆, 霜轮 and 空弦 are forged from a kazuuchi without diamond blocks");
      var recipes = level.getServer().getRecipeManager();
      t.check(
          recipes
              .byKey(ResourceKey.create(Registries.RECIPE, Oborokage.id("kazuuchi")))
              .isPresent(),
          "The kazuuchi recipe loads");
      // The swordsman: a kazuuchi in hand, eight damage with it, a 1% crystal and no relics.
      swordsman = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
      swordsman.setPos(.5, 100, 3);
      swordsman.finalizeSpawn(
          level,
          level.getCurrentDifficultyAt(swordsman.blockPosition()),
          EntitySpawnReason.COMMAND,
          null);
      level.addFreshEntity(swordsman);
      t.check(swordsman.getMainHandItem().is(Oborokage.KAZUUCHI), "A swordsman carries a kazuuchi");
      var drops = roll(Content.SWORDSMAN, 6000);
      int crystals = drops.getOrDefault(Content.INTENT_CRYSTAL, 0);
      boolean relics = Content.RELICS.values().stream().anyMatch(drops::containsKey);
      t.check(
          crystals > 20 && crystals < 120 && !relics && drops.size() == 1,
          "A swordsman drops only intent crystals, about 1%: " + crystals + " in 6000, " + drops);
      // The 樱下鬼: undead that burns by day; bones, 剑魄 about a third, crystals about a tenth.
      t.check(
          Content.BLOSSOM_ONI.create(level, EntitySpawnReason.COMMAND).is(EntityTypeTags.UNDEAD)
              && Content.BLOSSOM_ONI
                  .create(level, EntitySpawnReason.COMMAND)
                  .is(EntityTypeTags.BURN_IN_DAYLIGHT),
          "The 樱下鬼 is undead and burns in daylight");
      var oni = roll(Content.BLOSSOM_ONI, 3000);
      int souls = oni.getOrDefault(Content.BLADE_SOUL, 0),
          oniCrystals = oni.getOrDefault(Content.INTENT_CRYSTAL, 0);
      t.check(
          souls > 850
              && souls < 1150
              && oniCrystals > 220
              && oniCrystals < 380
              && Content.RELICS.values().stream().noneMatch(oni::containsKey),
          "The 樱下鬼 drops 剑魄 about 33% and intent crystals about 10%, no relics: "
              + souls
              + " / "
              + oniCrystals
              + " in 3000");
      // The 落英剑主: one of the four long blades, its blows capped, never executed.
      Set<Item> seen = new HashSet<>();
      for (int i = 0; i < 40; i++) {
        var lord = Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
        lord.finalizeSpawn(
            level, level.getCurrentDifficultyAt(BlockPos.ZERO), EntitySpawnReason.COMMAND, null);
        seen.add(lord.getMainHandItem().getItem());
        lord.discard();
      }
      t.check(
          seen.equals(
              Set.of(
                  Oborokage.TACHI, Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING)),
          "The 落英剑主 carries 胧影, 赤霆, 霜轮 or 空弦 and never a wakizashi: " + seen);
      var lord = Content.PETAL_LORD.create(level, EntitySpawnReason.COMMAND);
      lord.setPos(.5, 100, 4);
      lord.setYRot(0); // facing away from the player at z .5: no parry
      lord.setNoAi(true);
      level.addFreshEntity(lord);
      lord.hurtServer(level, level.damageSources().playerAttack(p), 1000);
      // Capped at 60 before its armor (10, toughness 4) takes its 8% off.
      t.check(
          lord.isAlive() && lord.getHealth() >= 200 - PetalLord.MAX_BLOW && lord.getHealth() < 150,
          "No single blow takes more than 60 from the 落英剑主: health=" + lord.getHealth());
      t.check(
          lord.is(TargetRules.FINISHER_EXEMPT) && lord.isPersistenceRequired(),
          "The 落英剑主 is spared the finisher and never despawns");
      t.check(
          PetalLord.RELIC_CHANCE == .079f
              && Content.RELICS
                  .keySet()
                  .containsAll(
                      List.of(
                          BladeStyle.TACHI,
                          BladeStyle.RAIKIRI,
                          BladeStyle.FROSTMOON,
                          BladeStyle.VOIDSTRING)),
          "The 落英剑主 drops its own blade's relic 7.9% of the time");
      lord.discard();
      // The whetstone grinds a kazuuchi and six 剑魄 into a wakizashi.
      BlockPos at = new BlockPos(3, 100, 3);
      level.setBlock(at, Content.WHETSTONE.defaultBlockState(), 3);
      p.getInventory().clearContent();
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.KAZUUCHI));
      p.getInventory().add(new ItemStack(Content.BLADE_SOUL, 5));
      p.setShiftKeyDown(true);
      var hit = new BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false);
      p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
      boolean refused = p.getMainHandItem().is(Oborokage.KAZUUCHI);
      p.getInventory().add(new ItemStack(Content.BLADE_SOUL, 2));
      p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
      t.check(
          refused
              && p.getMainHandItem().is(Oborokage.WAKIZASHI)
              && p.getInventory().countItem(Content.BLADE_SOUL) == 1,
          "Through a real sneaking click: five 剑魄 are not enough; six grind the kazuuchi into a"
              + " wakizashi");
      p.setShiftKeyDown(false);
      p.getInventory().clearContent();
      level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
      // The dojo: its template, structure and set, then placed with its people.
      var template = t.server.getStructureTemplateManager().get(Oborokage.id("dojo/dojo"));
      t.check(
          template.isPresent()
              && template.get().getSize().equals(new net.minecraft.core.Vec3i(17, 12, 17)),
          "The dojo's template loads at 17 x 12 x 17");
      t.check(
          level
                  .registryAccess()
                  .lookupOrThrow(Registries.STRUCTURE)
                  .containsKey(Oborokage.id("dojo"))
              && level
                  .registryAccess()
                  .lookupOrThrow(Registries.STRUCTURE_SET)
                  .containsKey(Oborokage.id("dojo")),
          "The dojo structure and its placement set are registered");
      // The test world is kept between runs: clear what an earlier run may have left here.
      var area = new net.minecraft.world.phys.AABB(DOJO).expandTowards(17, 12, 17).inflate(4);
      for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area))
        if (!(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
      template
          .get()
          .placeInWorld(
              level,
              DOJO,
              DOJO,
              new net.minecraft.world.level.levelgen.structure.templatesystem
                  .StructurePlaceSettings(),
              level.getRandom(),
              2);
      step = 1;
      age = 0;
      return false;
    }
    if (step == 1 && age == 10) {
      // Held weapons add their attack once the mob has ticked.
      double damage = swordsman.getAttributeValue(Attributes.ATTACK_DAMAGE);
      t.check(
          Math.abs(damage - 8) < .01,
          "A swordsman with its kazuuchi hits for 8 (not base plus a named blade): " + damage);
      swordsman.discard();
      var box = new net.minecraft.world.phys.AABB(DOJO).expandTowards(17, 12, 17);
      String all =
          level
              .getEntities((net.minecraft.world.entity.Entity) null, box.inflate(4), e -> true)
              .stream()
              .map(e -> e.getType().toShortString() + "@" + e.blockPosition().toShortString())
              .toList()
              .toString();
      var lords = level.getEntitiesOfClass(PetalLord.class, box);
      var swordsmen =
          level.getEntitiesOfClass(Swordsman.class, box, e -> e.getType() == Content.SWORDSMAN);
      var posts = level.getEntitiesOfClass(TrainingDummy.class, box);
      t.check(
          lords.size() == 1
              && BladeStyle.of(lords.get(0).getMainHandItem()) != null
              && lords.get(0).getMainHandItem().getItem() != Oborokage.KAZUUCHI,
          "A placed dojo has its master armed with a named blade: " + lords.size());
      t.check(
          swordsmen.size() == 3
              && swordsmen.stream().allMatch(s -> s.getMainHandItem().is(Oborokage.KAZUUCHI))
              && swordsmen.stream().allMatch(Mob::isPersistenceRequired)
              && posts.size() == 2,
          "A placed dojo has three armed swordsmen who stay, and two training posts: "
              + swordsmen.size()
              + " / "
              + posts.size()
              + " all="
              + all);
      t.check(
          level.getBlockState(DOJO.offset(8, 2, 14)).is(Blocks.CHEST)
              && level.getBlockState(DOJO.offset(7, 2, 14)).is(Content.WHETSTONE),
          "The dojo's chest sits beside a whetstone at the back of the hall");
      for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box))
        if (!(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
      return true;
    }
    return false;
  }
}

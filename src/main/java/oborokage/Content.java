package oborokage;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;

/** Everything added in 1.0: the swordsman, the training dummy, the whetstone and their items. */
public final class Content {
  public static final EntityType<Swordsman> SWORDSMAN =
      entity(
          "swordsman",
          FabricEntityType.Builder.createMob(
                  Swordsman::new,
                  MobCategory.MONSTER,
                  b ->
                      b.defaultAttributes(Swordsman::createAttributes)
                          .spawnPlacement(
                              SpawnPlacementTypes.ON_GROUND,
                              Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                              Monster::checkAnyLightMonsterSpawnRules))
              .sized(.6f, 1.95f)
              .clientTrackingRange(8));

  /** 樱下鬼's own voice: a whisper, a cracked sob when struck, a long wail as it fades. */
  public static final net.minecraft.sounds.SoundEvent
      BLOSSOM_ONI_AMBIENT = sound("entity.blossom_oni.ambient"),
      BLOSSOM_ONI_HURT = sound("entity.blossom_oni.hurt"),
      BLOSSOM_ONI_DEATH = sound("entity.blossom_oni.death");

  private static net.minecraft.sounds.SoundEvent sound(String name) {
    var id = Oborokage.id(name);
    return Registry.register(
        BuiltInRegistries.SOUND_EVENT,
        id,
        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id));
  }

  /** 樱下鬼: the undead swordsman of night-time cherry groves and blade tombs. */
  public static final EntityType<BlossomOni> BLOSSOM_ONI =
      entity(
          "blossom_oni",
          FabricEntityType.Builder.createMob(
                  BlossomOni::new,
                  MobCategory.MONSTER,
                  b ->
                      b.defaultAttributes(BlossomOni::createAttributes)
                          .spawnPlacement(
                              SpawnPlacementTypes.ON_GROUND,
                              Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                              BlossomOni::checkSpawn))
              .sized(.6f, 1.95f)
              .clientTrackingRange(8));

  /** 落英剑主: the dojo master; placed by the dojo structure, never spawned by the world. */
  public static final EntityType<PetalLord> PETAL_LORD =
      entity(
          "petal_lord",
          FabricEntityType.Builder.createMob(
                  PetalLord::new,
                  MobCategory.MONSTER,
                  b -> b.defaultAttributes(PetalLord::createAttributes))
              .sized(.66f, 2.15f)
              .clientTrackingRange(8));

  public static final EntityType<TrainingDummy> TRAINING_DUMMY =
      entity(
          "training_dummy",
          FabricEntityType.Builder.createMob(
                  TrainingDummy::new,
                  MobCategory.MISC,
                  b -> b.defaultAttributes(TrainingDummy::createAttributes))
              .sized(.7f, 2.0f)
              .fireImmune()
              .clientTrackingRange(8));

  public static final Block WHETSTONE =
      block(
          "whetstone",
          BlockBehaviour.Properties.of()
              .strength(2f, 6f)
              .sound(SoundType.STONE)
              .noOcclusion()
              .requiresCorrectToolForDrops());

  public static final Item WHETSTONE_ITEM =
      item("whetstone", p -> new BlockItem(WHETSTONE, p), new Item.Properties());

  /** 剑魄: dropped by the 樱下鬼; six of them grind a kazuuchi into a wakizashi at a whetstone. */
  public static final Item BLADE_SOUL =
      item(
          "blade_soul",
          p ->
              new Item(p) {
                @Override
                public void appendHoverText(
                    ItemStack s,
                    TooltipContext c,
                    net.minecraft.world.item.component.TooltipDisplay d,
                    java.util.function.Consumer<net.minecraft.network.chat.Component> out,
                    TooltipFlag f) {
                  out.accept(
                      net.minecraft.network.chat.Component.translatable(
                              "tooltip.oborokage.blade_soul", WhetstoneBlock.SOULS_PER_WAKIZASHI)
                          .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
              },
          new Item.Properties().rarity(Rarity.UNCOMMON));

  public static final Item INTENT_CRYSTAL =
      item("intent_crystal", IntentCrystalItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));

  public static final Map<Inscription, Item> STONES = new LinkedHashMap<>();

  static {
    for (Inscription i : Inscription.values())
      if (i != Inscription.ANCIENT)
        STONES.put(
            i,
            item(
                "inscription_" + i.id,
                p -> new InscriptionStoneItem(p, i),
                new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));
  }

  /** Relics found in blade tombs, one per blade; a crafting table restores them. */
  public static final Map<BladeStyle, Item> RELICS = new LinkedHashMap<>();

  static {
    for (BladeStyle s : BladeStyle.values())
      if (s != BladeStyle.PLAIN)
        RELICS.put(
            s,
            item(
                "broken_" + relicName(s),
                RelicItem::new,
                new Item.Properties().rarity(Rarity.RARE).stacksTo(1).fireResistant()));
  }

  public static final Item TRAINING_DUMMY_ITEM =
      item("training_dummy", TrainingDummyItem::new, new Item.Properties().stacksTo(16));

  public static final Item SWORDSMAN_SPAWN_EGG =
      item("swordsman_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(SWORDSMAN));

  public static final Item BLOSSOM_ONI_SPAWN_EGG =
      item("blossom_oni_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BLOSSOM_ONI));

  public static final Item PETAL_LORD_SPAWN_EGG =
      item("petal_lord_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(PETAL_LORD));

  public static String relicName(BladeStyle s) {
    return switch (s) {
      case TACHI -> "tachi";
      case WAKIZASHI -> "wakizashi";
      case RAIKIRI -> "raikiri";
      case FROSTMOON -> "frostmoon";
      case VOIDSTRING -> "voidstring";
      case PLAIN -> "kazuuchi";
    };
  }

  private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> b) {
    ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Oborokage.id(name));
    return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
  }

  private static Block block(String name, BlockBehaviour.Properties p) {
    ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Oborokage.id(name));
    return Registry.register(BuiltInRegistries.BLOCK, key, new WhetstoneBlock(p.setId(key)));
  }

  private static Item item(
      String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties p) {
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Oborokage.id(name));
    return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(p.setId(key)));
  }

  static void init() {
    TombGuard.init();
    TombCandles.init();
    // Every undead monster of the world hunts the living swordsmen and their master too (the
    // 樱下鬼 already does). Goals are added each time such a mob is loaded into a level.
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(
        (entity, level) -> {
          // Neutral undead (zombified piglins) keep to themselves.
          if (entity instanceof Monster mob
              && !(mob instanceof BlossomOni)
              && !(mob instanceof net.minecraft.world.entity.NeutralMob)
              && mob.is(net.minecraft.tags.EntityTypeTags.UNDEAD))
            ((oborokage.mixin.MobGoalsAccess) mob)
                .oborokage$targets()
                .addGoal(
                    3,
                    new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                        mob, Swordsman.class, true, (e, l) -> !(e instanceof BlossomOni)));
        });
    // Swordsmen walk only the cherry groves, by day and by night; the 樱下鬼 rise there after dark,
    // and often.
    if (ServerConfig.get().swordsmanSpawns) {
      BiomeModifications.addSpawn(
          BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE),
          MobCategory.MONSTER,
          SWORDSMAN,
          10,
          1,
          2);
      BiomeModifications.addSpawn(
          BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE),
          MobCategory.MONSTER,
          BLOSSOM_ONI,
          60,
          1,
          3);
    }
  }

  private Content() {}
}

package oborokage;

import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;

public final class Oborokage implements ModInitializer {
  public static final String ID = "oborokage";
  public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, id("tachi"));
  public static final Item TACHI =
      Registry.register(
          BuiltInRegistries.ITEM,
          KEY,
          new TachiItem(
              new Item.Properties()
                  .setId(KEY)
                  .sword(ToolMaterial.NETHERITE, 8, -2.6f)
                  .durability(4096)
                  .rarity(Rarity.EPIC)
                  .fireResistant()));
  public static final ResourceKey<Item> WAKI_KEY =
      ResourceKey.create(Registries.ITEM, id("wakizashi"));
  public static final Item WAKIZASHI =
      Registry.register(
          BuiltInRegistries.ITEM,
          WAKI_KEY,
          new WakizashiItem(
              new Item.Properties()
                  .setId(WAKI_KEY)
                  .sword(ToolMaterial.NETHERITE, 5, -1.6f)
                  .durability(2560)
                  .rarity(Rarity.EPIC)
                  .fireResistant()));

  /** 数打太刀: an iron blank, the base of every named blade's recipe and the swordsmen's weapon. */
  public static final ResourceKey<Item> KAZUUCHI_KEY =
      ResourceKey.create(Registries.ITEM, id("kazuuchi"));

  public static final Item KAZUUCHI =
      Registry.register(
          BuiltInRegistries.ITEM,
          KAZUUCHI_KEY,
          new KazuuchiItem(
              new Item.Properties()
                  .setId(KAZUUCHI_KEY)
                  .sword(ToolMaterial.IRON, 4, -2.6f)
                  .durability(512)));

  // Netherite-forged since 0.5.0: about a third more melee damage than the 0.4 diamond blades.
  public static final Item RAIKIRI = blade("raikiri", StyleBladeItem.Style.STORM, 8, -2.0f);
  public static final Item FROSTMOON = blade("frostmoon", StyleBladeItem.Style.FROST, 11, -2.7f);
  public static final Item VOIDSTRING = blade("voidstring", StyleBladeItem.Style.VOID, 9, -2.4f);

  private static Item blade(String name, StyleBladeItem.Style style, int damage, float speed) {
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
    return Registry.register(
        BuiltInRegistries.ITEM,
        key,
        new StyleBladeItem(
            new Item.Properties()
                .setId(key)
                .sword(ToolMaterial.NETHERITE, damage, speed)
                .durability(3584)
                .rarity(Rarity.EPIC)
                .fireResistant(),
            style));
  }

  public static boolean mainBlade(ItemStack stack) {
    return stack.is(TACHI)
        || stack.is(RAIKIRI)
        || stack.is(FROSTMOON)
        || stack.is(VOIDSTRING)
        || stack.is(KAZUUCHI);
  }

  public static final CreativeModeTab EQUIPMENT =
      Registry.register(
          BuiltInRegistries.CREATIVE_MODE_TAB,
          id("equipment"),
          net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
              .title(
                  net.minecraft.network.chat.Component.translatable(
                      "itemGroup.oborokage.equipment"))
              .icon(() -> new ItemStack(TACHI))
              .displayItems(
                  (parameters, output) -> {
                    output.accept(TACHI);
                    output.accept(WAKIZASHI);
                    output.accept(RAIKIRI);
                    output.accept(FROSTMOON);
                    output.accept(VOIDSTRING);
                    output.accept(KAZUUCHI);
                    output.accept(Content.WHETSTONE_ITEM);
                    output.accept(Content.TRAINING_DUMMY_ITEM);
                    output.accept(Content.INTENT_CRYSTAL);
                    Content.STONES.values().forEach(output::accept);
                    output.accept(Content.BLADE_SOUL);
                    Content.RELICS.values().forEach(output::accept);
                    output.accept(Content.SWORDSMAN_SPAWN_EGG);
                    output.accept(Content.BLOSSOM_ONI_SPAWN_EGG);
                    output.accept(Content.PETAL_LORD_SPAWN_EGG);
                    output.accept(Content.WITHERED_LORD_SPAWN_EGG);
                  })
              .build());
  public static final net.minecraft.core.particles.SimpleParticleType CRESCENT =
      Registry.register(
          BuiltInRegistries.PARTICLE_TYPE,
          id("crescent"),
          net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
  public static final net.minecraft.core.particles.SimpleParticleType STREAK =
      Registry.register(
          BuiltInRegistries.PARTICLE_TYPE,
          id("streak"),
          net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
  // 0.5.0 blade particles: moon petals, thunder sparks, ice shards and void motes.
  public static final net.minecraft.core.particles.SimpleParticleType PETAL = particle("petal");
  public static final net.minecraft.core.particles.SimpleParticleType SPARK = particle("spark");
  public static final net.minecraft.core.particles.SimpleParticleType SHARD = particle("shard");
  public static final net.minecraft.core.particles.SimpleParticleType MOTE = particle("mote");

  private static net.minecraft.core.particles.SimpleParticleType particle(String name) {
    return Registry.register(
        BuiltInRegistries.PARTICLE_TYPE,
        id(name),
        net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
  }

  public static boolean allowPlayerDamage = false;

  public static Identifier id(String s) {
    return Identifier.fromNamespaceAndPath(ID, s);
  }

  @Override
  public void onInitialize() {
    PayloadTypeRegistry.clientboundPlay().register(TechniqueEvent.TYPE, TechniqueEvent.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(SlashPayload.TYPE, SlashPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(ActionRequest.TYPE, ActionRequest.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(ActionEvent.TYPE, ActionEvent.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TetherEvent.TYPE, TetherEvent.CODEC);
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
        ActionRequest.TYPE,
        (message, context) ->
            context.server().execute(() -> DualCombat.request(context.player(), message)));
    net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
        // Only living targets belong to the blade chains; crystals, frames, boats and minecarts
        // keep
        // the ordinary attack so they can still be broken with a blade drawn.
        (p, l, h, e, hit) ->
            DualCombat.ownsAttack(p, e)
                ? net.minecraft.world.InteractionResult.FAIL
                : net.minecraft.world.InteractionResult.PASS);
    net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
        (e, source, amount) -> {
          if (e instanceof net.minecraft.server.level.ServerPlayer p) DualCombat.captureDamage(p);
          return DualCombat.allowDamage(e, source, amount);
        });
    net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(
        (entity, viewer) -> {
          if (entity instanceof net.minecraft.server.level.ServerPlayer p
              && ServerPlayNetworking.canSend(viewer, ActionEvent.TYPE)) {
            var snapshot = DualCombat.snapshot(p);
            if (snapshot != null && snapshot.move() != 0)
              ServerPlayNetworking.send(viewer, snapshot);
          }
        });
    net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> DualCombat.remove(handler.player));
    ServerConfig.load();
    Components.init();
    Content.init();
    ConfigIO.LOG.info(
        "Oborokage {} built for Minecraft {}",
        net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("?"),
        oborokage.compat.Compat.TARGET);
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
        (server, manager, success) -> {
          if (success) ServerConfig.load();
        });
    net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
        (entity, source, base, taken, blocked) -> {
          if (taken > 0 && !blocked) {
            Combat.markDamaged(entity.getUUID());
            if (entity instanceof net.minecraft.server.level.ServerPlayer p)
              DualCombat.afterDamage(p);
          }
        });
    net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, source) -> {
          if (entity instanceof net.minecraft.server.level.ServerPlayer p) {
            DualCombat.damaged(p);
            DualCombat.remove(p);
          }
          // 饮露: each enemy an inscribed blade fells restores a food point.
          if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer killer
              && Feats.enemy(entity)
              && BladeArts.enabled()
              && Inscription.has(killer.getMainHandItem(), Inscription.DEW))
            killer.getFoodData().eat(1, .2f);
        });
    ServerTickEvents.END_SERVER_TICK.register(Combat::tick);
    ServerLifecycleEvents.SERVER_STOPPED.register(s -> Combat.clear());
  }
}

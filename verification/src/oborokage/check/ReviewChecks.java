package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import oborokage.*;

public final class ReviewChecks {
  static void run(ServerChecks t) throws Exception {
    var p = t.player;
    var level = t.level;
    p.connection =
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
            t.server,
            new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
            p,
            net.minecraft.server.network.CommonListenerCookie.createInitial(
                p.getGameProfile(), false)) {
          @Override
          public void send(net.minecraft.network.protocol.Packet<?> packet) {}

          @Override
          public void send(
              net.minecraft.network.protocol.Packet<?> packet,
              io.netty.channel.ChannelFutureListener listener) {}
        };
    var latencyField =
        net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField(
            "latency");
    latencyField.setAccessible(true);
    int savedLatency = latencyField.getInt(p.connection);
    latencyField.setInt(p.connection, 200);
    t.check(
        DualCombat.guardWindow(p) == ServerConfig.get().guardWindowTicks + 2,
        "200ms RTT adds two ticks of guard allowance");
    latencyField.setInt(p.connection, 5000);
    t.check(
        DualCombat.guardWindow(p) == ServerConfig.get().guardWindowTicks + 3,
        "Guard latency compensation is capped at three ticks");
    latencyField.setInt(p.connection, savedLatency);
    Combat.clear();
    p.setPos(.5, 100, .5);
    p.setYRot(0);
    p.setXRot(0);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
    p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
    t.check(
        Move.of(1) == Move.of(1) && Move.of(999).id() == 0,
        "Move profiles cached; unknown move safely ignored");
    float base = WeaponDamage.scaled(p, p.getMainHandItem(), 18);
    p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
    float stronger = WeaponDamage.scaled(p, p.getMainHandItem(), 18);
    p.removeEffect(MobEffects.STRENGTH);
    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
    float weaker = WeaponDamage.scaled(p, p.getMainHandItem(), 18);
    p.removeEffect(MobEffects.WEAKNESS);
    t.check(stronger > base && weaker < base, "Strength and Weakness affect new technique damage");
    var attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
    var modifier =
        new AttributeModifier(Oborokage.id("qa_bonus"), 5, AttributeModifier.Operation.ADD_VALUE);
    attack.addTransientModifier(modifier);
    t.check(
        WeaponDamage.scaled(p, p.getOffhandItem(), 14) > 14,
        "External attack attribute affects offhand technique");
    attack.removeModifier(modifier.id());
    var ench = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    var sword = new ItemStack(Oborokage.WAKIZASHI);
    var target = t.probe(2);
    WeaponDamage.apply(p, target, sword, 20, false);
    float plain = target.lastDamage;
    target.damageCooldownTime = 0;
    sword.enchant(ench.getOrThrow(Enchantments.SHARPNESS), 5);
    WeaponDamage.apply(p, target, sword, 20, false);
    t.check(target.lastDamage > plain, "Offhand Sharpness modifies actual attack damage");
    sword = new ItemStack(Oborokage.WAKIZASHI);
    sword.enchant(ench.getOrThrow(Enchantments.SMITE), 5);
    target.damageCooldownTime = 0;
    WeaponDamage.apply(p, target, sword, 20, false);
    t.check(target.lastDamage > plain, "Smite applies to undead from the correct blade");
    sword = new ItemStack(Oborokage.WAKIZASHI);
    sword.enchant(ench.getOrThrow(Enchantments.FIRE_ASPECT), 2);
    target.damageCooldownTime = 0;
    WeaponDamage.apply(p, target, sword, 20, false);
    t.check(target.isOnFire(), "Offhand Fire Aspect executes native post-attack effect");
    sword = new ItemStack(Oborokage.WAKIZASHI);
    sword.enchant(ench.getOrThrow(Enchantments.SWEEPING_EDGE), 3);
    target.damageCooldownTime = 0;
    WeaponDamage.apply(p, target, sword, 20, true);
    t.check(target.lastDamage > plain, "Sweeping Edge improves area technique damage");
    target.discard();
    var spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.COMMAND);
    spider.setPos(.5, 100, 2);
    level.addFreshEntity(spider);
    sword = new ItemStack(Oborokage.WAKIZASHI);
    sword.enchant(ench.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), 1);
    WeaponDamage.apply(p, spider, sword, 1, false);
    t.check(
        spider.hasEffect(MobEffects.SLOWNESS),
        "Bane of Arthropods executes native slowdown effect");
    spider.discard();
    target = t.probe(2);
    target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
    sword = new ItemStack(Oborokage.WAKIZASHI);
    sword.enchant(ench.getOrThrow(Enchantments.KNOCKBACK), 2);
    WeaponDamage.apply(p, target, sword, 1, false);
    t.check(
        target.getDeltaMovement().horizontalDistanceSqr() > 0,
        "Knockback enchantment affects new strikes");
    target.discard();
    var pet = net.minecraft.world.entity.EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
    pet.setOwner(p);
    t.check(!TargetRules.allowed(p, pet), "Original and dual rules protect owned pets");
    ServerConfig.get().protectPets = false;
    t.check(TargetRules.allowed(p, pet), "Pet protection is configurable");
    ServerConfig.get().protectPets = true;
    var otherOwner = new ServerChecks.Fake(t.server, level);
    var otherPet = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
    otherPet.setOwner(otherOwner);
    t.check(!ServerConfig.get().protectOthersPets, "Others-pet protection defaults to false");
    t.check(
        TargetRules.allowed(p, otherPet),
        "Other owner's pet retains existing target behavior by default");
    ServerConfig.get().protectOthersPets = true;
    t.check(!TargetRules.allowed(p, otherPet), "protectOthersPets protects another owner's pet");
    Combat.strikeTarget(level, p, otherPet, 1);
    t.check(otherPet.isAlive(), "Original full-power iaido respects enabled others-pet protection");
    ServerConfig.get().protectPets = false;
    t.check(
        TargetRules.allowed(p, pet) && !TargetRules.allowed(p, otherPet),
        "Own and others-pet protection are independent");
    ServerConfig.get().protectPets = true;
    ServerConfig.get().protectOthersPets = false;
    var allied = t.probe(2);
    var scoreboard = level.getScoreboard();
    var team = scoreboard.getPlayerTeam("oboro_qa");
    if (team == null) team = scoreboard.addPlayerTeam("oboro_qa");
    scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
    scoreboard.addPlayerToTeam(allied.getScoreboardName(), team);
    t.check(!TargetRules.allowed(p, allied), "Same-team entities are protected");
    scoreboard.removePlayerFromTeam(p.getScoreboardName(), team);
    scoreboard.removePlayerFromTeam(allied.getScoreboardName(), team);
    allied.discard();
    var exempt = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
    exempt.setPermanentlyInvulnerable(true);
    t.check(exempt.is(TargetRules.FINISHER_EXEMPT), "Finisher exemption tag loads from a datapack");
    Combat.strikeTarget(level, p, exempt, 1);
    t.check(exempt.isAlive(), "Exempt invulnerable target is not forced through execution");
    var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
    var golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
    t.check(
        !TargetRules.allowed(p, villager) && !TargetRules.allowed(p, golem),
        "Default original and dual target rules protect villagers and golems");
    ServerConfig.get().protectVillagers = false;
    ServerConfig.get().protectGolems = false;
    t.check(
        TargetRules.allowed(p, villager) && TargetRules.allowed(p, golem),
        "Friendly protection can be explicitly disabled");
    ServerConfig.get().protectVillagers = true;
    ServerConfig.get().protectGolems = true;
    for (Item blade : List.of(Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING)) {
      p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
      t.check(DualCombat.equipped(p), "New main blade pairs with wakizashi: " + blade);
    }
    Item[] metals = {Items.COPPER_INGOT, Items.PACKED_ICE, Items.ENDER_PEARL};
    Item[] gems = {Items.REDSTONE, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD};
    Item[] blades = {Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING};
    for (int i = 0; i < 3; i++) {
      var input =
          CraftingInput.of(
              3,
              3,
              List.of(
                  ItemStack.EMPTY,
                  ItemStack.EMPTY,
                  new ItemStack(Items.DIAMOND),
                  ItemStack.EMPTY,
                  new ItemStack(metals[i]),
                  new ItemStack(Items.DIAMOND),
                  new ItemStack(Items.STICK),
                  new ItemStack(gems[i]),
                  ItemStack.EMPTY));
      var recipe = t.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
      t.check(
          recipe.isPresent() && recipe.get().value().assemble(input).is(blades[i]),
          "New Overworld recipe assembles correct blade: " + blades[i]);
    }
    Path config = Path.of("config/oborokage-server.properties");
    String old = Files.readString(config);
    try {
      Files.writeString(
          config,
          "guardWindowTicks=500\n"
              + "terrainMaxBlocks=99999\n"
              + "intentGainMultiplier=NaN\n"
              + "allowPlayerDamage=wat\n");
      ServerConfig.load();
      t.check(
          ServerConfig.get().guardWindowTicks == 10
              && ServerConfig.get().terrainMaxBlocks == 128
              && ServerConfig.get().intentGainMultiplier == 1
              && !Oborokage.allowPlayerDamage,
          "Config clamps bounds and handles invalid numbers/booleans");
      Files.writeString(config, "bad=\\uXYZZ\n");
      ServerConfig.load();
      t.check(
          ServerConfig.get().guardWindowTicks == 4 && !Oborokage.allowPlayerDamage,
          "Malformed config falls back without crashing");
    } finally {
      Files.writeString(config, old);
      ServerConfig.load();
    }
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
    Combat.clear();
    target = t.probe(2);
    t.act(1);
    int initial = DualCombat.state(p).stamina();
    t.act(2);
    t.act(1);
    t.check(
        DualCombat.snapshot(p).move() != Move.GUARD && DualCombat.state(p).stamina() == initial,
        "Immediate guard rearm is rejected and grants no stamina");
    Combat.clear();
    t.act(1);
    var arrow =
        new net.minecraft.world.entity.projectile.arrow.Arrow(
            level, .5, 101, 2, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
    arrow.setOwner(target);
    arrow.setDeltaMovement(0, 0, -3);
    t.check(
        DualCombat.reflectArrow(arrow, p)
            && arrow.getOwner() == p
            && arrow.getDeltaMovement().z > 0,
        "Precise guard reflects approaching arrow with new ownership");
    int intent = DualCombat.state(p).intent();
    var arrow2 =
        new net.minecraft.world.entity.projectile.arrow.Arrow(
            level, .5, 101, 2, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
    arrow2.setOwner(target);
    arrow2.setDeltaMovement(0, 0, -3);
    DualCombat.reflectArrow(arrow2, p);
    t.check(
        DualCombat.state(p).intent() == intent,
        "Same-source parries cannot repeatedly reward intent");
    arrow.discard();
    arrow2.discard();
    target.discard();
    Combat.clear();
  }
}

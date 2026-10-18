package oborokage.check;

import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import oborokage.*;

public final class ServerChecks implements ModInitializer {
  MinecraftServer server;
  ServerLevel level;
  Fake player;
  int age, stage, sequence;
  Probe target, behind;
  double startZ;
  List<String> results = new ArrayList<>();
  ExpansionChecks expansion;
  UltimateChecks ultimates;
  SoloChecks solo;

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        s -> {
          server = s;
          level = s.overworld();
        });
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (server == null || stage == 99) return;
          try {
            tick();
          } catch (Throwable t) {
            t.printStackTrace();
            results.add("FAIL " + t);
            finish();
          }
        });
  }

  void check(boolean value, String s) {
    if (!value) throw new AssertionError(s);
    results.add("PASS " + s);
    System.out.println("TACHI_CHECK PASS " + s);
  }

  Probe probe(double z) {
    Probe e = new Probe(level);
    e.setPos(.5, 100, z);
    e.setNoAi(true);
    e.setNoGravity(true);
    e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
    e.setPersistenceRequired();
    e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
    e.setHealth(1024);
    level.addFreshEntity(e);
    return e;
  }

  void tick() throws Exception {
    if (stage >= 5 && player != null) Feats.tick(player);
    if (++age > 600) throw new AssertionError("Timeout stage " + stage);
    if (stage == 0) {
      check(
          level.registryAccess().lookupOrThrow(Registries.ITEM) != null,
          oborokage.compat.Compat.TARGET + " temporary world and dynamic registries load");
      check(
          BuiltInRegistries.ITEM.keySet().stream()
                  .filter(i -> i.getNamespace().equals("oborokage"))
                  .count()
              == 5,
          "Exactly five weapons");
      CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
      var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Oborokage.id("equipment"));
      check(
          tab.getDisplayItems().iterator().next().is(Oborokage.TACHI),
          "Independent Oborokage tab begins with tachi");
      check(
          tab.getSearchTabDisplayItems().stream().anyMatch(i -> i.is(Oborokage.TACHI)),
          "Creative search includes tachi");
      check(
          tab.getDisplayItems().size() == 5
              && tab.getDisplayItems().stream().anyMatch(i -> i.is(Oborokage.WAKIZASHI)),
          "Independent tab contains five blades");
      check(
          !BuiltInRegistries.CREATIVE_MODE_TAB
              .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("combat"))
              .getDisplayItems()
              .stream()
              .anyMatch(i -> i.is(Oborokage.TACHI)),
          "No duplicate tachi in vanilla Combat tab");
      check(
          Oborokage.TACHI.getDefaultInstance().getMaxDamage() == 4096,
          "Original 4096 durability retained");
      for (String id :
          List.of(
              "first_moon",
              "twin_moons",
              "alternating_finish",
              "borrowed_blade",
              "decisive_step",
              "three_breaths",
              "break_and_cut",
              "twin_moon_ultimate",
              "flawless_night",
              "moon_still_hangs"))
        check(server.getAdvancements().get(Oborokage.id(id)) != null, "Advancement loads: " + id);
      recipes();
      var ench = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
      for (var item : List.of(Oborokage.TACHI, Oborokage.WAKIZASHI))
        for (String id : List.of("mending", "unbreaking", "sharpness"))
          check(
              ench.getOrThrow(
                      net.minecraft.resources.ResourceKey.create(
                          Registries.ENCHANTMENT,
                          net.minecraft.resources.Identifier.withDefaultNamespace(id)))
                  .value()
                  .isSupportedItem(new ItemStack(item)),
              "Blade supports " + id + " " + item);

      check(Combat.damage(1) == 1000, "Full-charge nominal damage exactly 1000");
      check(!Oborokage.allowPlayerDamage, "Server PvP defaults off");
      for (int cx = -2; cx <= 2; cx++)
        for (int cz = -2; cz <= 2; cz++)
          level.getChunkSource().updateChunkForced(new ChunkPos(cx, cz), true);
      player = new Fake(server, level);
      player.getAbilities().instabuild = false;
      player.getAbilities().invulnerable = false;
      player.setPos(.5, 100, .5);
      player.setYRot(0);
      for (int x = -4; x < 5; x++)
        for (int z = -3; z < 20; z++) {
          var b = new BlockPos(x, 99, z);
          level.getChunkAt(b);
          level.setBlock(b, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
      var e = probe(3);
      e.setPermanentlyInvulnerable(true);
      e.setAbsorptionAmount(500);
      e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 9));
      e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
      e.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
      Combat.strikeTarget(level, player, e, 1);
      check(
          !e.isAlive() && e.deaths == 1,
          "1024 HP + absorption + resistance + armor + invulnerable + totem killed with one death"
              + " callback");
      for (var type : List.of(EntityTypes.WITHER, EntityTypes.ENDER_DRAGON, EntityTypes.WARDEN)) {
        LivingEntity boss = type.create(level, EntitySpawnReason.COMMAND);
        boss.setPos(20, 110, 20);
        level.addFreshEntity(boss);
        Combat.strikeTarget(level, player, boss, 1);
        check(!boss.isAlive(), "Full charge kills " + type);
        boss.discard();
      }
      Fake friend = new Fake(server, level);
      friend.setHealth(20);
      Combat.strikeTarget(level, player, friend, 1);
      check(friend.getHealth() == 20, "PvP off protects another player");
      Combat.strikeTarget(level, player, player, 1);
      check(player.isAlive(), "Owner immune to own strike");
      target = probe(3);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      ((TachiItem) Oborokage.TACHI).releaseUsing(player.getMainHandItem(), level, player, 71980);
      stage = 1;
      age = 0;
      return;
    }
    if (stage == 1 && age == 30) {
      check(target.hits == 1, "Partial dash damages the same target exactly once");
      check(
          Math.abs(target.lastDamage - Combat.damage(.5f)) < .05
              && target.getHealth() < 1024
              && target.isAlive(),
          "Partial charge scales actual damage, input="
              + target.lastDamage
              + " health="
              + target.getHealth()
              + " max="
              + target.getMaxHealth());
      target.discard();
      player.setPos(.5, 100, .5);
      player.setYRot(0);
      player.setOnGround(true);
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 105; y++)
          level.setBlock(new BlockPos(x, y, 5), Blocks.OBSIDIAN.defaultBlockState(), 3);
      behind = probe(6.5);
      target = probe(2.5);
      Combat.start(player, 1);
      stage = 2;
      age = 0;
      return;
    }
    if (stage == 2 && age == 20)
      check(
          target.isAlive() && target.hits == 0,
          "Full dash marks the target; it stands until the sheath");
    if (stage == 2 && age == 40) {
      check(
          player.getZ() < 4.71,
          "Swept player collision stops before solid wall: z=" + player.getZ());
      check(behind.isAlive() && behind.hits == 0, "Wall blocks damage to a target behind it");
      check(!target.isAlive() && target.hits == 1, "Full dash kills visible target once");
      Combat.clear();
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 105; y++)
          level.setBlock(new BlockPos(x, y, 5), Blocks.AIR.defaultBlockState(), 3);
      var adv = server.getAdvancements().get(Oborokage.id("moon_still_hangs"));
      check(adv != null, "26.3 challenge advancement JSON loads");
      player.setPos(.5, 100, .5);
      probe(3);
      probe(6);
      probe(9);
      Combat.start(player, 1);
      stage = 3;
      age = 0;
      return;
    }
    if (stage == 3 && age == 48) {
      var a = server.getAdvancements().get(Oborokage.id("moon_still_hangs"));
      check(
          player.getAdvancements().getOrStartProgress(a).isDone(),
          "Three flawless full-charge kills award challenge after recovery");
      player.getAdvancements().revoke(a, "flawless_iaido");
      player.setPos(.5, 100, .5);
      probe(3);
      probe(6);
      probe(9);
      Combat.start(player, 1);
      net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE
          .invoker()
          .afterDamage(player, level.damageSources().generic(), 1, 1, false);
      stage = 4;
      age = 0;
      return;
    }
    if (stage == 4 && age == 48) {
      var a = server.getAdvancements().get(Oborokage.id("moon_still_hangs"));
      check(
          !player.getAdvancements().getOrStartProgress(a).isDone(),
          "Taking damage prevents flawless challenge award");
      Combat.clear();
      player.setPos(.5, 100, .5);
      player.setYRot(0);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Oborokage.TACHI));
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
      target = probe(3);
      target.setHealth(75);
      act(0);
      stage = 5;
      age = 0;
      return;
    }
    if (stage == 5) {
      if (age == 5 || age == 15) act(0);
      if (age == 40) {
        check(
            !target.isAlive() && target.hits == 4,
            "Dual chain has exactly four independent hit windows: hits="
                + target.hits
                + " health="
                + target.getHealth()
                + " pos="
                + target.position()
                + " move="
                + DualCombat.state(player).move());
        check(
            player.getMainHandItem().getDamageValue() == 2
                && player.getOffhandItem().getDamageValue() == 2,
            "Dual chain spends durability on the correct two blades: main="
                + player.getMainHandItem().getDamageValue()
                + " off="
                + player.getOffhandItem().getDamageValue()
                + " creative="
                + player.getAbilities().instabuild);
        check(Feats.has(player, "alternating_finish"), "Alternating cross finisher awards feat");
        check(Feats.has(player, "break_and_cut"), "Cross cut in broken-posture window awards feat");
        check(
            Feats.has(player, "first_moon") && Feats.has(player, "twin_moons"),
            "Weapon possession and 20 tick dual stance award roots");
        target = probe(2);
        act(1);
        stage = 6;
        age = 0;
        return;
      }
    }
    if (stage == 6) {
      if (age == 1) {
        float value = player.getHealth();
        boolean allowed =
            net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE
                .invoker()
                .allowDamage(player, level.damageSources().mobAttack(target), 6);
        check(!allowed, "Front melee perfect guard is cancelled before health damage");
        check(player.getHealth() == value, "Perfect guard does not need health refunds");
        act(2);
      }
      if (age == 44 || age == 88 || age == 132) act(1);
      if (age == 45 || age == 89 || age == 133) {
        check(
            !DualCombat.allowDamage(player, level.damageSources().mobAttack(target), 6),
            "Distinct precise guard event " + age);
        act(2);
      }
      if (age == 136) {
        check(
            Feats.has(player, "three_breaths"),
            "Three independent guards within window award challenge");
        check(DualCombat.state(player).intent() == 100, "Confirmed hits and guards fill intent");
        target.setHealth(20);
        act(0);
        stage = 7;
        age = 0;
        return;
      }
    }
    if (stage == 7 && age == 18) {
      check(
          !target.isAlive() && Feats.has(player, "borrowed_blade"),
          "Counter kills its own parried attacker and awards feat");
      target = probe(4);
      target.setHealth(1024);
      act(4);
      stage = 8;
      age = 0;
      return;
    }
    if (stage == 8 && age == 52) {
      check(!target.isAlive(), "Dual ultimate final window retains 1000 plus execution");
      check(Feats.has(player, "twin_moon_ultimate"), "Confirmed ultimate final kill awards feat");
      player.setPos(.5, 100, .5);
      act(3);
      stage = 9;
      age = 0;
      return;
    }
    if (stage == 9 && age == 8) {
      check(
          player.getZ() < -1.5, "Collision-aware backward shadow step travels at least two blocks");
      target = probe(player.getZ() + 2.0);
      target.setHealth(1024);
      act(0);
      stage = 10;
      age = 0;
      return;
    }
    if (stage == 10) {
      if (age == 22) {
        check(Feats.has(player, "decisive_step"), "Step followed by offhand hit awards feat");
        DualCombat.cancel(player);
        player.setPos(.5, 100, .5);
        var small1 = probe(2);
        small1.setHealth(5);
        var small2 = probe(2.7);
        small2.setHealth(5);
        act(0);
        stage = 11;
        age = 0;
        return;
      }
    }
    if (stage == 11 && age == 22) {
      check(
          Feats.has(player, "flawless_night"),
          "Five distinct enemy kills across actions award flawless night");
      DualCombat.cancel(player);
      int intent = DualCombat.state(player).intent();
      player.setPos(.5, 100, .5);
      boolean started = Combat.start(player, 1);
      check(started, "Old full iaido accepts immediately regardless of dual resources");
      check(DualCombat.state(player).intent() == intent, "Old iaido consumes no new intent");
      Combat.clear();
      int before = player.getMainHandItem().getDamageValue();
      var blade = (TachiItem) Oborokage.TACHI;
      check(
          !blade.releaseUsing(player.getMainHandItem(), level, player, 71997)
              && player.getMainHandItem().getDamageValue() == before,
          "Original sub-four-tick release remains invalid and free");
      check(
          blade.releaseUsing(player.getMainHandItem(), level, player, 71996)
              && player.getMainHandItem().getDamageValue() == before + 2,
          "Original four-tick minimum and two durability retained");
      for (int i = 0; i < 17; i++) player.getCooldowns().tick();
      check(
          player.getCooldowns().isOnCooldown(player.getMainHandItem()),
          "Original partial cooldown lasts at least 18 ticks");
      player.getCooldowns().tick();
      check(
          !player.getCooldowns().isOnCooldown(player.getMainHandItem()),
          "Original partial cooldown ends at 18 ticks");
      Combat.clear();
      before = player.getMainHandItem().getDamageValue();
      check(
          blade.releaseUsing(player.getMainHandItem(), level, player, 71960)
              && player.getMainHandItem().getDamageValue() == before + 8,
          "Original 40 tick full release spends eight durability");
      for (int i = 0; i < 44; i++) player.getCooldowns().tick();
      check(
          player.getCooldowns().isOnCooldown(player.getMainHandItem()),
          "Original full cooldown lasts at least 45 ticks");
      player.getCooldowns().tick();
      check(
          !player.getCooldowns().isOnCooldown(player.getMainHandItem()),
          "Original full cooldown ends at 45 ticks");
      Combat.clear();
      player.setPos(.5, 100, .5);
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 105; y++)
          level.setBlock(new BlockPos(x, y, 2), Blocks.OBSIDIAN.defaultBlockState(), 3);
      target = probe(3);
      act(0);
      stage = 12;
      age = 0;
      return;
    }
    if (stage == 12 && age == 15) {
      check(target.hits == 0, "New dual cut is blocked by solid wall");
      target.discard();
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 105; y++) {
          level.setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState(), 3);
          level.setBlock(new BlockPos(x, y, -1), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
      act(3);
      stage = 13;
      age = 0;
      return;
    }
    if (stage == 13 && age == 8) {
      check(player.getZ() >= .29, "New shadow step cannot cross a solid wall");
      for (int x = -3; x <= 3; x++)
        for (int y = 100; y < 105; y++)
          level.setBlock(new BlockPos(x, y, -1), Blocks.AIR.defaultBlockState(), 3);
      target = probe(2);
      act(0);
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      stage = 14;
      age = 0;
      return;
    }
    if (stage == 14 && age == 12) {
      check(target.hits == 0, "Switching offhand cancels pending new hit windows");
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Oborokage.WAKIZASHI));
      act(0);
      stage = 15;
      age = 0;
      return;
    }
    if (stage == 15) {
      if (age == 12) DualCombat.request(player, new ActionRequest(sequence, 0));
      if (age == 25) {
        check(target.hits == 1, "Replayed request sequence cannot execute a second attack");
        for (var probe : level.getEntitiesOfClass(Probe.class, player.getBoundingBox().inflate(48)))
          probe.discard();
        expansion = new ExpansionChecks(this);
        stage = 16;
        age = 0;
      }
      return;
    }
    if (stage == 16 && expansion.tick()) {
      ultimates = new UltimateChecks(this);
      stage = 17;
      age = 0;
      return;
    }
    if (stage == 17 && ultimates.tick()) {
      solo = new SoloChecks(this);
      stage = 18;
      age = 0;
      return;
    }
    if (stage == 18 && solo.tick()) {
      flawlessWindowBoundary();
      ReviewChecks.run(this);
      finish();
    }
  }

  void act(int a) {
    DualCombat.request(player, new ActionRequest(++sequence, a));
  }

  void flawlessWindowBoundary() throws Exception {
    var advancement = server.getAdvancements().get(Oborokage.id("flawless_night"));
    for (String criterion : advancement.value().criteria().keySet())
      player.getAdvancements().revoke(advancement, criterion);
    Feats.clear();
    Feats.hit(player, 0);
    Feats.hit(player, 1);
    var field = Feats.class.getDeclaredField("records");
    field.setAccessible(true);
    var record = ((Map<?, ?>) field.get(null)).get(player.getUUID());
    var main = record.getClass().getDeclaredField("mainHit");
    main.setAccessible(true);
    long now = level.getGameTime();
    main.setLong(record, now - 1199);
    for (int n = 0; n < 4; n++) Feats.kill(player, probe(12 + n), 200 + n, 3);
    main.setLong(record, now - 1201);
    Feats.kill(player, probe(16), 204, 2);
    check(
        !Feats.has(player, "flawless_night"),
        "Expired hand hit cannot survive through earlier kill records");
    Feats.hit(player, 0);
    Feats.kill(player, probe(17), 205, 1);
    check(
        Feats.has(player, "flawless_night"),
        "Fresh main and offhand hits restore valid sixty-second challenge window");
  }

  void recipes() {
    for (String name : List.of("tachi", "wakizashi")) {
      var input = RecipeInputs.of(name);
      var recipe =
          server
              .getRecipeManager()
              .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
      check(recipe.isPresent(), "Netherite and diamond-block recipe matches " + name);
      var result = recipe.orElseThrow().value().assemble(input);
      check(
          result.is(name.equals("tachi") ? Oborokage.TACHI : Oborokage.WAKIZASHI)
              && result.getDamageValue() == 0,
          "Recipe outputs original full-durability weapon");
    }
    // The 0.4 iron/diamond grid for the tachi must no longer craft anything.
    var cheap =
        net.minecraft.world.item.crafting.CraftingInput.of(
            3,
            3,
            List.of(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND),
                ItemStack.EMPTY,
                new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.DIAMOND),
                new ItemStack(Items.STICK),
                new ItemStack(Items.AMETHYST_SHARD),
                ItemStack.EMPTY));
    check(
        server
            .getRecipeManager()
            .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, cheap, level)
            .isEmpty(),
        "The old iron and diamond recipe no longer crafts a blade");
  }

  void finish() {
    stage = 99;
    try {
      Files.writeString(Path.of("server-results.txt"), String.join("\n", results) + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
    server.halt(false);
  }

  static class Probe extends Zombie {
    int hits, deaths;
    float lastDamage;

    Probe(Level l) {
      super(l);
    }

    @Override
    public boolean hurtServer(ServerLevel l, DamageSource s, float d) {
      if (s.getEntity() instanceof Fake) {
        hits++;
        lastDamage = d;
        System.out.println(
            "TACHI_PROBE damage="
                + d
                + " healthBefore="
                + getHealth()
                + " armor="
                + getArmorValue());
      }
      return super.hurtServer(l, s, d);
    }

    @Override
    public void die(DamageSource s) {
      deaths++;
      super.die(s);
    }
  }

  static class Fake extends ServerPlayer {
    final ItemCooldowns cool = new ItemCooldowns();

    Fake(MinecraftServer s, ServerLevel l) {
      super(
          s, l, new GameProfile(UUID.randomUUID(), "TachiTest"), ClientInformation.createDefault());
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel l, DamageSource source) {
      return getAbilities().invulnerable;
    }

    @Override
    public ItemCooldowns getCooldowns() {
      return cool;
    }

    @Override
    public void sendSystemMessage(Component c) {}

    @Override
    public void sendSystemMessage(Component c, boolean b) {}
  }
}

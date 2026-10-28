package oborokage;

import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class ServerConfig {
  public static final class Values {
    public boolean terrainCutEnabled = true;
    public int terrainMaxBlocks = 32, terrainBlocksPerTick = 4;
    public double terrainReach = 5, terrainMaxHardness = 5;
    public boolean allowPlayerDamage = false;
    public boolean protectPets = true;
    public boolean protectOthersPets = false;
    public boolean protectAllies = true;
    public boolean protectVillagers = true;
    public boolean protectGolems = true;
    public boolean finisherRetry = true;
    public boolean announceAdvancements = false;
    public boolean reflectArrows = true;
    public int guardWindowTicks = 4;
    public int guardLatencyCapTicks = 3;
    public int guardRearmTicks = 20;
    public int emptyGuardRecoveryTicks = 24;
    public int guardEntryCost = 8;
    public int perfectGuardCost = 8;
    public int staminaRegenDelayTicks = 20;
    public int parryRewardCooldownTicks = 40;
    public int parryIntent = 20;
    public int hitIntent = 12;
    public double intentGainMultiplier = 1.0;
    public double dodgeDistance = 2.8;
    public int dodgeCooldownTicks = 16;
    public int postureDurationTicks = 30;
    public int ultimateCooldownTicks = 100;
    public double ultimateReach = 6.5;
    public double dualDamageMultiplier = 1.0;
    public int maxTargetsPerWindow = 64;
    public boolean bladeArts = true;
    public boolean guardProjectiles = true;
    public int perfectDodgeTicks = 3;
    public int perfectDodgeIntent = 10;
    public int riposteWindowTicks = 20;
    public double riposteMultiplier = 1.5;
    public int voidMarkTicks = 100;
    public double voidMarkBonus = 1.25;
    public int voidMarkIntent = 8;
    public boolean ultimateGuard = true;
    public int zanshinTicks = 100;
    public int techniqueIntent = 6;
    public int iaidoIntent = 10;
    // 1.0: blade abilities cost hunger; at or below this food level the blade's arts fall silent.
    public double hungerCost = 1.0;
    public int fatigueFoodLevel = 6;
    public int plungeCooldownTicks = 20;
    public int stringDashCooldownTicks = 40;
    public int honeTicks = 6000;
    public boolean swordsmanSpawns = true;
    // 1.1.0: 百鬼夜行, the full-moon parade of the 樱下鬼 (NightParade).
    public boolean nightParade = true;
    public double nightParadeChance = .2;
    public int nightParadeMax = 12;
  }

  private static volatile Values current = new Values();

  public static Values get() {
    return current;
  }

  public static void load() {
    Properties p =
        ConfigIO.read(
            FabricLoader.getInstance().getConfigDir().resolve("oborokage-server.properties"),
            "# Oborokage settings. Server: /reload applies changes. Client: restart client.\n"
                + "allowPlayerDamage=false\n"
                + "protectPets=true\n"
                + "protectOthersPets=false\n"
                + "protectAllies=true\n"
                + "protectVillagers=true\n"
                + "protectGolems=true\n"
                + "finisherRetry=true\n"
                + "announceAdvancements=false\n"
                + "reflectArrows=true\n"
                + "guardWindowTicks=4\n"
                + "guardLatencyCapTicks=3\n"
                + "guardRearmTicks=20\n"
                + "emptyGuardRecoveryTicks=24\n"
                + "guardEntryCost=8\n"
                + "perfectGuardCost=8\n"
                + "staminaRegenDelayTicks=20\n"
                + "parryRewardCooldownTicks=40\n"
                + "parryIntent=20\n"
                + "hitIntent=12\n"
                + "intentGainMultiplier=1.0\n"
                + "dodgeDistance=2.8\n"
                + "dodgeCooldownTicks=16\n"
                + "postureDurationTicks=30\n"
                + "ultimateCooldownTicks=100\n"
                + "ultimateReach=6.5\n"
                + "dualDamageMultiplier=1.0\n"
                + "maxTargetsPerWindow=64\n"
                + "bladeArts=true\n"
                + "guardProjectiles=true\n"
                + "perfectDodgeTicks=3\n"
                + "perfectDodgeIntent=10\n"
                + "riposteWindowTicks=20\n"
                + "riposteMultiplier=1.5\n"
                + "voidMarkTicks=100\n"
                + "voidMarkBonus=1.25\n"
                + "voidMarkIntent=8\n"
                + "ultimateGuard=true\n"
                + "zanshinTicks=100\n"
                + "techniqueIntent=6\n"
                + "iaidoIntent=10\n"
                + "hungerCost=1.0\n"
                + "fatigueFoodLevel=6\n"
                + "plungeCooldownTicks=20\n"
                + "stringDashCooldownTicks=40\n"
                + "honeTicks=6000\n"
                + "swordsmanSpawns=true\n"
                + "nightParade=true\n"
                + "nightParadeChance=0.2\n"
                + "nightParadeMax=12\n"
                + "terrainCutEnabled=true\n"
                + "terrainMaxBlocks=32\n"
                + "terrainBlocksPerTick=4\n"
                + "terrainReach=5\n"
                + "terrainMaxHardness=5\n");
    Values next = new Values();
    next.allowPlayerDamage = ConfigIO.bool(p, "allowPlayerDamage", next.allowPlayerDamage);
    next.protectPets = ConfigIO.bool(p, "protectPets", next.protectPets);
    next.protectOthersPets = ConfigIO.bool(p, "protectOthersPets", next.protectOthersPets);
    next.protectAllies = ConfigIO.bool(p, "protectAllies", next.protectAllies);
    next.protectVillagers = ConfigIO.bool(p, "protectVillagers", next.protectVillagers);
    next.protectGolems = ConfigIO.bool(p, "protectGolems", next.protectGolems);
    next.finisherRetry = ConfigIO.bool(p, "finisherRetry", next.finisherRetry);
    next.announceAdvancements = ConfigIO.bool(p, "announceAdvancements", next.announceAdvancements);
    next.reflectArrows = ConfigIO.bool(p, "reflectArrows", next.reflectArrows);
    next.guardWindowTicks =
        (int) ConfigIO.number(p, "guardWindowTicks", next.guardWindowTicks, 1, 10);
    next.guardLatencyCapTicks =
        (int) ConfigIO.number(p, "guardLatencyCapTicks", next.guardLatencyCapTicks, 0, 3);
    next.guardRearmTicks =
        (int) ConfigIO.number(p, "guardRearmTicks", next.guardRearmTicks, 12, 100);
    next.emptyGuardRecoveryTicks =
        (int) ConfigIO.number(p, "emptyGuardRecoveryTicks", next.emptyGuardRecoveryTicks, 12, 100);
    next.guardEntryCost = (int) ConfigIO.number(p, "guardEntryCost", next.guardEntryCost, 1, 50);
    next.perfectGuardCost =
        (int) ConfigIO.number(p, "perfectGuardCost", next.perfectGuardCost, 1, 50);
    next.staminaRegenDelayTicks =
        (int) ConfigIO.number(p, "staminaRegenDelayTicks", next.staminaRegenDelayTicks, 0, 200);
    next.parryRewardCooldownTicks =
        (int)
            ConfigIO.number(p, "parryRewardCooldownTicks", next.parryRewardCooldownTicks, 12, 200);
    next.parryIntent = (int) ConfigIO.number(p, "parryIntent", next.parryIntent, 0, 100);
    next.hitIntent = (int) ConfigIO.number(p, "hitIntent", next.hitIntent, 0, 100);
    next.intentGainMultiplier =
        (double) ConfigIO.number(p, "intentGainMultiplier", next.intentGainMultiplier, 0, 5);
    next.dodgeDistance = (double) ConfigIO.number(p, "dodgeDistance", next.dodgeDistance, 1, 6);
    next.dodgeCooldownTicks =
        (int) ConfigIO.number(p, "dodgeCooldownTicks", next.dodgeCooldownTicks, 6, 200);
    next.postureDurationTicks =
        (int) ConfigIO.number(p, "postureDurationTicks", next.postureDurationTicks, 5, 200);
    next.ultimateCooldownTicks =
        (int) ConfigIO.number(p, "ultimateCooldownTicks", next.ultimateCooldownTicks, 48, 1200);
    next.ultimateReach = (double) ConfigIO.number(p, "ultimateReach", next.ultimateReach, 4.8, 10);
    next.dualDamageMultiplier =
        (double) ConfigIO.number(p, "dualDamageMultiplier", next.dualDamageMultiplier, 0.1, 10);
    next.maxTargetsPerWindow =
        (int) ConfigIO.number(p, "maxTargetsPerWindow", next.maxTargetsPerWindow, 8, 256);
    next.bladeArts = ConfigIO.bool(p, "bladeArts", next.bladeArts);
    next.guardProjectiles = ConfigIO.bool(p, "guardProjectiles", next.guardProjectiles);
    next.perfectDodgeTicks =
        (int) ConfigIO.number(p, "perfectDodgeTicks", next.perfectDodgeTicks, 0, 5);
    next.perfectDodgeIntent =
        (int) ConfigIO.number(p, "perfectDodgeIntent", next.perfectDodgeIntent, 0, 100);
    next.riposteWindowTicks =
        (int) ConfigIO.number(p, "riposteWindowTicks", next.riposteWindowTicks, 0, 60);
    next.riposteMultiplier = ConfigIO.number(p, "riposteMultiplier", next.riposteMultiplier, 1, 3);
    next.voidMarkTicks = (int) ConfigIO.number(p, "voidMarkTicks", next.voidMarkTicks, 20, 400);
    next.voidMarkBonus = ConfigIO.number(p, "voidMarkBonus", next.voidMarkBonus, 1, 3);
    next.voidMarkIntent = (int) ConfigIO.number(p, "voidMarkIntent", next.voidMarkIntent, 0, 100);
    next.ultimateGuard = ConfigIO.bool(p, "ultimateGuard", next.ultimateGuard);
    next.zanshinTicks = (int) ConfigIO.number(p, "zanshinTicks", next.zanshinTicks, 0, 400);
    next.techniqueIntent =
        (int) ConfigIO.number(p, "techniqueIntent", next.techniqueIntent, 0, 100);
    next.iaidoIntent = (int) ConfigIO.number(p, "iaidoIntent", next.iaidoIntent, 0, 100);
    next.hungerCost = (double) ConfigIO.number(p, "hungerCost", next.hungerCost, 0, 10);
    next.fatigueFoodLevel =
        (int) ConfigIO.number(p, "fatigueFoodLevel", next.fatigueFoodLevel, -1, 20);
    next.plungeCooldownTicks =
        (int) ConfigIO.number(p, "plungeCooldownTicks", next.plungeCooldownTicks, 0, 200);
    next.stringDashCooldownTicks =
        (int) ConfigIO.number(p, "stringDashCooldownTicks", next.stringDashCooldownTicks, 0, 400);
    next.honeTicks = (int) ConfigIO.number(p, "honeTicks", next.honeTicks, 0, 72000);
    next.swordsmanSpawns = ConfigIO.bool(p, "swordsmanSpawns", next.swordsmanSpawns);
    next.nightParade = ConfigIO.bool(p, "nightParade", next.nightParade);
    next.nightParadeChance = ConfigIO.number(p, "nightParadeChance", next.nightParadeChance, 0, 1);
    next.nightParadeMax = (int) ConfigIO.number(p, "nightParadeMax", next.nightParadeMax, 1, 24);
    next.terrainCutEnabled = ConfigIO.bool(p, "terrainCutEnabled", true);
    next.terrainMaxBlocks = (int) ConfigIO.number(p, "terrainMaxBlocks", 32, 1, 128);
    next.terrainBlocksPerTick = (int) ConfigIO.number(p, "terrainBlocksPerTick", 4, 1, 8);
    next.terrainReach = ConfigIO.number(p, "terrainReach", 5, 2, 6);
    next.terrainMaxHardness = ConfigIO.number(p, "terrainMaxHardness", 5, 1, 50);
    current = next;
    Oborokage.allowPlayerDamage = next.allowPlayerDamage;
  }

  private ServerConfig() {}
}

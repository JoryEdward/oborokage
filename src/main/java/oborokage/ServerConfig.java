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
                + "terrainCutEnabled=true\n"
                + "terrainMaxBlocks=32\n"
                + "terrainBlocksPerTick=4\n"
                + "terrainReach=5\n"
                + "terrainMaxHardness=5\n");
    Values next = new Values();
    next.allowPlayerDamage = ConfigIO.bool(p, "allowPlayerDamage", next.allowPlayerDamage);
    next.protectPets = ConfigIO.bool(p, "protectPets", next.protectPets);
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

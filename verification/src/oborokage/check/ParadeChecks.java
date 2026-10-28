package oborokage.check;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * 1.1.0's 百鬼夜行: only on a full-moon night; a parade is a 鬼将 (×1.35, 90 health, attack 11, named and
 * shown) and six to nine 樱下鬼 (no player near), twelve at most; the 鬼将 marches toward its stop and
 * the rest keep up behind it; marching, they take no foe; a blow at one turns all of them; at dawn
 * those not fighting go out, those fighting stay; the 鬼将 leaves 2-3 intent crystals and 3-5 剑魄
 * besides; a player's meeting is kept for a day; the settings' defaults.
 */
final class ParadeChecks {
  /** Far from the other checks; its chunks are kept loaded for the march, then let go. */
  private static final int X = 16, Z = 160, R = 6;

  private final ServerChecks t;
  private int age;
  private BlossomOni leader, second;
  private Mob foe;
  private Vec3 start;
  private double before;
  private static final String[] TIMES = {"6000", "42000", "18000"};
  private final boolean[] moon = new boolean[3];
  private final StringBuilder seen = new StringBuilder();

  ParadeChecks(ServerChecks t) {
    this.t = t;
  }

  private void time(String ticks) {
    t.server
        .getCommands()
        .performPrefixedCommand(t.server.createCommandSourceStack(), "time set " + ticks);
  }

  private void force(boolean on) {
    for (int cx = (X >> 4) - R; cx <= (X >> 4) + R; cx++)
      for (int cz = (Z >> 4) - R; cz <= (Z >> 4) + R; cz++)
        t.level.getChunkSource().updateChunkForced(new ChunkPos(cx, cz), on);
  }

  private List<BlossomOni> of(BlossomOni lead) {
    return t.level.getEntitiesOfClass(
        BlossomOni.class,
        new AABB(X - 120, -70, Z - 120, X + 120, 40, Z + 120),
        o -> o.isAlive() && lead.getUUID().equals(o.parade().mark()));
  }

  boolean tick() throws Exception {
    ServerLevel level = t.level;
    age++;
    BlockPos here = new BlockPos(X, -60, Z);
    if (age == 1) {
      force(true);
      var config = ServerConfig.get();
      t.check(
          config.nightParade && config.nightParadeChance == .2 && config.nightParadeMax == 12,
          "The parade's settings default to on, 20% a look, twelve at most");
      t.check(
          t.server.getAdvancements().get(Oborokage.id("night_parade")) != null,
          "Its advancement 百鬼夜行 loads");
      time(TIMES[0]);
      return false;
    }
    if (age >= 3 && age <= 7 && age % 2 == 1) {
      // The sky darkens on the tick after the time is set: read it then.
      int i = (age - 3) / 2;
      moon[i] = NightParade.fullMoonNight(level, here);
      seen.append(TIMES[i]).append(": dark ").append(level.isDarkOutside()).append(", ");
      if (i < 2) {
        time(TIMES[i + 1]);
        return false;
      }
      t.check(!moon[0] && !moon[1] && moon[2], "Only a full-moon night will do: " + seen);
      t.check(!NightParade.metLately(level, t.player), "A player who has met none may meet one");
      ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) t.player)
          .setAttached(NightParade.MET, level.getGameTime());
      t.check(NightParade.metLately(level, t.player), "Having met one, not again within a day");
      ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) t.player)
          .removeAttached(NightParade.MET);
      return false;
    }
    if (age == 20) {
      leader = NightParade.start(level, Vec3.atBottomCenterOf(here));
      t.check(leader != null, "A parade comes out on open ground");
      if (leader == null) {
        force(false);
        return true;
      }
      List<BlossomOni> all = of(leader);
      long followers = all.stream().filter(o -> o != leader).count();
      t.check(
          leader.elite()
              && Math.abs(leader.getScale() - 1.35f) < .01
              && leader.getMaxHealth() == 90
              && leader.getAttributeValue(Attributes.ATTACK_DAMAGE) == 11
              && leader.shouldShowName()
              && leader.parade().leads(),
          "Its 鬼将: ×"
              + leader.getScale()
              + ", health "
              + leader.getMaxHealth()
              + ", attack "
              + leader.getAttributeValue(Attributes.ATTACK_DAMAGE)
              + ", named "
              + leader.getName().getString());
      t.check(
          followers >= 6 && followers <= 9 && all.size() <= 12,
          "Six to nine 樱下鬼 follow it (no player near): " + followers);
      start = leader.position();
      before = Vec3.atBottomCenterOf(leader.parade().waypoint()).distanceTo(start);
      foe = Content.SWORDSMAN.create(level, EntitySpawnReason.COMMAND);
      BlossomOni last = all.get(all.size() - 1);
      foe.setPos(last.getX() + 4, last.getY(), last.getZ());
      foe.setNoAi(true);
      foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
      foe.setHealth(1000);
      level.addFreshEntity(foe);
    }
    if (age == 140) {
      double now = Vec3.atBottomCenterOf(leader.parade().waypoint()).distanceTo(leader.position());
      List<BlossomOni> all = of(leader);
      double far = 0;
      for (BlossomOni o : all) far = Math.max(far, o.distanceTo(leader));
      t.check(
          before - now >= 6 && far < 2.5 * 12 + 8,
          "The 鬼将 marches toward its stop and the rest keep up: "
              + String.format(Locale.ROOT, "%.1f -> %.1f, farthest %.1f", before, now, far));
      boolean quiet = all.stream().allMatch(o -> o.getTarget() == null && !o.parade().provoked());
      t.check(quiet, "Marching, they take no foe, not even a swordsman beside the column");
      BlossomOni struck = all.get(all.size() - 1);
      struck.hurtServer(level, level.damageSources().mobAttack(foe), 1);
      long turned =
          of(leader).stream().filter(o -> o.parade().provoked() && o.getTarget() == foe).count();
      t.check(
          turned == of(leader).size(),
          "A blow at one turns the whole parade on the attacker: "
              + turned
              + " of "
              + of(leader).size());
      second = NightParade.start(level, Vec3.atBottomCenterOf(here.offset(40, 0, 0)));
    }
    if (age == 150) {
      time("1000");
    }
    if (age == 200) {
      long gone = second == null ? -1 : of(second).size();
      long fighting = of(leader).stream().filter(o -> o.getTarget() != null).count();
      t.check(
          gone == 0 && fighting > 0,
          "At dawn a parade not fighting goes out; one fighting stays: "
              + gone
              + " left of the quiet one, "
              + fighting
              + " fighting");
      Vec3 at = leader.position();
      leader.kill(level);
      int crystals = 0, souls = 0;
      for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3))) {
        if (e.getItem().is(Content.INTENT_CRYSTAL)) crystals += e.getItem().getCount();
        if (e.getItem().is(Content.BLADE_SOUL)) souls += e.getItem().getCount();
        e.discard();
      }
      t.check(
          crystals >= 2 && crystals <= 4 && souls >= 3 && souls <= 6,
          "The 鬼将 leaves 2-3 intent crystals and 3-5 剑魄 besides: " + crystals + ", " + souls);
      for (var e :
          level.getEntitiesOfClass(
              Swordsman.class, new AABB(X - 120, -70, Z - 120, X + 120, 40, Z + 120))) e.discard();
      force(false);
      return true;
    }
    return false;
  }
}

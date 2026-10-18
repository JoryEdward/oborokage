package oborokage;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Server-confirmed feats; only vanilla advancements persist across sessions. */
public final class Feats {
  private static final Map<UUID, Record> records = new HashMap<>();

  private static final class Record {
    int equipped;
    long nextRootCheck;
    long mainHit = -1000000, offHit = -1000000;
    final Deque<Long> parries = new ArrayDeque<>();
    final Deque<Kill> kills = new ArrayDeque<>();
  }

  private record Kill(long tick, UUID target, long action, int hands) {}

  private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>
      HOSTILES =
          net.minecraft.tags.TagKey.create(
              net.minecraft.core.registries.Registries.ENTITY_TYPE, Oborokage.id("hostiles"));

  public static boolean enemy(LivingEntity t) {
    return t instanceof Enemy || t.is(HOSTILES);
  }

  public static void clear() {
    records.clear();
  }

  public static void retain(Set<UUID> ids) {
    records.keySet().retainAll(ids);
  }

  public static void award(ServerPlayer p, String id) {
    var a = p.level().getServer().getAdvancements().get(Oborokage.id(id));
    if (a != null)
      for (String criterion : a.value().criteria().keySet())
        p.getAdvancements().award(a, criterion);
  }

  /** Awards one criterion of a multi-part advancement (e.g. one blade of "all_ultimates"). */
  public static void awardCriterion(ServerPlayer p, String id, String criterion) {
    var a = p.level().getServer().getAdvancements().get(Oborokage.id(id));
    if (a != null && a.value().criteria().containsKey(criterion))
      p.getAdvancements().award(a, criterion);
  }

  public static boolean has(ServerPlayer p, String id) {
    var a = p.level().getServer().getAdvancements().get(Oborokage.id(id));
    return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
  }

  public static void tick(ServerPlayer p) {
    Record r = records.computeIfAbsent(p.getUUID(), x -> new Record());
    long now = p.level().getGameTime();
    if (now >= r.nextRootCheck) {
      r.nextRootCheck = now + 20;
      if (!has(p, "five_paths")) {
        Set<net.minecraft.world.item.Item> blades = new HashSet<>();
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
          var item = p.getInventory().getItem(slot);
          if (Oborokage.mainBlade(item) || item.is(Oborokage.WAKIZASHI)) blades.add(item.getItem());
        }
        if (blades.size() == 5) award(p, "five_paths");
      }
      if (!has(p, "first_moon")) {
        boolean obtained = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
          var stack = p.getInventory().getItem(i);
          if (Oborokage.mainBlade(stack) || stack.is(Oborokage.WAKIZASHI)) {
            obtained = true;
            break;
          }
        }
        if (obtained || has(p, "moon_still_hangs")) award(p, "first_moon");
      }
    }
    if (DualCombat.equipped(p)) {
      if (++r.equipped == 20) award(p, "twin_moons");
    } else r.equipped = 0;
  }

  public static void damaged(ServerPlayer p) {
    Record r = records.computeIfAbsent(p.getUUID(), x -> new Record());
    r.parries.clear();
    r.kills.clear();
    r.mainHit = r.offHit = -1000000;
  }

  public static void parry(ServerPlayer p) {
    Record r = records.computeIfAbsent(p.getUUID(), x -> new Record());
    long now = p.level().getGameTime();
    r.parries.addLast(now);
    while (r.parries.size() > 32) r.parries.removeFirst();
    while (!r.parries.isEmpty() && r.parries.getFirst() < now - 400) r.parries.removeFirst();
    if (r.parries.size() >= 3) award(p, "three_breaths");
  }

  public static void hit(ServerPlayer p, int hand) {
    var r = records.computeIfAbsent(p.getUUID(), x -> new Record());
    if (hand == 0) r.mainHit = p.level().getGameTime();
    else r.offHit = p.level().getGameTime();
  }

  public static void kill(ServerPlayer p, LivingEntity target, long action, int hands) {
    if (!enemy(target)) return;
    Record r = records.computeIfAbsent(p.getUUID(), x -> new Record());
    long now = p.level().getGameTime();
    while (!r.kills.isEmpty() && r.kills.getFirst().tick < now - 1200) r.kills.removeFirst();
    hands = (r.mainHit >= now - 1200 ? 1 : 0) | (r.offHit >= now - 1200 ? 2 : 0);
    if (r.kills.stream().noneMatch(k -> k.target.equals(target.getUUID())))
      r.kills.addLast(new Kill(now, target.getUUID(), action, hands));
    while (r.kills.size() > 64) r.kills.removeFirst();
    int flags = 0;
    Set<Long> actions = new HashSet<>();
    for (Kill k : r.kills) {
      flags |= k.hands;
      actions.add(k.action);
    }
    // Earlier kills must not extend the lifetime of a hand hit beyond the current window.
    if (r.kills.size() >= 5
        && actions.size() >= 3
        && flags == 3
        && r.mainHit >= now - 1200
        && r.offHit >= now - 1200) award(p, "flawless_night");
  }
}

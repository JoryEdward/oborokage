package oborokage;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public final class TargetRules {
  public static final TagKey<EntityType<?>> FINISHER_EXEMPT =
      TagKey.create(Registries.ENTITY_TYPE, Oborokage.id("finisher_exempt"));

  public static boolean allowed(ServerPlayer p, LivingEntity e) {
    if (e == p || !e.isAlive() || e.isSpectator()) return false;
    if (e instanceof Player q)
      return Oborokage.allowPlayerDamage && !q.getAbilities().instabuild && p.canHarmPlayer(q);
    var c = ServerConfig.get();
    if (c.protectAllies && p.isAlliedTo(e)) return false;
    if (e instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
      boolean own = pet.getOwnerReference().getUUID().equals(p.getUUID());
      if (own ? c.protectPets : c.protectOthersPets) return false;
    }
    if (c.protectVillagers && e instanceof AbstractVillager) return false;
    return !c.protectGolems || !(e instanceof IronGolem);
  }

  private TargetRules() {}
}

package oborokage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class WeaponDamage {
  public static float scaled(ServerPlayer p, ItemStack blade, float base) {
    AttributeInstance actual = new AttributeInstance(Attributes.ATTACK_DAMAGE, a -> {});
    actual.replaceFrom(p.getAttribute(Attributes.ATTACK_DAMAGE));
    p.getMainHandItem()
        .forEachModifier(
            EquipmentSlot.MAINHAND,
            (a, m) -> {
              if (a.equals(Attributes.ATTACK_DAMAGE)) actual.removeModifier(m.id());
            });
    AttributeInstance normal = new AttributeInstance(Attributes.ATTACK_DAMAGE, a -> {});
    normal.setBaseValue(1);
    blade.forEachModifier(
        EquipmentSlot.MAINHAND,
        (a, m) -> {
          if (a.equals(Attributes.ATTACK_DAMAGE)) {
            actual.removeModifier(m.id());
            actual.addTransientModifier(m);
            normal.addTransientModifier(m);
          }
        });
    return (float)
        (base
            * Math.max(0, actual.getValue())
            / Math.max(1, normal.getValue())
            * ServerConfig.get().dualDamageMultiplier);
  }

  public static boolean apply(
      ServerPlayer p, LivingEntity target, ItemStack blade, float amount, boolean sweeping) {
    ServerLevel level = (ServerLevel) p.level();
    DamageSource source =
        new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p) {
          @Override
          public ItemStack getWeaponItem() {
            return blade;
          }
        };
    float damage = EnchantmentHelper.modifyDamage(level, blade, target, source, amount);
    if (sweeping) {
      var enchant =
          level
              .registryAccess()
              .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
              .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE);
      int n = EnchantmentHelper.getItemEnchantmentLevel(enchant, blade);
      if (n > 0) damage *= 1 + .25f * n / (n + 1f);
    }
    boolean hit = target.hurtServer(level, source, damage);
    if (hit) {
      EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, blade);
      float kb = EnchantmentHelper.modifyKnockback(level, blade, target, source, 0);
      if (kb > 0)
        target.knockback(
            kb * .5,
            Math.sin(Math.toRadians(p.getYRot())),
            -Math.cos(Math.toRadians(p.getYRot())),
            source,
            damage);
    }
    return hit;
  }

  private WeaponDamage() {}
}

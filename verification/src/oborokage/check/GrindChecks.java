package oborokage.check;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import oborokage.*;

/**
 * 1.1.0: a kazuuchi ground into a wakizashi keeps its enchantments, inscription and name, comes out
 * whole, and costs six 剑魄.
 */
final class GrindChecks {
  private final ServerChecks t;

  GrindChecks(ServerChecks t) {
    this.t = t;
  }

  boolean tick() throws Exception {
    ServerLevel level = t.level;
    var p = t.player;
    var sharpness =
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SHARPNESS);
    ItemStack kazuuchi = new ItemStack(Oborokage.KAZUUCHI);
    kazuuchi.enchant(sharpness, 3);
    kazuuchi.set(Components.INSCRIPTION, "focus");
    kazuuchi.set(DataComponents.CUSTOM_NAME, Component.literal("Kogarasu"));
    kazuuchi.setDamageValue(200);
    p.getInventory().clearContent();
    p.setItemInHand(InteractionHand.MAIN_HAND, kazuuchi);
    p.getInventory().add(new ItemStack(Content.BLADE_SOUL, 8));
    var grind =
        WhetstoneBlock.class.getDeclaredMethod(
            "grind", ServerLevel.class, BlockPos.class, Player.class, InteractionHand.class);
    grind.setAccessible(true);
    grind.invoke(null, level, new BlockPos(0, 99, 0), p, InteractionHand.MAIN_HAND);
    ItemStack blade = p.getMainHandItem();
    t.check(
        blade.is(Oborokage.WAKIZASHI)
            && blade.getEnchantments().getLevel(sharpness) == 3
            && "focus".equals(blade.get(Components.INSCRIPTION))
            && blade.getHoverName().getString().equals("Kogarasu")
            && blade.getDamageValue() == 0
            && p.getInventory().countItem(Content.BLADE_SOUL) == 2,
        "Ground into a wakizashi, the kazuuchi keeps its enchantments, inscription and name, comes"
            + " out whole, for six 剑魄: "
            + blade
            + " sharpness "
            + blade.getEnchantments().getLevel(sharpness)
            + ", "
            + blade.get(Components.INSCRIPTION)
            + ", "
            + blade.getHoverName().getString()
            + ", damage "
            + blade.getDamageValue());
    p.getInventory().clearContent();
    return true;
  }
}

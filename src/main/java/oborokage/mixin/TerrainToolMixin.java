package oborokage.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import oborokage.TerrainCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class TerrainToolMixin {
  @Inject(method = "canDestroyBlock", at = @At("HEAD"), cancellable = true)
  private void cutting(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      CallbackInfoReturnable<Boolean> ci) {
    if (TerrainCut.breaking(player, (ItemStack) (Object) this)) ci.setReturnValue(true);
  }
}

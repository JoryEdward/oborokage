package oborokage.check.mixin;

import net.minecraft.server.level.ServerPlayer;
import oborokage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TerrainCut.class)
public class ObservedTerrain {
  @Inject(method = "start", at = @At("RETURN"))
  private static void observe(ServerPlayer p, float q, CallbackInfoReturnable<Boolean> ci) {
    System.out.println(
        "CUTQA start="
            + ci.getReturnValue()
            + " main="
            + p.getMainHandItem()
            + " config="
            + ServerConfig.get().terrainCutEnabled
            + " hardness="
            + ServerConfig.get().terrainMaxHardness);
  }
}

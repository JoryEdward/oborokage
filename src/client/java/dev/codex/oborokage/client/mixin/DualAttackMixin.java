package dev.codex.oborokage.client.mixin;
import dev.codex.oborokage.client.DualClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class)
public class DualAttackMixin {
 @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
 private void dualAttack(CallbackInfoReturnable<Boolean> ci){if(DualClient.interceptAttack())ci.setReturnValue(false);}
}

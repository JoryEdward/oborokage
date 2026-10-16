package dev.codex.oborokage.mixin;
import dev.codex.oborokage.DualCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LivingEntity.class)
public class GuardDamageMixin {
 @ModifyVariable(method="hurtServer",at=@At("HEAD"),argsOnly=true,ordinal=0)
 private float guard(float amount,ServerLevel level,DamageSource source,float original){return DualCombat.guardedAmount((LivingEntity)(Object)this,source,amount);}
}

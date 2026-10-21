package oborokage.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets vanilla undead be given a target goal against the living swordsmen. */
@Mixin(Mob.class)
public interface MobGoalsAccess {
  @Accessor("targetSelector")
  GoalSelector oborokage$targets();
}

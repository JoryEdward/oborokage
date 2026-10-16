package dev.codex.oborokage.client.mixin;
import dev.codex.oborokage.*;
import dev.codex.oborokage.client.OborokageClient;
import net.minecraft.client.renderer.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemModelResolver.class)
public abstract class SheathModelMixin {
 @Shadow public abstract void updateForTopItem(ItemStackRenderState state,ItemStack item,ItemDisplayContext context,Level level,ItemOwner owner,int seed);
 @Inject(method="updateForLiving",at=@At("HEAD"),cancellable=true)
 private void sheath(ItemStackRenderState state,ItemStack item,ItemDisplayContext context,LivingEntity owner,CallbackInfo ci){
  if((!item.is(Oborokage.TACHI)&&!item.is(Oborokage.WAKIZASHI))||context==ItemDisplayContext.GUI)return;
  var dual=dev.codex.oborokage.client.DualClient.scene(owner.getId());
  if(dual!=null&&dual.event.move()==Move.ULTIMATE&&(dual.age<7||dual.age>=34)){
   ItemStack render=item.copy();render.set(DataComponents.ITEM_MODEL,Oborokage.id(item.is(Oborokage.WAKIZASHI)?"waki_sheathed":"sheathed"));updateForTopItem(state,render,context,owner.level(),owner,owner.getId()+context.ordinal());ci.cancel();return;
  }
  if(item.is(Oborokage.WAKIZASHI))return;
  int age=OborokageClient.sceneAge(owner.getId());int delay=OborokageClient.sceneDelay(owner.getId());
  if(age>=0&&(age<delay||age>=34)){
   ItemStack render=item.copy();render.set(DataComponents.ITEM_MODEL,Oborokage.id(age<delay?"charged15":"sheathed"));
   updateForTopItem(state,render,context,owner.level(),owner,owner.getId()+context.ordinal());ci.cancel();
  }
 }
}

package dev.codex.oborokage;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
public final class TachiItem extends Item {
 public TachiItem(Properties p){super(p);}
 @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,net.minecraft.world.item.component.TooltipDisplay display,java.util.function.Consumer<net.minecraft.network.chat.Component> out,TooltipFlag flag){
  out.accept(net.minecraft.network.chat.Component.translatable("tooltip.oborokage.charge").withStyle(net.minecraft.ChatFormatting.GRAY));
  out.accept(net.minecraft.network.chat.Component.translatable("tooltip.oborokage.ultimate").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
  out.accept(net.minecraft.network.chat.Component.translatable("tooltip.oborokage.flavor").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE,net.minecraft.ChatFormatting.ITALIC));
 }
 @Override public int getUseDuration(ItemStack s,LivingEntity e){return 72000;}
 @Override public ItemUseAnimation getUseAnimation(ItemStack s){return ItemUseAnimation.NONE;}
 @Override public InteractionResult use(Level l,Player p,InteractionHand h){
  if(Combat.active(p.getUUID())||p.isPassenger()||p.isFallFlying())return InteractionResult.FAIL;
  if(p instanceof ServerPlayer server)DualCombat.cancel(server);
  p.startUsingItem(h);return InteractionResult.CONSUME;
 }
 @Override public void onUseTick(Level l,LivingEntity e,ItemStack s,int remaining){
  int t=72000-remaining;
  if(l instanceof ServerLevel sl && (t==1||t==20||t==40))
   sl.playSound(null,e.blockPosition(),t==40?SoundEvents.AMETHYST_BLOCK_CHIME:SoundEvents.AMETHYST_BLOCK_RESONATE,SoundSource.PLAYERS,.65f,t==40?1.7f:.65f+t*.018f);
 }
 @Override public boolean releaseUsing(ItemStack s,Level l,LivingEntity e,int remaining){
  if(!(e instanceof ServerPlayer p)||72000-remaining<4)return false;
  float q=Math.min(1f,(72000-remaining)/40f);
  if(!Combat.start(p,q))return false;
  p.getCooldowns().addCooldown(s,q>=1?45:18);
  s.hurtAndBreak(q>=1?8:2,p,p.getUsedItemHand()==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
  return true;
 }
}

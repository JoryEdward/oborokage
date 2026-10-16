package dev.codex.oborokage.client;
import dev.codex.oborokage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
final class WaistSheaths extends RenderLayer<AvatarRenderState,PlayerModel> {
 private final ItemModelResolver resolver;
 WaistSheaths(RenderLayerParent<AvatarRenderState,PlayerModel> parent,ItemModelResolver resolver){super(parent);this.resolver=resolver;}
 @Override public void submit(PoseStack pose,SubmitNodeCollector nodes,int light,AvatarRenderState state,float a,float b){
  var c=Minecraft.getInstance();if(c.level==null||!(c.level.getEntity(state.id) instanceof Player p)||!DualCombat.equipped(p)||p.isInvisible())return;
  var scene=DualClient.scene(state.id);if(scene!=null&&scene.event.move()==Move.ULTIMATE&&(scene.age<7||scene.age>=34))return;
  for(int side=0;side<2;side++){
   var stack=new ItemStack(side==0?Oborokage.TACHI:Oborokage.WAKIZASHI);stack.set(DataComponents.ITEM_MODEL,Oborokage.id(side==0?"long_scabbard":"short_scabbard"));
   var item=new ItemStackRenderState();resolver.updateForTopItem(item,stack,ItemDisplayContext.GROUND,p.level(),p,state.id+side);
   float sign=side==0?1:-1;if(p.getMainArm()==HumanoidArm.LEFT)sign=-sign;
   pose.pushPose();getParentModel().body.translateAndRotate(pose);pose.translate(sign*.23,.62,.15);pose.rotateDegrees(Axis.YP,sign*35);pose.rotateDegrees(Axis.ZP,sign*65);pose.scale(.72f,.72f,.72f);
   item.submit(pose,nodes,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();
  }
 }
}

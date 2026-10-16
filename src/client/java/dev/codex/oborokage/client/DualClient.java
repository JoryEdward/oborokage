package dev.codex.oborokage.client;
import dev.codex.oborokage.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.InteractionHand;

import java.util.*;

public final class DualClient {
 public static final Map<Integer,Scene> scenes=new HashMap<>();
 public static int intent,stamina=100,poseApplications,eventsReceived;
 private static int sequence;private static Object level;private static boolean guardHeld;private static int heartbeat;
 private static KeyMapping guard,step,ultimate;
 public static final class Scene {
  public final ActionEvent event;public float age;Scene(ActionEvent e,float age){event=e;this.age=age;}
 }
 public static Scene scene(int actor){return scenes.get(actor);}
 public static void init(){
  var cat=KeyMapping.Category.register(Oborokage.id("combat"));
  guard=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oborokage.guard",InputConstants.Type.KEYBOARD,InputConstants.KEY_H,cat));
  step=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oborokage.step",InputConstants.Type.KEYBOARD,InputConstants.KEY_V,cat));
  ultimate=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oborokage.ultimate",InputConstants.Type.KEYBOARD,InputConstants.KEY_J,cat));
  ClientPlayNetworking.registerGlobalReceiver(ActionEvent.TYPE,(p,c)->c.client().execute(()->receive(p)));
  ClientTickEvents.END_CLIENT_TICK.register(DualClient::tick);
  LevelRenderEvents.COLLECT_SUBMITS.register(BladeTrails::submit);
  net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type,renderer,helper,context)->{if(renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar)helper.register(new WaistSheaths(avatar,context.getItemModelResolver()));});
  HudElementRegistry.addLast(Oborokage.id("dual"),(g,d)->{
   Minecraft c=Minecraft.getInstance();if(c.player==null||!DualCombat.equipped(c.player)||c.player.isUsingItem()||OborokageClient.localFull&&OborokageClient.localAge>=0)return;
   int x=g.guiWidth()/2-60,y=g.guiHeight()-73;
   g.fill(x-2,y-2,x+122,y+19,0xa6100e1e);g.fill(x,y,x+intent*120/100,y+3,0xffd8c5ff);g.fill(x,y+6,x+stamina*120/100,y+8,0xff8bd4e5);
   g.text(c.font,Component.translatable("ui.oborokage.intent").append(" "+intent+" / ").append(Component.translatable("ui.oborokage.stamina")).append(" "+stamina),x,y+10,0xffe2d6ef);
   String controls=guard.getTranslatedKeyMessage().getString()+" · "+step.getTranslatedKeyMessage().getString()+" · "+ultimate.getTranslatedKeyMessage().getString();
   g.text(c.font,controls,x,y-12,0xffb7a6d5);
  });
 }
 public static void request(int action){if(ClientPlayNetworking.canSend(ActionRequest.TYPE))ClientPlayNetworking.send(new ActionRequest(++sequence,action));}
 public static boolean interceptAttack(){var c=Minecraft.getInstance();if(c.player==null||c.gui.screen()!=null||!DualCombat.equipped(c.player)||c.hitResult!=null&&c.hitResult.getType()==HitResult.Type.BLOCK)return false;request(0);return true;}
 private static void receive(ActionEvent e){
  var c=Minecraft.getInstance();if(c.level==null||c.player==null)return;eventsReceived++;
  boolean local=e.actor()==c.player.getId();if(local){intent=e.intent();stamina=e.stamina();}
  var previous=scenes.get(e.actor());
  if(e.kind()==ActionEvent.STATUS){if(previous!=null&&previous.event.move()==Move.GUARD&&e.move()!=Move.GUARD)scenes.remove(e.actor());return;}
  if(e.kind()==ActionEvent.CANCEL){if(previous!=null&&e.actionId()>=previous.event.actionId()){scenes.remove(e.actor());if(local&&previous.event.move()==Move.ULTIMATE)OborokageClient.cancelPresentation(e.actor());}return;}
  if(e.kind()==ActionEvent.IMPACT||e.kind()==ActionEvent.PARRY){
   for(int i=0;i<(e.kind()==ActionEvent.PARRY?12:7);i++){double a=i*Math.PI*2/18;c.level.addParticle(ParticleTypes.ELECTRIC_SPARK,e.origin().x+Math.cos(a)*.16,e.origin().y,e.origin().z+Math.sin(a)*.16,Math.cos(a)*.04,.015,Math.sin(a)*.04);}return;
  }
  if(e.move()==0||previous!=null&&e.actionId()<previous.event.actionId())return;
  OborokageClient.cancelPresentation(e.actor());
  long elapsed=c.level.getGameTime()-e.startTick();if(elapsed<0||elapsed>80)elapsed=e.serverTick()-e.startTick();
  scenes.put(e.actor(),new Scene(e,Math.clamp(elapsed,0,e.move()==Move.GUARD?600:Move.of(e.move()).length())));
  if(local&&e.move()==Move.ULTIMATE)OborokageClient.beginDualCamera(e.yaw(),(int)Math.clamp(elapsed,0,47));
 }
 private static void tick(Minecraft c){
  if(level!=c.level){scenes.clear();guardHeld=false;intent=0;stamina=100;level=c.level;}
  if(c.player==null||c.level==null)return;
  boolean pressed=c.gui.screen()==null&&DualCombat.equipped(c.player)&&guard.isDown();
  if(pressed!=guardHeld){request(pressed?1:2);guardHeld=pressed;heartbeat=0;}
  if(pressed&&++heartbeat>=20){request(1);heartbeat=0;}
  while(step.consumeClick())if(c.gui.screen()==null&&DualCombat.equipped(c.player))request(3);
  while(ultimate.consumeClick())if(c.gui.screen()==null&&DualCombat.equipped(c.player))request(4);
  var it=scenes.values().iterator();while(it.hasNext()){
   var s=it.next();var entity=c.level.getEntity(s.event.actor());
   if(entity==null||!entity.isAlive()||s.event.move()!=Move.GUARD&&++s.age>Move.of(s.event.move()).length()+3){it.remove();continue;}
   if(s.event.move()==Move.GUARD)s.age=Math.min(3,s.age+1);
  }
 }
 public static float cut(Scene s,float partial,int hand){
  float t=s.age+partial;int move=s.event.move();
  if(move==Move.GUARD)return 0;
  if(move==Move.DODGE)return (float)Math.sin(Math.min(1,t/6)*Math.PI)*.35f;
  int[] hits=Move.of(move).hits();int[] hands=Move.of(move).hands();float best=0;
  for(int i=0;i<hits.length;i++)if(hands[i]==hand||move==Move.ULTIMATE&&i==2){float a=(t-(hits[i]-3))/5;if(a>=0&&a<=1.8)best=Math.max(best,a<=1?(float)(a*a*(3-2*a)):(1-(a-1)/.8f));}
  return Math.clamp(best,0,1);
 }
 public static boolean pose(int id,HumanoidModel<?> model,float partial,HumanoidArm arm){
  Scene s=scene(id);if(s==null)return false;poseApplications++;
  var main=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;var off=arm==HumanoidArm.RIGHT?model.leftArm:model.rightArm;float sign=arm==HumanoidArm.RIGHT?1:-1;
  float a=cut(s,partial,0),b=cut(s,partial,1);int move=s.event.move();
  if(move==Move.GUARD){main.xRot=-1.13f;main.yRot=-.62f*sign;main.zRot=-.48f*sign;off.xRot=-1.13f;off.yRot=.62f*sign;off.zRot=.48f*sign;model.body.xRot=.1f;return true;}
  main.xRot=-.9f+.72f*a;main.yRot=sign*(-1.35f+2.75f*a);main.zRot=sign*(-.35f+.45f*a);
  off.xRot=-.78f+.68f*b;off.yRot=sign*(1.35f-2.8f*b);off.zRot=sign*(.45f-.5f*b);
  model.body.xRot=.10f;model.body.yRot=sign*(-.2f+.48f*a-.4f*b);
  if(move==Move.ULTIMATE&&s.age>18&&s.age<28)model.body.yRot+=sign*(float)Math.sin((s.age-18+partial)*Math.PI/10)*1.0f;
  model.rightLeg.xRot=-.22f;model.leftLeg.xRot=.27f;model.rightLeg.zRot=.07f;model.leftLeg.zRot=-.07f;
  return true;
 }
 public static void itemPose(PoseStack pose,InteractionHand hand,float partial){
  var c=Minecraft.getInstance();if(c.player==null)return;Scene s=scene(c.player.getId());if(s==null)return;
  float side=hand==InteractionHand.MAIN_HAND?1:-1;if(c.player.getMainArm()==HumanoidArm.LEFT)side=-side;
  float q=cut(s,partial,hand==InteractionHand.MAIN_HAND?0:1);
  if(s.event.move()==Move.GUARD){pose.translate(-side*.15,.25,-.18);pose.rotateDegrees(Axis.ZP,-side*38);return;}
  pose.translate(-side*.36*q,.12*q,-.20*q);pose.rotateDegrees(Axis.ZP,-side*75*q);pose.rotateDegrees(Axis.YP,-side*24*q);
 }
}

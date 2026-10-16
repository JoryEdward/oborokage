package dev.codex.oborokage.check;
import dev.codex.oborokage.*;
import dev.codex.oborokage.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import java.nio.file.*;
import java.util.*;
public final class NetClientChecks implements ClientModInitializer {
 int ticks,ready;boolean joined,finished,sawMain,sawUltimate;List<String> results=new ArrayList<>();boolean actor=Boolean.getBoolean("oborokage.qa.actor");
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);results.add("PASS "+text);}
 void shot(Minecraft c,String name){Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});}
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(finished)return;try{run(c);}catch(Throwable t){t.printStackTrace();results.add("FAIL "+t);finish(c);}});}
 void run(Minecraft c){if(++ticks>2400)throw new AssertionError("Network client timed out");if(c.player==null||c.level==null)return;
  if(c.level.players().size()>=2)joined=true;if(!joined)return;ready++;
  c.player.setYRot(actor?0:110);c.player.setXRot(5);
  for(var scene:DualClient.scenes.values())if(scene.event.actor()!=c.player.getId()){if(scene.event.move()==Move.MAIN)sawMain=true;if(scene.event.move()==Move.ULTIMATE)sawUltimate=true;}
  if(actor){
   if(ready==40){check(DualCombat.equipped(c.player),"Server equipment reaches attacker client");DualClient.request(0);}
   if(ready==70)check(DualClient.intent==100,"Confirmed server hits synchronize full intent over network");
   if(ready==95)DualClient.request(4);
   if(ready==111){check(OborokageClient.cameraActive(),"Attacker receives own ultimate camera");shot(c,"network-attacker-ultimate");}
   if(ready==160)check(c.options.getCameraType()==CameraType.FIRST_PERSON,"Attacker camera restores after networked ultimate");
  }else{
   if(ready==113)shot(c,"network-observer-dual-ultimate");
   if(ready==165){check(sawMain&&sawUltimate,"Observer receives ordered remote combo and ultimate actions");check(DualClient.poseApplications>0,"Observer renders remote dual arm animation");check(!OborokageClient.cameraActive()&&c.options.getCameraType()==CameraType.FIRST_PERSON,"Observer camera is never taken over");}
  }
  if(ready==180){check(c.getConnection()!=null,"Client remains connected after dual actions");finish(c);}
 }
 void finish(Minecraft c){finished=true;try{Files.writeString(Path.of("network-client-results.txt"),String.join("\n",results)+"\n");}catch(Exception e){throw new RuntimeException(e);}c.disconnectWithSavingScreen();c.stop();}
}

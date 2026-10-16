package dev.codex.oborokage.client;
import net.minecraft.client.particle.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;
public final class CrescentParticle extends SingleQuadParticle {
 private final float yaw,tilt,size;
 public CrescentParticle(ClientLevel l,double x,double y,double z,float yaw,float size,float tilt,TextureAtlasSprite sprite){
  super(l,x,y,z,sprite);this.yaw=yaw;this.size=size;this.tilt=tilt;quadSize=size;lifetime=16;hasPhysics=false;
 }
 @Override public void tick(){xo=x;yo=y;zo=z;if(++age>=lifetime)remove();alpha=(float)Math.pow(1-age/(float)lifetime,1.4);}
 @Override public float getQuadSize(float pt){return size*(.85f+.18f*Math.min(1,(age+pt)/3));}
 @Override protected int getLightCoords(float p){return 0xF000F0;}
 @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
 @Override public void extract(QuadParticleRenderState state,Camera camera,float pt){
  // A sweeping plane in world space, tilted from horizontal, with a short rotating follow-through.
  Quaternionf q=new Quaternionf().rotationY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(tilt)).rotateZ((float)Math.toRadians(-55+Math.min(1,(age+pt)/6)*65));
  extractRotatedQuad(state,camera,q,pt);
  extractRotatedQuad(state,camera,new Quaternionf(q).rotateY((float)Math.PI),pt);
 }
}

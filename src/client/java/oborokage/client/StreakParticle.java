package oborokage.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;

public final class StreakParticle extends SingleQuadParticle {
  private final float size, angle;

  public StreakParticle(
      ClientLevel l,
      double x,
      double y,
      double z,
      float size,
      float angle,
      TextureAtlasSprite sprite) {
    super(l, x, y, z, sprite);
    this.size = size;
    this.angle = angle;
    lifetime = 10;
    hasPhysics = false;
  }

  @Override
  public void tick() {
    xo = x;
    yo = y;
    zo = z;
    if (++age >= lifetime) remove();
    alpha = (1 - age / (float) lifetime) * .8f;
  }

  @Override
  public float getQuadSize(float pt) {
    return size;
  }

  @Override
  protected int getLightCoords(float p) {
    return 0xF000F0;
  }

  @Override
  protected Layer getLayer() {
    return Layer.TRANSLUCENT;
  }

  @Override
  public void extract(QuadParticleRenderState s, Camera c, float pt) {
    extractRotatedQuad(
        s, c, new Quaternionf(c.rotation()).rotateZ((float) Math.toRadians(angle)), pt);
  }
}

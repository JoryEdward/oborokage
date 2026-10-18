package oborokage.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;

/**
 * Bright blade particles. The kind decides motion and palette; velocity comes from the spawn call.
 *
 * <ul>
 *   <li>PETAL: lavender-to-blush moon petals that tumble and sway as they fall.
 *   <li>SPARK: crimson-gold thunder stars that burst and burn out quickly.
 *   <li>SHARD: ice crystals that spin and drop with gravity.
 *   <li>MOTE: violet void stars that hang, drift and twinkle.
 * </ul>
 */
public final class FxParticle extends SingleQuadParticle {
  public enum Kind {
    PETAL,
    SPARK,
    SHARD,
    MOTE
  }

  private final Kind kind;
  private final float phase;
  private float spin, size;
  private float angle, oAngle;

  public FxParticle(
      ClientLevel level,
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz,
      Kind kind,
      TextureAtlasSprite sprite) {
    super(level, x, y, z, sprite);
    this.kind = kind;
    xd = dx;
    yd = dy;
    zd = dz;
    hasPhysics = false;
    phase = random.nextFloat() * 6.28f;
    angle = oAngle = random.nextFloat() * 6.28f;
    switch (kind) {
      case PETAL -> {
        lifetime = 40 + random.nextInt(30);
        gravity = .012f;
        friction = .94f;
        size = .09f + random.nextFloat() * .06f;
        spin = (random.nextFloat() - .5f) * .3f;
        float t = random.nextFloat();
        setColor(.86f + .1f * t, .78f - .1f * t, 1f - .08f * t);
      }
      case SPARK -> {
        lifetime = 8 + random.nextInt(8);
        gravity = .02f;
        friction = .82f;
        size = .07f + random.nextFloat() * .08f;
        spin = 0;
        float t = random.nextFloat();
        setColor(1f, .35f + .5f * t, .18f + .3f * t);
      }
      case SHARD -> {
        lifetime = 18 + random.nextInt(14);
        gravity = .05f;
        friction = .96f;
        size = .06f + random.nextFloat() * .07f;
        spin = (random.nextFloat() - .5f) * .6f;
        float t = random.nextFloat();
        setColor(.72f + .28f * t, .92f + .08f * t, 1f);
      }
      case MOTE -> {
        lifetime = 30 + random.nextInt(40);
        gravity = 0;
        friction = .9f;
        size = .05f + random.nextFloat() * .07f;
        spin = 0;
        float t = random.nextFloat();
        setColor(.62f + .35f * t, .42f + .4f * t, 1f);
      }
    }
    quadSize = size;
  }

  @Override
  public void tick() {
    xo = x;
    yo = y;
    zo = z;
    oAngle = angle;
    angle += spin;
    if (age++ >= lifetime) {
      remove();
      return;
    }
    yd -= gravity;
    if (kind == Kind.PETAL) {
      xd += Math.sin(age * .21 + phase) * .004;
      zd += Math.cos(age * .17 + phase) * .004;
    }
    move(xd, yd, zd);
    xd *= friction;
    yd *= friction;
    zd *= friction;
    float life = age / (float) lifetime;
    alpha =
        switch (kind) {
          case SPARK -> 1 - life * life;
          case MOTE -> (1 - life) * (.55f + .45f * (float) Math.sin(age * .5 + phase));
          default -> Math.min(1, (1 - life) * 2.2f);
        };
  }

  @Override
  public float getQuadSize(float pt) {
    return kind == Kind.SPARK ? size * (1.2f - (age + pt) / lifetime * .7f) : size;
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
    float a = oAngle + (angle - oAngle) * pt;
    extractRotatedQuad(s, c, new Quaternionf(c.rotation()).rotateZ(a), pt);
  }
}

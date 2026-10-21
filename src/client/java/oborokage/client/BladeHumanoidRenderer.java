package oborokage.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** A humanoid on the player model with its own skin: the swordsman, the 樱下鬼 and 落英剑主. */
final class BladeHumanoidRenderer<T extends Mob>
    extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
  private final Identifier texture;
  private final float size;

  BladeHumanoidRenderer(EntityRendererProvider.Context context, Identifier texture) {
    this(context, texture, 1);
  }

  BladeHumanoidRenderer(EntityRendererProvider.Context context, Identifier texture, float size) {
    super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), .5f * size);
    this.texture = texture;
    this.size = size;
  }

  @Override
  protected void scale(HumanoidRenderState state, com.mojang.blaze3d.vertex.PoseStack pose) {
    if (size != 1) pose.scale(size, size, size);
  }

  @Override
  public HumanoidRenderState createRenderState() {
    return new HumanoidRenderState();
  }

  @Override
  public Identifier getTextureLocation(HumanoidRenderState state) {
    return texture;
  }
}

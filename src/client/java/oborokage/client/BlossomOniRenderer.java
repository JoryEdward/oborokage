package oborokage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import oborokage.BlossomOni;
import oborokage.Oborokage;

/**
 * 樱下鬼: the swordsman's ghost drawn half-seen. No legs below the ragged hem and no left arm, the
 * whole of it translucent (the skin fades toward the hem and is torn through), hovering with a slow
 * sway. Its broken blade is held as usual.
 */
public final class BlossomOniRenderer
    extends HumanoidMobRenderer<BlossomOni, HumanoidRenderState, BlossomOniRenderer.Model> {
  private static final Identifier TEXTURE = Oborokage.id("textures/entity/blossom_oni.png");

  public static final class Model extends HumanoidModel<HumanoidRenderState> {
    Model(ModelPart root) {
      super(root, RenderTypes::entityTranslucent);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
      super.setupAnim(state);
      leftLeg.visible = false;
      rightLeg.visible = false;
      leftArm.visible = false;
      // The hem trails a little behind it as it drifts.
      body.xRot += .08f + .04f * (float) Math.sin(state.ageInTicks * .09f);
    }
  }

  public BlossomOniRenderer(EntityRendererProvider.Context context) {
    super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), .3f);
  }

  @Override
  public HumanoidRenderState createRenderState() {
    return new HumanoidRenderState();
  }

  @Override
  protected void scale(HumanoidRenderState state, PoseStack pose) {
    // A slow hover (the pose is upside down here: +y is down).
    pose.translate(0, -.08f - .05f * (float) Math.sin(state.ageInTicks * .07f), 0);
  }

  @Override
  protected RenderType getRenderType(
      HumanoidRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
    return state.isInvisible && !translucent ? null : RenderTypes.entityTranslucent(TEXTURE);
  }

  @Override
  public Identifier getTextureLocation(HumanoidRenderState state) {
    return TEXTURE;
  }
}

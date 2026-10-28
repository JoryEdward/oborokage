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
import oborokage.Oborokage;
import oborokage.WitheredLord;

/**
 * 朽英剑主: drawn half-seen like the 樱下鬼 (translucent, no legs below the ragged hem, hovering) but with
 * both arms and a size larger (×1.15). In meditation it sits low with its head bowed and both hands
 * forward on its blade.
 */
public final class WitheredLordRenderer
    extends HumanoidMobRenderer<
        WitheredLord, WitheredLordRenderer.State, WitheredLordRenderer.Model> {
  private static final Identifier TEXTURE = Oborokage.id("textures/entity/withered_lord.png");
  static final float SIZE = 1.15f;

  public static final class State extends HumanoidRenderState {
    boolean meditating;
  }

  public static final class Model extends HumanoidModel<State> {
    Model(ModelPart root) {
      super(root, RenderTypes::entityTranslucent);
    }

    @Override
    public void setupAnim(State state) {
      super.setupAnim(state);
      leftLeg.visible = false;
      rightLeg.visible = false;
      if (state.meditating) {
        head.xRot = .45f;
        head.yRot = 0;
        body.xRot = .08f;
        rightArm.xRot = -.55f;
        rightArm.yRot = -.25f;
        rightArm.zRot = 0;
        leftArm.xRot = -.55f;
        leftArm.yRot = .25f;
        leftArm.zRot = 0;
        return;
      }
      // The hem trails a little behind it as it drifts.
      body.xRot += .06f + .03f * (float) Math.sin(state.ageInTicks * .08f);
    }
  }

  public WitheredLordRenderer(EntityRendererProvider.Context context) {
    super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), .35f * SIZE);
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public void extractRenderState(WitheredLord lord, State state, float partialTick) {
    super.extractRenderState(lord, state, partialTick);
    state.meditating = lord.meditating();
  }

  @Override
  protected void scale(State state, PoseStack pose) {
    pose.scale(SIZE, SIZE, SIZE);
    // The pose is upside down here (+y is down): seated low in meditation, else a slow hover.
    if (state.meditating) pose.translate(0, .5f, 0);
    else pose.translate(0, -.1f - .05f * (float) Math.sin(state.ageInTicks * .06f), 0);
  }

  @Override
  protected RenderType getRenderType(
      State state, boolean bodyVisible, boolean translucent, boolean glowing) {
    return state.isInvisible && !translucent ? null : RenderTypes.entityTranslucent(TEXTURE);
  }

  @Override
  public Identifier getTextureLocation(State state) {
    return TEXTURE;
  }
}

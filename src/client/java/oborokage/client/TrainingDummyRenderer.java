package oborokage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import oborokage.Oborokage;
import oborokage.TrainingDummy;

/**
 * The training stake: a squared log on a cross of planks, its striking section wrapped in straw and
 * bound with two ropes, a cap on top. A blow knocks it away from the striker and it rocks to rest;
 * wood does not blush red. The readout floats above it; otherwise, like any mob, it shows a name
 * only when given one.
 */
public final class TrainingDummyRenderer
    extends MobRenderer<TrainingDummy, TrainingDummyRenderer.State, TrainingDummyRenderer.Model> {
  public static final ModelLayerLocation LAYER =
      new ModelLayerLocation(Oborokage.id("training_dummy"), "main");
  private static final Identifier TEXTURE = Oborokage.id("textures/entity/training_dummy.png");

  public static final class State extends LivingEntityRenderState {
    /** 1 at the blow, falling to 0 as the stake comes to rest. */
    float sway;

    /** Where the blow came from, in radians from the way the stake faces. */
    float blow;
  }

  public static final class Model extends EntityModel<State> {
    private final ModelPart post;

    Model(ModelPart root) {
      super(root);
      post = root.getChild("post");
    }

    /** 64 x 64 texture: log, straw, cap, rope band and plank laid out without overlap. */
    public static LayerDefinition create() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeListBuilder plank = CubeListBuilder.create().texOffs(0, 46).addBox(-8, -2, -2, 16, 2, 4);
      root.addOrReplaceChild("plank_a", plank, PartPose.offset(0, 24, 0));
      root.addOrReplaceChild(
          "plank_b", plank, PartPose.offsetAndRotation(0, 24, 0, 0, (float) Math.PI / 2, 0));
      root.addOrReplaceChild(
          "post",
          CubeListBuilder.create()
              .texOffs(0, 0)
              .addBox(-3, -28, -3, 6, 28, 6)
              .texOffs(24, 0)
              .addBox(-5, -22, -5, 10, 13, 10)
              .texOffs(0, 34)
              .addBox(-5.5f, -20.5f, -5.5f, 11, 1, 11)
              .texOffs(0, 34)
              .addBox(-5.5f, -12.5f, -5.5f, 11, 1, 11)
              .texOffs(24, 23)
              .addBox(-4, -30, -4, 8, 2, 8),
          PartPose.offset(0, 22, 0));
      return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(State state) {
      super.setupAnim(state);
      // Knocked away from the striker, it rocks a few times and settles. Model -z is its front
      // and +x its left, so the top leans toward (sin, cos) of the blow's bearing.
      float k = state.sway;
      float lean = k <= 0 ? 0 : .32f * k * (float) Math.sin((1 - k) * 9.4f);
      post.xRot = -lean * Mth.cos(state.blow);
      post.zRot = lean * Mth.sin(state.blow);
    }
  }

  public TrainingDummyRenderer(EntityRendererProvider.Context context) {
    super(context, new Model(context.bakeLayer(LAYER)), .45f);
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public void extractRenderState(TrainingDummy dummy, State state, float partial) {
    super.extractRenderState(dummy, state, partial);
    state.hasRedOverlay = false;
    state.sway = dummy.hurtTime > 0 ? Math.max(0, (dummy.hurtTime - partial) / 10f) : 0;
    state.blow = Mth.wrapDegrees(dummy.blowYaw - state.bodyRot) * Mth.DEG_TO_RAD;
    // The readout is a close-range training aid; like a name tag it hides with the GUI (F1).
    Component readout = dummy.readout();
    if (!readout.getString().isEmpty()
        && state.distanceToCameraSq < 32 * 32
        && !oborokage.client.compat.ClientCompat.hudHidden(Minecraft.getInstance())) {
      state.nameTag = readout;
      state.nameTagAttachment =
          dummy.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, dummy.getYRot(partial));
    }
  }

  @Override
  public Identifier getTextureLocation(State state) {
    return TEXTURE;
  }
}

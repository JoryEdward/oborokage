package oborokage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import oborokage.*;

final class WaistSheaths extends RenderLayer<AvatarRenderState, PlayerModel> {
  private final ItemModelResolver resolver;
  private final java.util.Map<Item, ItemStack> stacks = new java.util.HashMap<>();
  private final java.util.Map<Integer, ItemStackRenderState[]> cache =
      new java.util.LinkedHashMap<>();
  private Object world;

  WaistSheaths(
      RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver resolver) {
    super(parent);
    this.resolver = resolver;
  }

  @Override
  public void submit(
      PoseStack pose,
      SubmitNodeCollector nodes,
      int light,
      AvatarRenderState state,
      float a,
      float b) {
    var c = Minecraft.getInstance();
    if (c.level == null
        || !(c.level.getEntity(state.id) instanceof Player p)
        || !DualCombat.equipped(p)
        || p.isInvisible()) return;
    var scene = DualClient.scene(state.id);
    if (scene != null && DualClient.sheathedPhase(scene)) return;
    if (world != c.level) {
      cache.clear();
      world = c.level;
    }
    if (cache.size() > 64) cache.clear();
    var renderStates =
        cache.computeIfAbsent(
            state.id,
            id ->
                new ItemStackRenderState[] {
                  new ItemStackRenderState(), new ItemStackRenderState()
                });
    for (int side = 0; side < 2; side++) {
      var type = side == 0 ? p.getMainHandItem().getItem() : Oborokage.WAKIZASHI;
      var stack =
          stacks.computeIfAbsent(
              type,
              key -> {
                var item = new ItemStack(key);
                String model =
                    key == Oborokage.TACHI
                        ? "long_scabbard"
                        : key == Oborokage.WAKIZASHI
                            ? "short_scabbard"
                            : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(key)
                                    .getPath()
                                + "_long_scabbard";
                item.set(DataComponents.ITEM_MODEL, Oborokage.id(model));
                return item;
              });
      var item = renderStates[side];
      resolver.updateForTopItem(
          item, stack, ItemDisplayContext.GROUND, p.level(), p, state.id + side);
      float sign = side == 0 ? 1 : -1;
      if (p.getMainArm() == HumanoidArm.LEFT) sign = -sign;
      pose.pushPose();
      getParentModel().body.translateAndRotate(pose);
      pose.translate(sign * .23, .62, .15);
      oborokage.client.compat.ClientCompat.rotate(pose, Axis.YP, sign * 35);
      oborokage.client.compat.ClientCompat.rotate(pose, Axis.ZP, sign * 65);
      pose.scale(.72f, .72f, .72f);
      item.submit(pose, nodes, light, OverlayTexture.NO_OVERLAY, 0);
      pose.popPose();
    }
  }
}

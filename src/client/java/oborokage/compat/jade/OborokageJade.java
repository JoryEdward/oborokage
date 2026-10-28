package oborokage.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import oborokage.Oborokage;
import oborokage.PetalLord;
import oborokage.TombGuard;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade, when it is installed (entrypoint {@code jade}; nothing here loads without it): looking at a
 * sword master shows the blade in its hand and whether its final form has opened (below half
 * health); looking at a blade tomb's chest shows whether the tomb has been awakened. Both come from
 * the server (a data provider apart from the tooltip, as Jade requires since Minecraft 1.21.6). The
 * 鬼将 needs nothing: its name is its own.
 */
public final class OborokageJade implements IWailaPlugin {
  /** Whether Jade has taken its server and client parts (read by the checks). */
  public static boolean common, client;

  @Override
  public void register(IWailaCommonRegistration registration) {
    common = true;
    registration.registerEntityDataProvider(LordData.INSTANCE, PetalLord.class);
    registration.registerBlockDataProvider(TombData.INSTANCE, ChestBlockEntity.class);
  }

  @Override
  public void registerClient(IWailaClientRegistration registration) {
    client = true;
    registration.registerEntityComponent(Lord.INSTANCE, PetalLord.class);
    registration.registerBlockComponent(Tomb.INSTANCE, ChestBlock.class);
  }

  /** A sword master, from the server: whether its final form has opened. */
  enum LordData implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
      return Oborokage.id("lord");
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
      if (accessor.getEntity() instanceof PetalLord lord)
        data.putBoolean("OborokageFinal", lord.enraged());
    }
  }

  /** A sword master: its blade, and its final form once open. */
  enum Lord implements IComponentProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
      return Oborokage.id("lord");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
      if (!(accessor.getEntity() instanceof PetalLord lord)) return;
      ItemStack blade = lord.getMainHandItem();
      if (!blade.isEmpty())
        tooltip.add(Component.translatable("jade.oborokage.blade", blade.getHoverName()));
      if (accessor.getServerData().getBooleanOr("OborokageFinal", false))
        tooltip.add(
            Component.translatable("jade.oborokage.final").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
  }

  /** A blade tomb's chest, from the server: awakened or not (only for a tomb's chest). */
  enum TombData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
      return Oborokage.id("tomb");
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
      if (accessor.getBlockEntity() instanceof ChestBlockEntity chest
          && accessor.getLevel() instanceof ServerLevel level
          && TombGuard.rod(level, accessor.getPosition()) != null)
        data.putBoolean(
            "OborokageTombAwake", Boolean.TRUE.equals(chest.getAttached(TombGuard.AWAKENED)));
    }
  }

  /** A blade tomb's chest: awakened, or still asleep. */
  enum Tomb implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
      return Oborokage.id("tomb");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
      CompoundTag data = accessor.getServerData();
      if (data.contains("OborokageTombAwake"))
        tooltip.add(
            Component.translatable(
                data.getBooleanOr("OborokageTombAwake", false)
                    ? "jade.oborokage.tomb.awake"
                    : "jade.oborokage.tomb.asleep"));
    }
  }
}

package oborokage;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Per-stack blade state introduced in 1.0. */
public final class Components {
  /** 胧影's moon, 0 (new) to 8 (full). */
  public static final DataComponentType<Integer> MOON_PHASE =
      register("moon_phase", Codec.intRange(0, Moon.FULL), ByteBufCodecs.VAR_INT);

  /** The inscription cut into the blade (see {@link Inscription}). */
  public static final DataComponentType<String> INSCRIPTION =
      register("inscription", Codec.STRING, ByteBufCodecs.STRING_UTF8);

  /** Game time until which a whetstone's edge lasts. */
  public static final DataComponentType<Long> HONED_UNTIL =
      register("honed_until", Codec.LONG, ByteBufCodecs.VAR_LONG);

  private static <T> DataComponentType<T> register(
      String name,
      Codec<T> codec,
      net.minecraft.network.codec.StreamCodec<
              ? super net.minecraft.network.RegistryFriendlyByteBuf, T>
          stream) {
    return Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        Oborokage.id(name),
        DataComponentType.<T>builder().persistent(codec).networkSynchronized(stream).build());
  }

  static void init() {}

  private Components() {}
}

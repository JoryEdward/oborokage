package oborokage;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

public final class FinisherDiagnostics {
  private static final Map<String, Long> warnings = new LinkedHashMap<>();

  public static void failed(LivingEntity e) {
    String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    long now = System.nanoTime();
    Long previous = warnings.get(type);
    if (previous == null || now - previous > 60_000_000_000L) {
      ConfigIO.LOG.warn(
          "Finisher target {} ({}) survived its normal death flow. Configure"
              + " oborokage:finisher_exempt for this entity type.",
          type,
          e.getUUID());
      if (warnings.size() >= 128) warnings.clear();
      warnings.put(type, now);
    }
  }

  private FinisherDiagnostics() {}
}

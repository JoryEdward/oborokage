package oborokage.check;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Test-only instrumentation; none of this class or its mixins goes in the published mod. */
public final class MaintenanceObservations {
  public static volatile boolean watchingCharge, watchingChat, watchingMining;
  public static int aura, hudFrames, hudWidth, guardPredictions, cancels;
  public static int intercepted, vanillaMining, serverBroadcasts, clientBroadcasts, singlePoses;
  public static float offX, offY, offZ;
  public static final List<Integer> resonates = Collections.synchronizedList(new ArrayList<>());
  public static final List<Integer> chimes = Collections.synchronizedList(new ArrayList<>());

  public static boolean advancement(Component c) {
    return c.getContents() instanceof TranslatableContents t
        && t.getKey().startsWith("chat.type.advancement.");
  }

  public static void resetCharge() {
    aura = hudFrames = hudWidth = singlePoses = 0;
    chimes.clear();
    resonates.clear();
    watchingCharge = true;
  }
}

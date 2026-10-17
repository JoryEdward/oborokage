package oborokage.client;

import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import oborokage.ConfigIO;

public final class ClientConfig {
  public static final class Values {
    public boolean cinematic = true;
    public boolean screenShake = true;
    public boolean thirdPersonUltimate = true;
    public boolean localPrediction = true;
    public boolean trailShaderCompat = true;
    public boolean tutorial = true;
    public boolean keyConflictWarning = true;
    public double hudScale = 1.0;
    public int hudOffsetX = 0;
    public int hudOffsetY = 0;
    public int trailMaxPlayers = 16;
    public int trailSegments = 32;
    public int trailVertexBudget = 16384;
    public int trailDistance = 48;
  }

  private static volatile Values current = new Values();

  public static Values get() {
    return current;
  }

  public static void load() {
    Properties p =
        ConfigIO.read(
            FabricLoader.getInstance().getConfigDir().resolve("oborokage-client.properties"),
            "# Oborokage client settings. Restart the client after changing.\n"
                + "cinematic=true\n"
                + "screenShake=true\n"
                + "thirdPersonUltimate=true\n"
                + "localPrediction=true\n"
                + "trailShaderCompat=true\n"
                + "tutorial=true\n"
                + "keyConflictWarning=true\n"
                + "hudScale=1.0\n"
                + "hudOffsetX=0\n"
                + "hudOffsetY=0\n"
                + "trailMaxPlayers=16\n"
                + "trailSegments=32\n"
                + "trailVertexBudget=16384\n"
                + "trailDistance=48\n");
    Values next = new Values();
    next.cinematic = ConfigIO.bool(p, "cinematic", next.cinematic);
    next.screenShake = ConfigIO.bool(p, "screenShake", next.screenShake);
    next.thirdPersonUltimate = ConfigIO.bool(p, "thirdPersonUltimate", next.thirdPersonUltimate);
    next.localPrediction = ConfigIO.bool(p, "localPrediction", next.localPrediction);
    next.trailShaderCompat = ConfigIO.bool(p, "trailShaderCompat", next.trailShaderCompat);
    next.tutorial = ConfigIO.bool(p, "tutorial", next.tutorial);
    next.keyConflictWarning = ConfigIO.bool(p, "keyConflictWarning", next.keyConflictWarning);
    next.hudScale = (double) ConfigIO.number(p, "hudScale", next.hudScale, 0.5, 2.0);
    next.hudOffsetX = (int) ConfigIO.number(p, "hudOffsetX", next.hudOffsetX, -2000, 2000);
    next.hudOffsetY = (int) ConfigIO.number(p, "hudOffsetY", next.hudOffsetY, -2000, 2000);
    next.trailMaxPlayers = (int) ConfigIO.number(p, "trailMaxPlayers", next.trailMaxPlayers, 1, 32);
    next.trailSegments = (int) ConfigIO.number(p, "trailSegments", next.trailSegments, 8, 64);
    next.trailVertexBudget =
        (int) ConfigIO.number(p, "trailVertexBudget", next.trailVertexBudget, 512, 65536);
    next.trailDistance = (int) ConfigIO.number(p, "trailDistance", next.trailDistance, 8, 96);
    current = next;
  }

  private ClientConfig() {}
}

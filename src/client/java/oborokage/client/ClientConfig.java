package oborokage.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import oborokage.ConfigIO;
import oborokage.ModLanguage;

public final class ClientConfig {
  public static final class Values implements Cloneable {
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
    public String language = "auto";

    Values copy() {
      try {
        return (Values) clone();
      } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
      }
    }
  }

  private static volatile Values current = new Values();

  public static Values get() {
    return current;
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("oborokage-client.properties");
  }

  public static void load() {
    Properties p =
        ConfigIO.read(
            file(),
            HEADER
                + "language=auto\n"
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
    next.language = p.getProperty("language", next.language).trim();
    if (!ModLanguage.CHOICES.contains(next.language)) {
      ConfigIO.LOG.warn("Invalid language={}, using auto", next.language);
      next.language = "auto";
    }
    current = next;
    ModLanguage.choose(next.language);
  }

  private static final String HEADER =
      "# Oborokage client settings. The settings screen (move codex K, or Mod Menu) changes them at"
          + " once;\n# edits made here take effect on the next start.\n"
          + "# Language of this mod's text: auto (follow the game), zh_cn, en_us or ja_jp.\n";

  /** Changes the settings now: what reads them sees the change at once (save() keeps them). */
  public static void update(java.util.function.Consumer<Values> change) {
    Values next = current.copy();
    change.accept(next);
    current = next;
    ModLanguage.choose(next.language);
    OborokageClient.applyConfig();
  }

  /** Back to the defaults (language included). */
  public static void reset() {
    current = new Values();
    ModLanguage.choose(current.language);
    OborokageClient.applyConfig();
  }

  /** Writes every setting to the file. */
  public static void save() {
    Values v = current;
    String text =
        HEADER
            + "language="
            + v.language
            + "\ncinematic="
            + v.cinematic
            + "\nscreenShake="
            + v.screenShake
            + "\nthirdPersonUltimate="
            + v.thirdPersonUltimate
            + "\nlocalPrediction="
            + v.localPrediction
            + "\ntrailShaderCompat="
            + v.trailShaderCompat
            + "\ntutorial="
            + v.tutorial
            + "\nkeyConflictWarning="
            + v.keyConflictWarning
            + "\nhudScale="
            + v.hudScale
            + "\nhudOffsetX="
            + v.hudOffsetX
            + "\nhudOffsetY="
            + v.hudOffsetY
            + "\ntrailMaxPlayers="
            + v.trailMaxPlayers
            + "\ntrailSegments="
            + v.trailSegments
            + "\ntrailVertexBudget="
            + v.trailVertexBudget
            + "\ntrailDistance="
            + v.trailDistance
            + "\n";
    try {
      Files.writeString(file(), text);
    } catch (Exception e) {
      ConfigIO.LOG.warn("Cannot save the client settings: {}", e.toString());
    }
  }

  /** Switches the mod's language now and keeps it in the file. */
  public static void setLanguage(String language) {
    update(v -> v.language = language);
    save();
  }

  private ClientConfig() {}
}

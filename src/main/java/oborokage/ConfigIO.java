package oborokage;

import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigIO {
  public static final Logger LOG = LoggerFactory.getLogger("Oborokage");

  public static Properties read(Path path, String defaults) {
    Properties p = new Properties();
    try {
      if (!Files.exists(path)) {
        Files.createDirectories(path.getParent());
        Files.writeString(path, defaults);
      }
      try (var in = Files.newInputStream(path)) {
        p.load(in);
      }
    } catch (Exception e) {
      LOG.warn("Cannot read {}; using safe defaults: {}", path, e.toString());
      p.clear();
    }
    return p;
  }

  public static boolean bool(Properties p, String key, boolean value) {
    String raw = p.getProperty(key);
    if (raw == null) return value;
    if (raw.equalsIgnoreCase("true")) return true;
    if (raw.equalsIgnoreCase("false")) return false;
    LOG.warn("Invalid {}={}, using {}", key, raw, value);
    return value;
  }

  public static double number(Properties p, String key, double value, double min, double max) {
    try {
      double n = Double.parseDouble(p.getProperty(key, Double.toString(value)));
      if (!Double.isFinite(n)) throw new NumberFormatException();
      return Math.clamp(n, min, max);
    } catch (Exception e) {
      LOG.warn("Invalid {}, using {}", key, value);
      return value;
    }
  }

  private ConfigIO() {}
}

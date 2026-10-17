package oborokage.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

public final class OborokageClient implements ClientModInitializer {
  private static final List<Scene> scenes = new ArrayList<>();
  public static int localAge = -1;
  public static boolean localFull = false;
  public static boolean cinematic = true, screenShake = true;
  private static Object lastLevel;
  public static boolean thirdPersonUltimate = true, dualUltimate = false;

  public static void cancelPresentation(int id) {
    scenes.removeIf(s -> s.p.owner() == id);
    var c = Minecraft.getInstance();
    if (c.player != null && c.player.getId() == id) {
      restoreCamera(c);
      localAge = -1;
      localFull = false;
    }
  }

  public static void beginDualCamera(float yaw, int age) {
    var c = Minecraft.getInstance();
    restoreCamera(c);
    dualUltimate = true;
    localAge = age;
    localFull = true;
    cinematicYaw = yaw;
    if (cinematic && thirdPersonUltimate) {
      previousCamera = c.options.getCameraType();
      c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
  }

  private static CameraType previousCamera;
  public static float cinematicYaw;

  public static boolean cameraActive() {
    return previousCamera != null && localFull && localAge >= 0 && localAge <= 48;
  }

  private static void restoreCamera(Minecraft c) {
    if (previousCamera != null) {
      if (c.options.getCameraType() == CameraType.THIRD_PERSON_BACK)
        c.options.setCameraType(previousCamera);
      previousCamera = null;
    }
  }

  public static int sceneDelay(int entityId) {
    for (Scene s : scenes)
      if (s.p.owner() == entityId) return s.p.charge() >= 1 ? Combat.FULL_WINDUP : 0;
    return 0;
  }

  public static int sceneAge(int entityId) {
    for (Scene s : scenes) if (s.p.owner() == entityId) return s.age;
    return -1;
  }

  @Override
  public void onInitializeClient() {
    readConfig();
    DualClient.init();
    TechniqueFx.init();
    ParticleProviderRegistry.getInstance()
        .register(
            Oborokage.STREAK,
            sprites ->
                (options, l, x, y, z, dx, dy, dz, random) ->
                    new StreakParticle(l, x, y, z, (float) dx, (float) dy, sprites.first()));
    ParticleProviderRegistry.getInstance()
        .register(
            Oborokage.CRESCENT,
            sprites ->
                (options, l, x, y, z, dx, dy, dz, random) ->
                    new CrescentParticle(
                        l, x, y, z, (float) dx, (float) dy, (float) dz, sprites.first()));
    ClientPlayNetworking.registerGlobalReceiver(
        SlashPayload.TYPE, (p, context) -> context.client().execute(() -> receive(p)));
    ClientTickEvents.END_CLIENT_TICK.register(OborokageClient::tick);
    HudElementRegistry.addLast(
        Oborokage.id("iaido"),
        (g, delta) -> {
          Minecraft c = Minecraft.getInstance();
          if (c.player == null) return;
          int w = g.guiWidth(), h = g.guiHeight();
          if (c.player.isUsingItem() && Oborokage.mainBlade(c.player.getUseItem())) {
            float maxTicks = c.player.getUseItem().is(Oborokage.RAIKIRI) ? 20f : 40f;
            float q = Math.min(1, c.player.getTicksUsingItem() / maxTicks);
            int y = h - 64;
            g.fill(w / 2 - 51, y - 2, w / 2 + 51, y + 5, 0xb5100e1e);
            g.fill(
                w / 2 - 49,
                y,
                w / 2 - 49 + (int) (98 * q),
                y + 2,
                q >= 1 ? 0xfff1ddff : 0xffa79cff);
            g.centeredText(
                c.font,
                Component.translatable(q >= 1 ? "ui.oborokage.ready" : "ui.oborokage.charge"),
                w / 2,
                y - 14,
                q >= 1 ? 0xfff2e5ff : 0xffc0b0d9);
          }
          if (!cinematic || !localFull || localAge < 0) return;
          float t = localAge + delta.getGameTimeDeltaPartialTick(false);
          int bar = (int) (h * .09 * Math.min(1, t / 3) * Math.min(1, (48 - t) / 10));
          if (bar > 0) {
            g.fill(0, 0, w, bar, 0xdd090713);
            g.fill(0, h - bar, w, h, 0xdd090713);
            g.fill(0, bar, w, bar + 1, 0x807d66b6);
          }
          if (t > 1 && t < 35) {
            int a = (int) (255 * Math.min(1, (35 - t) / 8));
            g.pose().pushMatrix();
            g.pose().translate(w - 270, h / 3f);
            g.pose().scale(2, 2);
            g.text(
                c.font,
                Component.translatable(
                    dualUltimate ? "ui.oborokage.dual_ultimate" : "ui.oborokage.ultimate"),
                0,
                0,
                (a << 24) | 0xe7ddff);
            g.pose().popMatrix();
            g.fill(w - 266, h / 3 + 26, w - 46, h / 3 + 27, (a << 24) | 0x9c7dd9);
            g.text(
                c.font,
                dualUltimate
                    ? "O B O R O K A G E   /   N I T E N"
                    : "O B O R O K A G E   /   I A I",
                w - 264,
                h / 3 + 36,
                (a << 24) | 0xb09ec9);
          }
          if (t >= 12 && t < 15) {
            int a = (int) (110 * (15 - t) / 3);
            for (int x = 0; x < w; x += 2) {
              int y = h / 2 - (int) ((x - w / 2) * .18);
              g.fill(x, y - 1, x + 2, y + 1, (a << 24) | 0xeaddff);
            }
          }
        });
  }

  private static void readConfig() {
    ClientConfig.load();
    var cfg = ClientConfig.get();
    cinematic = cfg.cinematic;
    screenShake = cfg.screenShake;
    thirdPersonUltimate = cfg.thirdPersonUltimate;
  }

  private static void receive(SlashPayload p) {
    Minecraft c = Minecraft.getInstance();
    if (c.level == null || c.player == null) return;
    if (p.kind() == 1) {
      streak(p.origin(), p.charge() >= 1 ? 1.6f : .9f, 28);
      streak(p.origin(), 1, -55);
      return;
    }
    DualClient.scenes.remove(p.owner());
    if (scenes.size() > 40) scenes.removeFirst();
    scenes.add(new Scene(p));
    if (p.owner() == c.player.getId()) {
      restoreCamera(c);
      dualUltimate = false;
      localAge = 0;
      localFull = p.charge() >= 1;
      cinematicYaw = p.yaw();
      if (localFull && cinematic && thirdPersonUltimate) {
        previousCamera = c.options.getCameraType();
        c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      }
    }
  }

  private static void tick(Minecraft c) {
    if (c.level != lastLevel) {
      restoreCamera(c);
      lastLevel = c.level;
      scenes.clear();
      localAge = -1;
    }
    if (c.level == null || c.player == null) {
      restoreCamera(c);
      return;
    }
    if (localAge >= 0 && ++localAge > 48) {
      restoreCamera(c);
      localAge = -1;
    }
    if (cameraActive()
        && (!c.player.isAlive()
            || c.gui.screen() != null
            || c.options.getCameraType() != CameraType.THIRD_PERSON_BACK)) {
      restoreCamera(c);
      localAge = -1;
    }
    for (var player : c.level.players()) {
      if (!player.isUsingItem()
          || !player.getUseItem().is(Oborokage.TACHI)
          || player.distanceToSqr(c.player) > 2304) continue;
      float q = Math.min(1, player.getTicksUsingItem() / 40f);
      double phase = player.tickCount * .28;
      Vec3 base = player.position().add(0, .9, 0);
      for (int i = 0; i < 4; i++) {
        double a = phase + i * Math.PI / 2;
        double radius = 1.2 - .8 * q;
        Vec3 pos = base.add(Math.cos(a) * radius, .15 * Math.sin(a * 2), Math.sin(a) * radius);
        dust(pos, q >= 1 ? 0xe4dbff : 0x8171c4, .55f);
      }
      if (q >= 1 && player.tickCount % 12 == 0) streak(base.add(0, .1, 0), .32f, 20);
    }
    var it = scenes.iterator();
    while (it.hasNext()) {
      Scene s = it.next();
      int t = s.age++;
      boolean full = s.p.charge() >= 1;
      int delay = full ? Combat.FULL_WINDUP : 0;
      if (t >= delay && t < delay + 7) {
        var e = c.level.getEntity(s.p.owner());
        Vec3 at = e == null ? s.p.origin() : e.position();
        Vec3 front =
            new Vec3(-Math.sin(Math.toRadians(s.p.yaw())), 0, Math.cos(Math.toRadians(s.p.yaw())));
        Vec3 center = at.add(0, 1.0, 0).add(front.scale(.5));
        if (t == delay + 1) arc(center, s.p.yaw(), full ? 3.7f : 2.3f, 68);
        if (full && t == delay + 3) arc(center.add(front.scale(-1.0)), s.p.yaw(), 3.2f, 82);
        if (t % 2 == 0) {
          streak(center.add(front.scale(-1)), full ? 1.75f : 1.1f, -12);
          streak(at.add(0, .4, 0), .8f, 8);
        }
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          Vec3 pos = at.add(Math.cos(a) * .65, .15, Math.sin(a) * .65);
          dust(pos, i % 3 == 0 ? 0xeee7ff : 0x7761b6, .6f);
        }
      }
      if (full && t == 34) {
        var e = c.level.getEntity(s.p.owner());
        Vec3 at = (e == null ? s.p.origin() : e.position()).add(0, 1, 0);
        streak(at, .8f, 0);
      }
      if (t > 47) it.remove();
    }
  }

  private static void dust(Vec3 p, int color, float size) {
    var l = Minecraft.getInstance().level;
    if (l != null) l.addParticle(new DustParticleOptions(color, size), p.x, p.y, p.z, 0, .005, 0);
  }

  private static void streak(Vec3 p, float size, float angle) {
    var l = Minecraft.getInstance().level;
    if (l != null) l.addParticle(Oborokage.STREAK, true, true, p.x, p.y, p.z, size, angle, 0);
  }

  private static void arc(Vec3 p, float yaw, float size, float tilt) {
    var l = Minecraft.getInstance().level;
    if (l != null) l.addParticle(Oborokage.CRESCENT, true, true, p.x, p.y, p.z, yaw, size, tilt);
  }

  private static final class Scene {
    final SlashPayload p;
    int age;

    Scene(SlashPayload p) {
      this.p = p;
    }
  }
}

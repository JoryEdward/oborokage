package oborokage.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
  public static boolean thirdPersonUltimate = true;

  public static void cancelPresentation(int id) {
    scenes.removeIf(s -> s.p.owner() == id);
    var c = Minecraft.getInstance();
    if (c.player != null && c.player.getId() == id) {
      Cinematics.cancel();
      localAge = -1;
      localFull = false;
    }
  }

  /** Full-charge iaido scenes for the world effects: owner, origin, yaw, age. */
  public interface IaidoSink {
    void accept(int owner, Vec3 origin, float yaw, float age);
  }

  public static void forEachFullIaido(IaidoSink sink) {
    for (Scene s : scenes)
      if (s.p.charge() >= 1) sink.accept(s.p.owner(), s.p.origin(), s.p.yaw(), s.age);
  }

  public static int sceneDelay(int entityId) {
    for (Scene s : scenes)
      if (s.p.owner() == entityId) return s.p.charge() >= 1 ? Combat.FULL_WINDUP : 0;
    return 0;
  }

  /** Storm combat charges in 20 ticks; every terrain charge takes 40. */
  public static int fullChargeTicks(net.minecraft.world.entity.player.Player p) {
    return p.getUseItem().is(Oborokage.RAIKIRI) && !p.isShiftKeyDown() ? 20 : 40;
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
    for (var entry :
        java.util.Map.of(
                Oborokage.PETAL, FxParticle.Kind.PETAL,
                Oborokage.SPARK, FxParticle.Kind.SPARK,
                Oborokage.SHARD, FxParticle.Kind.SHARD,
                Oborokage.MOTE, FxParticle.Kind.MOTE)
            .entrySet()) {
      var kind = entry.getValue();
      ParticleProviderRegistry.getInstance()
          .register(
              entry.getKey(),
              sprites ->
                  (options, l, x, y, z, dx, dy, dz, random) ->
                      new FxParticle(l, x, y, z, dx, dy, dz, kind, sprites.first()));
    }
    UltimateFx.init();
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
            float q = Math.min(1, c.player.getTicksUsingItem() / (float) fullChargeTicks(c.player));
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
          Cinematics.overlay(g, delta.getGameTimeDeltaPartialTick(false));
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
      localAge = 0;
      localFull = p.charge() >= 1;
      if (localFull) Cinematics.begin(Cinematics.IAIDO, p.yaw(), 0);
      else Cinematics.cancel();
    }
  }

  private static void tick(Minecraft c) {
    if (c.level != lastLevel) {
      Cinematics.cancel();
      lastLevel = c.level;
      scenes.clear();
      localAge = -1;
    }
    if (c.level == null || c.player == null) {
      Cinematics.cancel();
      return;
    }
    Cinematics.tick(c);
    if (localAge >= 0 && ++localAge > 48) localAge = -1;
    for (var player : c.level.players()) {
      if (!player.isUsingItem()
          || !Oborokage.mainBlade(player.getUseItem())
          || player.distanceToSqr(c.player) > 2304) continue;
      float q = Math.min(1, player.getTicksUsingItem() / (float) fullChargeTicks(player));
      double phase = player.tickCount * .28;
      Vec3 base = player.position().add(0, .9, 0);
      // The charge aura takes the blade's colours and, once full, its own particle.
      var style = BladeStyle.of(player.getUseItem());
      int dim = style == null ? 0x8171c4 : style.secondary,
          bright = style == null ? 0xe4dbff : style.primary;
      for (int i = 0; i < 4; i++) {
        double a = phase + i * Math.PI / 2;
        double radius = 1.2 - .8 * q;
        Vec3 pos = base.add(Math.cos(a) * radius, .15 * Math.sin(a * 2), Math.sin(a) * radius);
        dust(pos, q >= 1 ? bright : dim, .55f);
      }
      if (q >= 1 && style != null && player.tickCount % 3 == 0)
        c.level.addParticle(
            switch (style) {
              case RAIKIRI -> Oborokage.SPARK;
              case FROSTMOON -> Oborokage.SHARD;
              case VOIDSTRING -> Oborokage.MOTE;
              default -> Oborokage.PETAL;
            },
            base.x + (player.getRandom().nextDouble() - .5) * 1.4,
            base.y + player.getRandom().nextDouble() * 1.2,
            base.z + (player.getRandom().nextDouble() - .5) * 1.4,
            0,
            .02,
            0);
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

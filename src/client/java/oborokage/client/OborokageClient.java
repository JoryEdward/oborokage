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
import net.minecraft.world.phys.Vec3;
import oborokage.*;

public final class OborokageClient implements ClientModInitializer {
  private static final List<Scene> scenes = new ArrayList<>();
  public static int localAge = -1;
  public static boolean localFull = false;
  public static boolean cinematic = true, screenShake = true;
  private static Object lastLevel;
  public static boolean thirdPersonUltimate = true;

  /** A full-charge draw's presentation: windup, dash, stillness, the sheath at 34, aftermath. */
  static final int FULL_LENGTH = 64;

  public static void cancelPresentation(int id) {
    scenes.removeIf(s -> s.p.owner() == id);
    var c = Minecraft.getInstance();
    if (c.player != null && c.player.getId() == id) {
      Cinematics.cancel();
      localAge = -1;
      localFull = false;
    }
  }

  /** Iaido scenes for the world effects: owner, origin, yaw, charge, age. */
  public interface IaidoSink {
    void accept(int owner, Vec3 origin, float yaw, float charge, float age);
  }

  public static void forEachIaido(IaidoSink sink) {
    for (Scene s : scenes) sink.accept(s.p.owner(), s.p.origin(), s.p.yaw(), s.p.charge(), s.age);
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
    TetherFx.init();
    ClientTickEvents.END_CLIENT_TICK.register(TetherFx::tick);
    ClientTickEvents.END_CLIENT_TICK.register(OborokageClient::notice);
    net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(
        TetherFx::submit);
    net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
        Content.SWORDSMAN,
        ctx -> new BladeHumanoidRenderer<>(ctx, Oborokage.id("textures/entity/swordsman.png")));
    net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
        Content.BLOSSOM_ONI, BlossomOniRenderer::new);
    net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
        Content.PETAL_LORD,
        ctx ->
            new BladeHumanoidRenderer<>(ctx, Oborokage.id("textures/entity/petal_lord.png"), 1.1f));
    net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(
        TrainingDummyRenderer.LAYER, TrainingDummyRenderer.Model::create);
    net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
        Content.TRAINING_DUMMY, TrainingDummyRenderer::new);
    net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
        (stack, context, flag, lines) -> BladeTooltip.append(stack, lines));
    ClientTickEvents.END_CLIENT_TICK.register(ChargeFx::tick);
    net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(
        ChargeFx::submit);
    ClientPlayNetworking.registerGlobalReceiver(
        SlashPayload.TYPE, (p, context) -> context.client().execute(() -> receive(p)));
    ClientTickEvents.END_CLIENT_TICK.register(OborokageClient::tick);
    HudElementRegistry.addLast(
        Oborokage.id("iaido"),
        (g, delta) -> {
          Minecraft c = Minecraft.getInstance();
          if (c.player == null) return;
          float partial = delta.getGameTimeDeltaPartialTick(false);
          ChargeFx.hud(g, partial);
          Cinematics.overlay(g, partial);
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
      // The blade passes through: a full draw only marks the enemy, the cut lands at the sheath.
      streak(p.origin(), p.charge() >= 1 ? 1.6f : .9f, 28);
      streak(p.origin(), 1, -55);
      if (p.charge() >= 1) UltimateFx.impact(p.owner(), p.origin());
      return;
    }
    if (p.kind() == 2) {
      // The sheath clicks and the marked enemy falls.
      arc(p.origin(), p.yaw() + 90, 2.6f, 40);
      streak(p.origin(), 2.2f, 35);
      var r = c.level.getRandom();
      for (int i = 0; i < 26; i++) {
        double a = r.nextDouble() * Math.PI * 2;
        c.level.addParticle(
            i % 3 == 0 ? ParticleTypes.END_ROD : Oborokage.PETAL,
            true,
            true,
            p.origin().x,
            p.origin().y,
            p.origin().z,
            Math.cos(a) * .3,
            .05 + r.nextDouble() * .2,
            Math.sin(a) * .3);
      }
      return;
    }
    DualClient.scenes.remove(p.owner());
    // A new draw replaces the owner's previous one. A full draw's scene lasts 64 ticks, longer than
    // its 45-tick cooldown, and poses and the sheath model read the first scene they find.
    scenes.removeIf(s -> s.p.owner() == p.owner());
    if (scenes.size() > 40) scenes.removeFirst();
    scenes.add(new Scene(p));
    if (p.owner() == c.player.getId()) {
      Codex.see("tachi_iaido");
      localAge = 0;
      localFull = p.charge() >= 1;
      if (localFull) Cinematics.begin(Cinematics.IAIDO, p.yaw(), 0);
      else Cinematics.cancel();
    }
  }

  /** Once a second: note in the codex what the player has come across. */
  private static void notice(Minecraft c) {
    if (c.player == null || c.level == null || c.player.tickCount % 20 != 0) return;
    var main = c.player.getMainHandItem();
    if (Moon.full(main)) Codex.see("tachi_moon");
    if (Inscription.of(main) != null) Codex.see("inscription");
    if (Hone.honed(main, c.level.getGameTime())) Codex.see("whetstone");
    if (DualClient.intent >= 100) Codex.see("intent");
    if (main.is(Oborokage.KAZUUCHI)) Codex.see("kazuuchi");
    if (BladeStyle.of(main) != null
        && (DualCombat.mounted(c.player) || c.player.getAbilities().flying)) Codex.see("mounted");
    if (Vigor.fatigued(c.player)) Codex.see("vigor");
    for (var item : Content.RELICS.values())
      if (c.player.getInventory().contains(new net.minecraft.world.item.ItemStack(item))) {
        Codex.see("tomb");
        break;
      }
    for (var e :
        c.level.getEntitiesOfClass(
            net.minecraft.world.entity.Mob.class, c.player.getBoundingBox().inflate(12))) {
      if (e.getType() == Content.SWORDSMAN) Codex.see("swordsman");
      if (e instanceof BlossomOni) Codex.see("blossom_oni");
      if (e instanceof PetalLord) {
        Codex.see("petal_lord");
        Codex.see("dojo");
      }
      if (e instanceof TrainingDummy) Codex.see("dummy");
    }
  }

  private static void tick(Minecraft c) {
    if (c.level != lastLevel) {
      Cinematics.reset();
      lastLevel = c.level;
      scenes.clear();
      localAge = -1;
    }
    if (c.level == null || c.player == null) {
      Cinematics.reset();
      return;
    }
    Cinematics.tick(c);
    if (localAge >= 0 && ++localAge > (localFull ? FULL_LENGTH : 48)) localAge = -1;
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
      // More motes gather with every quarter of the charge. They keep below the eyes and out of
      // arm's reach, so a first-person view is not filled with them.
      int stage = ChargeFx.stage(q), motes = 3 + stage * 2;
      for (int i = 0; i < motes; i++) {
        double a = phase + i * Math.PI * 2 / motes;
        double radius = 1.25 - .45 * q;
        Vec3 pos =
            base.add(
                Math.cos(a) * radius,
                .15 * Math.sin(a * 2) + .1 * stage * ((i % 3) / 2.0) - .3,
                Math.sin(a) * radius);
        dust(pos, stage >= 2 ? bright : dim, .4f + .04f * stage);
      }
      double ring = player.getRandom().nextDouble() * Math.PI * 2;
      if (q >= 1 && style != null && player.tickCount % 3 == 0)
        c.level.addParticle(
            switch (style) {
              case RAIKIRI -> Oborokage.SPARK;
              case FROSTMOON -> Oborokage.SHARD;
              case VOIDSTRING -> Oborokage.MOTE;
              default -> Oborokage.PETAL;
            },
            base.x + Math.cos(ring) * 1.3,
            base.y - .6 + player.getRandom().nextDouble() * .9,
            base.z + Math.sin(ring) * 1.3,
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
      if (t > (full ? FULL_LENGTH : 47)) it.remove();
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

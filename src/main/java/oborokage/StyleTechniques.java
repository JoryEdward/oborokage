package oborokage;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Bounded server hit volumes: assault line, radial control, long-range energy line. */
public final class StyleTechniques {
  /** Outcome of a release: whether it fired, and how many enemies it felled. */
  public record Result(boolean fired, int kills) {
    static final Result FAILED = new Result(false, 0);
  }

  public static Result release(
      ServerPlayer p, ItemStack blade, StyleBladeItem.Style style, float charge) {
    if (Combat.active(p.getUUID())) return Result.FAILED;
    DualCombat.cancel(p);
    ServerLevel level = (ServerLevel) p.level();
    Vec3 origin = p.getEyePosition(), direction = p.getLookAngle();
    double range =
        switch (style) {
          case STORM -> 3 + charge * 3;
          case FROST -> 2 + charge * 4;
          case VOID -> 8 + charge * 20;
        };
    Vec3 end = origin.add(direction.scale(range));
    var wall =
        level.clip(
            new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    if (style != StyleBladeItem.Style.FROST && wall.getType() != HitResult.Type.MISS)
      end = wall.getLocation();
    double distance = origin.distanceTo(end);
    int processed = 0, frozen = 0, kills = 0;
    boolean full = charge >= 1;
    float base =
        (style == StyleBladeItem.Style.STORM ? 28 : style == StyleBladeItem.Style.FROST ? 22 : 36)
            * (1 + charge * 3);
    float scaled = WeaponDamage.scaled(p, blade, base) * BladeStyle.power(blade);
    var box =
        style == StyleBladeItem.Style.FROST
            ? p.getBoundingBox().inflate(range)
            : new AABB(origin, end).inflate(1.2);
    for (LivingEntity target :
        level.getEntitiesOfClass(LivingEntity.class, box, e -> TargetRules.allowed(p, e))) {
      if (processed >= ServerConfig.get().maxTargetsPerWindow) break;
      Vec3 center = target.getBoundingBox().getCenter(), delta = center.subtract(origin);
      if (style == StyleBladeItem.Style.FROST) {
        if (delta.lengthSqr() > range * range) continue;
      } else {
        double along = delta.dot(direction);
        if (along < 0
            || along > distance + target.getBbWidth() * .5
            || delta.subtract(direction.scale(along)).lengthSqr()
                > Math.pow(.8 + target.getBbWidth() * .5, 2)) continue;
      }
      if (level
              .clip(
                  new ClipContext(
                      origin, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
              .getType()
          != HitResult.Type.MISS) continue;
      processed++;
      boolean hit =
          WeaponDamage.apply(p, target, blade, scaled, style == StyleBladeItem.Style.FROST);
      if (hit && !target.isAlive() && Feats.enemy(target)) kills++;
      if (hit) DualCombat.reward(p, target, ServerConfig.get().techniqueIntent);
      if (hit && style == StyleBladeItem.Style.FROST) {
        target.addEffect(
            new MobEffectInstance(MobEffects.SLOWNESS, (int) (30 + charge * 50), 2), p);
        if (Feats.enemy(target) && ++frozen >= 3) Feats.award(p, "frost_truce");
        if (target.isAlive()) BladeArts.frostHit(p, target);
      }
      // 空弦 · 虚印: a full-charge line marks survivors for one empowered dual hit.
      if (hit && full && style == StyleBladeItem.Style.VOID && target.isAlive())
        BladeArts.mark(p, target);
      if (hit
          && style == StyleBladeItem.Style.VOID
          && delta.lengthSqr() >= 256
          && Feats.enemy(target)) Feats.award(p, "far_bank");
    }
    if (style == StyleBladeItem.Style.STORM) {
      Vec3 velocity =
          LivingEntity.collideBoundingBox(
              p,
              direction.multiply(1, 0, 1).normalize().scale(.7 + charge * .6),
              p.getBoundingBox(),
              level,
              java.util.List.of());
      p.setDeltaMovement(velocity.x, p.getDeltaMovement().y, velocity.z);
      if (p.connection != null) p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }
    // Small bounded native particles remain readable under vanilla and shader rendering.
    int points = style == StyleBladeItem.Style.FROST ? 40 : Math.min(64, (int) (distance * 3));
    for (int i = style == StyleBladeItem.Style.VOID ? 3 : 0; i < points; i++) {
      double t = i / (double) Math.max(1, points - 1);
      Vec3 at =
          style == StyleBladeItem.Style.FROST
              ? p.position()
                  .add(Math.cos(t * Math.PI * 2) * range, .8, Math.sin(t * Math.PI * 2) * range)
              : origin.lerp(end, t);
      level.sendParticles(
          style == StyleBladeItem.Style.STORM
              ? Oborokage.SPARK
              : style == StyleBladeItem.Style.FROST
                  ? (i % 2 == 0 ? Oborokage.SHARD : ParticleTypes.SNOWFLAKE)
                  : Oborokage.MOTE,
          at.x,
          at.y,
          at.z,
          1,
          .025,
          .025,
          .025,
          .015);
    }
    // 霜轮 · 回霜: a full-charge ring answers itself half a second later.
    if (full && style == StyleBladeItem.Style.FROST)
      BladeArts.schedulePulse(p, blade, p.position().add(0, .9, 0), range, scaled * .6f, 10);
    TechniqueEvent.send(p, style.ordinal(), origin, end, charge);
    oborokage.compat.Compat.swing(p, p.getUsedItemHand());
    level.playSound(
        null,
        p.blockPosition(),
        SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.PLAYERS,
        1,
        style == StyleBladeItem.Style.STORM ? 1.5f : .75f);
    return new Result(true, kills);
  }

  private StyleTechniques() {}
}

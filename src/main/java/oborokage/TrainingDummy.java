package oborokage;

import java.util.ArrayDeque;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 训练木桩: a wooden stake wrapped in straw that never falls. It sways back from each blow and sheds
 * straw. Every blow from a weapon is read out above it: the last blow's damage, the running combo
 * and the damage per second over the last ten seconds. The world itself (cactus, a wall, frost)
 * cannot hurt it. It is not an enemy, so it gives no intent and does not wax the moon. Sneak and
 * use it with an empty hand to take it back.
 */
public class TrainingDummy extends Mob {
  private static final int WINDOW = 200, COMBO_RESET = 60;

  /** The readout above the stake; synced, never saved, so a reload never shows a stale one. */
  private static final EntityDataAccessor<Component> READOUT =
      SynchedEntityData.defineId(TrainingDummy.class, EntityDataSerializers.COMPONENT);

  private final ArrayDeque<long[]> recent = new ArrayDeque<>();
  private long lastHit = -1000;
  private int combo;

  /** While a blow lands: the health it asked for, before the clamp at zero hides an overkill. */
  private boolean measuring;

  private float asked;

  /** Client side: the yaw the last blow came from, so the stake sways away from it. */
  public float blowYaw;

  public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
    super(type, level);
    setNoAi(true);
    setPersistenceRequired();
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 1000)
        .add(Attributes.KNOCKBACK_RESISTANCE, 1)
        .add(Attributes.MOVEMENT_SPEED, 0);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(READOUT, Component.empty());
  }

  /** What the stake currently shows, or an empty component. */
  public Component readout() {
    return entityData.get(READOUT);
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean removeWhenFarAway(double distance) {
    return false;
  }

  @Override
  public boolean canBreatheUnderwater() {
    return true;
  }

  @Override
  public boolean canFreeze() {
    return false;
  }

  @Override
  public boolean canBeLeashed() {
    return false;
  }

  private static boolean sourceless(DamageSource source) {
    return source.getEntity() == null && source.getDirectEntity() == null;
  }

  /** Only a blow or a shot reaches it, or a sourceless kill such as /kill and the void. */
  @Override
  public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
    return super.isInvulnerableTo(level, source)
        || sourceless(source) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
  }

  @Override
  protected SoundEvent getHurtSound(DamageSource source) {
    return SoundEvents.WOOD_HIT;
  }

  @Override
  protected SoundEvent getDeathSound() {
    return SoundEvents.WOOD_BREAK;
  }

  @Override
  public void setHealth(float health) {
    if (measuring) asked = health;
    super.setHealth(health);
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    // A sourceless kill is taken for real; every blow from someone is absorbed and read out.
    if (sourceless(source)) return super.hurtServer(level, source, amount);
    setHealth(getMaxHealth());
    asked = getMaxHealth();
    measuring = true;
    boolean hit;
    try {
      hit = super.hurtServer(level, source, amount);
    } finally {
      measuring = false;
    }
    float dealt = getMaxHealth() - asked;
    if (isAlive()) setHealth(getMaxHealth());
    if (hit && !isRemoved() && isAlive() && dealt > 0) record(level, dealt);
    return hit;
  }

  /** Only a sourceless kill (/kill, the void) removes it; every blow from a weapon is absorbed. */
  @Override
  public void die(DamageSource source) {
    if (sourceless(source) && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      super.die(source);
      return;
    }
    super.setHealth(getMaxHealth());
  }

  @Override
  public void handleDamageEvent(DamageSource source) {
    super.handleDamageEvent(source);
    Vec3 from = source.getSourcePosition();
    if (from != null)
      blowYaw = (float) Mth.atan2(-(from.x - getX()), from.z - getZ()) * Mth.RAD_TO_DEG;
    else blowYaw = yBodyRot;
  }

  private void record(ServerLevel level, float dealt) {
    long now = level.getGameTime();
    combo = now - lastHit > COMBO_RESET ? 1 : combo + 1;
    lastHit = now;
    recent.addLast(new long[] {now, Float.floatToIntBits(dealt)});
    while (!recent.isEmpty() && recent.getFirst()[0] < now - WINDOW) recent.removeFirst();
    float total = 0;
    for (long[] r : recent) total += Float.intBitsToFloat((int) r[1]);
    long span = Math.max(20, now - recent.getFirst()[0] + 1);
    float dps = total / (span / 20f);
    entityData.set(
        READOUT,
        Component.translatable(
            "entity.oborokage.training_dummy.readout",
            String.format(java.util.Locale.ROOT, "%.1f", dealt),
            combo,
            String.format(java.util.Locale.ROOT, "%.1f", dps)));
    level.sendParticles(
        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()),
        getX(),
        getY() + 1.1,
        getZ(),
        8,
        .25,
        .3,
        .25,
        .05);
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (level() instanceof ServerLevel level
        && combo > 0
        && level.getGameTime() - lastHit > COMBO_RESET) {
      combo = 0;
      recent.clear();
      entityData.set(READOUT, Component.empty());
    }
  }

  @Override
  protected InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (hand == InteractionHand.MAIN_HAND
        && player.isShiftKeyDown()
        && player.getMainHandItem().isEmpty()) {
      if (!level().isClientSide()) {
        if (!player.getAbilities().instabuild)
          player.setItemInHand(hand, new ItemStack(Content.TRAINING_DUMMY_ITEM));
        discard();
      }
      return InteractionResult.SUCCESS;
    }
    return super.mobInteract(player, hand);
  }
}

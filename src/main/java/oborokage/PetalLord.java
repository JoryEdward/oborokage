package oborokage;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 落英剑主 (Lord of Fallen Petals): the master of a cherry-grove dojo. It carries one of the four long
 * named blades (胧影, 赤霆, 霜轮, 空弦, equally likely) and fights with that blade's arts ({@link
 * LordArts}): sweeping cuts, a flash-step through every foe on the way, and the blade's ultimate.
 *
 * <p>It shows a boss bar to players nearby, never despawns, and no single blow takes more than
 * {@link #MAX_BLOW} from it. It turns its students on the foe it takes, challenges a player now and
 * then, rages below half health, and collects itself when left alone. Only the masters drop relics:
 * the broken form of their own blade, 7.9% of the time when a player fells the 落英剑主. The first time
 * it wakes it clears its hall of leaves grown in from the grove ({@link Hall}). The 朽英剑主 ({@link
 * WitheredLord}) is its undead form.
 */
public class PetalLord extends Swordsman {
  public static final float RELIC_CHANCE = .079f, MAX_BLOW = 60;
  private static final int BAR_RANGE = 40;

  // Its own kind's name (a field set up after the entity's type is known).
  private final ServerBossEvent bar =
      new ServerBossEvent(
          UUID.randomUUID(),
          getType().getDescription(),
          BossEvent.BossBarColor.PURPLE,
          BossEvent.BossBarOverlay.NOTCHED_10);
  private final LordArts arts = new LordArts(this);
  private boolean enraged;

  /** Whether it has cleared its hall of what grew in (once, the first time it wakes; saved). */
  private boolean tidied;

  /** The players it is fighting (1 or more): its health grows with them. */
  private int party = 1;

  private static final net.minecraft.resources.Identifier PARTY = Oborokage.id("party");

  /** Game time it last fought (had a target or was struck), and last spoke a challenge. */
  private long lastFight, lastChallenge = -10000;

  public PetalLord(EntityType<? extends PetalLord> type, Level level) {
    super(type, level);
    xpReward = 60;
    setPersistenceRequired();
  }

  public static AttributeSupplier.Builder createAttributes() {
    // With its blade's bonus (12 to 15) it cuts for 17 to 20.
    return Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 200)
        .add(Attributes.MOVEMENT_SPEED, .32)
        .add(Attributes.ATTACK_DAMAGE, 5)
        .add(Attributes.ARMOR, 10)
        .add(Attributes.ARMOR_TOUGHNESS, 4)
        .add(Attributes.FOLLOW_RANGE, 32)
        .add(Attributes.KNOCKBACK_RESISTANCE, .8);
  }

  /** Its arts: cooldowns, windups, the flash-step and the ultimate. */
  public LordArts arts() {
    return arts;
  }

  @Override
  protected float parryChance() {
    return .4f;
  }

  @Override
  protected int parryRecovery() {
    return 40;
  }

  /** The master parries as often hurt as whole: two in five, never more. */
  @Override
  protected float hurtParryBonus() {
    return 0;
  }

  @Override
  protected float stepChance() {
    return .3f;
  }

  @Override
  protected ItemStack defaultBlade(RandomSource random) {
    Item[] blades = {Oborokage.TACHI, Oborokage.RAIKIRI, Oborokage.FROSTMOON, Oborokage.VOIDSTRING};
    return new ItemStack(blades[random.nextInt(blades.length)]);
  }

  /** The style of the blade it carries, or null if it was disarmed by a command. */
  public BladeStyle style() {
    return BladeStyle.of(getMainHandItem());
  }

  @Override
  protected boolean busy() {
    return arts.busy();
  }

  /** Whether the blow now landing is part of an ultimate, which no blade can parry. */
  public boolean ultimateBlow() {
    return arts.ultimateBlow();
  }

  /** The master keeps to its hall. */
  @Override
  protected int homeRadius() {
    return 4;
  }

  @Override
  public boolean removeWhenFarAway(double distance) {
    return false;
  }

  /** The chance a player who fells it finds the relic of its blade. */
  protected float relicChance() {
    return RELIC_CHANCE;
  }

  /** Whether it is below half health and raging. */
  public boolean enraged() {
    return enraged;
  }

  /** Game time it last fought (had a target or was struck). */
  protected long lastFight() {
    return lastFight;
  }

  /** How many players it is fighting, as its health counts them. */
  public int party() {
    return party;
  }

  /**
   * Fights `n` players: its greatest health is half again as much for each player after the first,
   * two and a half times at most; its health keeps its share of it.
   */
  public void setParty(int n) {
    n = Math.max(1, n);
    if (n == party) return;
    float share = getHealth() / getMaxHealth();
    var health = getAttribute(Attributes.MAX_HEALTH);
    health.removeModifier(PARTY);
    double more = Math.min(1.5, .5 * (n - 1));
    if (more > 0)
      health.addTransientModifier(
          new net.minecraft.world.entity.ai.attributes.AttributeModifier(
              PARTY,
              more,
              net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation
                  .ADD_MULTIPLIED_BASE));
    party = n;
    setHealth(share * getMaxHealth());
  }

  /** Players within 32 blocks who can fight it (not in creative, not watching). */
  private int playersNear(ServerLevel level) {
    int n = 0;
    for (ServerPlayer p : level.players())
      if (p.isAlive() && !p.isSpectator() && !p.isCreative() && distanceToSqr(p) < 32 * 32) n++;
    return n;
  }

  @Override
  protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
    super.addAdditionalSaveData(out);
    out.putBoolean("HallTidied", tidied);
  }

  @Override
  protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
    super.readAdditionalSaveData(in);
    tidied = in.getBooleanOr("HallTidied", false);
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    // /kill and the void are not blows: they are not capped.
    boolean sourceless =
        source.getEntity() == null
            && source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    return super.hurtServer(level, source, sourceless ? amount : Math.min(amount, MAX_BLOW));
  }

  @Override
  public void setCustomName(Component name) {
    super.setCustomName(name);
    bar.setName(getDisplayName());
  }

  @Override
  public void stopSeenByPlayer(ServerPlayer player) {
    super.stopSeenByPlayer(player);
    bar.removePlayer(player);
  }

  /** Its cuts sweep: foes beside the one struck take half the blow. */
  @Override
  public boolean doHurtTarget(ServerLevel level, Entity target) {
    boolean hit = super.doHurtTarget(level, target);
    if (hit) arts.sweep(level, target);
    return hit;
  }

  /** Client side, each tick: what drifts about it (cherry petals). */
  protected void drift() {
    if (getRandom().nextInt(5) == 0)
      level()
          .addParticle(
              ParticleTypes.CHERRY_LEAVES,
              getRandomX(1.2),
              getY() + 1.6 + getRandom().nextDouble() * .8,
              getRandomZ(1.2),
              0,
              -.03,
              0);
  }

  /**
   * One server tick of an art of its own kind, before the master's arts: true while it holds them
   * back (the 朽英剑主's 「百鬼」). The 落英剑主 has none.
   */
  protected boolean ownArt(ServerLevel level) {
    return false;
  }

  @Override
  public void aiStep() {
    super.aiStep();
    if (level().isClientSide()) {
      drift();
      return;
    }
    if (!(level() instanceof ServerLevel level)) return;
    if (!tidied && tickCount > 20) {
      tidied = true;
      Hall.tidy(level, blockPosition());
    }
    long now = level.getGameTime();
    if (getTarget() != null || hurtTime > 0) lastFight = now;
    // In a fight it counts the players against it (more may come; none leave the count until the
    // fight is ten seconds over).
    if (tickCount % 20 == 0) {
      if (now - lastFight <= 200) setParty(Math.max(party, playersNear(level)));
      else if (party > 1) setParty(1);
    }
    // Left alone for ten seconds it collects itself: a point of health a second.
    if (now - lastFight > 200 && getHealth() < getMaxHealth() && tickCount % 20 == 0) {
      heal(1);
      if (getHealth() >= getMaxHealth() / 2) enraged = false;
    }
    bar.setProgress(getHealth() / getMaxHealth());
    if (tickCount % 10 == 0) {
      for (ServerPlayer p : level.players())
        if (isAlive() && p.isAlive() && distanceToSqr(p) < BAR_RANGE * BAR_RANGE) bar.addPlayer(p);
        else bar.removePlayer(p);
    }
    if (!isAlive()) return;
    if (!enraged && getHealth() < getMaxHealth() / 2) {
      enraged = true;
      speak(level, "enraged");
      level.playSound(
          null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1, 1.3f);
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 30, .4, .6, .4, .04);
    }
    if (!ownArt(level)) arts.tick(level, enraged);
  }

  // ----- its voice and its students

  /** Where its lines are: ui.oborokage.<sayings>.<line>. */
  protected String sayings() {
    return "lord_says";
  }

  /** A line to the players around it, in chat: the challenge, the turn at half health, its end. */
  protected void speak(ServerLevel level, String line) {
    var said =
        getType()
            .getDescription()
            .copy()
            .append(": ")
            .append(Component.translatable("ui.oborokage." + sayings() + "." + line))
            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
    for (ServerPlayer p : level.players())
      if (distanceToSqr(p) < 32 * 32) p.sendSystemMessage(said);
  }

  /** On a new foe: its students turn on it too, and a player is challenged (once in a while). */
  @Override
  public void setTarget(LivingEntity target) {
    LivingEntity before = getTarget();
    super.setTarget(target);
    // What it really took: a target it cannot fight is refused, and then it calls no one (two
    // masters would otherwise call each other without end).
    target = getTarget();
    if (target == null || target == before || !(level() instanceof ServerLevel level)) return;
    for (Swordsman s :
        level.getEntitiesOfClass(Swordsman.class, getBoundingBox().inflate(20, 6, 20)))
      if (s != this && s.ofTheDead() == ofTheDead() && s.getTarget() == null && s.isAlive())
        s.setTarget(target);
    long now = level.getGameTime();
    if (target instanceof Player && now - lastChallenge > 1200) {
      lastChallenge = now;
      speak(level, "challenge");
    }
  }

  @Override
  public void die(DamageSource source) {
    if (level() instanceof ServerLevel level && !isRemoved()) speak(level, "fall");
    super.die(source);
  }

  @Override
  protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    super.dropCustomDeathLoot(level, source, recentlyHit);
    BladeStyle style = style();
    if (recentlyHit && style != null && getRandom().nextFloat() < relicChance()) {
      Item relic = Content.RELICS.get(style);
      if (relic != null) spawnAtLocation(level, new ItemStack(relic));
    }
  }
}

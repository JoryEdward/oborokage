package oborokage;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The blade tomb wakes when it is robbed: the first time a player takes any of the tomb's own
 * contents out of its chest (what it held when first opened; a player's own items put in and taken
 * out again do not count), lightning falls on the blade-like lightning rod in its mound and two or
 * three 樱下鬼 rise around it. Opening the chest and looking is safe. Each tomb wakes once (the chest
 * remembers, in a persistent attachment); tombs generated before this behaviour existed wake the
 * same way, since a tomb is recognised by its layout.
 */
public final class TombGuard {
  public static final AttachmentType<Boolean> AWAKENED =
      AttachmentRegistry.createPersistent(Oborokage.id("tomb_awakened"), Codec.BOOL);

  /**
   * What the tomb's chest held when a player first opened it, as "slot:item:count". Only taking
   * from these stacks is robbery; a player's own items put in and taken out again are not.
   */
  public static final AttachmentType<List<String>> ORIGINAL =
      AttachmentRegistry.createPersistent(Oborokage.id("tomb_original"), Codec.STRING.listOf());

  /** On opening: remember a tomb chest's own contents, once (its loot is unpacked by then). */
  public static void opened(ServerLevel level, ChestBlockEntity chest) {
    if (chest.hasAttached(ORIGINAL)
        || Boolean.TRUE.equals(chest.getAttached(AWAKENED))
        || rod(level, chest.getBlockPos()) == null) return;
    remember(chest);
  }

  public static void remember(ChestBlockEntity chest) {
    List<String> own = new ArrayList<>();
    for (int i = 0; i < chest.getContainerSize(); i++) {
      ItemStack s = chest.getItem(i);
      if (!s.isEmpty())
        own.add(i + ":" + BuiltInRegistries.ITEM.getKey(s.getItem()) + ":" + s.getCount());
    }
    chest.setAttached(ORIGINAL, List.copyOf(own));
    chest.setChanged();
  }

  /** After a removal from this slot: is less of the tomb's own stack left there than it held? */
  public static boolean robbed(ChestBlockEntity chest, int slot) {
    List<String> own = chest.getAttached(ORIGINAL);
    if (own == null) return false;
    ItemStack now = chest.getItem(slot);
    for (String entry : own) {
      String[] part = entry.split(":", 2);
      if (Integer.parseInt(part[0]) != slot) continue;
      int cut = part[1].lastIndexOf(':');
      String item = part[1].substring(0, cut);
      int count = Integer.parseInt(part[1].substring(cut + 1));
      return !BuiltInRegistries.ITEM.getKey(now.getItem()).toString().equals(item)
          || now.getCount() < count;
    }
    return false;
  }

  /** Called after items leave a container's slot; cheap checks first (hoppers call it). */
  public static void taken(ServerLevel level, BlockPos pos, BlockEntity chest, int slot) {
    if (!(chest instanceof ChestBlockEntity box)
        || Boolean.TRUE.equals(chest.getAttached(AWAKENED))) return;
    // Taken before anyone opened it (a hopper below it): all it held was the tomb's own.
    boolean unopened = !box.hasAttached(ORIGINAL) && rod(level, pos) != null;
    if (!unopened && !robbed(box, slot)) return;
    // A robbery wakes the tomb even with nobody at the chest (a hopper did it, or the robber
    // stepped away): the dead turn on whoever is near, if anyone is.
    double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
    var near = level.getNearestPlayer(x, y, z, 8, false);
    if (near == null) near = level.getNearestPlayer(x, y, z, 32, false);
    awaken(level, pos, chest, near instanceof ServerPlayer p && !p.isSpectator() ? p : null);
  }

  /**
   * Wakes the tomb around this chest for this robber (null: nobody near), once; false if it is no
   * tomb or awake.
   */
  public static boolean awaken(
      ServerLevel level, BlockPos pos, BlockEntity chest, ServerPlayer robber) {
    if (Boolean.TRUE.equals(chest.getAttached(AWAKENED))) return false;
    BlockPos rod = rod(level, pos);
    if (rod == null) return false;
    chest.setAttached(AWAKENED, true);
    chest.setChanged();
    wake(level, rod, robber);
    return true;
  }

  /**
   * A tomb is known by its layout, whichever way it was turned: the chiseled stele right behind the
   * chest and the lightning rod three blocks in front of it. Returns the rod, or null.
   */
  public static BlockPos rod(ServerLevel level, BlockPos chest) {
    for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
      BlockPos stele = chest.relative(dir.getOpposite()), rod = chest.relative(dir, 3);
      if (level
              .getBlockState(stele)
              .is(net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS)
          && net.minecraft.core.registries.BuiltInRegistries.BLOCK
              .getKey(level.getBlockState(rod).getBlock())
              .getPath()
              .endsWith("lightning_rod")) return rod;
    }
    return null;
  }

  /** The chest of a blade tomb within six blocks of `pos`, or null. */
  public static BlockPos chestNear(ServerLevel level, BlockPos pos) {
    for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -2, -6), pos.offset(6, 2, 6)))
      if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CHEST)
          && rod(level, p.immutable()) != null) return p.immutable();
    return null;
  }

  static void wake(ServerLevel level, BlockPos rod, ServerPlayer robber) {
    level.addFreshEntity(
        oborokage.compat.Compat.visualLightning(
            level, rod.getX() + .5, rod.getY() + 1, rod.getZ() + .5));
    level.playSound(null, rod, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4, .8f);
    level.sendParticles(
        ParticleTypes.SOUL,
        rod.getX() + .5,
        rod.getY() + .6,
        rod.getZ() + .5,
        40,
        1.2,
        .4,
        1.2,
        .03);
    if (level.getDifficulty() == Difficulty.PEACEFUL) return;
    int count = 2 + level.getRandom().nextInt(2);
    for (int i = 0; i < count; i++) {
      double a = Math.PI * 2 * i / count + level.getRandom().nextDouble() * .6;
      double x = rod.getX() + .5 + Math.cos(a) * 2.2, z = rod.getZ() + .5 + Math.sin(a) * 2.2;
      BlossomOni oni = Content.BLOSSOM_ONI.create(level, EntitySpawnReason.EVENT);
      if (oni == null) continue;
      oni.snapTo(x, rod.getY(), z, (float) Math.toDegrees(a) + 90, 0);
      oni.finalizeSpawn(level, level.getCurrentDifficultyAt(rod), EntitySpawnReason.EVENT, null);
      // Called up in the open, they get a little while before the sun can take them.
      oni.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
      oni.setTarget(robber);
      level.addFreshEntity(oni);
      level.sendParticles(
          ParticleTypes.SOUL_FIRE_FLAME, x, rod.getY() + .3, z, 14, .3, .5, .3, .02);
    }
  }

  static void init() {
    // Touch the class so the attachment registers at startup.
    AWAKENED.identifier();
  }

  private TombGuard() {}
}

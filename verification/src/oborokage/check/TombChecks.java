package oborokage.check;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import oborokage.*;

/**
 * The blade tomb wakes when robbed: a tomb's chest (known by its stele and lightning rod) calls two
 * or three 樱下鬼 up around the rod once; a second robbery and an ordinary chest call nothing.
 */
final class TombChecks {
  private static final BlockPos AT = new BlockPos(34, 100, 30);
  private final ServerChecks t;
  private int age;
  private boolean woke;
  private BlockPos chest, rod;
  private net.minecraft.world.level.block.entity.BlockEntity be;

  TombChecks(ServerChecks t) {
    this.t = t;
  }

  boolean tick() throws Exception {
    var level = t.level;
    age++;
    if (age == 40) return verify(level);
    if (age != 1) return false;
    var area = new net.minecraft.world.phys.AABB(AT).expandTowards(9, 6, 9).inflate(6);
    for (var e : level.getEntitiesOfClass(BlossomOni.class, area)) e.discard();
    var template =
        t.server.getStructureTemplateManager().get(Oborokage.id("blade_tomb/tomb")).orElseThrow();
    template.placeInWorld(
        level,
        AT,
        AT,
        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
        level.getRandom(),
        2);
    chest = AT.offset(4, 1, 2);
    rod = AT.offset(4, 1, 5);
    be = level.getBlockEntity(chest);
    t.check(
        be != null && rod.equals(TombGuardAccess.rod(level, chest)),
        "A blade tomb's chest is known by its stele and its lightning rod");
    // Own items in and out again are not robbery; taking from the tomb's own stack is.
    var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) be;
    box.unpackLootTable(null);
    TombGuard.remember(box);
    int own = -1, theirs = -1;
    for (int i = 0; i < box.getContainerSize(); i++)
      if (box.getItem(i).isEmpty() && own < 0) own = i;
      else if (!box.getItem(i).isEmpty() && theirs < 0) theirs = i;
    box.setItem(
        own, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 5));
    box.removeItem(own, 5);
    boolean ownIsFine = !TombGuard.robbed(box, own);
    box.removeItem(theirs, 1);
    t.check(
        ownIsFine && TombGuard.robbed(box, theirs),
        "Putting your own item in and taking it out again is not robbery; taking the tomb's is");
    woke = TombGuard.awaken(level, chest, be, t.player);
    return false;
  }

  /** Counted a couple of seconds later, once the freshly loaded test chunks show their entities. */
  private boolean verify(net.minecraft.server.level.ServerLevel level) throws Exception {
    var area = new net.minecraft.world.phys.AABB(AT).expandTowards(9, 6, 9).inflate(6);
    int risen =
        level
            .getEntitiesOfClass(BlossomOni.class, new net.minecraft.world.phys.AABB(rod).inflate(4))
            .size();
    t.check(
        woke
            && risen >= 2
            && risen <= 3
            && Boolean.TRUE.equals(
                ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) be)
                    .getAttached(TombGuard.AWAKENED)),
        "Robbing the tomb calls lightning and two or three 樱下鬼 around the rod: "
            + risen
            + " woke="
            + woke
            + " difficulty="
            + level.getDifficulty());
    t.check(
        !TombGuard.awaken(level, chest, be, t.player)
            && level
                    .getEntitiesOfClass(
                        BlossomOni.class, new net.minecraft.world.phys.AABB(rod).inflate(4))
                    .size()
                == risen,
        "A tomb wakes only once");
    BlockPos plain = AT.offset(-4, 1, 0);
    level.setBlock(plain, Blocks.CHEST.defaultBlockState(), 3);
    t.check(
        !TombGuard.awaken(level, plain, level.getBlockEntity(plain), t.player),
        "An ordinary chest calls nothing");
    for (var e : level.getEntitiesOfClass(BlossomOni.class, area)) e.discard();
    return true;
  }

  /** TombGuard.rod is package-private in oborokage; reached by reflection. */
  static final class TombGuardAccess {
    static BlockPos rod(net.minecraft.server.level.ServerLevel level, BlockPos chest)
        throws Exception {
      var m =
          TombGuard.class.getDeclaredMethod(
              "rod", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
      m.setAccessible(true);
      return (BlockPos) m.invoke(null, level, chest);
    }
  }
}

package oborokage.check;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import oborokage.*;

/**
 * 1.1.0's /oborokage, run through the server's command dispatcher as an operator: reload; tomb info
 * and reset (an awakened tomb waits again, its chest untouched); summon lord and withered with a
 * blade; parade; and the intent it sets.
 */
final class CommandChecks {
  private static final int X = 60, Z = 200;
  private final ServerChecks t;
  private int age;

  CommandChecks(ServerChecks t) {
    this.t = t;
  }

  private int run(Vec3 at, String command) {
    var source =
        t.server
            .createCommandSourceStack()
            .withPosition(at)
            .withLevel(t.level)
            .withSuppressedOutput();
    try {
      return t.server.getCommands().getDispatcher().execute(command, source);
    } catch (Exception e) {
      System.out.println("COMMAND " + command + ": " + e);
      return -1;
    }
  }

  boolean tick() throws Exception {
    ServerLevel level = t.level;
    Vec3 here = new Vec3(X + .5, -60, Z + .5);
    // Its ground kept loaded while it runs (entities are not added to unloaded chunks); the
    // chunks come in over the next ticks.
    if (++age == 1)
      for (int cx = (X - 48) >> 4; cx <= (X + 32) >> 4; cx++)
        for (int cz = (Z - 16) >> 4; cz <= (Z + 16) >> 4; cz++)
          level
              .getChunkSource()
              .updateChunkForced(new net.minecraft.world.level.ChunkPos(cx, cz), true);
    if (age < 30) return false;
    t.check(run(here, "oborokage reload") == 1, "/oborokage reload runs");
    BlockPos tomb = new BlockPos(X + 10, -61, Z);
    t.server
        .getStructureTemplateManager()
        .get(Oborokage.id("blade_tomb/tomb"))
        .orElseThrow()
        .placeInWorld(level, tomb, tomb, new StructurePlaceSettings(), level.getRandom(), 2);
    var chest =
        (net.minecraft.world.level.block.entity.ChestBlockEntity)
            level.getBlockEntity(tomb.offset(4, 1, 2));
    chest.setItem(0, new ItemStack(Content.BLADE_SOUL, 3));
    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) chest)
        .setAttached(TombGuard.AWAKENED, true);
    String at = (tomb.getX() + 4) + " " + (tomb.getY() + 1) + " " + (tomb.getZ() + 4);
    int info = run(here, "oborokage tomb info " + at);
    int reset = run(here, "oborokage tomb reset " + at);
    boolean awake =
        ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) chest)
            .hasAttached(TombGuard.AWAKENED);
    t.check(
        info == 1 && reset == 1 && !awake && chest.getItem(0).is(Content.BLADE_SOUL),
        "/oborokage tomb info and reset: an awakened tomb waits again, its chest untouched");
    t.check(
        run(here, "oborokage tomb info " + (X - 40) + " -60 " + Z) == 0,
        "/oborokage tomb info finds no tomb where there is none");
    int lord = run(here, "oborokage summon lord raikiri");
    int withered = run(here.add(4, 0, 0), "oborokage summon withered voidstring");
    var box = new AABB(here, here).inflate(8);
    var lords = level.getEntitiesOfClass(PetalLord.class, box);
    boolean petal =
        lords.stream()
            .anyMatch(
                l ->
                    l.getType() == Content.PETAL_LORD && l.getMainHandItem().is(Oborokage.RAIKIRI));
    boolean dead =
        lords.stream()
            .anyMatch(
                l -> l instanceof WitheredLord && l.getMainHandItem().is(Oborokage.VOIDSTRING));
    t.check(
        lord == 1 && withered == 1 && petal && dead,
        "/oborokage summon calls up a master with the blade asked for: "
            + lord
            + " "
            + withered
            + " "
            + lords.stream().map(l -> l.getType() + " " + l.getMainHandItem()).toList());
    t.check(
        run(here, "oborokage summon lord katana") == -1
            || run(here, "oborokage summon lord katana") == 0,
        "/oborokage summon refuses a blade it does not know");
    lords.forEach(l -> l.discard());
    int before =
        level
            .getEntitiesOfClass(
                BlossomOni.class, new AABB(here, here).inflate(80), BlossomOni::elite)
            .size();
    int parade = run(here, "oborokage parade");
    int after =
        level
            .getEntitiesOfClass(
                BlossomOni.class, new AABB(here, here).inflate(80), BlossomOni::elite)
            .size();
    t.check(
        parade == 1 && after == before + 1, "/oborokage parade brings out a parade with its 鬼将");
    for (var o : level.getEntitiesOfClass(BlossomOni.class, new AABB(here, here).inflate(80)))
      o.discard();
    DualCombat.setIntent(t.player, 140);
    int high = DualCombat.state(t.player).intent();
    DualCombat.setIntent(t.player, 37);
    t.check(
        high == 100 && DualCombat.state(t.player).intent() == 37,
        "Intent set outright, never past 100 (/oborokage intent)");
    DualCombat.setIntent(t.player, 0);
    level.setBlock(tomb.offset(4, 1, 2), Blocks.AIR.defaultBlockState(), 2);
    for (int cx = (X - 48) >> 4; cx <= (X + 32) >> 4; cx++)
      for (int cz = (Z - 16) >> 4; cz <= (Z + 16) >> 4; cz++)
        level
            .getChunkSource()
            .updateChunkForced(new net.minecraft.world.level.ChunkPos(cx, cz), false);
    return true;
  }
}

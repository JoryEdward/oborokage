package oborokage.check;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Independent of any Oborokage Java package; inspect native saved item stacks. */
public final class RollbackChecks implements ModInitializer {
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          try {
            var level = server.overworld();
            BlockPos at = new BlockPos(0, 100, 0);
            level.getChunkAt(at);
            String phase = System.getProperty("oborokage.qa.rollback");
            if (phase.equals("seed")) {
              level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
              var chest = (ChestBlockEntity) level.getBlockEntity(at);
              int slot = 0;
              for (String id : List.of("tachi", "wakizashi", "raikiri"))
                chest.setItem(
                    slot++,
                    new ItemStack(
                        BuiltInRegistries.ITEM.getValue(
                            Identifier.fromNamespaceAndPath("oborokage", id))));
              chest.setChanged();
            }
            var chest = (ChestBlockEntity) level.getBlockEntity(at);
            String result =
                phase
                    + " "
                    + chest.getItem(0)
                    + " | "
                    + chest.getItem(1)
                    + " | "
                    + chest.getItem(2);
            System.out.println("ROLLBACKQA " + result);
            Files.writeString(
                Path.of("rollback-results.txt"),
                result + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
            if (chest.getItem(0).isEmpty()) throw new AssertionError("Original tachi lost");
            if (!phase.equals("seed")
                && (!chest.getItem(1).isEmpty() || !chest.getItem(2).isEmpty()))
              throw new AssertionError("Expected unavailable items removed");
          } catch (Throwable e) {
            e.printStackTrace();
            try {
              Files.writeString(
                  Path.of("rollback-results.txt"),
                  "FAIL " + e + "\n",
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
          } finally {
            server.halt(false);
          }
        });
  }
}

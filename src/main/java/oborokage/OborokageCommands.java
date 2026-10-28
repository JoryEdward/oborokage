package oborokage;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * /oborokage, for server operators (game masters): {@code reload} the server settings; {@code tomb
 * info|reset <pos>} a blade tomb's records (awakened, and what its chest first held); {@code summon
 * lord|withered [blade]} a master where the command runs; {@code intent <player> <0-100>}; {@code
 * parade}, a 百鬼夜行 brought out near where it runs.
 */
public final class OborokageCommands {
  private static final Map<String, Item> BLADES =
      Map.of(
          "tachi", Oborokage.TACHI,
          "raikiri", Oborokage.RAIKIRI,
          "frostmoon", Oborokage.FROSTMOON,
          "voidstring", Oborokage.VOIDSTRING);

  private OborokageCommands() {}

  static void init() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registries, environment) ->
            dispatcher.register(
                literal("oborokage")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("reload")
                            .executes(
                                c -> {
                                  ServerConfig.load();
                                  c.getSource()
                                      .sendSuccess(
                                          () -> Component.translatable("commands.oborokage.reload"),
                                          true);
                                  return 1;
                                }))
                    .then(
                        literal("tomb")
                            .then(
                                literal("info")
                                    .then(
                                        argument("pos", BlockPosArgument.blockPos())
                                            .executes(
                                                c ->
                                                    tomb(
                                                        c.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(
                                                            c, "pos"),
                                                        false))))
                            .then(
                                literal("reset")
                                    .then(
                                        argument("pos", BlockPosArgument.blockPos())
                                            .executes(
                                                c ->
                                                    tomb(
                                                        c.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(
                                                            c, "pos"),
                                                        true)))))
                    .then(
                        literal("summon")
                            .then(master("lord", Content.PETAL_LORD))
                            .then(master("withered", Content.WITHERED_LORD)))
                    .then(
                        literal("intent")
                            .then(
                                argument("player", EntityArgument.player())
                                    .then(
                                        argument("value", IntegerArgumentType.integer(0, 100))
                                            .executes(
                                                c -> {
                                                  var p = EntityArgument.getPlayer(c, "player");
                                                  int v =
                                                      IntegerArgumentType.getInteger(c, "value");
                                                  DualCombat.setIntent(p, v);
                                                  c.getSource()
                                                      .sendSuccess(
                                                          () ->
                                                              Component.translatable(
                                                                  "commands.oborokage.intent",
                                                                  p.getDisplayName(),
                                                                  v),
                                                          true);
                                                  return 1;
                                                }))))
                    .then(literal("parade").executes(c -> parade(c.getSource())))));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> master(
      String name, EntityType<? extends PetalLord> type) {
    return literal(name)
        .executes(c -> summon(c.getSource(), type, null))
        .then(
            argument("blade", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(BLADES.keySet(), b))
                .executes(
                    c -> summon(c.getSource(), type, StringArgumentType.getString(c, "blade"))));
  }

  private static int tomb(CommandSourceStack source, BlockPos pos, boolean reset) {
    ServerLevel level = source.getLevel();
    BlockPos chest = TombGuard.chestNear(level, pos);
    if (chest == null
        || !(level.getBlockEntity(chest)
            instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box)) {
      source.sendFailure(Component.translatable("commands.oborokage.tomb.none"));
      return 0;
    }
    String at = chest.getX() + " " + chest.getY() + " " + chest.getZ();
    if (reset) {
      box.removeAttached(TombGuard.AWAKENED);
      box.removeAttached(TombGuard.ORIGINAL);
      box.setChanged();
      source.sendSuccess(() -> Component.translatable("commands.oborokage.tomb.reset", at), true);
      return 1;
    }
    boolean awake = Boolean.TRUE.equals(box.getAttached(TombGuard.AWAKENED));
    List<String> original = box.getAttachedOrElse(TombGuard.ORIGINAL, List.of());
    source.sendSuccess(
        () ->
            Component.translatable(
                "commands.oborokage.tomb.info",
                at,
                Component.translatable(
                    awake ? "commands.oborokage.tomb.awake" : "commands.oborokage.tomb.asleep"),
                original.size()),
        false);
    return 1;
  }

  private static int summon(
      CommandSourceStack source, EntityType<? extends PetalLord> type, String blade) {
    Item item = blade == null ? null : BLADES.get(blade);
    if (blade != null && item == null) {
      source.sendFailure(Component.translatable("commands.oborokage.summon.blade", blade));
      return 0;
    }
    ServerLevel level = source.getLevel();
    PetalLord lord = type.create(level, EntitySpawnReason.COMMAND);
    if (lord == null) return 0;
    var at = source.getPosition();
    lord.snapTo(at.x, at.y, at.z, source.getRotation().y, 0);
    lord.finalizeSpawn(
        level, level.getCurrentDifficultyAt(lord.blockPosition()), EntitySpawnReason.COMMAND, null);
    if (item != null) lord.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    level.addFreshEntity(lord);
    source.sendSuccess(
        () ->
            Component.translatable(
                "commands.oborokage.summon",
                lord.getDisplayName(),
                lord.getMainHandItem().getHoverName()),
        true);
    return 1;
  }

  private static int parade(CommandSourceStack source) {
    BlossomOni leader = NightParade.start(source.getLevel(), source.getPosition());
    if (leader == null) {
      source.sendFailure(Component.translatable("commands.oborokage.parade.none"));
      return 0;
    }
    source.sendSuccess(() -> Component.translatable("commands.oborokage.parade"), true);
    return 1;
  }
}

package dev.codex.oborokage.check;

import dev.codex.oborokage.Oborokage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.nio.file.*;
import java.util.*;

/** Narrow check of native inventory-triggered recipe unlocks, in an isolated world. */
public final class RecipeBookChecks implements ClientModInitializer {
    int ticks, deadline;
    boolean done;
    volatile boolean initiallyLocked, tachiUnlocked, wakizashiUnlocked, checked;
    volatile Throwable serverFailure;
    final List<String> results = new ArrayList<>();

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (done) return;
            try { tick(c); }
            catch (Throwable t) { t.printStackTrace(); results.add("FAIL " + t); finish(c); }
        });
    }
    void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        results.add("PASS " + message);
        System.out.println("TACHI_RECIPEBOOK PASS " + message);
    }
    void tick(Minecraft c) {
        if (++deadline > 2400) throw new AssertionError("Recipe-book world load timeout");
        if (c.level == null || c.player == null || c.getSingleplayerServer() == null) return;
        ++ticks;
        if (serverFailure != null) throw new AssertionError(serverFailure);
        if (ticks == 20) {
            check(true, "Current installed mod combination loads in isolated recipe-book world");
            var server = c.getSingleplayerServer();
            var uuid = c.player.getUUID();
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(uuid);
                    initiallyLocked = List.of("tachi", "wakizashi").stream().noneMatch(id ->
                        p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, Oborokage.id(id))));
                    p.getInventory().add(new ItemStack(Items.AMETHYST_SHARD));
                    p.inventoryMenu.broadcastChanges();
                } catch (Throwable t) { serverFailure = t; }
            });
        }
        if (ticks == 50) {
            var server = c.getSingleplayerServer();
            var uuid = c.player.getUUID();
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(uuid);
                    var t = server.getAdvancements().get(Oborokage.id("recipes/combat/tachi"));
                    var w = server.getAdvancements().get(Oborokage.id("recipes/combat/wakizashi"));
                    tachiUnlocked = t != null && p.getAdvancements().getOrStartProgress(t).isDone()
                        && p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, Oborokage.id("tachi")));
                    wakizashiUnlocked = w != null && p.getAdvancements().getOrStartProgress(w).isDone()
                        && p.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, Oborokage.id("wakizashi")));
                    checked = true;
                } catch (Throwable t) { serverFailure = t; }
            });
        }
        if (ticks == 80) {
            check(initiallyLocked, "Both recipes start locked for fresh test player");
            check(checked && tachiUnlocked, "Obtaining amethyst normally unlocks tachi recipe and advancement");
            check(checked && wakizashiUnlocked, "Obtaining amethyst normally unlocks wakizashi recipe and advancement");
            var context = SlotDisplayContext.fromLevel(c.level);
            var displays = c.player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .flatMap(entry -> entry.resultItems(context).stream()).toList();
            check(displays.stream().anyMatch(stack -> stack.is(Oborokage.TACHI)), "Client recipe book receives tachi display");
            check(displays.stream().anyMatch(stack -> stack.is(Oborokage.WAKIZASHI)), "Client recipe book receives wakizashi display");
            finish(c);
        }
    }
    void finish(Minecraft c) {
        done = true;
        try { Files.writeString(Path.of("recipebook-results.txt"), String.join("\n", results) + "\n"); }
        catch (Exception e) { e.printStackTrace(); }
        c.disconnectWithSavingScreen();
        c.stop();
    }
}

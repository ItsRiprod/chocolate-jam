package com.chocolate.machine.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class DungeonInfoCommand extends AbstractPlayerCommand {
    private HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double SEARCH_RADIUS = 50.0;

    public DungeonInfoCommand() {
        super("info", "Show dungeon and spawner information at your location");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        DungeonModule module = DungeonModule.get();
        if (module == null) {
            playerRef.sendMessage(Message.raw("Dungeon module not initialized."));
            return;
        }

        ObjectArrayList<Ref<EntityStore>> spawners = new ObjectArrayList<>();
        List<Ref<EntityStore>> entities = EntityFloodFill.floodFillSpawners(ref, store,
                SpawnerComponent.getComponentType());

        spawners.addAll(entities);

        playerRef.sendMessage(Message.raw("=== Dungeon Info ==="));
        playerRef
                .sendMessage(Message.raw("Spawners within " + (int) SEARCH_RADIUS + " blocks: " + spawners.size()));

        if (spawners.isEmpty()) {
            playerRef.sendMessage(Message.raw("No spawners found nearby."));
            return;
        }

        // count by type and registration status
        Map<String, Integer> typeCounts = new HashMap<>();
        int registered = 0;
        int unregistered = 0;
        int spawned = 0;
        Ref<EntityStore> dungeonRef = null;

        dungeonRef = DungeonFinder.findNearestDungeonToPlayer(ref, store);

        DungeonComponent dungeon = null;
        if (dungeonRef != null && dungeonRef.isValid())
            dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());

        for (Ref<EntityStore> spawnerRef : spawners) {
            if (!spawnerRef.isValid())
                continue;

            SpawnerComponent spawner = store.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner == null) {
                LOGGER.atWarning().log("Spawner entity missing SpawnerComponent: " + spawnerRef);
                continue;
            }

            String executionId = spawner.getExecutionId();
            if (executionId.isEmpty()) {
                executionId = "(unconfigured)";
            }
            typeCounts.merge(executionId, 1, Integer::sum);
            LOGGER.atInfo().log("Found spawner with execution ID: " + executionId);

            if (dungeon != null && dungeon.getSpawnerRefs().contains(spawnerRef)) {
                registered++;
            } else {
                unregistered++;
            }

            spawned++;
        }

        playerRef.sendMessage(Message.raw("Registered: " + registered + " | Unregistered: " + unregistered));
        playerRef.sendMessage(Message.raw("Active (spawned): " + spawned));

        playerRef.sendMessage(Message.raw("Types:"));
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            playerRef.sendMessage(Message.raw("  - " + entry.getKey() + ": " + entry.getValue()));
        }

        if (dungeon != null) {
            playerRef.sendMessage(Message.raw("--- Dungeon Status ---"));
            playerRef.sendMessage(Message.raw("Active: " + (dungeon.isActive() ? "Yes" : "No")));
            playerRef.sendMessage(Message.raw("Total spawners: " + dungeon.getSpawnerCount()));
            playerRef.sendMessage(Message.raw("Dungeon blocks: " + dungeon.getDungeonBlockCount()));

            if (dungeon.getArtifactHolderRef() != null && dungeon.getArtifactHolderRef().isValid()) {
                playerRef.sendMessage(Message.raw("Artifact holder: Player"));
            }
        }
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Show dungeon info at your location");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate info");
    }
}
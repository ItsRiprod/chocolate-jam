package com.chocolate.machine.command;

import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeonToggleCommand extends AbstractPlayerCommand {

    public DungeonToggleCommand() {
        super("toggle", "Toggle dungeon Active state (assembles dungeon on first run)");
        this.addAliases("to", "activate", "deactivate");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        DungeonModule module = DungeonModule.get();
        if (module == null) {
            playerRef.sendMessage(Message.raw("Dungeon module not initialized."));
            return;
        }

        DungeonService dungeonService = module.getDungeonService();

        Ref<EntityStore> dungeonRef = findDungeonNearPlayer(store, playerEntityRef);

        if (dungeonRef == null || !dungeonRef.isValid()) {
            playerRef.sendMessage(Message.raw("Assembling a new dungeon..."));
            dungeonRef = assembleNewDungeon(store, playerEntityRef, dungeonService, playerRef);
            if (dungeonRef == null) {
                playerRef.sendMessage(Message.raw("No spawners found nearby. Cannot create dungeon."));
                return;
            }
        }

        // Check for and merge nearby dungeons (within 50 blocks)
        DungeonService.MergeResult mergeResult = dungeonService.checkAndMergeDungeons(dungeonRef, store);
        dungeonRef = mergeResult.primaryDungeonRef;

        if (mergeResult.merged) {
            playerRef.sendMessage(Message.raw("Merged nearby dungeon networks."));
        }
        if (mergeResult.hasConflict) {
            playerRef.sendMessage(Message.raw("WARNING: Multiple BossRooms detected nearby! Check server logs."));
        }

        DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            playerRef.sendMessage(Message.raw("Error: Dungeon entity missing DungeonComponent."));
            return;
        }

        // Debug info to help diagnose double-activation issues
        String dungeonId = dungeon.getDungeonId();
        playerRef.sendMessage(Message.raw("Using dungeon '" + (dungeonId.isEmpty() ? "(unnamed)" : dungeonId) +
                "' (active=" + dungeon.isActive() + ", spawners=" + dungeon.getSpawnerCount() + ")"));

        if (dungeon.isActive()) {
            dungeonService.deactivate(dungeonRef, store);
            playerRef.sendMessage(Message.raw("Dungeon DEACTIVATED. All spawners despawned."));
        } else {
            // Add player as dungeoneer if not already
            ensurePlayerIsDungeoneer(store, playerEntityRef, dungeonRef, dungeon);

            dungeonService.activate(dungeonRef, playerEntityRef, store);
            playerRef.sendMessage(Message.raw("Dungeon ACTIVATED. " + dungeon.getSpawnerCount() + " spawners triggered."));
        }
    }

    private void ensurePlayerIsDungeoneer(Store<EntityStore> store, Ref<EntityStore> playerEntityRef,
            Ref<EntityStore> dungeonRef, DungeonComponent dungeon) {

        DungeoneerComponent existingDungeoneer = store.getComponent(playerEntityRef, DungeoneerComponent.getComponentType());
        if (existingDungeoneer != null) {
            return; // Already a dungeoneer
        }

        // Add DungeoneerComponent to player
        Vector3d spawnPosition = dungeon.getSpawnPosition();
        if (spawnPosition.getX() == 0 && spawnPosition.getY() == 0 && spawnPosition.getZ() == 0) {
            // Use dungeon entity position as fallback
            var transform = store.getComponent(dungeonRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                spawnPosition = transform.getPosition();
            }
        }

        DungeoneerComponent dungeoneer = new DungeoneerComponent(dungeon.getDungeonId(), spawnPosition);
        dungeoneer.setDungeonRef(dungeonRef);
        dungeoneer.setRelicHolder(true);

        store.addComponent(playerEntityRef, DungeoneerComponent.getComponentType(), dungeoneer);
        dungeon.addDungeoneerRef(playerEntityRef);
    }

    private Ref<EntityStore> findDungeonNearPlayer(Store<EntityStore> store, Ref<EntityStore> playerEntityRef) {
        return DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);
    }

    private Ref<EntityStore> assembleNewDungeon(Store<EntityStore> store, Ref<EntityStore> playerEntityRef,
            DungeonService dungeonService, PlayerRef playerRef) {

        List<Ref<EntityStore>> spawners = EntityFloodFill.floodFillSpawners(
                playerEntityRef,
                store,
                SpawnerComponent.getComponentType());

        if (spawners.isEmpty()) {
            return null;
        }

        // look for existing dungeon controller
        Ref<EntityStore> dungeonRef = null;
        for (Ref<EntityStore> spawnerRef : spawners) {
            if (!spawnerRef.isValid()) {
                continue;
            }

            DungeonComponent existing = store.getComponent(spawnerRef, DungeonComponent.getComponentType());
            if (existing != null) {
                dungeonRef = spawnerRef;
                break;
            }
        }

        // no controller found, use first spawner
        if (dungeonRef == null) {
            dungeonRef = spawners.get(0);
            store.ensureAndGetComponent(dungeonRef, DungeonComponent.getComponentType());

            playerRef.sendMessage(Message.raw("Created new dungeon controller from first spawner."));
        }

        int count = dungeonService.registerDungeon(dungeonRef, store);
        playerRef.sendMessage(Message.raw("Assembled dungeon with " + count + " spawners."));

        return dungeonRef;
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Toggle dungeon Active state (assembles dungeon on first run)");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate toggle");
    }
}

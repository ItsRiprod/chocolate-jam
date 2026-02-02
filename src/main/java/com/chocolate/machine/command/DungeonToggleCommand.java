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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeonToggleCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
            dungeonRef = assembleNewDungeon(store, playerEntityRef, dungeonService, playerRef, world);
            if (dungeonRef == null) {
                playerRef.sendMessage(Message.raw("No spawners found nearby. Cannot create dungeon."));
                return;
            }
        }

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

        String dungeonId = dungeon.getDungeonId();
        playerRef.sendMessage(Message.raw("Using dungeon '" + (dungeonId.isEmpty() ? "(unnamed)" : dungeonId) +
                "' (active=" + dungeon.isActive() + ", spawners=" + dungeon.getSpawnerCount() +
                ", blocks=" + dungeon.getDungeonBlockCount() + ")"));

        if (dungeon.isActive()) {
            cleanupAllDungeoneers(store, dungeon);

            dungeonService.deactivate(dungeonRef, store);
            playerRef.sendMessage(Message.raw("Dungeon DEACTIVATED. Spawners despawned, blocks reset, dungeoneers removed."));
        } else {
            ensurePlayerIsDungeoneer(store, playerEntityRef, dungeonRef, dungeon);

            dungeonService.activate(dungeonRef, playerEntityRef, store);
            playerRef.sendMessage(Message.raw("Dungeon ACTIVATED. " + dungeon.getSpawnerCount() + " spawners, " +
                    dungeon.getDungeonBlockCount() + " blocks triggered."));
        }
    }

    private void ensurePlayerIsDungeoneer(Store<EntityStore> store, Ref<EntityStore> playerEntityRef,
            Ref<EntityStore> dungeonRef, DungeonComponent dungeon) {

        Vector3d spawnPosition = dungeon.getSpawnPosition();
        LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] dungeon.getSpawnPosition() = (%.1f, %.1f, %.1f)",
                spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ());

        if (spawnPosition.getX() == 0 && spawnPosition.getY() == 0 && spawnPosition.getZ() == 0) {
            var transform = store.getComponent(dungeonRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                spawnPosition = transform.getPosition();
                LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] using dungeon transform position = (%.1f, %.1f, %.1f)",
                        spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ());
            }
        }

        DungeoneerComponent dungeoneer = store.getComponent(playerEntityRef, DungeoneerComponent.getComponentType());
        boolean isNew = (dungeoneer == null);

        LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] isNew=%s, dungeonId=%s", isNew, dungeon.getDungeonId());

        if (isNew) {
            dungeoneer = new DungeoneerComponent(dungeon.getDungeonId(), spawnPosition);
        } else {
            LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] updating existing dungeoneer, old spawn=(%.1f, %.1f, %.1f)",
                    dungeoneer.getSpawnPosition().getX(), dungeoneer.getSpawnPosition().getY(), dungeoneer.getSpawnPosition().getZ());
            dungeoneer.setDungeonId(dungeon.getDungeonId());
            dungeoneer.setSpawnPosition(spawnPosition);
        }

        dungeoneer.setDungeonRef(dungeonRef);
        dungeoneer.setRelicHolder(true);

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player != null) {
            World world = store.getExternalData().getWorld();
            PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());

            if (isNew) {
                dungeoneer.setOriginalRespawnPoints(worldData.getRespawnPoints());
            }

            Vector3i blockPos = new Vector3i(
                    (int) spawnPosition.getX(),
                    (int) spawnPosition.getY(),
                    (int) spawnPosition.getZ());
            PlayerRespawnPointData dungeonSpawn = new PlayerRespawnPointData(
                    blockPos, spawnPosition, "Dungeon");
            worldData.setRespawnPoints(new PlayerRespawnPointData[] { dungeonSpawn });

            LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] set respawn point to (%.1f, %.1f, %.1f)",
                    spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ());
        }

        if (isNew) {
            store.addComponent(playerEntityRef, DungeoneerComponent.getComponentType(), dungeoneer);
        }

        if (!dungeon.getDungeoneerRefs().contains(playerEntityRef)) {
            dungeon.addDungeoneerRef(playerEntityRef);
        }

        LOGGER.atInfo().log("[ensurePlayerIsDungeoneer] done - dungeoneer spawn=(%.1f, %.1f, %.1f), dungeonRef valid=%s",
                dungeoneer.getSpawnPosition().getX(), dungeoneer.getSpawnPosition().getY(), dungeoneer.getSpawnPosition().getZ(),
                dungeonRef != null && dungeonRef.isValid());
    }

    private void cleanupAllDungeoneers(Store<EntityStore> store, DungeonComponent dungeon) {
        // triggers DungeoneerRespawnRestoreSystem
        int count = 0;
        for (Ref<EntityStore> dungeoneerRef : dungeon.getDungeoneerRefs()) {
            if (dungeoneerRef.isValid()) {
                store.removeComponent(dungeoneerRef, DungeoneerComponent.getComponentType());
                count++;
            }
        }
        dungeon.clearDungeoneerRefs();
        dungeon.setArtifactHolderRef(null);
        LOGGER.atInfo().log("[cleanupAllDungeoneers] removed DungeoneerComponent from %d players", count);
    }

    private Ref<EntityStore> findDungeonNearPlayer(Store<EntityStore> store, Ref<EntityStore> playerEntityRef) {
        return DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);
    }

    private Ref<EntityStore> assembleNewDungeon(Store<EntityStore> store, Ref<EntityStore> playerEntityRef,
            DungeonService dungeonService, PlayerRef playerRef, World world) {

        List<Ref<EntityStore>> spawners = EntityFloodFill.floodFillSpawners(
                playerEntityRef,
                store,
                SpawnerComponent.getComponentType());

        if (spawners.isEmpty()) {
            return null;
        }

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

        if (dungeonRef == null) {
            dungeonRef = spawners.get(0);
            store.ensureAndGetComponent(dungeonRef, DungeonComponent.getComponentType());

            playerRef.sendMessage(Message.raw("Created new dungeon controller from first spawner."));
        }

        int count = dungeonService.registerDungeon(dungeonRef, store, world);
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

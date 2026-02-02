package com.chocolate.machine.dungeon.system;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeonBossRoomSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // prevents dupe additions when multiple dungeons process same player
    public static class PendingDungeoneerResource implements Resource<EntityStore> {
        private final Set<Ref<EntityStore>> pendingPlayers = new HashSet<>();

        public boolean tryClaimPlayer(Ref<EntityStore> playerRef) {
            return pendingPlayers.add(playerRef);
        }

        public void clear() {
            pendingPlayers.clear();
        }

        @Override
        public Resource<EntityStore> clone() {
            PendingDungeoneerResource copy = new PendingDungeoneerResource();
            copy.pendingPlayers.addAll(this.pendingPlayers);
            return copy;
        }
    }

    private static ResourceType<EntityStore, PendingDungeoneerResource> pendingResourceType;

    public static void setPendingResourceType(ResourceType<EntityStore, PendingDungeoneerResource> type) {
        pendingResourceType = type;
    }

    public static ResourceType<EntityStore, PendingDungeoneerResource> getPendingResourceType() {
        return pendingResourceType;
    }

    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> playerSpatialResource;

    public DungeonBossRoomSystem() {
        this.playerSpatialResource = EntityModule.get().getPlayerSpatialResourceType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        DungeonComponent dungeon = chunk.getComponent(index, DungeonComponent.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());

        if (dungeon == null || transform == null) {
            return;
        }

        Ref<EntityStore> dungeonRef = chunk.getReferenceTo(index);
        Vector3d bossRoomPosition = transform.getPosition();
        double radius = dungeon.getTriggerRadius();
        String dungeonId = dungeon.getDungeonId();

        // Get the pending resource to track additions this tick
        PendingDungeoneerResource pendingResource = commandBuffer.getResource(pendingResourceType);

        // Query players near this boss room
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = commandBuffer.getResource(playerSpatialResource);
        List<Ref<EntityStore>> nearbyPlayers = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(bossRoomPosition, radius, nearbyPlayers);

        for (int i = 0; i < nearbyPlayers.size(); i++) {
            Ref<EntityStore> playerRef = nearbyPlayers.get(i);
            if (!playerRef.isValid()) {
                continue;
            }

            // Check dungeoneerRefs for THIS dungeon - already tracked
            if (dungeon.getDungeoneerRefs().contains(playerRef)) {
                continue;
            }

            // Check if player already has DungeoneerComponent (from previous tick/load)
            if (store.getComponent(playerRef, DungeoneerComponent.getComponentType()) != null) {
                continue;
            }

            // Try to claim this player via the Resource (atomic within this tick)
            // Returns false if another dungeon already claimed them this tick
            if (!pendingResource.tryClaimPlayer(playerRef)) {
                continue;
            }

            // We've claimed this player - add the component
            Vector3d spawnPosition = dungeon.getSpawnPosition();
            if (spawnPosition.getX() == 0 && spawnPosition.getY() == 0 && spawnPosition.getZ() == 0) {
                spawnPosition = bossRoomPosition;
            }

            DungeoneerComponent dungeoneer = new DungeoneerComponent(dungeonId, spawnPosition);
            dungeoneer.setDungeonRef(dungeonRef);

            // backup and override respawn point
            Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                World world = commandBuffer.getExternalData().getWorld();
                String worldName = world.getName();
                PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(worldName);

                PlayerRespawnPointData[] originalRespawn = worldData.getRespawnPoints();
                dungeoneer.setOriginalRespawnPoints(originalRespawn);

                Vector3i blockPos = new Vector3i(
                        (int) spawnPosition.getX(),
                        (int) spawnPosition.getY(),
                        (int) spawnPosition.getZ());
                PlayerRespawnPointData dungeonSpawn = new PlayerRespawnPointData(
                        blockPos, spawnPosition, "Dungeon");
                worldData.setRespawnPoints(new PlayerRespawnPointData[] { dungeonSpawn });

                LOGGER.atInfo().log("[DungeonBossRoomSystem] Set dungeon respawn for player, backed up %d original points",
                        originalRespawn != null ? originalRespawn.length : 0);
            }

            commandBuffer.addComponent(playerRef, DungeoneerComponent.getComponentType(), dungeoneer);
            dungeon.addDungeoneerRef(playerRef);

            // Notify player
            PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent != null) {
                playerRefComponent.sendMessage(Message.raw("You have entered the dungeon!"));
                LOGGER.atInfo().log("[DungeonBossRoomSystem] Player %s entered dungeon '%s'",
                        playerRefComponent.getUsername(), dungeonId);
            }
        }

        // clean up stale refs
        dungeon.cleanupInvalidDungeoneerRefs();
    }
}

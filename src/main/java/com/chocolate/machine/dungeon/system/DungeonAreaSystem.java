package com.chocolate.machine.dungeon.system;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Monitors the dungeon entrance area.
 * Detects when players with DungeoneerComponent leave the entrance (escape).
 */
public class DungeonAreaSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> playerSpatialResource;

    public DungeonAreaSystem() {
        this.playerSpatialResource = EntityModule.get().getPlayerSpatialResourceType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonEntranceComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        DungeonEntranceComponent entrance = chunk.getComponent(index, DungeonEntranceComponent.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());

        if (entrance == null || transform == null) {
            return;
        }

        Vector3d entrancePosition = transform.getPosition();
        double radius = entrance.getTriggerRadius();
        String dungeonId = entrance.getDungeonId();

        // spatial query for nearby players
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = commandBuffer.getResource(playerSpatialResource);
        List<Ref<EntityStore>> nearbyPlayers = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(entrancePosition, radius, nearbyPlayers);

        // clear current tick's inside set, rebuild from spatial query
        Set<UUID> currentInside = entrance.getPlayersInside();
        Set<UUID> lastTickInside = entrance.getPlayersInsideLastTick();
        currentInside.clear();

        for (int i = 0; i < nearbyPlayers.size(); i++) {
            Ref<EntityStore> playerRef = nearbyPlayers.get(i);
            if (!playerRef.isValid()) {
                continue;
            }

            PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent == null) {
                continue;
            }

            UUID playerUuid = playerRefComponent.getUuid();
            currentInside.add(playerUuid);
        }

        // check who left - was in last tick but not this tick
        for (UUID previouslyInside : lastTickInside) {
            if (!currentInside.contains(previouslyInside)) {
                handlePlayerLeftEntrance(previouslyInside, dungeonId, entrance, commandBuffer);
            }
        }

        // update for next tick
        entrance.updateLastTickTracking();
    }

    private void handlePlayerLeftEntrance(UUID playerUuid, String dungeonId,
            DungeonEntranceComponent entrance,
            ComponentAccessor<EntityStore> commandBuffer) {

        // Find the player ref by UUID
        Ref<EntityStore> playerRef = findPlayerByUuid(playerUuid, commandBuffer);
        if (playerRef == null || !playerRef.isValid()) {
            entrance.getPlayersInside().remove(playerUuid);
            return;
        }

        // Check if they have DungeoneerComponent
        DungeoneerComponent dungeoneer = commandBuffer.getComponent(playerRef, DungeoneerComponent.getComponentType());
        if (dungeoneer == null || !dungeoneer.getDungeonId().equals(dungeonId)) {
            entrance.getPlayersInside().remove(playerUuid);
            return;
        }

        // Player with DungeoneerComponent left the entrance - they escaped!
        PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        boolean isRelicHolder = dungeoneer.isRelicHolder();

        LOGGER.atInfo().log("[DungeonAreaSystem] Player %s escaped dungeon '%s' (relicHolder: %s)",
                playerRefComponent != null ? playerRefComponent.getUsername() : playerUuid,
                dungeonId, isRelicHolder);

        // Remove DungeoneerComponent from player
        commandBuffer.removeComponent(playerRef, DungeoneerComponent.getComponentType());
        entrance.getPlayersInside().remove(playerUuid);

        // remove from dungeon tracking
        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        DungeonComponent dungeon = null;
        if (dungeonRef != null && dungeonRef.isValid()) {
            dungeon = commandBuffer.getComponent(dungeonRef, DungeonComponent.getComponentType());
            if (dungeon != null) {
                dungeon.removeDungeoneerRef(playerRef);
            }
        }

        if (playerRefComponent != null) {
            playerRefComponent.sendMessage(Message.raw("You escaped the dungeon!"));
        }

        if (isRelicHolder) {
            handleRelicHolderEscaped(dungeoneer, commandBuffer);
        } else if (dungeon != null && dungeon.isActive() && dungeon.getDungeoneerRefs().isEmpty()) {
            // last player left without relic holder escaping - deactivate
            LOGGER.atInfo().log("[DungeonAreaSystem] All dungeoneers left dungeon '%s', deactivating",
                    dungeon.getDungeonId());
            DungeonModule.get().getDungeonService().deactivate(dungeonRef, commandBuffer);
        }
    }

    private void handleRelicHolderEscaped(DungeoneerComponent dungeoneer, ComponentAccessor<EntityStore> commandBuffer) {

        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        if (dungeonRef == null || !dungeonRef.isValid()) {
            LOGGER.atWarning().log("[DungeonAreaSystem] Relic holder escaped but dungeon ref invalid");
            return;
        }

        DungeonComponent dungeon = commandBuffer.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return;
        }

        LOGGER.atInfo().log("[DungeonAreaSystem] Relic holder escaped! Deactivating dungeon '%s'",
                dungeon.getDungeonId());

        // deactivate via singleton
        DungeonModule.get().getDungeonService().deactivate(dungeonRef, commandBuffer);

        // Remove DungeoneerComponent from all other dungeoneers
        for (Ref<EntityStore> dungeoneerRef : dungeon.getDungeoneerRefs()) {
            if (dungeoneerRef.isValid()) {
                DungeoneerComponent otherDungeoneer = commandBuffer.getComponent(dungeoneerRef, DungeoneerComponent.getComponentType());
                if (otherDungeoneer != null) {
                    commandBuffer.removeComponent(dungeoneerRef, DungeoneerComponent.getComponentType());

                    PlayerRef otherPlayer = commandBuffer.getComponent(dungeoneerRef, PlayerRef.getComponentType());
                    if (otherPlayer != null) {
                        otherPlayer.sendMessage(Message.raw("The relic holder escaped! Dungeon complete!"));
                    }
                }
            }
        }

        dungeon.clearDungeoneerRefs();
        dungeon.setArtifactHolderRef(null);
    }

    private Ref<EntityStore> findPlayerByUuid(UUID playerUuid, ComponentAccessor<EntityStore> accessor) {
        Ref<EntityStore> result = accessor.getExternalData().getRefFromUUID(playerUuid);
        return result;
    }
}

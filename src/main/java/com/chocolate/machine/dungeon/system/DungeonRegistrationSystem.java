package com.chocolate.machine.dungeon.system;

import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Handles dungeon registration when DungeonComponent entities spawn.
 * Flood fills to find nearby SpawnerComponent entities and links them to the dungeon.
 * Also finds the DungeonEntranceComponent with matching dungeonId.
 */
public class DungeonRegistrationSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double REGISTRATION_RADIUS = 200.0;
    private static final double ENTRANCE_SEARCH_RADIUS = 300.0;

    private final SpawnableRegistry spawnableRegistry;
    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialResource;

    public DungeonRegistrationSystem() {
        this.spawnableRegistry = SpawnableRegistry.getInstance();
        this.entitySpatialResource = EntityModule.get().getEntitySpatialResourceType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        DungeonComponent dungeon = commandBuffer.getComponent(ref, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return;
        }

        // debug: log where this entity came from
        TransformComponent t = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        Vector3d pos = t != null ? t.getPosition() : new Vector3d(0,0,0);
        LOGGER.atInfo().log("[DungeonRegistrationSystem] DungeonComponent entity added (reason=%s) at (%.1f, %.1f, %.1f) dungeonId='%s'",
                reason, pos.getX(), pos.getY(), pos.getZ(), dungeon.getDungeonId());

        // skip if already registered
        if (dungeon.isRegistered()) {
            LOGGER.atFine().log("[DungeonRegistrationSystem] Dungeon '%s' already registered, skipping",
                    dungeon.getDungeonId());
            return;
        }

        // crash recovery: if dungeon was active but we're re-registering, it means
        // server restarted and runtime state was lost. reset to clean state
        if (dungeon.isActive()) {
            LOGGER.atWarning().log("[DungeonRegistrationSystem] Dungeon '%s' was active but unregistered - likely server crash. Resetting state.",
                    dungeon.getDungeonId());
            dungeon.setActive(false);
            dungeon.setArtifactHolderRef(null);
            dungeon.clearDungeoneerRefs();
        }

        LOGGER.atInfo().log("[DungeonRegistrationSystem] Registering dungeon '%s'...", dungeon.getDungeonId());

        // Check for nearby dungeons to merge BEFORE registering spawners
        DungeonModule module = DungeonModule.get();
        Ref<EntityStore> primaryRef = ref;
        if (module != null) {
            DungeonService dungeonService = module.getDungeonService();
            DungeonService.MergeResult mergeResult = dungeonService.checkAndMergeDungeons(ref, commandBuffer);
            primaryRef = mergeResult.primaryDungeonRef;

            if (mergeResult.hasConflict) {
                LOGGER.atSevere().log("[DungeonRegistrationSystem] BossRoom conflict detected! Check logs above.");
            }

            if (mergeResult.merged) {
                LOGGER.atInfo().log("[DungeonRegistrationSystem] Merged with nearby dungeon network");
            }

            // If we were merged into another dungeon, this entity no longer has DungeonComponent
            if (!primaryRef.equals(ref)) {
                LOGGER.atInfo().log("[DungeonRegistrationSystem] This dungeon was merged into another, skipping registration");
                return;
            }

            // Re-fetch dungeon component in case it changed during merge
            dungeon = commandBuffer.getComponent(ref, DungeonComponent.getComponentType());
            if (dungeon == null) {
                return;
            }
        }

        // Register spawners via flood fill
        int spawnerCount = registerSpawners(ref, dungeon, commandBuffer);

        // Find and link entrance
        boolean foundEntrance = linkEntrance(ref, dungeon, commandBuffer);

        dungeon.setRegistered(true);

        LOGGER.atInfo().log("[DungeonRegistrationSystem] Dungeon '%s' registered: %d spawners, entrance found: %s",
                dungeon.getDungeonId(), spawnerCount, foundEntrance);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> accessor) {

        DungeonComponent dungeon = store.getComponent(ref, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return;
        }

        LOGGER.atInfo().log("[DungeonRegistrationSystem] Dungeon '%s' being removed, cleaning up",
                dungeon.getDungeonId());

        // Clean up spawner references
        for (Ref<EntityStore> spawnerRef : dungeon.getSpawnerRefs()) {
            if (!spawnerRef.isValid()) continue;

            SpawnerComponent spawner = accessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner != null) {
                // Cleanup spawnable action
                String executionId = spawner.getExecutionId();
                if (!executionId.isEmpty()) {
                    Spawnable spawnable = spawnableRegistry.get(executionId);
                    if (spawnable != null) {
                        spawnable.cleanup(spawnerRef, accessor);
                    }
                }
            }
        }

        dungeon.clearSpawnerRefs();
    }

    private int registerSpawners(Ref<EntityStore> dungeonRef, DungeonComponent dungeon,
            ComponentAccessor<EntityStore> accessor) {

        List<Ref<EntityStore>> nearbySpawners = EntityFloodFill.floodFillSpawners(
                dungeonRef, accessor, SpawnerComponent.getComponentType(), REGISTRATION_RADIUS);
        int registeredCount = 0;

        for (Ref<EntityStore> spawnerRef : nearbySpawners) {
            SpawnerComponent spawner = accessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());

            if (spawner == null) {
                continue;
            }

            // Link spawner to dungeon
            dungeon.addSpawnerRef(spawnerRef);

            // Register the spawnable action
            String executionId = spawner.getExecutionId();
            if (!executionId.isEmpty()) {
                Spawnable spawnable = spawnableRegistry.get(executionId);
                if (spawnable != null) {
                    spawnable.register(spawnerRef, accessor);
                    LOGGER.atFine().log("[DungeonRegistrationSystem] Registered spawner '%s' to dungeon '%s'",
                            executionId, dungeon.getDungeonId());
                } else {
                    LOGGER.atWarning().log("[DungeonRegistrationSystem] No spawnable for executionId: %s", executionId);
                }
            }

            registeredCount++;
        }

        return registeredCount;
    }

    private boolean linkEntrance(Ref<EntityStore> dungeonRef, DungeonComponent dungeon, ComponentAccessor<EntityStore> accessor) {
        TransformComponent dungeonTransform = accessor.getComponent(dungeonRef, TransformComponent.getComponentType());
        if (dungeonTransform == null) {
            return false;
        }

        Vector3d dungeonPosition = dungeonTransform.getPosition();
        String dungeonId = dungeon.getDungeonId();

        // Search for entrance entities nearby
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = accessor.getResource(entitySpatialResource);
        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(dungeonPosition, ENTRANCE_SEARCH_RADIUS, nearbyEntities);

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) continue;

            DungeonEntranceComponent entrance = accessor.getComponent(entityRef, DungeonEntranceComponent.getComponentType());
            if (entrance == null) continue;

            // Check if dungeonId matches
            if (entrance.getDungeonId().equals(dungeonId)) {
                dungeon.setEntranceRef(entityRef);
                LOGGER.atInfo().log("[DungeonRegistrationSystem] Linked entrance to dungeon '%s'", dungeonId);
                return true;
            }
        }

        LOGGER.atWarning().log("[DungeonRegistrationSystem] No entrance found for dungeon '%s' within radius %s",
                dungeonId, ENTRANCE_SEARCH_RADIUS);
        return false;
    }
}

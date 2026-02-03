package com.chocolate.machine.dungeon.system;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeonRegistrationSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final SpawnableRegistry spawnableRegistry;

    public DungeonRegistrationSystem() {
        this.spawnableRegistry = SpawnableRegistry.getInstance();
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

        LOGGER.atInfo().log("[DungeonRegistrationSystem] Registering dungeon '%s'...", dungeon.getDungeonId());

        // Delegate to DungeonService for unified registration logic
        DungeonModule module = DungeonModule.get();
        if (module != null) {
            DungeonService dungeonService = module.getDungeonService();
            if (dungeonService == null) {
                LOGGER.atWarning().log("[DungeonRegistrationSystem] DungeonService not available");
                return;
            }
            World world = commandBuffer.getExternalData().getWorld();
            int spawnerCount;
            try {
                spawnerCount = dungeonService.registerDungeon(ref, commandBuffer, world);
            } catch (Exception e) {
                LOGGER.atSevere().log("[DungeonRegistrationSystem] Failed to register dungeon: %s", e.getMessage());
                return;
            }

            // Re-fetch dungeon to get updated counts
            dungeon = commandBuffer.getComponent(ref, DungeonComponent.getComponentType());
            int blockCount = dungeon != null ? dungeon.getDungeonBlockCount() : 0;
            boolean hasEntrance = dungeon != null && dungeon.getEntranceRef() != null;

            LOGGER.atInfo().log("[DungeonRegistrationSystem] Dungeon '%s' registered: %d spawners, %d dungeon blocks, entrance found: %s",
                    dungeon != null ? dungeon.getDungeonId() : "(null)", spawnerCount, blockCount, hasEntrance);
        } else {
            LOGGER.atWarning().log("[DungeonRegistrationSystem] DungeonModule not available, cannot register dungeon");
        }
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
                        try {
                            spawnable.cleanup(spawnerRef, accessor);
                        } catch (Exception e) {
                            LOGGER.atWarning().log("failed to cleanup spawnable '%s': %s", executionId, e.getMessage());
                        }
                    }
                }
            }
        }

        dungeon.clearSpawnerRefs();
        dungeon.clearDungeonBlocks();
    }
}

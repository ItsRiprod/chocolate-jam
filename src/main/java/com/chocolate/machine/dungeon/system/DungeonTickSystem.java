package com.chocolate.machine.dungeon.system;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double SPAWNER_TICK_RADIUS = 50.0;
    private static final double SPAWNER_TICK_RADIUS_SQ = SPAWNER_TICK_RADIUS * SPAWNER_TICK_RADIUS;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        DungeonComponent dungeon = chunk.getComponent(index, DungeonComponent.getComponentType());
        if (dungeon == null || !dungeon.isActive()) {
            return;
        }

        List<Ref<EntityStore>> spawnerRefs = dungeon.getSpawnerRefs();
        List<Ref<EntityStore>> dungeoneerRefs = dungeon.getDungeoneerRefs();
        SpawnableRegistry registry = SpawnableRegistry.getInstance();

        // player-centric: only tick spawners near players
        Set<Ref<EntityStore>> spawnersToTick = new HashSet<>();

        for (int p = 0; p < dungeoneerRefs.size(); p++) {
            Ref<EntityStore> playerRef = dungeoneerRefs.get(p);
            if (!playerRef.isValid()) continue;

            TransformComponent playerTransform = commandBuffer.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) continue;

            Vector3d playerPos = playerTransform.getPosition();

            for (int s = 0; s < spawnerRefs.size(); s++) {
                Ref<EntityStore> spawnerRef = spawnerRefs.get(s);
                if (!spawnerRef.isValid()) continue;

                TransformComponent spawnerTransform = commandBuffer.getComponent(spawnerRef, TransformComponent.getComponentType());
                if (spawnerTransform == null) continue;

                double distSq = playerPos.distanceSquaredTo(spawnerTransform.getPosition());
                if (distSq <= SPAWNER_TICK_RADIUS_SQ) {
                    spawnersToTick.add(spawnerRef);
                }
            }
        }

        // tick only spawners near players
        for (Ref<EntityStore> spawnerRef : spawnersToTick) {
            if (!spawnerRef.isValid()) continue;

            SpawnerComponent spawner = commandBuffer.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner == null || !spawner.isActive()) continue;

            Spawnable spawnable = registry.get(spawner.getExecutionId());
            if (spawnable == null) continue;

            try {
                spawnable.tick(dt, spawnerRef, commandBuffer);
            } catch (Exception e) {
                LOGGER.atSevere().log("tick failed for spawner '%s': %s", spawner.getExecutionId(), e.getMessage());
            }
        }
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }
}

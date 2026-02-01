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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public class DungeonTickSystem extends EntityTickingSystem<EntityStore> {

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
        SpawnableRegistry registry = SpawnableRegistry.getInstance();

        for (int i = 0; i < spawnerRefs.size(); i++) {
            Ref<EntityStore> spawnerRef = spawnerRefs.get(i);
            if (!spawnerRef.isValid()) {
                continue;
            }

            SpawnerComponent spawner = commandBuffer.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner == null || !spawner.isActive()) {
                continue;
            }

            Spawnable spawnable = registry.get(spawner.getExecutionId());
            if (spawnable == null) {
                continue;
            }

            spawnable.tick(dt, spawnerRef, commandBuffer);
        }
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }
}

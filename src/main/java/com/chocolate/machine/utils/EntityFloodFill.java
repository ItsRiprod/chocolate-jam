package com.chocolate.machine.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class EntityFloodFill {
    private static final Double RADIUS = 100.0;


    private static final ThreadLocal<ObjectArrayList<Ref<EntityStore>>> NEARBY_LIST = ThreadLocal
            .withInitial(ObjectArrayList::new);

    public static List<Ref<EntityStore>> floodFillSpawners(
            @Nonnull Ref<EntityStore> startRef,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ComponentType<EntityStore, SpawnerComponent> spawnerType) {

        return floodFillSpawners(startRef, accessor, spawnerType, RADIUS);
    }

    // finds all SpawnerComponent entities within radius using spatial BFS
    @Nonnull
    public static List<Ref<EntityStore>> floodFillSpawners(
            @Nonnull Ref<EntityStore> startRef,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ComponentType<EntityStore, SpawnerComponent> spawnerType,
            @Nonnull Double radius) {

        ObjectArrayList<Ref<EntityStore>> result = new ObjectArrayList<>();
        IntOpenHashSet visitedIndices = new IntOpenHashSet();
        Deque<Ref<EntityStore>> queue = new ArrayDeque<>();

        queue.add(startRef);
        visitedIndices.add(startRef.getIndex());

        SpatialResource<Ref<EntityStore>, EntityStore> entitySpatial = accessor
                .getResource(EntityModule.get().getEntitySpatialResourceType());

        ObjectArrayList<Ref<EntityStore>> nearby = NEARBY_LIST.get();

        while (!queue.isEmpty()) {
            Ref<EntityStore> current = queue.poll();

            if (!current.isValid()) {
                continue;
            }

            TransformComponent transform = accessor.getComponent(current, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            Vector3d position = transform.getPosition();

            nearby.clear();
            entitySpatial.getSpatialStructure().collect(position, radius, nearby);

            for (int i = 0; i < nearby.size(); i++) {
                Ref<EntityStore> candidate = nearby.get(i);

                if (!candidate.isValid()) {
                    continue;
                }

                int index = candidate.getIndex();
                if (visitedIndices.contains(index)) {
                    continue;
                }

                if (accessor.getComponent(candidate, spawnerType) != null) {
                    visitedIndices.add(index);
                    queue.add(candidate);
                    result.add(candidate);
                }
            }
        }
        ;

        return result;
    }
}

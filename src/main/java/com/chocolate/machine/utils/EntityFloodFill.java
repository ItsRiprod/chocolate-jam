package com.chocolate.machine.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Component;
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

    public static <T extends Component<EntityStore>> List<Ref<EntityStore>> floodFillSpawners(
            @Nonnull Ref<EntityStore> startRef,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ComponentType<EntityStore, T> componentType) {

        return floodFillSpawners(startRef, accessor, componentType, RADIUS);
    }

    // finds all entities with the specified component within radius using spatial BFS
    @Nonnull
    public static <T extends Component<EntityStore>> List<Ref<EntityStore>> floodFillSpawners(
            @Nonnull Ref<EntityStore> startRef,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ComponentType<EntityStore, T> componentType,
            @Nonnull Double radius) {

        TransformComponent transform = accessor.getComponent(startRef, TransformComponent.getComponentType());
        if (transform == null) {
            return new ObjectArrayList<>();
        }
        return floodFillFromPosition(transform.getPosition(), accessor, componentType, radius);
    }

    // finds all entities with the specified component within radius using spatial BFS from a position
    @Nonnull
    public static <T extends Component<EntityStore>> List<Ref<EntityStore>> floodFillFromPosition(
            @Nonnull Vector3d startPosition,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ComponentType<EntityStore, T> componentType,
            @Nonnull Double radius) {

        ObjectArrayList<Ref<EntityStore>> result = new ObjectArrayList<>();
        IntOpenHashSet visitedIndices = new IntOpenHashSet();
        Deque<Vector3d> positionQueue = new ArrayDeque<>();

        positionQueue.add(startPosition);

        EntityModule entityModule = EntityModule.get();
        if (entityModule == null) {
            return result;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> entitySpatial = accessor
                .getResource(entityModule.getEntitySpatialResourceType());
        if (entitySpatial == null) {
            return result;
        }

        ObjectArrayList<Ref<EntityStore>> nearby = NEARBY_LIST.get();
        if (nearby == null) {
            nearby = new ObjectArrayList<>();
        }

        while (!positionQueue.isEmpty()) {
            Vector3d currentPos = positionQueue.poll();

            nearby.clear();
            entitySpatial.getSpatialStructure().collect(currentPos, radius, nearby);

            for (int i = 0; i < nearby.size(); i++) {
                Ref<EntityStore> candidate = nearby.get(i);

                if (!candidate.isValid()) {
                    continue;
                }

                int index = candidate.getIndex();
                if (visitedIndices.contains(index)) {
                    continue;
                }

                if (accessor.getComponent(candidate, componentType) != null) {
                    visitedIndices.add(index);
                    result.add(candidate);

                    TransformComponent transform = accessor.getComponent(candidate, TransformComponent.getComponentType());
                    if (transform != null) {
                        positionQueue.add(transform.getPosition());
                    }
                }
            }
        }

        return result;
    }
}

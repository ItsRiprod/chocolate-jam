package com.chocolate.machine.utils;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeonFinder {

    private static final double DEFAULT_SEARCH_RADIUS = 500.0;
    public static final double MERGE_RANGE = 50.0;

    @Nullable
    public static Ref<EntityStore> findNearestDungeon(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor) {
        return findNearestDungeon(position, accessor, DEFAULT_SEARCH_RADIUS);
    }

    @Nullable
    public static Ref<EntityStore> findNearestDungeon(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            double searchRadius) {

        EntityModule entityModule = EntityModule.get();
        if (entityModule == null) {
            return null;
        }
        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialType =
                entityModule.getEntitySpatialResourceType();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = accessor.getResource(entitySpatialType);
        if (spatial == null) {
            return null;
        }

        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
        if (nearbyEntities == null) {
            return null;
        }
        spatial.getSpatialStructure().collect(position, searchRadius, nearbyEntities);

        Ref<EntityStore> nearestDungeon = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) continue;

            DungeonComponent dungeon = accessor.getComponent(entityRef, DungeonComponent.getComponentType());
            if (dungeon == null) continue;

            TransformComponent transform = accessor.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;

            double distance = position.distanceSquaredTo(transform.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestDungeon = entityRef;
            }
        }

        return nearestDungeon;
    }

    @Nullable
    public static Ref<EntityStore> findNearestDungeonToPlayer(
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        TransformComponent playerTransform = accessor.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return null;
        }

        return findNearestDungeon(playerTransform.getPosition(), accessor);
    }

    @Nonnull
    public static List<Ref<EntityStore>> findAllDungeonsInRange(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            double searchRadius) {

        List<Ref<EntityStore>> result = new ArrayList<>();

        EntityModule entityModule = EntityModule.get();
        if (entityModule == null) {
            return result;
        }
        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialType =
                entityModule.getEntitySpatialResourceType();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = accessor.getResource(entitySpatialType);
        if (spatial == null) {
            return result;
        }

        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
        if (nearbyEntities == null) {
            return result;
        }
        spatial.getSpatialStructure().collect(position, searchRadius, nearbyEntities);

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) continue;

            DungeonComponent dungeon = accessor.getComponent(entityRef, DungeonComponent.getComponentType());
            if (dungeon == null) continue;

            result.add(entityRef);
        }

        return result;
    }

    @Nonnull
    public static List<Ref<EntityStore>> findDungeonsToMerge(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        TransformComponent transform = accessor.getComponent(dungeonRef, TransformComponent.getComponentType());
        if (transform == null) {
            return new ArrayList<>();
        }

        List<Ref<EntityStore>> allDungeons = findAllDungeonsInRange(transform.getPosition(), accessor, MERGE_RANGE);

        allDungeons.removeIf(ref -> ref.equals(dungeonRef));

        return allDungeons;
    }
}

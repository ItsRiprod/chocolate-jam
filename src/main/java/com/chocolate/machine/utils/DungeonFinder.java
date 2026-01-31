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

/**
 * Utility class for finding nearby dungeon entities.
 */
public class DungeonFinder {

    private static final double DEFAULT_SEARCH_RADIUS = 500.0;

    /** Range within which dungeons should be merged into one network */
    public static final double MERGE_RANGE = 50.0;

    /**
     * Find the nearest DungeonComponent entity to a given position.
     *
     * @param position The position to search from
     * @param accessor Component accessor
     * @return The nearest dungeon ref, or null if none found
     */
    @Nullable
    public static Ref<EntityStore> findNearestDungeon(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor) {
        return findNearestDungeon(position, accessor, DEFAULT_SEARCH_RADIUS);
    }

    /**
     * Find the nearest DungeonComponent entity to a given position.
     *
     * @param position The position to search from
     * @param accessor Component accessor
     * @param searchRadius The radius to search within
     * @return The nearest dungeon ref, or null if none found
     */
    @Nullable
    public static Ref<EntityStore> findNearestDungeon(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            double searchRadius) {

        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialType =
                EntityModule.get().getEntitySpatialResourceType();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = accessor.getResource(entitySpatialType);

        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
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

    /**
     * Find the nearest DungeonComponent entity to a player entity.
     *
     * @param playerRef The player entity reference
     * @param accessor Component accessor
     * @return The nearest dungeon ref, or null if none found
     */
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

    /**
     * Find ALL DungeonComponent entities within a given range of a position.
     * Used to detect multiple dungeons that should be merged.
     *
     * @param position The position to search from
     * @param accessor Component accessor
     * @param searchRadius The radius to search within
     * @return List of dungeon refs found (may be empty)
     */
    @Nonnull
    public static List<Ref<EntityStore>> findAllDungeonsInRange(
            @Nonnull Vector3d position,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            double searchRadius) {

        List<Ref<EntityStore>> result = new ArrayList<>();

        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialType =
                EntityModule.get().getEntitySpatialResourceType();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = accessor.getResource(entitySpatialType);

        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
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

    /**
     * Find ALL DungeonComponent entities within MERGE_RANGE of a dungeon entity.
     * Used to find dungeons that should be merged into one network.
     *
     * @param dungeonRef The dungeon entity to search from
     * @param accessor Component accessor
     * @return List of other dungeon refs within merge range (excludes the input dungeon)
     */
    @Nonnull
    public static List<Ref<EntityStore>> findDungeonsToMerge(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        TransformComponent transform = accessor.getComponent(dungeonRef, TransformComponent.getComponentType());
        if (transform == null) {
            return new ArrayList<>();
        }

        List<Ref<EntityStore>> allDungeons = findAllDungeonsInRange(transform.getPosition(), accessor, MERGE_RANGE);

        // Remove self from list
        allDungeons.removeIf(ref -> ref.equals(dungeonRef));

        return allDungeons;
    }
}

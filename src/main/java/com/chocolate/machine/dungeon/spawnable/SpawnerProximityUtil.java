package com.chocolate.machine.dungeon.spawnable;

import java.util.List;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class SpawnerProximityUtil {

    public static final double SPAWN_RADIUS = 50.0;

    public static boolean isPlayerNearby(
            Ref<EntityStore> spawnerRef,
            CommandBuffer<EntityStore> commandBuffer) {

        TransformComponent spawnerTransform = commandBuffer.getComponent(
                spawnerRef, TransformComponent.getComponentType());
        if (spawnerTransform == null) {
            return false;
        }

        return isPlayerNearby(spawnerTransform.getPosition(), commandBuffer);
    }

    public static boolean isPlayerNearby(
            Vector3d position,
            CommandBuffer<EntityStore> commandBuffer) {

        EntityModule entityModule = EntityModule.get();
        if (entityModule == null) {
            return false;
        }

        ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> playerSpatialType =
                entityModule.getPlayerSpatialResourceType();
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatial =
                commandBuffer.getResource(playerSpatialType);
        if (playerSpatial == null) {
            return false;
        }

        List<Ref<EntityStore>> nearbyPlayers = SpatialResource.getThreadLocalReferenceList();
        if (nearbyPlayers == null) {
            return false;
        }

        playerSpatial.getSpatialStructure().collect(position, SPAWN_RADIUS, nearbyPlayers);

        return !nearbyPlayers.isEmpty();
    }
}

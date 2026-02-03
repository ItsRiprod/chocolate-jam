package com.chocolate.machine.dungeon.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class SpawnedEntityComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, SpawnedEntityComponent> componentType;

    private final long spawnTimestamp;
    private final String spawnerId;

    public SpawnedEntityComponent() {
        this.spawnTimestamp = System.currentTimeMillis();
        this.spawnerId = "";
    }

    public SpawnedEntityComponent(String spawnerId) {
        this.spawnTimestamp = System.currentTimeMillis();
        this.spawnerId = spawnerId != null ? spawnerId : "";
    }

    public static void setComponentType(ComponentType<EntityStore, SpawnedEntityComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, SpawnedEntityComponent> getComponentType() {
        return componentType;
    }

    public long getSpawnTimestamp() {
        return spawnTimestamp;
    }

    public String getSpawnerId() {
        return spawnerId;
    }

    @Nonnull
    @Override
    public SpawnedEntityComponent clone() {
        return new SpawnedEntityComponent(this.spawnerId);
    }
}

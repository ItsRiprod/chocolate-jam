package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// per-spawner state for skeleton action
public class SkeletonActionComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, SkeletonActionComponent> componentType;

    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;

    public SkeletonActionComponent() {
        this.spawnedRef = null;
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, SkeletonActionComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, SkeletonActionComponent> getComponentType() {
        return componentType;
    }

    @Nullable
    public Ref<EntityStore> getSpawnedRef() {
        return spawnedRef;
    }

    public void setSpawnedRef(@Nullable Ref<EntityStore> spawnedRef) {
        this.spawnedRef = spawnedRef;
    }

    public boolean hasSpawned() {
        return spawnedRef != null && spawnedRef.isValid();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void reset() {
        this.spawnedRef = null;
        this.active = false;
    }

    @Nonnull
    @Override
    public SkeletonActionComponent clone() {
        SkeletonActionComponent copy = new SkeletonActionComponent();
        copy.spawnedRef = this.spawnedRef;
        copy.active = this.active;
        return copy;
    }
}

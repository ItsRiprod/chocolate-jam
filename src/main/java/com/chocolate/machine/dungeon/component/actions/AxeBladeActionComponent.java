package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// per-spawner state for axe blade action
public class AxeBladeActionComponent implements Component<EntityStore> {

    // Empty codec - no configurable fields to persist, only runtime state
    public static final BuilderCodec<AxeBladeActionComponent> CODEC = BuilderCodec
            .builder(AxeBladeActionComponent.class, AxeBladeActionComponent::new)
            .build();

    private static ComponentType<EntityStore, AxeBladeActionComponent> componentType;

    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;

    public AxeBladeActionComponent() {
        this.spawnedRef = null;
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, AxeBladeActionComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, AxeBladeActionComponent> getComponentType() {
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
    public AxeBladeActionComponent clone() {
        AxeBladeActionComponent copy = new AxeBladeActionComponent();
        copy.spawnedRef = this.spawnedRef;
        copy.active = this.active;
        return copy;
    }
}

package com.chocolate.machine.dungeon.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// attached to spawner entities, marks entity as a spawner
public class SpawnerComponent implements Component<EntityStore> {

    public static final BuilderCodec<SpawnerComponent> CODEC = BuilderCodec
            .builder(SpawnerComponent.class, SpawnerComponent::new)
            .append(new KeyedCodec<>("ExecutionId", Codec.STRING),
                    (c, v) -> c.executionId = v,
                    c -> c.executionId)
            .addValidator(Validators.nonNull())
            .add()
            .build();

    private static ComponentType<EntityStore, SpawnerComponent> componentType;

    
    @Nonnull
    private String executionId = "";

    // runtime (not serialized)
    @Nullable
    private boolean isActive = false;
    
    public SpawnerComponent() {
    }

    public SpawnerComponent(@Nonnull String executionId) {
        this.executionId = executionId;
    }

    public static void setComponentType(ComponentType<EntityStore, SpawnerComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, SpawnerComponent> getComponentType() {
        return componentType;
    }

    @Nonnull
    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(@Nonnull String executionId) {
        this.executionId = executionId;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Nonnull
    @Override
    public SpawnerComponent clone() {
        SpawnerComponent copy = new SpawnerComponent(this.executionId);
        return copy;
    }
}

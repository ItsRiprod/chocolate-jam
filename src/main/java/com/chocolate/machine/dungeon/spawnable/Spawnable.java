package com.chocolate.machine.dungeon.spawnable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public interface Spawnable {

    @Nonnull
    String getId();

    void register(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor);

    void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor);

    void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor);

    void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor);

    void cleanup(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor);

    default void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }
}

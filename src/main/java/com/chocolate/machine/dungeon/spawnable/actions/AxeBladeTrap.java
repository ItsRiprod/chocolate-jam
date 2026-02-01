package com.chocolate.machine.dungeon.spawnable.actions;

import com.chocolate.machine.dungeon.component.actions.AxeBladeActionComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AxeBladeTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "axe";

    public AxeBladeTrap() {
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        AxeBladeActionComponent existing = componentAccessor.getComponent(
                spawnerRef, AxeBladeActionComponent.getComponentType());

        if (existing != null) {
            LOGGER.atFine().log("AxeBladeActionComponent already exists, resetting");
            existing.reset();
            return;
        }

        componentAccessor.addComponent(spawnerRef, AxeBladeActionComponent.getComponentType(),
                new AxeBladeActionComponent());

        LOGGER.atFine().log("Registered AxeBladeActionComponent for spawner");
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            LOGGER.atWarning().log("Cannot activate axe blade: spawner ref is invalid");
            return;
        }

        AxeBladeActionComponent state = componentAccessor.getComponent(
                spawnerRef, AxeBladeActionComponent.getComponentType());

        if (state == null) {
            LOGGER.atWarning().log("No AxeBladeActionComponent found, registering first");
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, AxeBladeActionComponent.getComponentType());
        }

        if (state.hasSpawned()) {
            LOGGER.atFine().log("Axe blade already spawned");
            return;
        }

        TransformComponent spawnerTransform = componentAccessor.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            LOGGER.atWarning().log("Spawner has no TransformComponent");
            return;
        }

        Vector3d position = spawnerTransform.getPosition().clone();
        Vector3f rotation = spawnerTransform.getRotation().clone();

        // TODO: actual axe blade entity, placeholder for now
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, rotation));

        Ref<EntityStore> spawnedRef = componentAccessor.addEntity(holder, AddReason.SPAWN);

        if (spawnedRef == null) {
            LOGGER.atWarning().log("Failed to spawn axe blade entity");
            return;
        }

        state.setSpawnedRef(spawnedRef);
        state.setActive(true);

        LOGGER.atInfo().log("Spawned axe blade at (%.1f, %.1f, %.1f)",
                position.getX(), position.getY(), position.getZ());
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        AxeBladeActionComponent state = componentAccessor.getComponent(
                spawnerRef, AxeBladeActionComponent.getComponentType());

        if (state == null) {
            LOGGER.atFine().log("No AxeBladeActionComponent found, nothing to deactivate");
            return;
        }

        Ref<EntityStore> spawnedRef = state.getSpawnedRef();
        if (spawnedRef == null || !spawnedRef.isValid()) {
            state.setSpawnedRef(null);
            state.setActive(false);
            return;
        }

        if (componentAccessor instanceof CommandBuffer) {
            ((CommandBuffer<EntityStore>) componentAccessor).tryRemoveEntity(spawnedRef, RemoveReason.REMOVE);
        } else if (componentAccessor instanceof Store) {
            ((Store<EntityStore>) componentAccessor).removeEntity(spawnedRef, RemoveReason.REMOVE);
        }

        state.setSpawnedRef(null);
        state.setActive(false);

        LOGGER.atInfo().log("Despawned axe blade");
    }

    @Override
    public void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        activate(spawnerRef, componentAccessor);

        LOGGER.atInfo().log("Reset axe blade spawner");
    }

    @Override
    public void cleanup(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        if (componentAccessor.getComponent(spawnerRef, AxeBladeActionComponent.getComponentType()) != null) {
            componentAccessor.removeComponent(spawnerRef, AxeBladeActionComponent.getComponentType());
        }

        LOGGER.atFine().log("Cleaned up AxeBladeActionComponent");
    }
}

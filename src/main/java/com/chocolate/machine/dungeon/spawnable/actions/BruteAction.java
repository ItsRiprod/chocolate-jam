package com.chocolate.machine.dungeon.spawnable.actions;

import com.chocolate.machine.dungeon.component.actions.SkeletonActionComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnerProximityUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;

public class BruteAction implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "brute";

    private static final String SKELETON_ROLE = "Tier3_Enemy";

    private static final String SKELETON_GROUP = null;

    public BruteAction() {
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

        SkeletonActionComponent existing = componentAccessor.getComponent(
                spawnerRef, SkeletonActionComponent.getComponentType());

        if (existing != null) {
            LOGGER.atFine().log("SkeletonActionComponent already exists, resetting");
            existing.reset();
            return;
        }

        componentAccessor.addComponent(spawnerRef, SkeletonActionComponent.getComponentType(),
                new SkeletonActionComponent());

        LOGGER.atFine().log("Registered SkeletonActionComponent for spawner");
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            LOGGER.atWarning().log("Cannot activate brute: spawner ref is invalid");
            return;
        }

        SkeletonActionComponent state = componentAccessor.getComponent(
                spawnerRef, SkeletonActionComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, SkeletonActionComponent.getComponentType());
            if (state == null) {
                LOGGER.atSevere().log("Failed to register BruteAction");
                return;
            }
        }

        state.setActive(true);
        LOGGER.atFine().log("Brute spawner activated, will spawn when player nearby");
    }

    @Override
    public void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SkeletonActionComponent state = commandBuffer.getComponent(
                spawnerRef, SkeletonActionComponent.getComponentType());

        if (state == null || !state.isActive()) {
            return;
        }

        if (state.hasSpawned()) {
            return;
        }

        if (!SpawnerProximityUtil.isPlayerNearby(spawnerRef, commandBuffer)) {
            return;
        }

        spawnNPC(spawnerRef, state, commandBuffer);
    }

    private void spawnNPC(
            Ref<EntityStore> spawnerRef,
            SkeletonActionComponent state,
            CommandBuffer<EntityStore> commandBuffer) {

        TransformComponent spawnerTransform = commandBuffer.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return;
        }

        Vector3d position = spawnerTransform.getPosition().clone();
        Vector3f rotation = spawnerTransform.getRotation().clone();

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atSevere().log("NPCPlugin not available");
            return;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        if (store == null) {
            return;
        }

        Pair<Ref<EntityStore>, ?> npcPair = npcPlugin.spawnNPC(
                store, SKELETON_ROLE, SKELETON_GROUP, position, rotation);

        if (npcPair == null || npcPair.first() == null) {
            LOGGER.atWarning().log("Failed to spawn brute - role '%s' may not exist", SKELETON_ROLE);
            return;
        }

        state.setSpawnedRef(npcPair.first());

        LOGGER.atInfo().log("Spawned brute at (%.1f, %.1f, %.1f)",
                position.getX(), position.getY(), position.getZ());
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        SkeletonActionComponent state = componentAccessor.getComponent(
                spawnerRef, SkeletonActionComponent.getComponentType());

        if (state == null) {
            LOGGER.atFine().log("No SkeletonActionComponent found, nothing to deactivate");
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

        LOGGER.atInfo().log("Despawned skeleton");
    }

    @Override
    public void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        activate(spawnerRef, componentAccessor);

        LOGGER.atInfo().log("Reset skeleton spawner");
    }

    @Override
    public void cleanup(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        if (componentAccessor.getComponent(spawnerRef, SkeletonActionComponent.getComponentType()) != null) {
            componentAccessor.removeComponent(spawnerRef, SkeletonActionComponent.getComponentType());
        }

        LOGGER.atFine().log("Cleaned up SkeletonActionComponent");
    }

    private Store<EntityStore> getStore(ComponentAccessor<EntityStore> accessor) {
        if (accessor instanceof Store) {
            return (Store<EntityStore>) accessor;
        } else if (accessor instanceof CommandBuffer) {
            return ((CommandBuffer<EntityStore>) accessor).getStore();
        }
        return null;
    }
}

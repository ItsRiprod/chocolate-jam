package com.chocolate.machine.dungeon.spawnable.actions;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.actions.LaserTrapActionComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class LaserTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "laser";

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        LaserTrapActionComponent existing = componentAccessor.getComponent(
                spawnerRef, LaserTrapActionComponent.getComponentType());

        if (existing != null) {
            existing.reset();
            return;
        }

        try {
            componentAccessor.ensureAndGetComponent(spawnerRef, LaserTrapActionComponent.getComponentType());
        } catch (IllegalArgumentException e) {
            // race: component added by another spawner in same tick
        }
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        LaserTrapActionComponent state = componentAccessor.getComponent(
                spawnerRef, LaserTrapActionComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, LaserTrapActionComponent.getComponentType());
            if (state == null) {
                LOGGER.atWarning().log("failed to register LaserTrapActionComponent");
                return;
            }
        }

        state.setActive(true);
        state.resetFireTimer();

        LOGGER.atInfo().log("Laser trap activated");
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        LaserTrapActionComponent state = componentAccessor.getComponent(
                spawnerRef, LaserTrapActionComponent.getComponentType());

        if (state == null) {
            return;
        }

        state.setActive(false);
    }

    @Override
    public void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        activate(spawnerRef, componentAccessor);
    }

    @Override
    public void cleanup(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        deactivate(spawnerRef, componentAccessor);
        if (componentAccessor.getComponent(spawnerRef, LaserTrapActionComponent.getComponentType()) != null) {
            componentAccessor.removeComponent(spawnerRef, LaserTrapActionComponent.getComponentType());
        }
    }

    @Override
    public void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        LaserTrapActionComponent laser = commandBuffer.getComponent(
                spawnerRef, LaserTrapActionComponent.getComponentType());

        if (laser == null || !laser.isActive()) {
            return;
        }

        TransformComponent spawnerTransform = commandBuffer.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return;
        }

        laser.addFireTime(dt);

        if (laser.getFireTimer() >= laser.getFireInterval()) {
            fireProjectile(laser, spawnerTransform, commandBuffer);
            laser.resetFireTimer();
        }
    }

    private void fireProjectile(
            LaserTrapActionComponent laser,
            TransformComponent spawnerTransform,
            CommandBuffer<EntityStore> commandBuffer) {

        Vector3d basePos = spawnerTransform.getPosition();

        double spawnX = basePos.x + laser.getOffsetX();
        double spawnY = basePos.y + laser.getOffsetY();
        double spawnZ = basePos.z + laser.getOffsetZ();

        Vector3d spawnPos = new Vector3d(spawnX, spawnY, spawnZ);

        float pitch = laser.getPitch();
        float yaw = laser.getYaw();
        Vector3f rotation = new Vector3f(pitch, yaw, 0f);

        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
        if (timeResource == null) {
            LOGGER.atWarning().log("TimeResource not available");
            return;
        }

        Holder<EntityStore> holder;
        try {
            holder = ProjectileComponent.assembleDefaultProjectile(
                    timeResource,
                    laser.getProjectileId(),
                    spawnPos,
                    rotation);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to assemble projectile: %s", e.getMessage());
            return;
        }

        if (holder == null) {
            LOGGER.atWarning().log("Failed to assemble projectile - holder is null");
            return;
        }

        ProjectileComponent projectileComponent = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectileComponent == null || !projectileComponent.initialize()) {
            LOGGER.atWarning().log("Failed to initialize projectile: %s", laser.getProjectileId());
            return;
        }

        BoundingBox boundingBox = holder.getComponent(BoundingBox.getComponentType());
        if (boundingBox != null) {
            projectileComponent.initializePhysics(boundingBox);
        }

        UUID creatorUuid = UUID.randomUUID();
        projectileComponent.shoot(
                holder,
                creatorUuid,
                spawnX, spawnY, spawnZ,
                yaw,
                pitch);
    }
}

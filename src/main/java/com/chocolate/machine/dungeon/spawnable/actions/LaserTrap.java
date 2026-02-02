package com.chocolate.machine.dungeon.spawnable.actions;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.actions.LaserTrapActionComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class LaserTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "laser";

    // sound for when laser fires
    private static final String FIRE_SOUND_ASSET = "SFX_Staff_Ice_Shoot";

    private int fireSoundIndex = -1;

    private int getFireSoundIndex() {
        if (fireSoundIndex < 0) {
            fireSoundIndex = SoundEvent.getAssetMap().getIndex(FIRE_SOUND_ASSET);
        }
        return fireSoundIndex;
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

        LaserTrapActionComponent existing = componentAccessor.getComponent(
                spawnerRef, LaserTrapActionComponent.getComponentType());

        if (existing != null) {
            existing.reset();
            return;
        }

        componentAccessor.addComponent(spawnerRef, LaserTrapActionComponent.getComponentType(),
                new LaserTrapActionComponent());
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
        }

        // Direction is configured via /cm t config offset command
        // Uses hardcoded defaults from LaserTrapActionComponent (pitch=90 shoots up)

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
        // only remove if the component exists on this entity
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

        // check if time to fire
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

        // Calculate spawn position with configured offsets
        double spawnX = basePos.x + laser.getOffsetX();
        double spawnY = basePos.y + laser.getOffsetY();
        double spawnZ = basePos.z + laser.getOffsetZ();

        Vector3d spawnPos = new Vector3d(spawnX, spawnY, spawnZ);

        // Use configured direction (set via /cm t config rotate)
        float pitch = laser.getPitch();
        float yaw = laser.getYaw();
        Vector3f rotation = new Vector3f(pitch, yaw, 0f);

        // get time resource for despawn component
        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());

        // assemble the projectile entity
        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                timeResource,
                laser.getProjectileId(),
                spawnPos,
                rotation);

        // get the projectile component and initialize it
        ProjectileComponent projectileComponent = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectileComponent == null || !projectileComponent.initialize()) {
            LOGGER.atWarning().log("Failed to initialize projectile: %s", laser.getProjectileId());
            return;
        }

        // initialize physics with bounding box
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

        // spawn the projectile entity
        Ref<EntityStore> projectileRef = commandBuffer.addEntity(holder, AddReason.SPAWN);

        if (projectileRef != null) {
            // play fire sound
            SoundUtil.playSoundEvent3d(getFireSoundIndex(), SoundCategory.SFX, spawnPos, commandBuffer);

            LOGGER.atFine().log("Fired laser projectile at (%.1f, %.1f, %.1f) yaw=%.2f pitch=%.2f",
                    spawnX, spawnY, spawnZ, yaw, pitch);
        }
    }
}

package com.chocolate.machine.dungeon.spawnable.actions;

import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.actions.LaserTrapActionComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.modules.collision.CollisionMath;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import it.unimi.dsi.fastutil.objects.ObjectList;

public class BeamTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "beam";

    private static final String DAMAGE_CAUSE_NAME = "Environment";
    private static final String ENVIRONMENT_SOURCE_TYPE = "beam_trap";
    private static final double MAX_BEAM_DISTANCE = 64.0;
    private static final double PARTICLE_VIEW_DISTANCE = 75.0;
    private static final String BEAM_PARTICLE_SYSTEM = "CM_Line_System";
    private static final Color BEAM_COLOR = new Color((byte) 255, (byte) 100, (byte) 100);

    private int damageCauseIndex = -1;

    private int getDamageCauseIndex() {
        if (damageCauseIndex < 0) {
            damageCauseIndex = DamageCause.getAssetMap().getIndex(DAMAGE_CAUSE_NAME);
        }
        return damageCauseIndex;
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

        // set with default short interval for beam traps
        componentAccessor.addComponent(spawnerRef, LaserTrapActionComponent.getComponentType(),
                new LaserTrapActionComponent(0.25f));
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

        state.setActive(true);
        state.resetFireTimer();

        LOGGER.atInfo().log("Beam trap activated");
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

        Vector3d basePos = spawnerTransform.getPosition();
        double startX = basePos.x + laser.getOffsetX();
        double startY = basePos.y + laser.getOffsetY();
        double startZ = basePos.z + laser.getOffsetZ();
        Vector3d startPos = new Vector3d(startX, startY, startZ);

        float pitch = laser.getPitch();
        float yaw = laser.getYaw();
        Vector3d direction = Transform.getDirection(pitch, yaw);

        World world = commandBuffer.getExternalData().getWorld();
        Vector3d hitLocation = TargetUtil.getTargetLocation(
                world,
                blockId -> blockId != 0,
                startX, startY, startZ,
                direction.x, direction.y, direction.z,
                MAX_BEAM_DISTANCE);

        Vector3d endPos;
        if (hitLocation != null) {
            endPos = hitLocation;
        } else {
            endPos = new Vector3d(
                    startX + direction.x * MAX_BEAM_DISTANCE,
                    startY + direction.y * MAX_BEAM_DISTANCE,
                    startZ + direction.z * MAX_BEAM_DISTANCE);
        }

        renderBeam(spawnerRef, startPos, endPos, yaw, pitch, commandBuffer);

        laser.addFireTime(dt);
        if (laser.getFireTimer() >= laser.getFireInterval()) {
            damageEntitiesAlongBeam(spawnerRef, laser, startPos, direction, endPos, commandBuffer);
            laser.resetFireTimer();
        }
    }

    private void renderBeam(
            Ref<EntityStore> spawnerRef,
            Vector3d startPos,
            Vector3d endPos,
            float yaw,
            float pitch,
            CommandBuffer<EntityStore> commandBuffer) {

        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource = commandBuffer.getResource(
                EntityModule.get().getPlayerSpatialResourceType());
        ObjectList<Ref<EntityStore>> playerRefs = SpatialResource.getThreadLocalReferenceList();
        playerSpatialResource.getSpatialStructure().collect(startPos, PARTICLE_VIEW_DISTANCE, playerRefs);

        if (playerRefs.isEmpty()) {
            return;
        }

        ParticleUtil.spawnParticleEffect(
                BEAM_PARTICLE_SYSTEM,
                startPos.x, startPos.y, startPos.z,
                yaw, pitch, 0.0f,
                spawnerRef,
                playerRefs,
                commandBuffer);
    }

    private void damageEntitiesAlongBeam(
            Ref<EntityStore> spawnerRef,
            LaserTrapActionComponent laser,
            Vector3d startPos,
            Vector3d direction,
            Vector3d endPos,
            CommandBuffer<EntityStore> commandBuffer) {

        double beamLength = startPos.distanceTo(endPos);
        Vector3d midPoint = new Vector3d(
                (startPos.x + endPos.x) / 2,
                (startPos.y + endPos.y) / 2,
                (startPos.z + endPos.z) / 2);

        double searchRadius = (beamLength / 2) + 2.0;
        List<Ref<EntityStore>> nearbyEntities = TargetUtil.getAllEntitiesInSphere(midPoint, searchRadius, commandBuffer);

        for (Ref<EntityStore> entityRef : nearbyEntities) {
            if (!entityRef.isValid() || entityRef.equals(spawnerRef)) {
                continue;
            }

            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                continue;
            }

            BoundingBox boundingBoxComponent = commandBuffer.getComponent(entityRef, BoundingBox.getComponentType());
            TransformComponent entityTransform = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());

            if (boundingBoxComponent == null || entityTransform == null) {
                continue;
            }

            Vector3d entityPos = entityTransform.getPosition();
            Box boundingBox = boundingBoxComponent.getBoundingBox();
            Vector2d minMax = new Vector2d();

            boolean hit = CollisionMath.intersectRayAABB(
                    startPos, direction,
                    entityPos.getX(), entityPos.getY(), entityPos.getZ(),
                    boundingBox, minMax);

            if (hit && minMax.x <= beamLength) {
                Damage damage = new Damage(
                        new Damage.EnvironmentSource(ENVIRONMENT_SOURCE_TYPE),
                        getDamageCauseIndex(),
                        laser.getDamage() * 1.15f);

                DamageSystems.executeDamage(entityRef, commandBuffer, damage);
            }
        }
    }
}

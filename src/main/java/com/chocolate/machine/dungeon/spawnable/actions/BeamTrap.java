package com.chocolate.machine.dungeon.spawnable.actions;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.SpawnedEntityComponent;
import com.chocolate.machine.dungeon.component.actions.LaserBeamComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionMath;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public class BeamTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "beam";

    private static final String LASER_BEAM_MODEL_ASSET = "Laser_Beam";
    private static final String LASER_BEAM_SOUND = "SFX_LaserBeam_Loop";

    private static final String DAMAGE_CAUSE_NAME = "Environment";
    private static final String ENVIRONMENT_SOURCE_TYPE = "beam_trap";

    private int damageCauseIndex = -1;
    private int soundIndex = -1;

    private int getDamageCauseIndex() {
        if (damageCauseIndex < 0) {
            damageCauseIndex = DamageCause.getAssetMap().getIndex(DAMAGE_CAUSE_NAME);
        }
        return damageCauseIndex;
    }

    private int getSoundIndex() {
        if (soundIndex < 0) {
            soundIndex = SoundEvent.getAssetMap().getIndex(LASER_BEAM_SOUND);
        }
        return soundIndex;
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

        LaserBeamComponent existing = componentAccessor.getComponent(
                spawnerRef, LaserBeamComponent.getComponentType());

        if (existing != null) {
            existing.reset();
            return;
        }

        componentAccessor.ensureAndGetComponent(spawnerRef, LaserBeamComponent.getComponentType());
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        LaserBeamComponent state = componentAccessor.getComponent(
                spawnerRef, LaserBeamComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, LaserBeamComponent.getComponentType());
            if (state == null) {
                LOGGER.atWarning().log("failed to register LaserBeamComponent");
                return;
            }
        }

        TransformComponent spawnerTransform = componentAccessor.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return;
        }

        removeBeamSegments(state, componentAccessor);

        World world = componentAccessor.getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("cannot activate beam trap: world is null");
            return;
        }
        spawnBeamSegments(spawnerRef, state, spawnerTransform, world, componentAccessor);

        state.setActive(true);
        state.resetDamageTimer();
        state.addSoundTime(LaserBeamComponent.SOUND_INTERVAL);

        LOGGER.atInfo().log("Laser trap activated with %d beam segments", state.getBeamSegments().size());
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        LaserBeamComponent state = componentAccessor.getComponent(
                spawnerRef, LaserBeamComponent.getComponentType());

        if (state == null) {
            return;
        }

        removeBeamSegments(state, componentAccessor);
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
        if (componentAccessor.getComponent(spawnerRef, LaserBeamComponent.getComponentType()) != null) {
            componentAccessor.removeComponent(spawnerRef, LaserBeamComponent.getComponentType());
        }
    }

    @Override
    public void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        LaserBeamComponent state = commandBuffer.getComponent(
                spawnerRef, LaserBeamComponent.getComponentType());

        if (state == null || !state.isActive()) {
            return;
        }

        TransformComponent spawnerTransform = commandBuffer.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return;
        }

        state.addSoundTime(dt);
        if (state.getSoundTimer() >= LaserBeamComponent.SOUND_INTERVAL) {
            Vector3d basePos = spawnerTransform.getPosition();
            Vector3d soundPos = new Vector3d(
                    basePos.x + state.getOffsetX(),
                    basePos.y + state.getOffsetY(),
                    basePos.z + state.getOffsetZ());
            SoundUtil.playSoundEvent3d(getSoundIndex(), SoundCategory.SFX, soundPos, commandBuffer);
            state.resetSoundTimer();
        }

        state.addDamageTime(dt);

        if (state.getDamageTimer() >= state.getDamageInterval()) {
            damageEntitiesAlongBeam(spawnerRef, state, spawnerTransform, commandBuffer);
            state.resetDamageTimer();
        }
    }

    private void spawnBeamSegments(
            Ref<EntityStore> spawnerRef,
            LaserBeamComponent state,
            TransformComponent spawnerTransform,
            World world,
            ComponentAccessor<EntityStore> componentAccessor) {

        Vector3d basePos = spawnerTransform.getPosition();
        double startX = basePos.x + state.getOffsetX();
        double startY = basePos.y + state.getOffsetY();
        double startZ = basePos.z + state.getOffsetZ();

        float pitchDeg = state.getPitch();
        float yawDeg = state.getYaw();
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float yawRad = (float) Math.toRadians(yawDeg);
        Vector3d direction = Transform.getDirection(pitchRad, yawRad);

        double blockHitDistance = findBlockHitDistance(world, startX, startY, startZ, direction, state);

        int numSegments = (int) Math.ceil(blockHitDistance / LaserBeamComponent.BEAM_SEGMENT_HEIGHT) + 2; // add two for making it "connect" with the last block
        if (numSegments < 1) {
            numSegments = 1;
        }

        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(LASER_BEAM_MODEL_ASSET);
        if (modelAsset == null) {
            LOGGER.atWarning().log("Failed to find Laser_Beam model asset");
            return;
        }

        for (int i = 0; i < numSegments; i++) {
            double segmentOffset = i * LaserBeamComponent.BEAM_SEGMENT_HEIGHT;

            double segX = startX + direction.x * segmentOffset;
            double segY = startY + direction.y * segmentOffset;
            double segZ = startZ + direction.z * segmentOffset;

            Ref<EntityStore> segmentRef = spawnBeamSegment(
                    segX, segY, segZ, pitchDeg, yawDeg, modelAsset, componentAccessor);

            if (segmentRef != null && segmentRef.isValid()) {
                state.addBeamSegment(segmentRef);
            }
        }
    }

    private double findBlockHitDistance(
            World world,
            double startX, double startY, double startZ,
            Vector3d direction,
            LaserBeamComponent state) {

        // skips the first few units to avoid hitting the spawner block itself
        double rayStartX = startX + direction.x * LaserBeamComponent.MIN_BLOCK_HIT_DISTANCE;
        double rayStartY = startY + direction.y * LaserBeamComponent.MIN_BLOCK_HIT_DISTANCE;
        double rayStartZ = startZ + direction.z * LaserBeamComponent.MIN_BLOCK_HIT_DISTANCE;

        Vector3d hitLocation = TargetUtil.getTargetLocation(
                world,
                blockId -> blockId != 0,
                rayStartX, rayStartY, rayStartZ,
                direction.x, direction.y, direction.z,
                LaserBeamComponent.MAX_BEAM_DISTANCE);

        if (hitLocation != null) {
            double distance = Math.sqrt(
                    Math.pow(hitLocation.x - startX, 2) +
                            Math.pow(hitLocation.y - startY, 2) +
                            Math.pow(hitLocation.z - startZ, 2));

            if (distance < LaserBeamComponent.MIN_BLOCK_HIT_DISTANCE) {
                return LaserBeamComponent.MIN_BLOCK_HIT_DISTANCE;
            }
            return distance;
        }

        return LaserBeamComponent.MAX_BEAM_DISTANCE;
    }

    private Ref<EntityStore> spawnBeamSegment(
            double x, double y, double z,
            float pitchDeg, float yawDeg,
            ModelAsset modelAsset,
            ComponentAccessor<EntityStore> componentAccessor) {

        Vector3d position = new Vector3d(x, y, z);
        float modelPitchDeg = pitchDeg - 90f;
        float modelPitchRad = (float) Math.toRadians(modelPitchDeg);
        float yawRad = (float) Math.toRadians(yawDeg);
        Vector3f rotation = new Vector3f(modelPitchRad, yawRad, 0f);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, rotation));

        holder.addComponent(UUIDComponent.getComponentType(),
                new UUIDComponent(UUID.randomUUID()));

        int networkId = componentAccessor.getExternalData().takeNextNetworkId();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(networkId));

        holder.ensureComponent(EntityModule.get().getVisibleComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

        if (SpawnedEntityComponent.getComponentType() != null) {
            holder.addComponent(SpawnedEntityComponent.getComponentType(), new SpawnedEntityComponent(ID));
        }

        Model model;
        try {
            model = Model.createScaledModel(modelAsset, 2.0f);
        } catch (Exception e) {
            LOGGER.atWarning().log("failed to create beam model: %s", e.getMessage());
            return null;
        }
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

        return componentAccessor.addEntity(holder, AddReason.SPAWN);
    }

    private void removeBeamSegments(
            LaserBeamComponent state,
            ComponentAccessor<EntityStore> componentAccessor) {

        List<Ref<EntityStore>> segments = state.getBeamSegments();

        for (Ref<EntityStore> segmentRef : segments) {
            if (segmentRef != null && segmentRef.isValid()) {
                if (componentAccessor instanceof CommandBuffer) {
                    ((CommandBuffer<EntityStore>) componentAccessor).tryRemoveEntity(segmentRef, RemoveReason.REMOVE);
                } else if (componentAccessor instanceof Store) {
                    ((Store<EntityStore>) componentAccessor).removeEntity(segmentRef, RemoveReason.REMOVE);
                }
            }
        }

        state.clearBeamSegments();
    }

    private void damageEntitiesAlongBeam(
            Ref<EntityStore> spawnerRef,
            LaserBeamComponent state,
            TransformComponent spawnerTransform,
            CommandBuffer<EntityStore> commandBuffer) {

        Vector3d basePos = spawnerTransform.getPosition();
        double startX = basePos.x + state.getOffsetX();
        double startY = basePos.y + state.getOffsetY();
        double startZ = basePos.z + state.getOffsetZ();
        Vector3d startPos = new Vector3d(startX, startY, startZ);

        float pitchRad = (float) Math.toRadians(state.getPitch());
        float yawRad = (float) Math.toRadians(state.getYaw());
        Vector3d direction = Transform.getDirection(pitchRad, yawRad);

        World world = commandBuffer.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        double beamLength = findBlockHitDistance(world, startX, startY, startZ, direction, state);

        Vector3d endPos = new Vector3d(
                startX + direction.x * beamLength,
                startY + direction.y * beamLength,
                startZ + direction.z * beamLength);

        Vector3d midPoint = new Vector3d(
                (startPos.x + endPos.x) / 2,
                (startPos.y + endPos.y) / 2,
                (startPos.z + endPos.z) / 2);

        double searchRadius = (beamLength / 2) + 2.0;
        List<Ref<EntityStore>> nearbyEntities = TargetUtil.getAllEntitiesInSphere(midPoint, searchRadius,
                commandBuffer);

        List<Ref<EntityStore>> beamSegments = List.copyOf(state.getBeamSegments());

        for (Ref<EntityStore> entityRef : nearbyEntities) {
            if (!entityRef.isValid() || entityRef.equals(spawnerRef)) {
                continue;
            }

            boolean isBeamSegment = false;
            for (Ref<EntityStore> segment : beamSegments) {
                if (entityRef.equals(segment)) {
                    isBeamSegment = true;
                    break;
                }
            }
            if (isBeamSegment) {
                continue;
            }

            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                continue;
            }

            BoundingBox boundingBoxComponent = commandBuffer.getComponent(entityRef, BoundingBox.getComponentType());
            TransformComponent entityTransform = commandBuffer.getComponent(entityRef,
                    TransformComponent.getComponentType());

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
                        state.getDamage());

                DamageSystems.executeDamage(entityRef, commandBuffer, damage);
            }
        }
    }
}

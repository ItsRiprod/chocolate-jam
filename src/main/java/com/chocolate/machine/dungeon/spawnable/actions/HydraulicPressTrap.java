package com.chocolate.machine.dungeon.spawnable.actions;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.actions.HydraulicPressActionComponent;
import com.chocolate.machine.dungeon.component.actions.HydraulicPressActionComponent.Phase;
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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.asset.type.camera.CameraEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.particle.config.WorldParticle;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.protocol.SoundCategory;

public class HydraulicPressTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "press";
    private static final String PRESS_MODEL_ASSET = "Press";

    private static final String ANIM_PRESS = "Press";
    private static final String ANIM_RELEASE = "Release";
    private static final String ANIM_IDLE = "Idle";

    private static final String DAMAGE_CAUSE_NAME = "Environment";
    private static final String ENVIRONMENT_SOURCE_TYPE = "hydraulic_press";

    private static final String IMPACT_SOUND_ASSET = "SFX_Mace_T2_Impact";
    private static final String IMPACT_PARTICLE_SYSTEM = "Impact_Mace_Bash";
    private static final String CAMERA_SHAKE_ASSET = "Impact_Strong";

    // world effects for press slam (plays for all nearby players)
    private static final String SLAM_SOUND_ASSET = "SFX_Metal_Hit";
    private static final String SLAM_PARTICLE_SYSTEM = "Block_Land_Hard_Dust";

    private int damageCauseIndex = -1;
    private int impactSoundIndex = -1;
    private int cameraShakeIndex = -1;
    private int slamSoundIndex = -1;

    private int getDamageCauseIndex() {
        if (damageCauseIndex < 0) {
            damageCauseIndex = DamageCause.getAssetMap().getIndex(DAMAGE_CAUSE_NAME);
        }
        return damageCauseIndex;
    }

    private int getImpactSoundIndex() {
        if (impactSoundIndex < 0) {
            impactSoundIndex = SoundEvent.getAssetMap().getIndex(IMPACT_SOUND_ASSET);
        }
        return impactSoundIndex;
    }

    private int getCameraShakeIndex() {
        if (cameraShakeIndex < 0) {
            cameraShakeIndex = CameraEffect.getAssetMap().getIndex(CAMERA_SHAKE_ASSET);
        }
        return cameraShakeIndex;
    }

    private int getSlamSoundIndex() {
        if (slamSoundIndex < 0) {
            slamSoundIndex = SoundEvent.getAssetMap().getIndex(SLAM_SOUND_ASSET);
        }
        return slamSoundIndex;
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

        HydraulicPressActionComponent existing = componentAccessor.getComponent(
                spawnerRef, HydraulicPressActionComponent.getComponentType());

        if (existing != null) {
            existing.reset();
            return;
        }

        componentAccessor.addComponent(spawnerRef, HydraulicPressActionComponent.getComponentType(),
                new HydraulicPressActionComponent());
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        HydraulicPressActionComponent state = componentAccessor.getComponent(
                spawnerRef, HydraulicPressActionComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, HydraulicPressActionComponent.getComponentType());
        }

        if (state.hasSpawned()) {
            return;
        }

        TransformComponent spawnerTransform = componentAccessor.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return;
        }

        Vector3d position = spawnerTransform.getPosition().clone();
        Vector3f rotation = spawnerTransform.getRotation().clone();

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, rotation));

        holder.addComponent(UUIDComponent.getComponentType(),
                new UUIDComponent(UUID.randomUUID()));

        int networkId = componentAccessor.getExternalData().takeNextNetworkId();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(networkId));

        holder.ensureComponent(EntityModule.get().getVisibleComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(PRESS_MODEL_ASSET);
        if (modelAsset != null) {
            Model model = Model.createScaledModel(modelAsset, 1.0f);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        }

        Ref<EntityStore> spawnedRef = componentAccessor.addEntity(holder, AddReason.SPAWN);

        if (spawnedRef == null) {
            return;
        }

        state.setSpawnedRef(spawnedRef);
        state.setActive(true);
        state.setPhase(Phase.IDLE);
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        HydraulicPressActionComponent state = componentAccessor.getComponent(
                spawnerRef, HydraulicPressActionComponent.getComponentType());

        if (state == null) {
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
        if (componentAccessor.getComponent(spawnerRef, HydraulicPressActionComponent.getComponentType()) != null) {
            componentAccessor.removeComponent(spawnerRef, HydraulicPressActionComponent.getComponentType());
        }
    }

    @Override
    public void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        HydraulicPressActionComponent press = commandBuffer.getComponent(
                spawnerRef, HydraulicPressActionComponent.getComponentType());

        if (press == null || !press.isActive()) {
            return;
        }

        Ref<EntityStore> pressRef = press.getSpawnedRef();
        if (pressRef == null || !pressRef.isValid()) {
            return;
        }

        TransformComponent pressTransform = commandBuffer.getComponent(pressRef,
                TransformComponent.getComponentType());
        if (pressTransform == null) {
            return;
        }

        Vector3d pressPosition = pressTransform.getPosition();
        press.addPhaseTime(dt);

        switch (press.getPhase()) {
            case IDLE:
                startPressCycle(press, pressRef, commandBuffer);
                break;

            case PRESSING:
                handlePressingPhase(press, pressRef, pressPosition, commandBuffer);
                break;

            case RETRACTING:
                handleRetractingPhase(press, pressRef, commandBuffer);
                break;

            case COOLDOWN:
                handleCooldownPhase(press);
                break;
        }
    }

    private void startPressCycle(HydraulicPressActionComponent press,
            Ref<EntityStore> pressRef,
            CommandBuffer<EntityStore> commandBuffer) {
        press.setPhase(Phase.PRESSING);
        press.setHasDamagedThisCycle(false);

        AnimationUtils.playAnimation(
                pressRef,
                AnimationSlot.Action,
                ANIM_PRESS,
                true,
                commandBuffer);
    }

    private void handlePressingPhase(HydraulicPressActionComponent press,
            Ref<EntityStore> pressRef,
            Vector3d pressPosition,
            CommandBuffer<EntityStore> commandBuffer) {
        // trigger damage at the exact moment the press hits (synced with animation frame 35)
        if (!press.hasDamagedThisCycle() && press.getPhaseTimer() >= press.getDamageDelayTime()) {
            checkAndDamageEntities(press, pressPosition, commandBuffer);
        }

        if (press.getPhaseTimer() >= press.getPressAnimationDuration()) {
            startRetract(press, pressRef, commandBuffer);
        }
    }

    private void handleRetractingPhase(HydraulicPressActionComponent press,
            Ref<EntityStore> pressRef,
            CommandBuffer<EntityStore> commandBuffer) {
        if (press.getPhaseTimer() >= press.getRetractAnimationDuration()) {
            startCooldown(press, pressRef, commandBuffer);
        }
    }

    private void handleCooldownPhase(HydraulicPressActionComponent press) {
        if (press.getPhaseTimer() >= press.getCurrentCooldownDuration()) {
            press.setPhase(Phase.IDLE);
        }
    }

    private void checkAndDamageEntities(HydraulicPressActionComponent press,
            Vector3d position,
            CommandBuffer<EntityStore> commandBuffer) {

        // play slam sound and particle for all nearby players
        SoundUtil.playSoundEvent3d(getSlamSoundIndex(), SoundCategory.SFX, position, commandBuffer);
        ParticleUtil.spawnParticleEffect(SLAM_PARTICLE_SYSTEM, position, commandBuffer);

        // build damage zone aabb (static box below press origin)
        Box damageZone = new Box(
                position.x - press.getDamageZoneWidth() / 2,
                position.y + press.getDamageZoneOffsetY(),
                position.z - press.getDamageZoneDepth() / 2,
                position.x + press.getDamageZoneWidth() / 2,
                position.y + press.getDamageZoneOffsetY() + press.getDamageZoneHeight(),
                position.z + press.getDamageZoneDepth() / 2);

        // get min/max vectors for TargetUtil
        Vector3d zoneMin = new Vector3d(damageZone.min.x, damageZone.min.y, damageZone.min.z);
        Vector3d zoneMax = new Vector3d(damageZone.max.x, damageZone.max.y, damageZone.max.z);

        LOGGER.atInfo().log("Press damage check at pos=(%.1f, %.1f, %.1f), zone y=[%.1f to %.1f]",
                position.x, position.y, position.z, zoneMin.y, zoneMax.y);

        // use TargetUtil to find both players and entities in the box
        List<Ref<EntityStore>> nearbyEntities = TargetUtil.getAllEntitiesInBox(zoneMin, zoneMax, commandBuffer);

        LOGGER.atInfo().log("Found %d nearby entities (players + entities)", nearbyEntities.size());

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) {
                LOGGER.atInfo().log("Entity %d: invalid ref", i);
                continue;
            }

            // skip the press entity itself
            if (entityRef.equals(press.getSpawnedRef())) {
                LOGGER.atInfo().log("Entity %d: is press entity, skipping", i);
                continue;
            }

            // must have stats to take damage
            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                LOGGER.atInfo().log("Entity %d: no EntityStatMap, skipping", i);
                continue;
            }

            TransformComponent entityTransform = commandBuffer.getComponent(entityRef,
                    TransformComponent.getComponentType());
            if (entityTransform == null) {
                continue;
            }

            Vector3d entityPos = entityTransform.getPosition();
            LOGGER.atInfo().log("Damaging entity at (%.1f, %.1f, %.1f)", entityPos.x, entityPos.y, entityPos.z);

            // calculate knockback direction - push outward from press center
            double dx = entityPos.x - position.x;
            double dz = entityPos.z - position.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            double knockbackX = 0;
            double knockbackZ = 0;
            if (horizontalDist > 0.01) {
                knockbackX = (dx / horizontalDist) * press.getKnockbackForceHorizontal();
                knockbackZ = (dz / horizontalDist) * press.getKnockbackForceHorizontal();
            }

            // get or create knockback component on the entity (required for it to actually work)
            KnockbackComponent knockback = commandBuffer.getComponent(entityRef, KnockbackComponent.getComponentType());
            if (knockback == null) {
                knockback = new KnockbackComponent();
                commandBuffer.putComponent(entityRef, KnockbackComponent.getComponentType(), knockback);
            }
            knockback.setVelocity(new Vector3d(knockbackX, press.getKnockbackForceY(), knockbackZ));
            knockback.setVelocityType(ChangeVelocityType.Set);
            knockback.setDuration(press.getKnockbackDuration());

            Damage damage = new Damage(
                    new Damage.EnvironmentSource(ENVIRONMENT_SOURCE_TYPE),
                    getDamageCauseIndex(),
                    press.getDamageAmount());
            // knockback is already applied via component on entity, don't add to damage metadata
            damage.putMetaObject(Damage.IMPACT_SOUND_EFFECT,
                    new Damage.SoundEffect(getImpactSoundIndex()));

            // impact particles
            WorldParticle impactParticle = new WorldParticle(
                    IMPACT_PARTICLE_SYSTEM, null, 1.0f, null, null);
            damage.putMetaObject(Damage.IMPACT_PARTICLES,
                    new Damage.Particles(null, new WorldParticle[] { impactParticle }, 75.0));

            // camera shake
            damage.putMetaObject(Damage.CAMERA_EFFECT,
                    new Damage.CameraEffect(getCameraShakeIndex()));

            DamageSystems.executeDamage(entityRef, commandBuffer, damage);
        }

        press.setHasDamagedThisCycle(true);
    }

    private void startRetract(HydraulicPressActionComponent press,
            Ref<EntityStore> pressRef,
            CommandBuffer<EntityStore> commandBuffer) {
        press.setPhase(Phase.RETRACTING);

        AnimationUtils.playAnimation(
                pressRef,
                AnimationSlot.Action,
                ANIM_RELEASE,
                true,
                commandBuffer);
    }

    private void startCooldown(HydraulicPressActionComponent press,
            Ref<EntityStore> pressRef,
            CommandBuffer<EntityStore> commandBuffer) {
        press.setPhase(Phase.COOLDOWN);

        AnimationUtils.playAnimation(
                pressRef,
                AnimationSlot.Action,
                ANIM_IDLE,
                true,
                commandBuffer);
    }
}
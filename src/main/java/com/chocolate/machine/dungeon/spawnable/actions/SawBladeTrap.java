package com.chocolate.machine.dungeon.spawnable.actions;

import com.chocolate.machine.dungeon.component.SpawnedEntityComponent;
import com.chocolate.machine.dungeon.component.actions.SawBladeComponent;
import com.chocolate.machine.dungeon.component.actions.SawBladeComponent.Phase;
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
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.particle.config.WorldParticle;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SawBladeTrap implements Spawnable {
    private final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "sawblade";
    private static final String SAWBLADE_MODEL_ASSET = "SawBlade";

    private static final String ANIM_SPIN = "SawBlade_Spin";
    private static final String ANIM_ENTER = "Sawblade_Enter";
    private static final String ANIM_EXIT = "Sawblade_Exit";

    private static final String AMBIENT_SPARK_PARTICLE = "CM_SawSparks_System";
    private static final String IMPACT_SPARK_PARTICLE = "CM_Sparks_System";
    private static final String SAWBLADE_SOUND = "SFX_SawBlade_Loop";

    private static final String DAMAGE_CAUSE_NAME = "Environment";
    private static final String ENVIRONMENT_SOURCE_TYPE = "sawblade";

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
            soundIndex = SoundEvent.getAssetMap().getIndex(SAWBLADE_SOUND);
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

        SawBladeComponent state = componentAccessor.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (spawnerRef == null || !spawnerRef.isValid()) {
            LOGGER.atWarning().log("Invalid spawner reference in SawBladeTrap.register");
            return;
        }

        if (state == null) {
            try {
                state = componentAccessor.ensureAndGetComponent(spawnerRef, SawBladeComponent.getComponentType());
            } catch (IllegalArgumentException e) {
                state = componentAccessor.getComponent(spawnerRef, SawBladeComponent.getComponentType());
            }
            if (state == null) return;
        }

        state.setActive(false);
        state.setPhase(Phase.HIDDEN);
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            LOGGER.atWarning().log("Invalid spawner reference in SawBladeTrap.activate");
            return;
        }

        SawBladeComponent state = componentAccessor.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, SawBladeComponent.getComponentType());
            if (state == null) {
                LOGGER.atWarning().log("failed to register SawBladeComponent");
                return;
            }
        }

        if (!state.hasSpawned()) {
            register(spawnerRef, componentAccessor);
        }

        Ref<EntityStore> spawnedRef = state.getSpawnedRef();
        if (spawnedRef == null || !spawnedRef.isValid()) {
            spawnedRef = this.spawnBlade(spawnerRef, componentAccessor, state);
            state.setSpawnedRef(spawnedRef);
        }

        state.setActive(true);
        state.setPendingDeactivation(false);
        state.setPhase(Phase.ENTERING);

        AnimationUtils.playAnimation(spawnedRef, AnimationSlot.Movement, ANIM_ENTER, false, componentAccessor);
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        SawBladeComponent state = componentAccessor.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (state == null) {
            return;
        }

        state.setPendingDeactivation(true);
    }

    @Override
    public void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        SawBladeComponent state = componentAccessor.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (state == null) {
            return;
        }

        Ref<EntityStore> spawnedRef = state.getSpawnedRef();
        if (spawnedRef != null && spawnedRef.isValid()) {
            if (componentAccessor instanceof CommandBuffer) {
                ((CommandBuffer<EntityStore>) componentAccessor).tryRemoveEntity(spawnedRef, RemoveReason.REMOVE);
            } else if (componentAccessor instanceof Store) {
                ((Store<EntityStore>) componentAccessor).removeEntity(spawnedRef, RemoveReason.REMOVE);
            }
        }

        state.reset();
        register(spawnerRef, componentAccessor);
    }

    @Override
    public void cleanup(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        SawBladeComponent state = componentAccessor.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (state != null) {
            Ref<EntityStore> spawnedRef = state.getSpawnedRef();
            if (spawnedRef != null && spawnedRef.isValid()) {
                if (componentAccessor instanceof CommandBuffer) {
                    ((CommandBuffer<EntityStore>) componentAccessor).tryRemoveEntity(spawnedRef, RemoveReason.REMOVE);
                } else if (componentAccessor instanceof Store) {
                    ((Store<EntityStore>) componentAccessor).removeEntity(spawnedRef, RemoveReason.REMOVE);
                }
            }
            componentAccessor.removeComponent(spawnerRef, SawBladeComponent.getComponentType());
        }
    }

    @Nullable
    public Ref<EntityStore> spawnBlade(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            SawBladeComponent state) {

        TransformComponent spawnerTransform = componentAccessor.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return null;
        }

        Vector3d position = spawnerTransform.getPosition().clone();
        float yaw = state.getMovementAxis() == SawBladeComponent.MovementAxis.X ? (float) Math.toRadians(90) : 0f;
        Vector3f rotation = new Vector3f(0f, yaw, 0f);

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

        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(SAWBLADE_MODEL_ASSET);
        if (modelAsset != null) {
            Model model = Model.createScaledModel(modelAsset, 2.5f);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        }

        Ref<EntityStore> spawnedRef = componentAccessor.addEntity(holder, AddReason.SPAWN);
        return spawnedRef;
    }

    @Override
    public void tick(
            float dt,
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SawBladeComponent state = commandBuffer.getComponent(
                spawnerRef, SawBladeComponent.getComponentType());

        if (state == null || !state.isActive()) {
            return;
        }

        Ref<EntityStore> bladeRef = state.getSpawnedRef();
        if (bladeRef == null || !bladeRef.isValid()) {
            return;
        }

        TransformComponent bladeTransform = commandBuffer.getComponent(bladeRef,
                TransformComponent.getComponentType());
        if (bladeTransform == null) {
            return;
        }

        Vector3d bladePosition = bladeTransform.getPosition();
        state.addPhaseTime(dt);

        switch (state.getPhase()) {
            case HIDDEN:
                break;

            case ENTERING:
                handleEnteringPhase(state, bladeRef, bladePosition, commandBuffer);
                break;

            case ACTIVE:
                handleActivePhase(state, bladeRef, bladePosition, dt, commandBuffer);
                break;

            case PEEKING:
                handlePeekingPhase(state, bladeRef, bladePosition, commandBuffer);
                break;

            case EXITING:
                handleExitingPhase(state, bladeRef, bladePosition, commandBuffer);
                break;
        }
    }

    private void handleEnteringPhase(SawBladeComponent state,
            Ref<EntityStore> bladeRef,
            Vector3d bladePosition,
            CommandBuffer<EntityStore> commandBuffer) {

        if (state.getPhaseTimer() >= state.getEnterDuration()) {
            state.setPhase(Phase.ACTIVE);
            state.setLastDamageTime(0f);

            AnimationUtils.playAnimation(bladeRef, AnimationSlot.Action, ANIM_SPIN, true, commandBuffer);
            SoundUtil.playSoundEvent3d(getSoundIndex(), SoundCategory.SFX, bladePosition, commandBuffer);
            spawnSparks(bladePosition, commandBuffer);
        }
    }

    private void handleActivePhase(SawBladeComponent state,
            Ref<EntityStore> bladeRef,
            Vector3d bladePosition,
            float dt,
            CommandBuffer<EntityStore> commandBuffer) {

        if (state.isPendingDeactivation()) {
            state.setPhase(Phase.EXITING);
            AnimationUtils.stopAnimation(bladeRef, AnimationSlot.Action, commandBuffer);
            AnimationUtils.playAnimation(bladeRef, AnimationSlot.Movement, ANIM_EXIT, false, commandBuffer);
            return;
        }

        if (state.getPhaseTimer() >= state.getActiveDuration()) {
            state.setPhase(Phase.PEEKING);
            AnimationUtils.stopAnimation(bladeRef, AnimationSlot.Action, commandBuffer);
            AnimationUtils.playAnimation(bladeRef, AnimationSlot.Movement, ANIM_EXIT, false, commandBuffer);
            return;
        }

        state.setLastDamageTime(state.getLastDamageTime() + dt);

        if (state.getLastDamageTime() >= state.getDamageTickInterval()) {
            state.setLastDamageTime(0f);
            dealDamageInRadius(state, bladeRef, bladePosition, commandBuffer);
        }
    }

    private void handlePeekingPhase(SawBladeComponent state,
            Ref<EntityStore> bladeRef,
            Vector3d bladePosition,
            CommandBuffer<EntityStore> commandBuffer) {

        if (state.isPendingDeactivation()) {
            if (state.getPhaseTimer() >= state.getExitDuration()) {
                state.setPhase(Phase.HIDDEN);
                state.setActive(false);
                state.setPendingDeactivation(false);
            }
            return;
        }

        if (state.getPhaseTimer() >= state.getPeekDuration()) {
            state.setPhase(Phase.ENTERING);
            AnimationUtils.playAnimation(bladeRef, AnimationSlot.Movement, ANIM_ENTER, false, commandBuffer);
        }
    }

    private void handleExitingPhase(SawBladeComponent state,
            Ref<EntityStore> bladeRef,
            Vector3d bladePosition,
            CommandBuffer<EntityStore> commandBuffer) {

        if (state.getPhaseTimer() >= state.getExitDuration()) {
            state.setPhase(Phase.HIDDEN);
            state.setActive(false);
            state.setPendingDeactivation(false);
        }
    }

    private void dealDamageInRadius(SawBladeComponent state,
            Ref<EntityStore> bladeRef,
            Vector3d position,
            CommandBuffer<EntityStore> commandBuffer) {

        float radius = state.getDamageRadius();
        Vector3d min = new Vector3d(position.x - radius, position.y - 0.5, position.z - radius);
        Vector3d max = new Vector3d(position.x + radius, position.y + 2.0, position.z + radius);

        List<Ref<EntityStore>> nearbyEntities = TargetUtil.getAllEntitiesInBox(min, max, commandBuffer);

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) {
                continue;
            }

            if (entityRef.equals(bladeRef)) {
                continue;
            }

            TransformComponent entityTransform = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());
            if (entityTransform == null) {
                continue;
            }

            double dx = entityTransform.getPosition().x - position.x;
            double dz = entityTransform.getPosition().z - position.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > radius * radius) {
                continue;
            }

            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                continue;
            }

            Damage damage = new Damage(
                    new Damage.EnvironmentSource(ENVIRONMENT_SOURCE_TYPE),
                    getDamageCauseIndex(),
                    state.getDamageAmount());

            WorldParticle sparkParticle = new WorldParticle(
                    IMPACT_SPARK_PARTICLE, null, 1.0f, null, null);
            damage.putMetaObject(Damage.IMPACT_PARTICLES,
                    new Damage.Particles(null, new WorldParticle[] { sparkParticle }, 75.0));

            DamageSystems.executeDamage(entityRef, commandBuffer, damage);
        }
    }

    private void spawnSparks(Vector3d position, CommandBuffer<EntityStore> commandBuffer) {
        ParticleUtil.spawnParticleEffect(AMBIENT_SPARK_PARTICLE, position, commandBuffer);
    }
}

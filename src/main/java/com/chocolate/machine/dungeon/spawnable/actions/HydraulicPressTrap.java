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
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class HydraulicPressTrap implements Spawnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "press";
    private static final String PRESS_MODEL_ASSET = "Press";

    private static final String ANIM_PRESS = "Press";
    private static final String ANIM_RELEASE = "Release";
    private static final String ANIM_IDLE = "Idle";

    private static final String DAMAGE_CAUSE_NAME = "Physical";

    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> entitySpatialResource;
    private int damageCauseIndex = -1;

    public HydraulicPressTrap() {
        this.entitySpatialResource = EntityModule.get().getEntitySpatialResourceType();
    }

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
        componentAccessor.removeComponent(spawnerRef, HydraulicPressActionComponent.getComponentType());
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
        if (!press.hasDamagedThisCycle()) {
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

        SpatialResource<Ref<EntityStore>, EntityStore> spatial = commandBuffer.getResource(entitySpatialResource);
        List<Ref<EntityStore>> nearbyEntities = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(position, press.getDamageRadius(), nearbyEntities);

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) {
                continue;
            }

            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                continue;
            }

            if (entityRef.equals(press.getSpawnedRef())) {
                continue;
            }

            Damage damage = new Damage(
                    Damage.NULL_SOURCE,
                    getDamageCauseIndex(),
                    press.getDamageAmount());
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
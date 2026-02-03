package com.chocolate.machine.dungeon.spawnable.actions;

import com.chocolate.machine.dungeon.component.SpawnedEntityComponent;
import com.chocolate.machine.dungeon.component.actions.BigFreakingHammerComponent;
import com.chocolate.machine.dungeon.component.actions.BigFreakingHammerComponent.HammerPhase;
import com.chocolate.machine.dungeon.component.actions.BigFreakingHammerComponent.KnockbackAxis;
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
import com.hypixel.hytale.protocol.SoundCategory;
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
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HammerTrap implements Spawnable {
    private final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String ID = "hammer";
    private static final String HAMMER_MODEL_ASSET = "Big_Hammer";

    private static final String ANIM_IDLE = "Hammer_Idle";
    private static final String ANIM_SWING_R = "Hammer_Swing_R";
    private static final String ANIM_SWING_L = "Hammer_Swing_L";

    private static final String DAMAGE_CAUSE_NAME = "Environment";
    private static final String ENVIRONMENT_SOURCE_TYPE = "big_hammer";

    private static final String IMPACT_SOUND_ASSET = "SFX_Battleaxe_T2_Impact";
    private static final String IMPACT_PARTICLE_SYSTEM = "Impact_Mace_Bash";
    private static final String CAMERA_SHAKE_ASSET = "Impact_Hammer";

    private static final String SWING_SOUND_ASSET = "SFX_Battleaxe_T2_Swing";

    private int damageCauseIndex = -1;
    private int impactSoundIndex = -1;
    private int cameraShakeIndex = -1;
    private int swingSoundIndex = -1;

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

    private int getSwingSoundIndex() {
        if (swingSoundIndex < 0) {
            swingSoundIndex = SoundEvent.getAssetMap().getIndex(SWING_SOUND_ASSET);
        }
        return swingSoundIndex;
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

        BigFreakingHammerComponent state = componentAccessor.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

        if (spawnerRef == null || !spawnerRef.isValid()) {
            LOGGER.atWarning().log("Invalid spawner reference in HammerTrap.register");
            return;
        }

        if (state == null) {
            state = new BigFreakingHammerComponent();
            try {
                componentAccessor.addComponent(spawnerRef, BigFreakingHammerComponent.getComponentType(), state);
            } catch (IllegalArgumentException e) {
                state = componentAccessor.getComponent(spawnerRef, BigFreakingHammerComponent.getComponentType());
                if (state == null) return;
            }
        }

        if (state.hasSpawned()) {
            return;
        }

        Ref<EntityStore> spawnedRef = this.spawnHammer(spawnerRef, componentAccessor, state);

        if (spawnedRef == null) {
            return;
        }

        state.setSpawnedRef(spawnedRef);
        state.setActive(false);
        state.setPhase(HammerPhase.IDLE);
    }

    @Override
    public void activate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            LOGGER.atWarning().log("Invalid spawner reference in HammerTrap.activate");
            return;
        }

        BigFreakingHammerComponent state = componentAccessor.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

        if (state == null) {
            register(spawnerRef, componentAccessor);
            state = componentAccessor.getComponent(spawnerRef, BigFreakingHammerComponent.getComponentType());
            if (state == null) {
                LOGGER.atWarning().log("failed to register BigFreakingHammerComponent");
                return;
            }
        }

        if (!state.hasSpawned()) {
            register(spawnerRef, componentAccessor);
        }

        // Ensure hammer is spawned
        Ref<EntityStore> spawnedRef = state.getSpawnedRef();
        if (spawnedRef == null || !spawnedRef.isValid()) {
            spawnedRef = this.spawnHammer(spawnerRef, componentAccessor, state);
            state.setSpawnedRef(spawnedRef);
        }

        state.setActive(true);
        state.setPendingDeactivation(false);
    }

    @Override
    public void deactivate(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        BigFreakingHammerComponent state = componentAccessor.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

        if (state == null) {
            return;
        }

        state.setPendingDeactivation(true);
        Ref<EntityStore> hammerRef = state.getSpawnedRef();
        if (hammerRef == null || !hammerRef.isValid()) {
            return;
        }
        AnimationUtils.playAnimation(hammerRef, AnimationSlot.Action, ANIM_IDLE, true, componentAccessor);
    }

    @Override
    public void reset(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        if (!spawnerRef.isValid()) {
            return;
        }

        BigFreakingHammerComponent state = componentAccessor.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

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

        BigFreakingHammerComponent state = componentAccessor.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

        if (state != null) {
            Ref<EntityStore> spawnedRef = state.getSpawnedRef();
            if (spawnedRef != null && spawnedRef.isValid()) {
                if (componentAccessor instanceof CommandBuffer) {
                    ((CommandBuffer<EntityStore>) componentAccessor).tryRemoveEntity(spawnedRef, RemoveReason.REMOVE);
                } else if (componentAccessor instanceof Store) {
                    ((Store<EntityStore>) componentAccessor).removeEntity(spawnedRef, RemoveReason.REMOVE);
                }
            }
            componentAccessor.removeComponent(spawnerRef, BigFreakingHammerComponent.getComponentType());
        }
    }

    @Nullable
    public Ref<EntityStore> spawnHammer(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor, BigFreakingHammerComponent state) {

        TransformComponent spawnerTransform = componentAccessor.getComponent(
                spawnerRef, TransformComponent.getComponentType());

        if (spawnerTransform == null) {
            return null;
        }

        Vector3d position = spawnerTransform.getPosition().clone();
        float yaw = state.getKnockbackAxis() == KnockbackAxis.Z ? 90f : 0f;
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

        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(HAMMER_MODEL_ASSET);
        if (modelAsset != null) {
            Model model = Model.createScaledModel(modelAsset, state.getScale());
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

        BigFreakingHammerComponent press = commandBuffer.getComponent(
                spawnerRef, BigFreakingHammerComponent.getComponentType());

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
                handleIdlePhase(press, pressRef, pressPosition, commandBuffer);
                break;

            case SWING_LEFT:
                handleSwingLeftPhase(press, pressRef, pressPosition, commandBuffer);
                break;

            case SWING_RIGHT:
                handleSwingRightPhase(press, pressRef, pressPosition, commandBuffer);
                break;
        }
    }

    private void handleIdlePhase(BigFreakingHammerComponent hammer,
            Ref<EntityStore> hammerRef,
            Vector3d hammerPosition,
            CommandBuffer<EntityStore> commandBuffer) {
        if (hammer.isPendingDeactivation()) {
            hammer.setActive(false);
            hammer.setPendingDeactivation(false);
            AnimationUtils.playAnimation(hammerRef, AnimationSlot.Action, ANIM_IDLE, true, commandBuffer);
            hammer.setNeedsIdleAnimation(false);
            return;
        }

        hammer.setPhase(HammerPhase.SWING_LEFT);
        hammer.setHasDamagedThisCycle(false);
        SoundUtil.playSoundEvent3d(getSwingSoundIndex(), SoundCategory.SFX, hammerPosition, commandBuffer);
        AnimationUtils.playAnimation(hammerRef, AnimationSlot.Action, ANIM_SWING_L, true, commandBuffer);
    }

    private void handleSwingLeftPhase(BigFreakingHammerComponent hammer,
            Ref<EntityStore> hammerRef,
            Vector3d hammerPosition,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!hammer.hasDamagedThisCycle() && hammer.getPhaseTimer() >= hammer.getDamageDelayTime()) {
            checkAndDamageEntities(hammer, hammerPosition, commandBuffer, 1.0);
        }

        if (hammer.getPhaseTimer() >= hammer.getSwingAnimationDuration()) {
            hammer.setPhase(HammerPhase.SWING_RIGHT);
            hammer.setHasDamagedThisCycle(false);
            SoundUtil.playSoundEvent3d(getSwingSoundIndex(), SoundCategory.SFX, hammerPosition, commandBuffer);
            AnimationUtils.playAnimation(hammerRef, AnimationSlot.Action, ANIM_SWING_R, true, commandBuffer);
        }
    }

    private void handleSwingRightPhase(BigFreakingHammerComponent hammer,
            Ref<EntityStore> hammerRef,
            Vector3d hammerPosition,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!hammer.hasDamagedThisCycle() && hammer.getPhaseTimer() >= hammer.getDamageDelayTime()) {
            checkAndDamageEntities(hammer, hammerPosition, commandBuffer, -1.0);
        }

        if (hammer.getPhaseTimer() >= hammer.getSwingAnimationDuration()) {
            hammer.setPhase(HammerPhase.IDLE);
        }
    }

    private void checkAndDamageEntities(BigFreakingHammerComponent hammer,
            Vector3d position,
            CommandBuffer<EntityStore> commandBuffer,
            double knockbackDirection) {

        float xHalf, zHalf;
        if (hammer.getKnockbackAxis() == KnockbackAxis.X) {
            xHalf = hammer.getDamageZoneWidth() / 2;
            zHalf = hammer.getDamageZoneDepth() / 2;
        } else {
            xHalf = hammer.getDamageZoneDepth() / 2;
            zHalf = hammer.getDamageZoneWidth() / 2;
        }

        Box damageZone = new Box(
                position.x - xHalf,
                position.y + hammer.getDamageZoneOffsetY(),
                position.z - zHalf,
                position.x + xHalf,
                position.y + hammer.getDamageZoneOffsetY() + hammer.getDamageZoneHeight(),
                position.z + zHalf);

        Vector3d zoneMin = new Vector3d(damageZone.min.x, damageZone.min.y, damageZone.min.z);
        Vector3d zoneMax = new Vector3d(damageZone.max.x, damageZone.max.y, damageZone.max.z);

        List<Ref<EntityStore>> nearbyEntities = TargetUtil.getAllEntitiesInBox(zoneMin, zoneMax, commandBuffer);

        for (int i = 0; i < nearbyEntities.size(); i++) {
            Ref<EntityStore> entityRef = nearbyEntities.get(i);
            if (!entityRef.isValid()) {
                continue;
            }

            Ref<EntityStore> hammerSpawnedRef = hammer.getSpawnedRef();
            if (hammerSpawnedRef != null && entityRef.equals(hammerSpawnedRef)) {
                continue;
            }

            EntityStatMap stats = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
            if (stats == null) {
                continue;
            }

            KnockbackComponent knockback = commandBuffer.getComponent(entityRef, KnockbackComponent.getComponentType());
            if (knockback == null) {
                knockback = new KnockbackComponent();
                commandBuffer.putComponent(entityRef, KnockbackComponent.getComponentType(), knockback);
            }

            Vector3d velocity;
            if (hammer.getKnockbackAxis() == KnockbackAxis.Z) {
                velocity = new Vector3d(0, hammer.getKnockbackForceY(),
                        knockbackDirection * hammer.getKnockbackForceHorizontal());
            } else {
                velocity = new Vector3d(knockbackDirection * hammer.getKnockbackForceHorizontal(),
                        hammer.getKnockbackForceY(), 0);
            }
            knockback.setVelocity(velocity);

            knockback.setVelocityType(ChangeVelocityType.Set);
            knockback.setDuration(hammer.getKnockbackDuration());

            Damage damage = new Damage(
                    new Damage.EnvironmentSource(ENVIRONMENT_SOURCE_TYPE),
                    getDamageCauseIndex(),
                    hammer.getDamageAmount());
            damage.putMetaObject(Damage.IMPACT_SOUND_EFFECT,
                    new Damage.SoundEffect(getImpactSoundIndex()));

            WorldParticle impactParticle = new WorldParticle(
                    IMPACT_PARTICLE_SYSTEM, null, 1.0f, null, null);
            damage.putMetaObject(Damage.IMPACT_PARTICLES,
                    new Damage.Particles(null, new WorldParticle[] { impactParticle }, 75.0));

            damage.putMetaObject(Damage.CAMERA_EFFECT,
                    new Damage.CameraEffect(getCameraShakeIndex()));

            DamageSystems.executeDamage(entityRef, commandBuffer, damage);
        }

        hammer.setHasDamagedThisCycle(true);
    }
}

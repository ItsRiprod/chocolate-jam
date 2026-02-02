package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SawBladeComponent implements Component<EntityStore> {

    public enum Phase {
        HIDDEN,
        ENTERING,
        ACTIVE,
        PEEKING,
        EXITING
    }

    public enum MovementAxis {
        X,
        Z
    }

    public static final BuilderCodec<SawBladeComponent> CODEC = BuilderCodec
            .builder(SawBladeComponent.class, SawBladeComponent::new)
            .append(new KeyedCodec<>("DamageAmount", Codec.FLOAT),
                    (c, v) -> c.damageAmount = v,
                    c -> c.damageAmount)
            .add()
            .append(new KeyedCodec<>("DamageTickInterval", Codec.FLOAT),
                    (c, v) -> c.damageTickInterval = v,
                    c -> c.damageTickInterval)
            .add()
            .append(new KeyedCodec<>("DamageRadius", Codec.FLOAT),
                    (c, v) -> c.damageRadius = v,
                    c -> c.damageRadius)
            .add()
            .append(new KeyedCodec<>("MovementAxis", Codec.STRING),
                    (c, v) -> c.movementAxis = MovementAxis.valueOf(v),
                    c -> c.movementAxis.name())
            .add()
            .build();

    private static ComponentType<EntityStore, SawBladeComponent> componentType;

    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;
    private Phase phase = Phase.HIDDEN;
    private float phaseTimer = 0f;
    private boolean pendingDeactivation = false;
    private float lastDamageTime = 0f;

    private float damageAmount = 25f;
    private float damageTickInterval = 0.5f;
    private float damageRadius = 0.5f;
    private MovementAxis movementAxis = MovementAxis.Z;
    private float enterDuration = 1.0f;
    private float exitDuration = 1.0f;
    private float activeDuration = 10.13f;
    private float peekDuration = 2.5f;

    public SawBladeComponent() {
        this.spawnedRef = null;
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, SawBladeComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, SawBladeComponent> getComponentType() {
        return componentType;
    }

    @Nullable
    public Ref<EntityStore> getSpawnedRef() {
        return spawnedRef;
    }

    public void setSpawnedRef(@Nullable Ref<EntityStore> ref) {
        this.spawnedRef = ref;
    }

    public boolean hasSpawned() {
        return spawnedRef != null && spawnedRef.isValid();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        this.phaseTimer = 0f;
    }

    public float getPhaseTimer() {
        return phaseTimer;
    }

    public void addPhaseTime(float dt) {
        this.phaseTimer += dt;
    }

    public boolean isPendingDeactivation() {
        return pendingDeactivation;
    }

    public void setPendingDeactivation(boolean pending) {
        this.pendingDeactivation = pending;
    }

    public float getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(float time) {
        this.lastDamageTime = time;
    }

    public float getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(float amount) {
        this.damageAmount = amount;
    }

    public float getDamageTickInterval() {
        return damageTickInterval;
    }

    public float getDamageRadius() {
        return damageRadius;
    }

    public MovementAxis getMovementAxis() {
        return movementAxis;
    }

    public void setMovementAxis(MovementAxis axis) {
        this.movementAxis = axis;
    }

    public float getEnterDuration() {
        return enterDuration;
    }

    public float getExitDuration() {
        return exitDuration;
    }

    public float getActiveDuration() {
        return activeDuration;
    }

    public float getPeekDuration() {
        return peekDuration;
    }

    public void reset() {
        this.spawnedRef = null;
        this.active = false;
        this.phase = Phase.HIDDEN;
        this.phaseTimer = 0f;
        this.pendingDeactivation = false;
        this.lastDamageTime = 0f;
    }

    @Nonnull
    @Override
    public SawBladeComponent clone() {
        SawBladeComponent copy = new SawBladeComponent();
        copy.spawnedRef = this.spawnedRef;
        copy.active = this.active;
        copy.phase = this.phase;
        copy.phaseTimer = this.phaseTimer;
        copy.pendingDeactivation = this.pendingDeactivation;
        copy.lastDamageTime = this.lastDamageTime;
        copy.damageAmount = this.damageAmount;
        copy.damageTickInterval = this.damageTickInterval;
        copy.damageRadius = this.damageRadius;
        copy.movementAxis = this.movementAxis;
        copy.enterDuration = this.enterDuration;
        copy.exitDuration = this.exitDuration;
        copy.activeDuration = this.activeDuration;
        copy.peekDuration = this.peekDuration;
        return copy;
    }
}

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
import java.util.Random;

/**
 * Per-spawner runtime state for hydraulic press trap.
 * Manages the press cycle: IDLE -> PRESSING -> RETRACTING -> COOLDOWN -> loop
 */
public class HydraulicPressActionComponent implements Component<EntityStore> {

    public static final BuilderCodec<HydraulicPressActionComponent> CODEC = BuilderCodec
            .builder(HydraulicPressActionComponent.class, HydraulicPressActionComponent::new)
            .append(new KeyedCodec<>("PressAnimationDuration", Codec.FLOAT),
                    (c, v) -> c.pressAnimationDuration = v,
                    c -> c.pressAnimationDuration)
            .add()
            .append(new KeyedCodec<>("RetractAnimationDuration", Codec.FLOAT),
                    (c, v) -> c.retractAnimationDuration = v,
                    c -> c.retractAnimationDuration)
            .add()
            .append(new KeyedCodec<>("MinCooldown", Codec.FLOAT),
                    (c, v) -> c.minCooldown = v,
                    c -> c.minCooldown)
            .add()
            .append(new KeyedCodec<>("MaxCooldown", Codec.FLOAT),
                    (c, v) -> c.maxCooldown = v,
                    c -> c.maxCooldown)
            .add()
            .append(new KeyedCodec<>("DamageAmount", Codec.FLOAT),
                    (c, v) -> c.damageAmount = v,
                    c -> c.damageAmount)
            .add()
            .append(new KeyedCodec<>("VelocityMultiplier", Codec.FLOAT),
                    (c, v) -> c.velocityMultiplier = v,
                    c -> c.velocityMultiplier)
            .add()
            .build();

    public enum PressPhase {
        IDLE,
        PRESSING,
        RETRACTING,
        COOLDOWN
    }

    private static ComponentType<EntityStore, HydraulicPressActionComponent> componentType;
    private static final Random RANDOM = new Random();

    // runtime state
    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;
    private PressPhase phase = PressPhase.IDLE;
    private float phaseTimer = 0f;
    private float currentCooldownDuration = 0f;
    private boolean hasDamagedThisCycle = false;

    // configurable via command
    private float pressAnimationDuration = 3.0495f;
    private float retractAnimationDuration = 3.0f;
    private float minCooldown = 0.0f;
    private float maxCooldown = 2.0f;
    private float damageAmount = 25f;
    private float velocityMultiplier = 1.0f;

    // fixed values
    private float damageDelayTime = 0.2033f;
    private float damageZoneWidth = 2.0f;
    private float damageZoneHeight = 2.0f;
    private float damageZoneDepth = 2.0f;
    private float damageZoneOffsetY = 0.0f;
    private float knockbackForceY = 1.3f;
    private float knockbackForceHorizontal = 1.3f;
    private float knockbackDuration = 0f;

    public HydraulicPressActionComponent() {
        this.spawnedRef = null;
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, HydraulicPressActionComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, HydraulicPressActionComponent> getComponentType() {
        return componentType;
    }

    // --- Spawned entity ref ---

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

    // --- Active state ---

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // --- Phase management ---

    public PressPhase getPhase() {
        return phase;
    }

    public void setPhase(PressPhase phase) {
        this.phase = phase;
        this.phaseTimer = 0f;
        if (phase == PressPhase.COOLDOWN) {
            this.currentCooldownDuration = minCooldown + RANDOM.nextFloat() * (maxCooldown - minCooldown);
        }
    }

    public float getPhaseTimer() {
        return phaseTimer;
    }

    public void addPhaseTime(float dt) {
        this.phaseTimer += dt;
    }

    public float getCurrentCooldownDuration() {
        return currentCooldownDuration;
    }

    public boolean hasDamagedThisCycle() {
        return hasDamagedThisCycle;
    }

    public void setHasDamagedThisCycle(boolean value) {
        this.hasDamagedThisCycle = value;
    }

    // --- Configurable via command ---

    public float getPressAnimationDuration() {
        return pressAnimationDuration;
    }

    public void setPressAnimationDuration(float duration) {
        this.pressAnimationDuration = duration;
    }

    public float getRetractAnimationDuration() {
        return retractAnimationDuration;
    }

    public void setRetractAnimationDuration(float duration) {
        this.retractAnimationDuration = duration;
    }

    public float getMinCooldown() {
        return minCooldown;
    }

    public void setMinCooldown(float minCooldown) {
        this.minCooldown = minCooldown;
    }

    public float getMaxCooldown() {
        return maxCooldown;
    }

    public void setMaxCooldown(float maxCooldown) {
        this.maxCooldown = maxCooldown;
    }

    public float getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(float amount) {
        this.damageAmount = amount;
    }

    public float getVelocityMultiplier() {
        return velocityMultiplier;
    }

    public void setVelocityMultiplier(float multiplier) {
        this.velocityMultiplier = multiplier;
    }

    // --- Fixed values ---

    public float getDamageDelayTime() {
        return damageDelayTime;
    }

    public float getDamageZoneWidth() {
        return damageZoneWidth;
    }

    public float getDamageZoneHeight() {
        return damageZoneHeight;
    }

    public float getDamageZoneDepth() {
        return damageZoneDepth;
    }

    public float getDamageZoneOffsetY() {
        return damageZoneOffsetY;
    }

    public float getKnockbackForceY() {
        return knockbackForceY * velocityMultiplier;
    }

    public float getKnockbackForceHorizontal() {
        return knockbackForceHorizontal * velocityMultiplier;
    }

    public float getKnockbackDuration() {
        return knockbackDuration;
    }

    // --- Reset ---

    public void reset() {
        this.spawnedRef = null;
        this.active = false;
        this.phase = PressPhase.IDLE;
        this.phaseTimer = 0f;
        this.currentCooldownDuration = 0f;
        this.hasDamagedThisCycle = false;
    }

    @Nonnull
    @Override
    public HydraulicPressActionComponent clone() {
        HydraulicPressActionComponent copy = new HydraulicPressActionComponent();
        copy.spawnedRef = this.spawnedRef;
        copy.active = this.active;
        copy.phase = this.phase;
        copy.phaseTimer = this.phaseTimer;
        copy.currentCooldownDuration = this.currentCooldownDuration;
        copy.pressAnimationDuration = this.pressAnimationDuration;
        copy.retractAnimationDuration = this.retractAnimationDuration;
        copy.minCooldown = this.minCooldown;
        copy.maxCooldown = this.maxCooldown;
        copy.damageAmount = this.damageAmount;
        copy.velocityMultiplier = this.velocityMultiplier;
        copy.hasDamagedThisCycle = this.hasDamagedThisCycle;
        return copy;
    }
}

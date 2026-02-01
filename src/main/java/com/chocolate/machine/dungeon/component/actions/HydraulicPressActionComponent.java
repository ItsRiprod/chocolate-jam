package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

/**
 * Per-spawner state for hydraulic press trap (runtime only).
 * Manages the press cycle: IDLE -> PRESSING -> RETRACTING -> COOLDOWN -> loop
 */
public class HydraulicPressActionComponent implements Component<EntityStore> {

    public enum Phase {
        IDLE,           // Waiting to start cycle
        PRESSING,       // Press animation playing, checking for entities to damage
        RETRACTING,     // Retract animation playing
        COOLDOWN        // Random delay before next cycle
    }

    private static ComponentType<EntityStore, HydraulicPressActionComponent> componentType;
    private static final Random RANDOM = new Random();

    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;

    // State machine
    private Phase phase = Phase.IDLE;
    private float phaseTimer = 0f;
    private float currentCooldownDuration = 0f;

    // Configurable timings (seconds)
    private float pressAnimationDuration = 3.0f;
    private float retractAnimationDuration = 1.0f;
    private float minCooldown = 1.0f;
    private float maxCooldown = 3.0f;

    // Damage config
    private float damageRadius = 1.5f;
    private float damageAmount = 25f;
    private boolean hasDamagedThisCycle = false;

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

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        this.phaseTimer = 0f;
        if (phase == Phase.COOLDOWN) {
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

    // --- Timing config ---

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

    // --- Damage config ---

    public float getDamageRadius() {
        return damageRadius;
    }

    public void setDamageRadius(float radius) {
        this.damageRadius = radius;
    }

    public float getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(float amount) {
        this.damageAmount = amount;
    }

    public boolean hasDamagedThisCycle() {
        return hasDamagedThisCycle;
    }

    public void setHasDamagedThisCycle(boolean value) {
        this.hasDamagedThisCycle = value;
    }

    // --- Reset ---

    public void reset() {
        this.spawnedRef = null;
        this.active = false;
        this.phase = Phase.IDLE;
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
        copy.damageRadius = this.damageRadius;
        copy.damageAmount = this.damageAmount;
        copy.hasDamagedThisCycle = this.hasDamagedThisCycle;
        return copy;
    }
}

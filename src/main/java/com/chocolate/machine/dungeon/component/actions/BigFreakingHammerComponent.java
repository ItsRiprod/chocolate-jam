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

public class BigFreakingHammerComponent implements Component<EntityStore> {

    public enum KnockbackAxis {
        X,
        Z
    }

    public static final BuilderCodec<BigFreakingHammerComponent> CODEC = BuilderCodec
            .builder(BigFreakingHammerComponent.class, BigFreakingHammerComponent::new)
            .append(new KeyedCodec<>("DamageAmount", Codec.FLOAT),
                    (c, v) -> c.damageAmount = v,
                    c -> c.damageAmount)
            .add()
            .append(new KeyedCodec<>("KnockbackForce", Codec.FLOAT),
                    (c, v) -> c.knockbackForceHorizontal = v,
                    c -> c.knockbackForceHorizontal)
            .add()
            .append(new KeyedCodec<>("KnockbackAxis", Codec.STRING),
                    (c, v) -> c.knockbackAxis = KnockbackAxis.valueOf(v),
                    c -> c.knockbackAxis.name())
            .add()
            .build();

    public enum HammerPhase {
        IDLE,
        SWING_RIGHT,
        SWING_LEFT
    }

    private static ComponentType<EntityStore, BigFreakingHammerComponent> componentType;

    // runtime state
    @Nullable
    private Ref<EntityStore> spawnedRef;
    private boolean active;
    private HammerPhase phase = HammerPhase.IDLE;
    private float phaseTimer = 0f;
    private float currentCooldownDuration = 0f;
    private boolean hasDamagedThisCycle = false;

    private float damageAmount = 75f;
    private KnockbackAxis knockbackAxis = KnockbackAxis.X;
    private boolean pendingDeactivation = false;
    private boolean needsIdleAnimation = true;

    private float damageDelayTime = 0.45f;
    private float damageZoneWidth = 6.0f;
    private float damageZoneHeight = 3.0f;
    private float damageZoneDepth = 2.0f;
    private float damageZoneOffsetY = 0.0f;
    private float knockbackForceY = 5.0f;
    private float knockbackForceHorizontal = 8.0f;
    private float knockbackDuration = 0f;

    // animation durations
    private float swingAnimationDuration = 1.0f;

    public BigFreakingHammerComponent() {
        this.spawnedRef = null;
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, BigFreakingHammerComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, BigFreakingHammerComponent> getComponentType() {
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

    public HammerPhase getPhase() {
        return phase;
    }

    public void setPhase(HammerPhase phase) {
        this.phase = phase;
        this.phaseTimer = 0f;
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

    public float getDamageAmount() {
        return damageAmount;
    }

    public void setDamageAmount(float amount) {
        this.damageAmount = amount;
    }

    public KnockbackAxis getKnockbackAxis() {
        return knockbackAxis;
    }

    public void setKnockbackAxis(KnockbackAxis axis) {
        this.knockbackAxis = axis;
    }

    public void setKnockbackForceHorizontal(float force) {
        this.knockbackForceHorizontal = force;
    }

    public boolean isPendingDeactivation() {
        return pendingDeactivation;
    }

    public void setPendingDeactivation(boolean pending) {
        this.pendingDeactivation = pending;
    }

    public boolean needsIdleAnimation() {
        return needsIdleAnimation;
    }

    public void setNeedsIdleAnimation(boolean needs) {
        this.needsIdleAnimation = needs;
    }

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
        return knockbackForceY;
    }

    public float getKnockbackForceHorizontal() {
        return knockbackForceHorizontal;
    }

    public float getKnockbackDuration() {
        return knockbackDuration;
    }

    // -- Animation durations ---

    public float getSwingAnimationDuration() {
        return swingAnimationDuration;
    }

    // --- Reset ---

    public void reset() {
        this.spawnedRef = null;
        this.active = false;
        this.phase = HammerPhase.IDLE;
        this.phaseTimer = 0f;
        this.currentCooldownDuration = 0f;
        this.hasDamagedThisCycle = false;
        this.pendingDeactivation = false;
        this.needsIdleAnimation = true;
    }

    @Nonnull
    @Override
    public BigFreakingHammerComponent clone() {
        BigFreakingHammerComponent copy = new BigFreakingHammerComponent();
        copy.spawnedRef = this.spawnedRef;
        copy.active = this.active;
        copy.phase = this.phase;
        copy.phaseTimer = this.phaseTimer;
        copy.currentCooldownDuration = this.currentCooldownDuration;
        copy.damageAmount = this.damageAmount;
        copy.knockbackAxis = this.knockbackAxis;
        copy.knockbackForceHorizontal = this.knockbackForceHorizontal;
        copy.hasDamagedThisCycle = this.hasDamagedThisCycle;
        copy.pendingDeactivation = this.pendingDeactivation;
        copy.needsIdleAnimation = this.needsIdleAnimation;
        return copy;
    }
}

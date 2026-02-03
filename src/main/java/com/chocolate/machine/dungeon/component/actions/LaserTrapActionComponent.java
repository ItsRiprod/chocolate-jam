package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class LaserTrapActionComponent implements Component<EntityStore> {

    public static final BuilderCodec<LaserTrapActionComponent> CODEC = BuilderCodec
            .builder(LaserTrapActionComponent.class, LaserTrapActionComponent::new)
            .append(new KeyedCodec<>("FireInterval", Codec.FLOAT),
                    (c, v) -> c.fireInterval = v,
                    c -> c.fireInterval)
            .add()
            .append(new KeyedCodec<>("Damage", Codec.FLOAT),
                    (c, v) -> c.damage = v,
                    c -> c.damage)
            .add()
            .append(new KeyedCodec<>("OffsetX", Codec.FLOAT),
                    (c, v) -> c.offsetX = v,
                    c -> c.offsetX)
            .add()
            .append(new KeyedCodec<>("OffsetY", Codec.FLOAT),
                    (c, v) -> c.offsetY = v,
                    c -> c.offsetY)
            .add()
            .append(new KeyedCodec<>("OffsetZ", Codec.FLOAT),
                    (c, v) -> c.offsetZ = v,
                    c -> c.offsetZ)
            .add()
            .append(new KeyedCodec<>("Yaw", Codec.FLOAT),
                    (c, v) -> c.yaw = v,
                    c -> c.yaw)
            .add()
            .append(new KeyedCodec<>("Pitch", Codec.FLOAT),
                    (c, v) -> c.pitch = v,
                    c -> c.pitch)
            .add()
            .build();

    private static ComponentType<EntityStore, LaserTrapActionComponent> componentType;

    // runtime state
    private boolean active;
    private float fireTimer = 0f;
    private float yaw = 0f;
    private float pitch = 90f;  // Default shoots up (model faces up by default)

    // configurable via command
    private float fireInterval = 2.0f;
    private float damage = 45f;
    private float offsetX = 0f;  // Spawn position offset X
    private float offsetY = 0f;  // Spawn position offset Y
    private float offsetZ = 0f;  // Spawn position offset Z

    private String projectileId = "Laser_Projectile";

    public LaserTrapActionComponent() {
        this.active = false;
    }

    public LaserTrapActionComponent(float fireTimer) {
        this.active = false;
        this.fireTimer = fireTimer;
    }

    public static void setComponentType(ComponentType<EntityStore, LaserTrapActionComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, LaserTrapActionComponent> getComponentType() {
        return componentType;
    }

    // --- Active state ---

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // --- Timer management ---

    public float getFireTimer() {
        return fireTimer;
    }

    public void addFireTime(float dt) {
        this.fireTimer += dt;
    }

    public void resetFireTimer() {
        this.fireTimer = 0f;
    }

    // --- Direction (set via /cm t config offset) ---

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    // --- Configurable via command ---

    public float getFireInterval() {
        return fireInterval;
    }

    public void setFireInterval(float interval) {
        this.fireInterval = interval;
    }

    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    public float getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetZ(float offsetZ) {
        this.offsetZ = offsetZ;
    }

    // --- Fixed values ---

    public String getProjectileId() {
        return projectileId;
    }

    // --- Reset ---

    public void reset() {
        this.active = false;
        this.fireTimer = 0f;
    }

    @Nonnull
    @Override
    public LaserTrapActionComponent clone() {
        LaserTrapActionComponent copy = new LaserTrapActionComponent();
        copy.active = this.active;
        copy.fireTimer = this.fireTimer;
        copy.fireInterval = this.fireInterval;
        copy.damage = this.damage;
        copy.offsetX = this.offsetX;
        copy.offsetY = this.offsetY;
        copy.offsetZ = this.offsetZ;
        copy.yaw = this.yaw;
        copy.pitch = this.pitch;
        return copy;
    }
}

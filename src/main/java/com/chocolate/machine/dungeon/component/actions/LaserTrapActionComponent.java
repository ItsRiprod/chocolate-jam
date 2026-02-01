package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Per-spawner runtime state for laser trap.
 * Periodically shoots laser projectiles in a configurable direction.
 */
public class LaserTrapActionComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, LaserTrapActionComponent> componentType;
    private static final Random RANDOM = new Random();

    // runtime state
    private boolean active;
    private float fireTimer = 0f;
    private float yaw = 0f;
    private float pitch = 0f;

    // configurable via command
    private float fireInterval = 2.0f;
    private float damage = 10f;
    private float speed = 40f;

    // fixed values
    private float minFireInterval = 1.5f;
    private float maxFireInterval = 3.0f;
    private boolean randomizeInterval = false;
    private String projectileId = "Laser_Projectile";
    private float spawnOffsetX = 0f;
    private float spawnOffsetY = 0.5f;
    private float spawnOffsetZ = 0f;

    public LaserTrapActionComponent() {
        this.active = false;
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
        if (randomizeInterval) {
            this.fireInterval = minFireInterval + RANDOM.nextFloat() * (maxFireInterval - minFireInterval);
        }
    }

    // --- Direction (set from spawner rotation) ---

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

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    // --- Fixed values ---

    public String getProjectileId() {
        return projectileId;
    }

    public float getSpawnOffsetX() {
        return spawnOffsetX;
    }

    public float getSpawnOffsetY() {
        return spawnOffsetY;
    }

    public float getSpawnOffsetZ() {
        return spawnOffsetZ;
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
        copy.speed = this.speed;
        copy.yaw = this.yaw;
        copy.pitch = this.pitch;
        return copy;
    }
}

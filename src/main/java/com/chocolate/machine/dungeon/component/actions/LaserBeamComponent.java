package com.chocolate.machine.dungeon.component.actions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class LaserBeamComponent implements Component<EntityStore> {

    public static final BuilderCodec<LaserBeamComponent> CODEC = BuilderCodec
            .builder(LaserBeamComponent.class, LaserBeamComponent::new)
            .append(new KeyedCodec<>("Damage", Codec.FLOAT),
                    (c, v) -> c.damage = v,
                    c -> c.damage)
            .add()
            .append(new KeyedCodec<>("DamageInterval", Codec.FLOAT),
                    (c, v) -> c.damageInterval = v,
                    c -> c.damageInterval)
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

    private static ComponentType<EntityStore, LaserBeamComponent> componentType;

    public static final float BEAM_SEGMENT_HEIGHT = 1.0f;
    public static final float MIN_BLOCK_HIT_DISTANCE = 2.0f;
    public static final float MAX_BEAM_DISTANCE = 64.0f;

    public static final float SOUND_INTERVAL = 0.3f;

    private boolean active;
    private float soundTimer = 0f;
    private float damageTimer = 0f;
    private float yaw = 0f;
    private float pitch = 90f;

    private float damage = 25f;
    private float damageInterval = 0.3f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float offsetZ = 0f;

    private final List<Ref<EntityStore>> beamSegments = new ArrayList<>();

    public LaserBeamComponent() {
        this.active = false;
    }

    public static void setComponentType(ComponentType<EntityStore, LaserBeamComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, LaserBeamComponent> getComponentType() {
        return componentType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float getSoundTimer() {
        return soundTimer;
    }

    public void addSoundTime(float dt) {
        this.soundTimer += dt;
    }

    public void resetSoundTimer() {
        this.soundTimer = 0f;
    }

    public float getDamageTimer() {
        return damageTimer;
    }

    public void addDamageTime(float dt) {
        this.damageTimer += dt;
    }

    public void resetDamageTimer() {
        this.damageTimer = 0f;
    }

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

    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getDamageInterval() {
        return damageInterval;
    }

    public void setDamageInterval(float interval) {
        this.damageInterval = interval;
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

    public List<Ref<EntityStore>> getBeamSegments() {
        return beamSegments;
    }

    public void addBeamSegment(Ref<EntityStore> segment) {
        beamSegments.add(segment);
    }

    public void clearBeamSegments() {
        beamSegments.clear();
    }

    public void reset() {
        this.active = false;
        this.soundTimer = 0f;
        this.damageTimer = 0f;
        beamSegments.clear();
    }

    @Nonnull
    @Override
    public LaserBeamComponent clone() {
        LaserBeamComponent copy = new LaserBeamComponent();
        copy.active = this.active;
        copy.soundTimer = this.soundTimer;
        copy.damageTimer = this.damageTimer;
        copy.damage = this.damage;
        copy.damageInterval = this.damageInterval;
        copy.offsetX = this.offsetX;
        copy.offsetY = this.offsetY;
        copy.offsetZ = this.offsetZ;
        copy.yaw = this.yaw;
        copy.pitch = this.pitch;
        copy.beamSegments.addAll(this.beamSegments);
        return copy;
    }
}

package com.chocolate.machine.dungeon.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonComponent implements Component<EntityStore> {

    public static final BuilderCodec<DungeonComponent> CODEC = BuilderCodec
            .builder(DungeonComponent.class, DungeonComponent::new)
            .append(new KeyedCodec<>("DungeonId", Codec.STRING),
                    (c, v) -> c.dungeonId = v,
                    c -> c.dungeonId)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("TriggerRadius", Codec.DOUBLE),
                    (c, v) -> c.triggerRadius = v,
                    c -> c.triggerRadius)
            .add()
            .append(new KeyedCodec<>("SpawnX", Codec.DOUBLE),
                    (c, v) -> c.spawnX = v,
                    c -> c.spawnX)
            .add()
            .append(new KeyedCodec<>("SpawnY", Codec.DOUBLE),
                    (c, v) -> c.spawnY = v,
                    c -> c.spawnY)
            .add()
            .append(new KeyedCodec<>("SpawnZ", Codec.DOUBLE),
                    (c, v) -> c.spawnZ = v,
                    c -> c.spawnZ)
            .add()
            .append(new KeyedCodec<>("Active", Codec.BOOLEAN),
                    (c, v) -> c.active = v,
                    c -> c.active)
            .add()
            .build();

    private static ComponentType<EntityStore, DungeonComponent> componentType;

    @Nonnull
    private String dungeonId = "";
    private double triggerRadius = 10.0;
    private double spawnX = 0.0;
    private double spawnY = 0.0;
    private double spawnZ = 0.0;
    private boolean active = false;
    private boolean triggered = false;

    // these were made 30 minutes before submission because normal registration wasnt working!
    private boolean pendingActivation = false;
    private boolean pendingDelayedActivation = false;
    private float activationDelayTimer = 0f;

    @Nullable
    private Ref<EntityStore> artifactHolderRef;
    @Nullable
    private Ref<EntityStore> pendingActivationPlayerRef;
    @Nullable
    private Ref<EntityStore> entranceRef;
    @Nonnull
    private final List<Ref<EntityStore>> dungeoneerRefs = new ArrayList<>();
    @Nonnull
    private final List<Ref<EntityStore>> spawnerRefs = new ArrayList<>();
    @Nonnull
    private final List<DungeonBlockEntry> dungeonBlocks = new ArrayList<>();
    private boolean registered = false;

    public DungeonComponent() {
    }

    public static void setComponentType(ComponentType<EntityStore, DungeonComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, DungeonComponent> getComponentType() {
        return componentType;
    }

    // Dungeon ID
    @Nonnull
    public String getDungeonId() {
        return dungeonId;
    }

    public void setDungeonId(@Nonnull String dungeonId) {
        this.dungeonId = dungeonId;
    }

    // Trigger radius for boss room detection
    public double getTriggerRadius() {
        return triggerRadius;
    }

    public void setTriggerRadius(double triggerRadius) {
        this.triggerRadius = triggerRadius;
    }

    // Spawn position for respawn
    @Nonnull
    public Vector3d getSpawnPosition() {
        return new Vector3d(spawnX, spawnY, spawnZ);
    }

    public void setSpawnPosition(@Nonnull Vector3d position) {
        this.spawnX = position.getX();
        this.spawnY = position.getY();
        this.spawnZ = position.getZ();
    }

    // Active state
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public boolean isPendingActivation() {
        return pendingActivation;
    }

    public void setPendingActivation(boolean pending) {
        this.pendingActivation = pending;
    }

    @Nullable
    public Ref<EntityStore> getPendingActivationPlayerRef() {
        return pendingActivationPlayerRef;
    }

    public void setPendingActivationPlayerRef(@Nullable Ref<EntityStore> ref) {
        this.pendingActivationPlayerRef = ref;
    }

    public boolean isPendingDelayedActivation() {
        return pendingDelayedActivation;
    }

    public void setPendingDelayedActivation(boolean pending) {
        this.pendingDelayedActivation = pending;
    }

    public float getActivationDelayTimer() {
        return activationDelayTimer;
    }

    public void setActivationDelayTimer(float timer) {
        this.activationDelayTimer = timer;
    }

    // Registration state
    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    // Artifact holder (relic carrier)
    @Nullable
    public Ref<EntityStore> getArtifactHolderRef() {
        return artifactHolderRef;
    }

    public void setArtifactHolderRef(@Nullable Ref<EntityStore> ref) {
        this.artifactHolderRef = ref;
    }

    // Entrance reference
    @Nullable
    public Ref<EntityStore> getEntranceRef() {
        return entranceRef;
    }

    public void setEntranceRef(@Nullable Ref<EntityStore> ref) {
        this.entranceRef = ref;
    }

    // Dungeoneer refs (players with DungeoneerComponent in this dungeon)
    // returns unmodifiable view - use add/remove methods to modify
    @Nonnull
    public List<Ref<EntityStore>> getDungeoneerRefs() {
        return Collections.unmodifiableList(dungeoneerRefs);
    }

    public void addDungeoneerRef(@Nonnull Ref<EntityStore> ref) {
        if (!dungeoneerRefs.contains(ref)) {
            dungeoneerRefs.add(ref);
        }
    }

    public void removeDungeoneerRef(@Nonnull Ref<EntityStore> ref) {
        dungeoneerRefs.remove(ref);
    }

    public void clearDungeoneerRefs() {
        dungeoneerRefs.clear();
    }

    // removes stale refs that are no longer valid
    public void cleanupInvalidDungeoneerRefs() {
        dungeoneerRefs.removeIf(ref -> !ref.isValid());
    }

    // Spawner refs - unmodifiable view
    @Nonnull
    public List<Ref<EntityStore>> getSpawnerRefs() {
        return Collections.unmodifiableList(spawnerRefs);
    }

    public void addSpawnerRef(@Nonnull Ref<EntityStore> ref) {
        if (!spawnerRefs.contains(ref)) {
            spawnerRefs.add(ref);
        }
    }

    public void removeSpawnerRef(@Nonnull Ref<EntityStore> ref) {
        spawnerRefs.remove(ref);
    }

    public void clearSpawnerRefs() {
        spawnerRefs.clear();
    }

    public int getSpawnerCount() {
        return spawnerRefs.size();
    }

    // Dungeon blocks - blocks that change state with dungeon activation
    @Nonnull
    public List<DungeonBlockEntry> getDungeonBlocks() {
        return Collections.unmodifiableList(dungeonBlocks);
    }

    public void addDungeonBlock(@Nonnull DungeonBlockEntry entry) {
        if (!dungeonBlocks.contains(entry)) {
            dungeonBlocks.add(entry);
        }
    }

    public void clearDungeonBlocks() {
        dungeonBlocks.clear();
    }

    public int getDungeonBlockCount() {
        return dungeonBlocks.size();
    }

    // reset state
    public void reset() {
        this.setActive(false);
        this.setTriggered(false);
        this.setPendingActivation(false);
        this.setPendingDelayedActivation(false);
        this.setActivationDelayTimer(0f);
        this.setPendingActivationPlayerRef(null);
        this.setArtifactHolderRef(null);
        this.clearDungeoneerRefs();
        this.clearSpawnerRefs();
        this.clearDungeonBlocks();
        this.setEntranceRef(null);
        this.setRegistered(false);
    }

    @Nonnull
    @Override
    public DungeonComponent clone() {
        DungeonComponent copy = new DungeonComponent();
        copy.dungeonId = this.dungeonId;
        copy.triggerRadius = this.triggerRadius;
        copy.spawnX = this.spawnX;
        copy.spawnY = this.spawnY;
        copy.spawnZ = this.spawnZ;
        copy.active = this.active;
        copy.triggered = this.triggered;
        copy.pendingActivation = this.pendingActivation;
        copy.pendingDelayedActivation = this.pendingDelayedActivation;
        copy.activationDelayTimer = this.activationDelayTimer;
        copy.artifactHolderRef = this.artifactHolderRef;
        copy.pendingActivationPlayerRef = this.pendingActivationPlayerRef;
        copy.entranceRef = this.entranceRef;
        copy.dungeoneerRefs.addAll(this.dungeoneerRefs);
        copy.spawnerRefs.addAll(this.spawnerRefs);
        copy.dungeonBlocks.addAll(this.dungeonBlocks);
        copy.registered = this.registered;
        return copy;
    }
}

package com.chocolate.machine.dungeon.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attached to players when they enter a dungeon's boss room.
 * Tracks their dungeon participation state.
 */
public class DungeoneerComponent implements Component<EntityStore> {

    public static final BuilderCodec<DungeoneerComponent> CODEC = BuilderCodec
            .builder(DungeoneerComponent.class, DungeoneerComponent::new)
            .append(new KeyedCodec<>("DungeonId", Codec.STRING),
                    (c, v) -> c.dungeonId = v,
                    c -> c.dungeonId)
            .add()
            .append(new KeyedCodec<>("IsRelicHolder", Codec.BOOLEAN),
                    (c, v) -> c.isRelicHolder = v,
                    c -> c.isRelicHolder)
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
            .append(new KeyedCodec<>("OriginalRespawnPoints",
                    new ArrayCodec<>(PlayerRespawnPointData.CODEC, PlayerRespawnPointData[]::new)),
                    (c, v) -> c.originalRespawnPoints = v,
                    c -> c.originalRespawnPoints)
            .add()
            .build();

    private static ComponentType<EntityStore, DungeoneerComponent> componentType;

    @Nonnull
    private String dungeonId = "";
    private boolean isRelicHolder = false;

    // Spawn position (copied from dungeon when entering boss room)
    private double spawnX = 0.0;
    private double spawnY = 0.0;
    private double spawnZ = 0.0;

    // backup of player's original respawn points (restored when leaving dungeon)
    @Nullable
    private PlayerRespawnPointData[] originalRespawnPoints;

    // Runtime reference to the dungeon entity (not serialized)
    @Nullable
    private Ref<EntityStore> dungeonRef;

    public DungeoneerComponent() {
    }

    public DungeoneerComponent(@Nonnull String dungeonId, @Nonnull Vector3d spawnPosition) {
        this.dungeonId = dungeonId;
        this.spawnX = spawnPosition.getX();
        this.spawnY = spawnPosition.getY();
        this.spawnZ = spawnPosition.getZ();
    }

    public static void setComponentType(ComponentType<EntityStore, DungeoneerComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, DungeoneerComponent> getComponentType() {
        return componentType;
    }

    @Nonnull
    public String getDungeonId() {
        return dungeonId;
    }

    public void setDungeonId(@Nonnull String dungeonId) {
        this.dungeonId = dungeonId;
    }

    public boolean isRelicHolder() {
        return isRelicHolder;
    }

    public void setRelicHolder(boolean relicHolder) {
        isRelicHolder = relicHolder;
    }

    @Nonnull
    public Vector3d getSpawnPosition() {
        return new Vector3d(spawnX, spawnY, spawnZ);
    }

    public void setSpawnPosition(@Nonnull Vector3d position) {
        this.spawnX = position.getX();
        this.spawnY = position.getY();
        this.spawnZ = position.getZ();
    }

    @Nullable
    public Ref<EntityStore> getDungeonRef() {
        return dungeonRef;
    }

    public void setDungeonRef(@Nullable Ref<EntityStore> dungeonRef) {
        this.dungeonRef = dungeonRef;
    }

    @Nullable
    public PlayerRespawnPointData[] getOriginalRespawnPoints() {
        return originalRespawnPoints;
    }

    public void setOriginalRespawnPoints(@Nullable PlayerRespawnPointData[] originalRespawnPoints) {
        this.originalRespawnPoints = originalRespawnPoints;
    }

    @Nonnull
    @Override
    public DungeoneerComponent clone() {
        DungeoneerComponent copy = new DungeoneerComponent();
        copy.dungeonId = this.dungeonId;
        copy.isRelicHolder = this.isRelicHolder;
        copy.spawnX = this.spawnX;
        copy.spawnY = this.spawnY;
        copy.spawnZ = this.spawnZ;
        copy.originalRespawnPoints = this.originalRespawnPoints;
        copy.dungeonRef = this.dungeonRef;
        return copy;
    }
}

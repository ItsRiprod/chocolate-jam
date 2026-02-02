package com.chocolate.machine.dungeon.component;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;

public class DungeonBlockEntry {

    private static final String ACTIVE_STATE = "On";
    private static final String INACTIVE_STATE = "default";

    @Nonnull
    private final Vector3i position;
    @Nonnull
    private final String blockTypeId;

    public DungeonBlockEntry(@Nonnull Vector3i position, @Nonnull String blockTypeId) {
        this.position = position.clone();
        this.blockTypeId = blockTypeId;
    }

    public DungeonBlockEntry(int x, int y, int z, @Nonnull String blockTypeId) {
        this.position = new Vector3i(x, y, z);
        this.blockTypeId = blockTypeId;
    }

    @Nonnull
    public Vector3i getPosition() {
        return position;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public int getZ() {
        return position.getZ();
    }

    @Nonnull
    public String getBlockTypeId() {
        return blockTypeId;
    }

    @Nonnull
    public String getActiveState() {
        return ACTIVE_STATE;
    }

    @Nonnull
    public String getInactiveState() {
        return INACTIVE_STATE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DungeonBlockEntry that = (DungeonBlockEntry) o;
        return position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }

    @Override
    public String toString() {
        return "DungeonBlockEntry{pos=" + position + ", type=" + blockTypeId + "}";
    }
}

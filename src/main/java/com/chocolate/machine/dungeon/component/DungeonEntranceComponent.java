package com.chocolate.machine.dungeon.component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// attached to dungeon controller, holds dungeon state
public class DungeonEntranceComponent implements Component<EntityStore> {

    public static final BuilderCodec<DungeonEntranceComponent> CODEC = BuilderCodec.builder(
            DungeonEntranceComponent.class, DungeonEntranceComponent::new)
            .append(
                    new KeyedCodec<>("TriggerRadius", Codec.DOUBLE),
                    (c, v) -> c.triggerRadius = v,
                    c -> c.triggerRadius)
            .add()
            .append(
                    new KeyedCodec<>("DungeonId", Codec.STRING),
                    (c, v) -> c.dungeonId = v,
                    c -> c.dungeonId)
            .add()
            .build();

    private double triggerRadius = 5.0;
    private String dungeonId;
    private static ComponentType<EntityStore, DungeonEntranceComponent> componentType;

    public static void setComponentType(ComponentType<EntityStore, DungeonEntranceComponent> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, DungeonEntranceComponent> getComponentType() {
        return componentType;
    }


    // Track players currently inside (per-instance state)
    private final Set<UUID> playersInside = ConcurrentHashMap.newKeySet();

    // Track players who were inside last tick (for escape detection)
    private final Set<UUID> playersInsideLastTick = ConcurrentHashMap.newKeySet();

    public double getTriggerRadius() {
        return triggerRadius;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public void setDungeonId(String dungeonId) {
        this.dungeonId = dungeonId;
    }

    public Set<UUID> getPlayersInside() {
        return playersInside;
    }

    public Set<UUID> getPlayersInsideLastTick() {
        return playersInsideLastTick;
    }

    /**
     * Called at end of tick to update last-tick tracking.
     * Copies current inside set to last-tick set.
     */
    public void updateLastTickTracking() {
        playersInsideLastTick.clear();
        playersInsideLastTick.addAll(playersInside);
    }

    @Override
    public DungeonEntranceComponent clone() {
        DungeonEntranceComponent copy = new DungeonEntranceComponent();
        copy.triggerRadius = this.triggerRadius;
        copy.dungeonId = this.dungeonId;
        // Copy runtime state
        copy.playersInside.addAll(this.playersInside);
        copy.playersInsideLastTick.addAll(this.playersInsideLastTick);
        return copy;
    }
}

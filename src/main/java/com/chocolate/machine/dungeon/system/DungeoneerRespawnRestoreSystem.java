package com.chocolate.machine.dungeon.system;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class DungeoneerRespawnRestoreSystem extends RefChangeSystem<EntityStore, DungeoneerComponent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public ComponentType<EntityStore, DungeoneerComponent> componentType() {
        return DungeoneerComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DungeoneerComponent component,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, DungeoneerComponent oldComponent,
            @Nonnull DungeoneerComponent newComponent, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DungeoneerComponent component,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (!ref.isValid()) {
            return;
        }

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRespawnPointData[] originalRespawn = component.getOriginalRespawnPoints();

        try {
            World world = commandBuffer.getExternalData().getWorld();
            if (world == null) return;
            String worldName = world.getName();
            if (worldName == null) return;
            PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(worldName);
            if (worldData == null) return;

            worldData.setRespawnPoints(originalRespawn);

            LOGGER.atInfo().log("[DungeoneerRespawnRestoreSystem] Restored %d original respawn points for player",
                    originalRespawn != null ? originalRespawn.length : 0);
        } catch (Exception e) {
            LOGGER.atWarning().log("failed to restore respawn points: %s", e.getMessage());
        }
    }
}

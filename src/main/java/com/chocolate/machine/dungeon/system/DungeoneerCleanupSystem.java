package com.chocolate.machine.dungeon.system;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Handles cleanup when a player with DungeoneerComponent is removed (disconnect, etc).
 * If relic holder disconnects, resets the dungeon.
 */
public class DungeoneerCleanupSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), DungeoneerComponent.getComponentType());
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        DungeoneerComponent dungeoneer = store.getComponent(ref, DungeoneerComponent.getComponentType());
        if (dungeoneer == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        String playerName = playerRef != null ? playerRef.getUsername() : "Unknown";

        LOGGER.atInfo().log("[DungeoneerCleanupSystem] Player '%s' removed while in dungeon '%s' (relicHolder: %s)",
                playerName, dungeoneer.getDungeonId(), dungeoneer.isRelicHolder());

        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        if (dungeonRef == null || !dungeonRef.isValid()) {
            return;
        }

        DungeonComponent dungeon = commandBuffer.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return;
        }

        // remove from dungeon tracking
        dungeon.removeDungeoneerRef(ref);

        if (dungeoneer.isRelicHolder()) {
            // relic holder left - full reset
            LOGGER.atInfo().log("[DungeoneerCleanupSystem] Relic holder disconnected! Resetting dungeon '%s'",
                    dungeon.getDungeonId());

            DungeonModule.get().getDungeonService().reset(dungeonRef, commandBuffer);

            for (Ref<EntityStore> otherRef : dungeon.getDungeoneerRefs()) {
                if (!otherRef.isValid()) continue;

                PlayerRef otherPlayer = commandBuffer.getComponent(otherRef, PlayerRef.getComponentType());
                if (otherPlayer != null) {
                    otherPlayer.sendMessage(Message.raw("The relic holder disconnected! Dungeon reset."));
                }
                commandBuffer.removeComponent(otherRef, DungeoneerComponent.getComponentType());
            }

            dungeon.clearDungeoneerRefs();
            dungeon.setArtifactHolderRef(null);
        } else if (dungeon.isActive() && dungeon.getDungeoneerRefs().isEmpty()) {
            // last non-relic player left - deactivate
            LOGGER.atInfo().log("[DungeoneerCleanupSystem] All dungeoneers left dungeon '%s', deactivating",
                    dungeon.getDungeonId());
            DungeonModule.get().getDungeonService().deactivate(dungeonRef, commandBuffer);
        }
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // not used - we only care about removal
    }
}

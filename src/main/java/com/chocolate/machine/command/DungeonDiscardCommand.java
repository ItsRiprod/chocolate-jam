package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Discard/unregister the nearest dungeon.
 * Useful for prefab editing to clear stale data.
 */
public class DungeonDiscardCommand extends AbstractPlayerCommand {

    public DungeonDiscardCommand() {
        super("discard", "Discard/unregister the nearest dungeon (clears stale data)");
        this.addAliases("clear", "reset");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        DungeonModule module = DungeonModule.get();
        if (module == null) {
            playerRef.sendMessage(Message.raw("Dungeon module not initialized."));
            return;
        }

        DungeonService dungeonService = module.getDungeonService();

        // Find nearest dungeon
        Ref<EntityStore> dungeonRef = DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);

        if (dungeonRef == null || !dungeonRef.isValid()) {
            playerRef.sendMessage(Message.raw("No dungeon found nearby."));
            return;
        }

        DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            playerRef.sendMessage(Message.raw("Found entity but no DungeonComponent."));
            return;
        }

        String dungeonId = dungeon.getDungeonId();
        TransformComponent transform = store.getComponent(dungeonRef, TransformComponent.getComponentType());
        String posStr = transform != null
                ? String.format("(%.1f, %.1f, %.1f)",
                        transform.getPosition().getX(),
                        transform.getPosition().getY(),
                        transform.getPosition().getZ())
                : "(unknown)";

        playerRef.sendMessage(Message.raw("Discarding dungeon '" + dungeonId + "' at " + posStr + "..."));

        // Deactivate if active
        if (dungeon.isActive()) {
            dungeonService.deactivate(dungeonRef, store);
            playerRef.sendMessage(Message.raw("Deactivated dungeon."));
        }

        // Clean up all spawners
        int spawnerCount = dungeon.getSpawnerCount();
        for (Ref<EntityStore> spawnerRef : dungeon.getSpawnerRefs()) {
            if (!spawnerRef.isValid()) continue;

            SpawnerComponent spawner = store.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner != null) {
                dungeonService.cleanupSpawner(spawnerRef, store);
            }
        }

        // Remove DungeoneerComponent from all dungeoneers
        int dungeoneerCount = dungeon.getDungeoneerRefs().size();
        for (Ref<EntityStore> dungeoneerRef : dungeon.getDungeoneerRefs()) {
            if (!dungeoneerRef.isValid()) continue;

            DungeoneerComponent dungeoneer = store.getComponent(dungeoneerRef, DungeoneerComponent.getComponentType());
            if (dungeoneer != null) {
                store.removeComponent(dungeoneerRef, DungeoneerComponent.getComponentType());
            }
        }

        // Clear all references
        dungeon.clearSpawnerRefs();
        dungeon.clearDungeoneerRefs();
        dungeon.setEntranceRef(null);
        dungeon.setArtifactHolderRef(null);
        dungeon.setActive(false);
        dungeon.setRegistered(false);
        dungeon.reset();

        playerRef.sendMessage(Message.raw("Discarded dungeon:"));
        playerRef.sendMessage(Message.raw("  - Unlinked " + spawnerCount + " spawners"));
        playerRef.sendMessage(Message.raw("  - Removed " + dungeoneerCount + " dungeoneers"));
        playerRef.sendMessage(Message.raw("  - Cleared entrance reference"));
        playerRef.sendMessage(Message.raw("Use /chocolate dungeon register to re-register."));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Discard/unregister the nearest dungeon");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate dungeon discard");
    }
}

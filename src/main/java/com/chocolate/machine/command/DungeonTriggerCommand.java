package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Toggle dungeon trigger state for testing:
 * - First run: Make executor the relic holder, activate dungeon
 * - Second run: Simulate relic holder escaping (deactivate dungeon)
 */
public class DungeonTriggerCommand extends AbstractPlayerCommand {

    public DungeonTriggerCommand() {
        super("trigger", "Toggle: become relic holder / simulate escape");
        this.addAliases("test", "tr");
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

        // Check if player is already a dungeoneer with relic
        DungeoneerComponent existingDungeoneer = store.getComponent(playerEntityRef, DungeoneerComponent.getComponentType());

        if (existingDungeoneer != null && existingDungeoneer.isRelicHolder()) {
            // Player is relic holder - simulate escape
            handleSimulateEscape(playerEntityRef, playerRef, existingDungeoneer, dungeonService, store);
        } else {
            // Player is not relic holder - become relic holder
            handleBecomeRelicHolder(playerEntityRef, playerRef, existingDungeoneer, dungeonService, store);
        }
    }

    private void handleBecomeRelicHolder(Ref<EntityStore> playerEntityRef, PlayerRef playerRef,
            DungeoneerComponent existingDungeoneer, DungeonService dungeonService, Store<EntityStore> store) {

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

        // Add DungeoneerComponent if not already present
        if (existingDungeoneer == null) {
            Vector3d spawnPosition = dungeon.getSpawnPosition();
            existingDungeoneer = new DungeoneerComponent(dungeonId, spawnPosition);
            existingDungeoneer.setDungeonRef(dungeonRef);
            store.addComponent(playerEntityRef, DungeoneerComponent.getComponentType(), existingDungeoneer);
            dungeon.addDungeoneerRef(playerEntityRef);
            playerRef.sendMessage(Message.raw("Added as dungeoneer to '" + dungeonId + "'"));
        }

        // Make player the relic holder
        existingDungeoneer.setRelicHolder(true);
        dungeon.setArtifactHolderRef(playerEntityRef);

        // Activate dungeon if not already active
        if (!dungeon.isActive()) {
            dungeonService.activate(dungeonRef, playerEntityRef, store);
            playerRef.sendMessage(Message.raw("You are now the relic holder! Dungeon '" + dungeonId + "' activated."));
            playerRef.sendMessage(Message.raw(dungeon.getSpawnerCount() + " spawners triggered."));
        } else {
            playerRef.sendMessage(Message.raw("You are now the relic holder! Dungeon was already active."));
        }

        playerRef.sendMessage(Message.raw("Run /chocolate dungeon trigger again to simulate escape."));
    }

    private void handleSimulateEscape(Ref<EntityStore> playerEntityRef, PlayerRef playerRef,
            DungeoneerComponent dungeoneer, DungeonService dungeonService, Store<EntityStore> store) {

        String dungeonId = dungeoneer.getDungeonId();
        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();

        playerRef.sendMessage(Message.raw("Simulating escape from dungeon '" + dungeonId + "'..."));

        // Clear relic holder status
        dungeoneer.setRelicHolder(false);

        if (dungeonRef != null && dungeonRef.isValid()) {
            DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());

            if (dungeon != null) {
                // Deactivate dungeon
                dungeonService.deactivate(dungeonRef, store);
                dungeon.setArtifactHolderRef(null);

                // Remove DungeoneerComponent from all players (use tryRemove for safety)
                for (Ref<EntityStore> otherRef : dungeon.getDungeoneerRefs()) {
                    if (otherRef.isValid()) {
                        store.tryRemoveComponent(otherRef, DungeoneerComponent.getComponentType());
                    }
                }
                dungeon.clearDungeoneerRefs();

                playerRef.sendMessage(Message.raw("Dungeon deactivated! All dungeoneers cleared."));
            }
        }

        // Remove our DungeoneerComponent (tryRemove handles if already removed in the loop above)
        store.tryRemoveComponent(playerEntityRef, DungeoneerComponent.getComponentType());

        playerRef.sendMessage(Message.raw("Escape simulated successfully!"));
        playerRef.sendMessage(Message.raw("Run /chocolate dungeon trigger again to become relic holder."));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Toggle: become relic holder / simulate escape");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate dungeon trigger");
    }
}

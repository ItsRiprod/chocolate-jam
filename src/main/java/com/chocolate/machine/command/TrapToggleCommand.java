package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public class TrapToggleCommand extends AbstractPlayerCommand {

    public TrapToggleCommand() {
        super("toggle", "Toggle Active state of the SpawnMarker you are looking at");
        this.addAliases("t", "tog");
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
        if (dungeonService == null) {
            playerRef.sendMessage(Message.raw("DungeonService not available."));
            return;
        }

        Ref<EntityStore> targetRef = TargetUtil.getTargetEntity(playerEntityRef, store);

        if (targetRef == null || !targetRef.isValid()) {
            playerRef.sendMessage(Message.raw("No entity in view. Look at a SpawnMarker entity."));
            return;
        }

        SpawnerComponent spawner = store.getComponent(targetRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            playerRef.sendMessage(Message.raw("Target entity is not a Trap (no SpawnerComponent)."));
            return;
        }

        String executionId = spawner.getExecutionId();
        if (executionId.isEmpty()) {
            playerRef.sendMessage(Message.raw("Trap has no executionId configured. Use /chocolate trap set first."));
            return;
        }

        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        String posStr = "(unknown)";
        if (transform != null) {
            posStr = String.format("(%.1f, %.1f, %.1f)",
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ());
        }

        boolean isActive = isTrapActive(store, targetRef, executionId);

        if (isActive) {
            boolean success = dungeonService.deactivateSpawner(targetRef, store);
            if (success) {
                playerRef.sendMessage(Message.raw("DEACTIVATED trap '" + executionId + "' at " + posStr));
            } else {
                playerRef.sendMessage(Message.raw("Failed to deactivate trap at " + posStr));
            }
        } else {
            boolean success = dungeonService.activateSpawner(targetRef, store);
            if (success) {
                playerRef.sendMessage(Message.raw("ACTIVATED trap '" + executionId + "' at " + posStr));
            } else {
                playerRef.sendMessage(Message.raw("Failed to activate trap at " + posStr));
            }
        }
    }

    private boolean isTrapActive(Store<EntityStore> store, Ref<EntityStore> trapRef, String executionId) {
        SpawnerComponent comp = store.getComponent(trapRef, SpawnerComponent.getComponentType());
        return comp != null && comp.isActive();
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Toggle Active state of the Trap you are looking at");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate trap toggle");
    }
}

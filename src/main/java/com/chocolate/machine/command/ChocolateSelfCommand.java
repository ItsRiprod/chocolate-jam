package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ChocolateSelfCommand extends AbstractPlayerCommand {

    public ChocolateSelfCommand() {
        super("self", "Show your current dungeon status");
        this.addAliases("me", "status");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        playerRef.sendMessage(Message.raw("=== Chocolate Machine Status ==="));

        TransformComponent playerTransform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (playerTransform != null) {
            Vector3d pos = playerTransform.getPosition();
            playerRef.sendMessage(Message.raw(String.format("Position: (%.1f, %.1f, %.1f)",
                    pos.getX(), pos.getY(), pos.getZ())));
        }

        DungeoneerComponent dungeoneer = store.getComponent(playerEntityRef, DungeoneerComponent.getComponentType());

        if (dungeoneer == null) {
            playerRef.sendMessage(Message.raw("Dungeoneer: No (not in a dungeon)"));
            playerRef.sendMessage(Message.raw("Relic Holder: No"));
            playerRef.sendMessage(Message.raw("Dungeon Network: None"));
        } else {
            playerRef.sendMessage(Message.raw("Dungeoneer: Yes"));
            playerRef.sendMessage(Message.raw("Relic Holder: " + (dungeoneer.isRelicHolder() ? "Yes" : "No")));
            playerRef.sendMessage(Message.raw("Dungeon Network: " + dungeoneer.getDungeonId()));

            Vector3d spawnPos = dungeoneer.getSpawnPosition();
            playerRef.sendMessage(Message.raw(String.format("Dungeon Spawn: (%.1f, %.1f, %.1f)",
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ())));

            Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
            if (dungeonRef != null && dungeonRef.isValid()) {
                DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
                if (dungeon != null) {
                    playerRef.sendMessage(Message.raw("Dungeon Active: " + (dungeon.isActive() ? "Yes" : "No")));
                    playerRef.sendMessage(Message.raw("Dungeon Registered: " + (dungeon.isRegistered() ? "Yes" : "No")));
                    playerRef.sendMessage(Message.raw("Spawners: " + dungeon.getSpawnerCount()));
                    playerRef.sendMessage(Message.raw("Dungeoneers: " + dungeon.getDungeoneerRefs().size()));
                }
            } else {
                playerRef.sendMessage(Message.raw("Dungeon Ref: Invalid or missing"));
            }
        }

        playerRef.sendMessage(Message.raw("================================"));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Show your current dungeon status");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate self");
    }
}

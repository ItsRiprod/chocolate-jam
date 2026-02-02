package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
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

public class DungeonRegisterCommand extends AbstractPlayerCommand {

    public DungeonRegisterCommand() {
        super("register", "Find nearest dungeon and re-register spawners");
        this.addAliases("reg", "refresh");
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

        Ref<EntityStore> dungeonRef = DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);

        if (dungeonRef == null || !dungeonRef.isValid()) {
            playerRef.sendMessage(Message.raw("No dungeon found nearby. Place a BossRoom prefab first."));
            return;
        }

        DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            playerRef.sendMessage(Message.raw("Found entity but no DungeonComponent. This shouldn't happen."));
            return;
        }

        TransformComponent transform = store.getComponent(dungeonRef, TransformComponent.getComponentType());
        String posStr = "(unknown)";
        if (transform != null) {
            posStr = String.format("(%.1f, %.1f, %.1f)",
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ());
        }

        String dungeonId = dungeon.getDungeonId();
        int oldCount = dungeon.getSpawnerCount();

        if (dungeon.isRegistered()) {
            playerRef.sendMessage(Message.raw("Re-registering dungeon '" + dungeonId + "' at " + posStr));
            playerRef.sendMessage(Message.raw("Previous spawner count: " + oldCount));
            dungeon.reset();
        } else {
            playerRef.sendMessage(Message.raw("Registering dungeon '" + dungeonId + "' at " + posStr));
        }

        int registeredCount = dungeonService.registerDungeon(dungeonRef, store, world);

        playerRef.sendMessage(Message.raw("Registered " + registeredCount + " spawners to dungeon."));
        playerRef.sendMessage(Message.raw("Registered " + dungeon.getDungeonBlockCount() + " dungeon blocks."));

        // Check entrance
        if (dungeon.getEntranceRef() != null && dungeon.getEntranceRef().isValid()) {
            playerRef.sendMessage(Message.raw("Entrance linked successfully."));
        } else {
            playerRef.sendMessage(Message.raw("Warning: No entrance found with matching dungeonId."));
        }

        if (registeredCount == 0) {
            playerRef.sendMessage(Message.raw("No spawners found. Use /chocolate trap place to add traps."));
        }
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Find nearest dungeon and re-register spawners");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate dungeon register");
    }
}

package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public class DungeonDeleteCommand extends AbstractPlayerCommand {

    private final OptionalArg<Boolean> nearestArg;

    public DungeonDeleteCommand() {
        super("delete", "Delete a dungeon bossroom");
        this.addAliases("del", "remove");

        this.nearestArg = this.withOptionalArg("nearest", "Delete nearest dungeon instead of looking at", ArgTypes.BOOLEAN);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Boolean useNearest = nearestArg.get(context);
        Ref<EntityStore> targetRef;

        if (useNearest != null && useNearest) {
            targetRef = DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);
            if (targetRef == null || !targetRef.isValid()) {
                playerRef.sendMessage(Message.raw("No dungeon found nearby."));
                return;
            }
        } else {
            targetRef = TargetUtil.getTargetEntity(playerEntityRef, store);
            if (targetRef == null || !targetRef.isValid()) {
                playerRef.sendMessage(Message.raw("No entity in view. Look at a dungeon bossroom entity."));
                return;
            }
        }

        DungeonComponent dungeon = store.getComponent(targetRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            playerRef.sendMessage(Message.raw("Target entity is not a dungeon bossroom."));
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

        String dungeonId = dungeon.getDungeonId();

        dungeon.reset();
        store.removeComponent(targetRef, DungeonComponent.getComponentType());

        playerRef.sendMessage(Message.raw("Deleted dungeon '" + dungeonId + "' at " + posStr));
        playerRef.sendMessage(Message.raw("Note: The entity still exists. Use /kill to remove it completely."));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Delete a dungeon bossroom");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/cm d delete [--nearest]");
    }
}

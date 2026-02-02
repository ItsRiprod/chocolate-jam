package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
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

public class DungeonEntranceCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> dungeonIdArg;
    private final OptionalArg<Boolean> deleteArg;

    public DungeonEntranceCommand() {
        super("entrance", "Assign or delete a dungeon entrance");
        this.addAliases("ent", "entry");

        this.dungeonIdArg = this.withOptionalArg("dungeon_id", "Dungeon ID to link to", ArgTypes.STRING);
        this.deleteArg = this.withOptionalArg("delete", "Delete the entrance instead of creating", ArgTypes.BOOLEAN);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Ref<EntityStore> targetRef = TargetUtil.getTargetEntity(playerEntityRef, store);
        if (targetRef == null || !targetRef.isValid()) {
            playerRef.sendMessage(Message.raw("No entity in view. Look at an entity."));
            return;
        }

        if (targetRef.equals(playerEntityRef)) {
            playerRef.sendMessage(Message.raw("Cannot use yourself as an entrance."));
            return;
        }

        Boolean shouldDelete = deleteArg.get(context);

        if (shouldDelete != null && shouldDelete) {
            handleDelete(store, targetRef, playerRef);
        } else {
            handleAssign(context, store, playerEntityRef, targetRef, playerRef);
        }
    }

    private void handleDelete(Store<EntityStore> store, Ref<EntityStore> targetRef, PlayerRef playerRef) {
        DungeonEntranceComponent entrance = store.getComponent(targetRef, DungeonEntranceComponent.getComponentType());
        if (entrance == null) {
            playerRef.sendMessage(Message.raw("Target entity is not a dungeon entrance."));
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

        String dungeonId = entrance.getDungeonId();
        store.removeComponent(targetRef, DungeonEntranceComponent.getComponentType());

        playerRef.sendMessage(Message.raw("Deleted entrance for dungeon '" + dungeonId + "' at " + posStr));
    }

    private void handleAssign(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef, Ref<EntityStore> targetRef, PlayerRef playerRef) {

        DungeonComponent existingDungeon = store.getComponent(targetRef, DungeonComponent.getComponentType());
        if (existingDungeon != null) {
            playerRef.sendMessage(Message.raw("Entity is already a dungeon bossroom. Cannot also be an entrance."));
            return;
        }

        DungeonEntranceComponent existingEntrance = store.getComponent(targetRef, DungeonEntranceComponent.getComponentType());
        if (existingEntrance != null) {
            playerRef.sendMessage(Message.raw("Entity is already a dungeon entrance (ID: " + existingEntrance.getDungeonId() + ")."));
            playerRef.sendMessage(Message.raw("Use --delete to remove it first."));
            return;
        }

        SpawnerComponent existingSpawner = store.getComponent(targetRef, SpawnerComponent.getComponentType());
        if (existingSpawner != null) {
            playerRef.sendMessage(Message.raw("Entity is already a spawner. Remove spawner first."));
            return;
        }

        String dungeonId = dungeonIdArg.get(context);

        if (dungeonId == null || dungeonId.isEmpty()) {
            Ref<EntityStore> nearestDungeon = DungeonFinder.findNearestDungeonToPlayer(playerEntityRef, store);
            if (nearestDungeon != null && nearestDungeon.isValid()) {
                DungeonComponent dungeon = store.getComponent(nearestDungeon, DungeonComponent.getComponentType());
                if (dungeon != null) {
                    dungeonId = dungeon.getDungeonId();
                    playerRef.sendMessage(Message.raw("Auto-linking to nearest dungeon: " + dungeonId));
                }
            }
        }

        if (dungeonId == null || dungeonId.isEmpty()) {
            playerRef.sendMessage(Message.raw("No dungeon_id specified and no dungeon found nearby."));
            playerRef.sendMessage(Message.raw("Use --dungeon_id <id> or create a bossroom first."));
            return;
        }

        DungeonEntranceComponent entrance = new DungeonEntranceComponent();
        entrance.setDungeonId(dungeonId);
        store.addComponent(targetRef, DungeonEntranceComponent.getComponentType(), entrance);

        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        String posStr = "(unknown)";
        if (transform != null) {
            posStr = String.format("(%.1f, %.1f, %.1f)",
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ());
        }

        playerRef.sendMessage(Message.raw("Created entrance for dungeon '" + dungeonId + "' at " + posStr));
        playerRef.sendMessage(Message.raw("Use '/cm d register' to link entrance to dungeon."));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Assign or delete a dungeon entrance");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/cm d entrance [--dungeon_id <id>] [--delete]");
    }
}

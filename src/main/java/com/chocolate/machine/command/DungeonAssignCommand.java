package com.chocolate.machine.command;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
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

public class DungeonAssignCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> dungeonIdArg;

    public DungeonAssignCommand() {
        super("assign", "Assign an entity as a dungeon bossroom");
        this.addAliases("create", "make");

        this.dungeonIdArg = this.withOptionalArg("dungeon_id", "Dungeon ID (auto-generated if not specified)", ArgTypes.STRING);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Ref<EntityStore> targetRef = TargetUtil.getTargetEntity(playerEntityRef, store);
        if (targetRef == null || !targetRef.isValid()) {
            playerRef.sendMessage(Message.raw("No entity in view. Look at an entity to assign as bossroom."));
            return;
        }

        if (targetRef.equals(playerEntityRef)) {
            playerRef.sendMessage(Message.raw("Cannot assign yourself as a bossroom."));
            return;
        }

        DungeonComponent existingDungeon = store.getComponent(targetRef, DungeonComponent.getComponentType());
        if (existingDungeon != null) {
            playerRef.sendMessage(Message.raw("Entity is already a dungeon bossroom (ID: " + existingDungeon.getDungeonId() + ")."));
            return;
        }

        DungeonEntranceComponent existingEntrance = store.getComponent(targetRef, DungeonEntranceComponent.getComponentType());
        if (existingEntrance != null) {
            playerRef.sendMessage(Message.raw("Entity is already a dungeon entrance. Remove entrance first."));
            return;
        }

        SpawnerComponent existingSpawner = store.getComponent(targetRef, SpawnerComponent.getComponentType());
        if (existingSpawner != null) {
            playerRef.sendMessage(Message.raw("Entity is already a spawner. Remove spawner first."));
            return;
        }

        String dungeonId = dungeonIdArg.get(context);
        if (dungeonId == null || dungeonId.isEmpty()) {
            dungeonId = "dungeon_" + UUID.randomUUID().toString().substring(0, 8);
        }

        DungeonComponent dungeon = new DungeonComponent();
        dungeon.setDungeonId(dungeonId);

        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (transform != null) {
            dungeon.setSpawnPosition(transform.getPosition());
        }

        store.addComponent(targetRef, DungeonComponent.getComponentType(), dungeon);

        String posStr = "(unknown)";
        if (transform != null) {
            posStr = String.format("(%.1f, %.1f, %.1f)",
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ());
        }

        playerRef.sendMessage(Message.raw("Created dungeon bossroom '" + dungeonId + "' at " + posStr));
        playerRef.sendMessage(Message.raw("Use '/cm d register' to register spawners and entrance."));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Assign an entity as a dungeon bossroom");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/cm d assign [--dungeon_id <id>]");
    }
}

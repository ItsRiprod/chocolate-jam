package com.chocolate.machine.command;

import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TrapListCommand extends AbstractPlayerCommand {

    public TrapListCommand() {
        super("list", "List all Trap locations found via flood-fill");
        this.addAliases("ls", "traps");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // flood-fill from player position using spatial indexing
        List<Ref<EntityStore>> spawners = EntityFloodFill.floodFillSpawners(
                playerEntityRef,
                store,
                SpawnerComponent.getComponentType());

        if (spawners.isEmpty()) {
            playerRef.sendMessage(Message.raw("No Traps found within flood-fill radius."));
            return;
        }

        playerRef.sendMessage(Message.raw("=== Trap Locations (" + spawners.size() + " found) ==="));
        int index = 1;
        for (Ref<EntityStore> spawnerRef : spawners) {
            if (!spawnerRef.isValid()) {
                continue;
            }

            SpawnerComponent spawner = store.getComponent(spawnerRef, SpawnerComponent.getComponentType());
            if (spawner == null) {
                continue;
            }

            TransformComponent transform = store.getComponent(spawnerRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            String posStr = String.format("(%.1f, %.1f, %.1f)",
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ());

            String execId = spawner.getExecutionId();
            if (execId.isEmpty()) {
                execId = "(unconfigured)";
            }

            playerRef.sendMessage(Message.raw(
                    String.format("%d. %s - %s", index, posStr, execId)));

            index++;
        }
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("List all Traps locations found via flood-fill");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate trap list");
    }
}

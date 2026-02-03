package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.SpawnedEntityComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;

import java.util.function.BiConsumer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class DungeonCleanCommand extends AbstractPlayerCommand {

    public DungeonCleanCommand() {
        super("clean", "Remove all spawned trap/dungeon entities");
        addAliases("clear-spawned", "purge");
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        if (SpawnedEntityComponent.getComponentType() == null) {
            playerRef.sendMessage(Message.raw("SpawnedEntityComponent not registered"));
            return;
        }

        ObjectArrayList<Ref<EntityStore>> toRemove = new ObjectArrayList<>();

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector = (chunk, cmdBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef != null && entityRef.isValid()) {
                    toRemove.add(entityRef);
                }
            }
        };
        store.forEachChunk(Query.and(SpawnedEntityComponent.getComponentType()), collector);

        int removed = 0;
        for (Ref<EntityStore> entityRef : toRemove) {
            if (entityRef.isValid()) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                removed++;
            }
        }

        playerRef.sendMessage(Message.raw("Removed " + removed + " spawned entities"));
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Remove all spawned trap/dungeon entities");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/cm d clean");
    }
}

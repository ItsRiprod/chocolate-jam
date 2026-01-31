package com.chocolate.machine.command;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

/**
 * Set execution ID of the spawner you're looking at.
 * Auto-registers the spawner into the nearest dungeon if within range.
 */
public class TrapSetCommand extends AbstractPlayerCommand {

    private static final double AUTO_REGISTER_RANGE = 50.0;

    private final RequiredArg<String> executionId;

    public TrapSetCommand() {
        super("set", "Set the type of the trap entity you're looking at");
        this.addAliases("s");

        this.executionId = this.withRequiredArg("executionId",
                "Execution ID of the trap " + getSpawnableSuggestions().toString(), ArgTypes.STRING);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String newId = executionId.get(context);

        SpawnableRegistry registry = SpawnableRegistry.getInstance();

        if (!registry.isRegistered(newId)) {
            context.sendMessage(Message.raw(
                    "Unknown trap ID: " + newId + ". Available: " + getSpawnableSuggestions().toString()));
            return;
        }

        Ref<EntityStore> targetRef = TargetUtil.getTargetEntity(ref, store);
        if (targetRef == null) {
            context.sendMessage(Message.raw("No entity in view. Look at a Spawn_Marker entity."));
            return;
        }

        SpawnerComponent spawner = store.getComponent(targetRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            // add SpawnerComponent if missing
            spawner = new SpawnerComponent();
            store.addComponent(targetRef, SpawnerComponent.getComponentType(), spawner);
        }

        String oldId = spawner.getExecutionId();

        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        String posStr = transform != null
                ? String.format("(%.1f, %.1f, %.1f)",
                        transform.getPosition().getX(),
                        transform.getPosition().getY(),
                        transform.getPosition().getZ())
                : "(unknown)";

        DungeonModule module = DungeonModule.get();
        if (module != null) {
            DungeonService service = module.getDungeonService();
            service.setSpawnerAction(targetRef, newId, store);

            // Auto-register into nearest dungeon if not already registered
            autoRegisterSpawner(targetRef, spawner, transform, store, playerRef);
        } else {
            // fallback: just set ID directly
            spawner.setExecutionId(newId);
        }

        if (oldId.isEmpty()) {
            context.sendMessage(Message.raw("Set trap at " + posStr + " to " + newId));
        } else {
            context.sendMessage(Message.raw("Changed trap at " + posStr + " from " + oldId + " to " + newId));
        }
    }

    private void autoRegisterSpawner(Ref<EntityStore> spawnerRef, SpawnerComponent spawner,
            TransformComponent transform, Store<EntityStore> store, PlayerRef playerRef) {

        if (transform == null) return;

        // Only search within AUTO_REGISTER_RANGE blocks
        Ref<EntityStore> dungeonRef = DungeonFinder.findNearestDungeon(
                transform.getPosition(), store, AUTO_REGISTER_RANGE);
        if (dungeonRef == null || !dungeonRef.isValid()) {
            playerRef.sendMessage(Message.raw("No dungeon within " + (int) AUTO_REGISTER_RANGE
                    + " blocks. Trap configured but not linked to a dungeon."));
            return;
        }

        DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return;
        }

        // Link spawner to dungeon
        dungeon.addSpawnerRef(spawnerRef);

        playerRef.sendMessage(Message.raw("Auto-registered to dungeon '" + dungeon.getDungeonId() + "'"));
    }

    private List<String> getSpawnableSuggestions() {
        return new ArrayList<>(SpawnableRegistry.getInstance().getRegisteredIds());
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Set the execution ID of the trap you're looking at");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate trap set <executionId>");
    }
}

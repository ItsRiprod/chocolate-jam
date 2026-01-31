package com.chocolate.machine.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Place a trap spawner entity at the player's current location.
 * Auto-registers into the nearest dungeon if within range.
 */
public class TrapPlaceCommand extends AbstractPlayerCommand {

    private static final double AUTO_REGISTER_RANGE = 50.0;
    private static final String SPAWN_MARKER_MODEL = "Spawn_Marker";

    private RequiredArg<String> executionId;

    public TrapPlaceCommand() {
        super("place", "Place a trap entity at your current location");
        this.addAliases("pl");
        executionId = withRequiredArg("executionId", "Execution ID of the trap to place", ArgTypes.STRING);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String execId = executionId.get(context);

        SpawnableRegistry registry = SpawnableRegistry.getInstance();
        if (!registry.isRegistered(execId)) {
            playerRef.sendMessage(Message.raw(
                    "Unknown trap ID: " + execId + ". Available: " + getSpawnableSuggestions().toString()));
            return;
        }

        // Get player position
        TransformComponent playerTransform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            playerRef.sendMessage(Message.raw("Could not get player position."));
            return;
        }

        Vector3d position = playerTransform.getPosition();

        // Create a new entity with SpawnerComponent
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // Add transform
        TransformComponent transform = new TransformComponent(position, new Vector3f(0, 0, 0));
        holder.addComponent(TransformComponent.getComponentType(), transform);

        // Add UUID
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));

        // Add Spawn_Marker model for visibility
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(SPAWN_MARKER_MODEL);
        if (modelAsset != null) {
            Model model = Model.createScaledModel(modelAsset, 1.0f);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        }

        // Add SpawnerComponent with execution ID
        SpawnerComponent spawner = new SpawnerComponent(execId);
        holder.addComponent(SpawnerComponent.getComponentType(), spawner);

        // Spawn the entity
        Ref<EntityStore> spawnerRef = store.addEntity(holder, AddReason.SPAWN);

        String posStr = String.format("(%.1f, %.1f, %.1f)", position.getX(), position.getY(), position.getZ());
        playerRef.sendMessage(Message.raw("Placed trap '" + execId + "' at " + posStr));

        // Register the spawnable action
        DungeonModule module = DungeonModule.get();
        if (module != null) {
            Spawnable spawnable = registry.get(execId);
            if (spawnable != null) {
                spawnable.register(spawnerRef, store);
            }

            // Auto-register into nearest dungeon (only if within range)
            autoRegisterSpawner(spawnerRef, spawner, position, store, playerRef);
        }
    }

    private void autoRegisterSpawner(Ref<EntityStore> spawnerRef, SpawnerComponent spawner,
            Vector3d position, Store<EntityStore> store, PlayerRef playerRef) {

        // Only search within AUTO_REGISTER_RANGE blocks
        Ref<EntityStore> dungeonRef = DungeonFinder.findNearestDungeon(position, store, AUTO_REGISTER_RANGE);
        if (dungeonRef == null || !dungeonRef.isValid()) {
            playerRef.sendMessage(Message.raw("No dungeon within " + (int) AUTO_REGISTER_RANGE
                    + " blocks. Spawner placed but not linked to a dungeon."));
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
        return Message.raw("Place a trap entity at your current location");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/chocolate trap place <executionId>");
    }
}

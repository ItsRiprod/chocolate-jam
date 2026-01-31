package com.chocolate.machine.dungeon.system;

import java.util.Set;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Intercepts player deaths in dungeons.
 * When a dungeoneer dies:
 * - If relic holder: reset dungeon, teleport ALL dungeoneers to spawn
 * - If not relic holder: teleport just them back to spawn
 */
public class DungeonDeathSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process players with DungeoneerComponent
        return Query.and(Player.getComponentType(), DungeoneerComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run first, before other death systems
        return RootDependency.firstSet();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        DungeoneerComponent dungeoneer = commandBuffer.getComponent(ref, DungeoneerComponent.getComponentType());
        if (dungeoneer == null) {
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        String playerName = playerRef != null ? playerRef.getUsername() : "Unknown";

        LOGGER.atInfo().log("[DungeonDeathSystem] Dungeoneer '%s' died in dungeon '%s' (relicHolder: %s)",
                playerName, dungeoneer.getDungeonId(), dungeoneer.isRelicHolder());

        // cancel death immediately - must use store for synchronous removal
        deathComponent.setShowDeathMenu(false);
        commandBuffer.removeComponent(ref, DeathComponent.getComponentType());

        if (dungeoneer.isRelicHolder()) {
            handleRelicHolderDeath(ref, dungeoneer, commandBuffer);
        } else {
            handleDungeoneerDeath(ref, dungeoneer, commandBuffer);
        }
    }

    private void handleRelicHolderDeath(Ref<EntityStore> deadPlayerRef, DungeoneerComponent dungeoneer,
            ComponentAccessor<EntityStore> accessor) {

        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        if (dungeonRef == null || !dungeonRef.isValid()) {
            LOGGER.atWarning().log("[DungeonDeathSystem] Relic holder died but dungeon ref invalid");
            respawnPlayer(deadPlayerRef, dungeoneer.getSpawnPosition(), accessor, "You died! Dungeon reset.");
            return;
        }

        DungeonComponent dungeon = accessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            respawnPlayer(deadPlayerRef, dungeoneer.getSpawnPosition(), accessor, "You died! Dungeon reset.");
            return;
        }

        LOGGER.atInfo().log("[DungeonDeathSystem] Relic holder died! Resetting dungeon '%s' and teleporting %d dungeoneers",
                dungeon.getDungeonId(), dungeon.getDungeoneerRefs().size());

        // reset dungeon via singleton
        DungeonModule.get().getDungeonService().reset(dungeonRef, accessor);

        Vector3d spawnPosition = dungeon.getSpawnPosition();

        // Teleport ALL dungeoneers back to spawn
        for (Ref<EntityStore> dungeoneerRef : dungeon.getDungeoneerRefs()) {
            if (!dungeoneerRef.isValid()) continue;

            DungeoneerComponent otherDungeoneer = accessor.getComponent(dungeoneerRef, DungeoneerComponent.getComponentType());
            if (otherDungeoneer == null) continue;

            // Reset relic holder status
            otherDungeoneer.setRelicHolder(false);

            // Teleport to spawn
            String message = dungeoneerRef.equals(deadPlayerRef)
                    ? "You died! The dungeon has been reset."
                    : "The relic holder died! The dungeon has been reset.";

            respawnPlayer(dungeoneerRef, spawnPosition, accessor, message);
        }

        // Clear artifact holder
        dungeon.setArtifactHolderRef(null);
    }

    private void handleDungeoneerDeath(Ref<EntityStore> deadPlayerRef, DungeoneerComponent dungeoneer,
            ComponentAccessor<EntityStore> accessor) {

        Vector3d spawnPosition = dungeoneer.getSpawnPosition();
        respawnPlayer(deadPlayerRef, spawnPosition, accessor, "You died! Respawning at dungeon entrance.");
    }

    private void respawnPlayer(Ref<EntityStore> playerRef, Vector3d spawnPosition,
            ComponentAccessor<EntityStore> accessor, String message) {

        // restore health first
        EntityStatMap statMap = accessor.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
            statMap.setStatValue(DefaultEntityStatTypes.getHealth(), health.getMax());
        }

        // teleport
        Transform spawnTransform = new Transform(
                spawnPosition.getX(),
                spawnPosition.getY(),
                spawnPosition.getZ(),
                0, 0, 0
        );
        Teleport teleport = Teleport.createForPlayer(null, spawnTransform);
        accessor.addComponent(playerRef, Teleport.getComponentType(), teleport);

        PlayerRef playerRefComponent = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent != null) {
            playerRefComponent.sendMessage(Message.raw(message));
        }

        LOGGER.atInfo().log("[DungeonDeathSystem] Respawned player at %s", spawnPosition);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, DeathComponent oldComponent,
            @Nonnull DeathComponent newComponent, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Not used
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Not used
    }
}

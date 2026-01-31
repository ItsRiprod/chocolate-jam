package com.chocolate.machine.dungeon;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.utils.DungeonFinder;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

// handles dungeon lifecycle: registration, activation, spawner control
public class DungeonService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final SpawnableRegistry spawnableRegistry;

    public DungeonService() {
        this.spawnableRegistry = SpawnableRegistry.getInstance();
    }

    // flood-fill to find and link all spawners to dungeon
    public int registerDungeon(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        DungeonComponent dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            LOGGER.atWarning().log("Cannot register dungeon: entity has no DungeonComponent");
            return 0;
        }

        if (dungeon.isRegistered()) {
            LOGGER.atWarning().log("Dungeon already registered, skipping spawner registration");
            return dungeon.getSpawnerCount();
        }

        TransformComponent dungeonTransform = componentAccessor.getComponent(dungeonRef,
                TransformComponent.getComponentType());
        if (dungeonTransform == null) {
            LOGGER.atWarning().log("Cannot register dungeon: entity has no TransformComponent");
            return 0;
        }

        int registeredCount = 0;

        List<Ref<EntityStore>> nearbySpawners = EntityFloodFill.floodFillSpawners(
                dungeonRef, componentAccessor, SpawnerComponent.getComponentType());

        for (Ref<EntityStore> spawnerRef : nearbySpawners) {
            SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef,
                    SpawnerComponent.getComponentType());

            if (spawner == null) {
                continue;
            }

            dungeon.addSpawnerRef(spawnerRef);
            registerSpawnerAction(spawnerRef, spawner, componentAccessor);

            registeredCount++;
            LOGGER.atFine().log("Registered spawner '%s' to dungeon", spawner.getExecutionId());
        }

        dungeon.setRegistered(true);
        LOGGER.atInfo().log("Dungeon registration complete: %d spawners registered", registeredCount);

        return registeredCount;
    }

    /**
     * Result of checking for nearby dungeons that should be merged.
     */
    public static class MergeResult {
        /** The primary dungeon to use (has BossRoom, or was chosen as primary) */
        public final Ref<EntityStore> primaryDungeonRef;
        /** Whether any merging occurred */
        public final boolean merged;
        /** Whether there was a conflict (multiple BossRooms) */
        public final boolean hasConflict;

        public MergeResult(Ref<EntityStore> primaryDungeonRef, boolean merged, boolean hasConflict) {
            this.primaryDungeonRef = primaryDungeonRef;
            this.merged = merged;
            this.hasConflict = hasConflict;
        }
    }

    /**
     * Check for and merge nearby dungeon networks within MERGE_RANGE (50 blocks).
     * Prefers the dungeon with a configured BossRoom (non-empty dungeonId).
     * If both have BossRooms, logs an error with both locations.
     *
     * @param dungeonRef The dungeon to check from
     * @param store Store for component removal
     * @return MergeResult with the primary dungeon to use
     */
    public MergeResult checkAndMergeDungeons(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> store) {

        DungeonComponent dungeon = store.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return new MergeResult(dungeonRef, false, false);
        }

        // Find other dungeons within merge range
        List<Ref<EntityStore>> nearbyDungeons = DungeonFinder.findDungeonsToMerge(dungeonRef, store);

        if (nearbyDungeons.isEmpty()) {
            return new MergeResult(dungeonRef, false, false);
        }

        // Determine which dungeon has a configured BossRoom (non-empty dungeonId)
        boolean thisHasBossRoom = !dungeon.getDungeonId().isEmpty();
        TransformComponent thisTransform = store.getComponent(dungeonRef, TransformComponent.getComponentType());
        Vector3d thisPosition = thisTransform != null ? thisTransform.getPosition() : new Vector3d(0, 0, 0);

        Ref<EntityStore> primaryRef = dungeonRef;
        DungeonComponent primaryDungeon = dungeon;
        boolean hasConflict = false;

        for (Ref<EntityStore> otherRef : nearbyDungeons) {
            if (!otherRef.isValid()) continue;

            DungeonComponent otherDungeon = store.getComponent(otherRef, DungeonComponent.getComponentType());
            if (otherDungeon == null) continue;

            boolean otherHasBossRoom = !otherDungeon.getDungeonId().isEmpty();
            TransformComponent otherTransform = store.getComponent(otherRef, TransformComponent.getComponentType());
            Vector3d otherPosition = otherTransform != null ? otherTransform.getPosition() : new Vector3d(0, 0, 0);

            // check for duplicate paste - exact same position means duplicate entity
            double distSq = thisPosition.distanceSquaredTo(otherPosition);
            if (distSq < 1.0) {
                // same position - this is a duplicate, keep the existing one (other is already registered)
                if (otherDungeon.isRegistered()) {
                    LOGGER.atInfo().log("[DungeonService] Duplicate dungeon detected at same position, removing newer entity");
                    store.removeComponent(dungeonRef, DungeonComponent.getComponentType());
                    return new MergeResult(otherRef, true, false);
                }
                // neither registered yet, keep this one
                LOGGER.atInfo().log("[DungeonService] Duplicate dungeon detected at same position, removing duplicate");
                store.removeComponent(otherRef, DungeonComponent.getComponentType());
                continue;
            }

            // both have BossRooms but different positions - actual conflict
            if (thisHasBossRoom && otherHasBossRoom) {
                LOGGER.atSevere().log(
                        "[DungeonService] ERROR: Two BossRooms within %.0f blocks! " +
                        "BossRoom 1 '%s' at (%.1f, %.1f, %.1f), " +
                        "BossRoom 2 '%s' at (%.1f, %.1f, %.1f). " +
                        "These dungeons will NOT be merged - fix the prefab placement!",
                        DungeonFinder.MERGE_RANGE,
                        dungeon.getDungeonId(),
                        thisPosition.getX(), thisPosition.getY(), thisPosition.getZ(),
                        otherDungeon.getDungeonId(),
                        otherPosition.getX(), otherPosition.getY(), otherPosition.getZ());
                hasConflict = true;
                continue;
            }

            // Decide which is primary: prefer the one with BossRoom
            Ref<EntityStore> secondaryRef;
            DungeonComponent secondaryDungeon;

            if (otherHasBossRoom && !thisHasBossRoom) {
                // Other has BossRoom, make it primary
                primaryRef = otherRef;
                primaryDungeon = otherDungeon;
                secondaryRef = dungeonRef;
                secondaryDungeon = dungeon;
                thisHasBossRoom = true; // Now we have a BossRoom
            } else {
                // This is primary (either has BossRoom or neither does)
                secondaryRef = otherRef;
                secondaryDungeon = otherDungeon;
            }

            // Merge: copy spawners from secondary to primary
            LOGGER.atInfo().log("[DungeonService] Merging dungeon at (%.1f, %.1f, %.1f) into '%s'",
                    otherPosition.getX(), otherPosition.getY(), otherPosition.getZ(),
                    primaryDungeon.getDungeonId().isEmpty() ? "(unnamed)" : primaryDungeon.getDungeonId());

            int spawnersMerged = secondaryDungeon.getSpawnerCount();
            for (Ref<EntityStore> spawnerRef : secondaryDungeon.getSpawnerRefs()) {
                if (spawnerRef.isValid()) {
                    primaryDungeon.addSpawnerRef(spawnerRef);
                }
            }

            // Copy entrance ref if secondary has one and primary doesn't
            if (primaryDungeon.getEntranceRef() == null && secondaryDungeon.getEntranceRef() != null) {
                primaryDungeon.setEntranceRef(secondaryDungeon.getEntranceRef());
            }

            // Clear secondary's refs so onEntityRemove doesn't clean up spawners that now belong to primary
            secondaryDungeon.clearSpawnerRefs();
            secondaryDungeon.clearDungeoneerRefs();
            secondaryDungeon.setEntranceRef(null);

            // Remove DungeonComponent from secondary entity
            store.removeComponent(secondaryRef, DungeonComponent.getComponentType());

            LOGGER.atInfo().log("[DungeonService] Merged %d spawners, removed secondary DungeonComponent",
                    spawnersMerged);
        }

        return new MergeResult(primaryRef, !nearbyDungeons.isEmpty() && !hasConflict, hasConflict);
    }

    public void activate(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nullable Ref<EntityStore> artifactHolderRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        DungeonComponent dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            LOGGER.atWarning().log("Cannot activate: entity has no DungeonComponent");
            return;
        }

        if (dungeon.isActive()) {
            LOGGER.atFine().log("Dungeon already active");
            return;
        }

        // auto-register if needed
        if (!dungeon.isRegistered()) {
            LOGGER.atInfo().log("Dungeon not registered, registering spawners first...");
            registerDungeon(dungeonRef, componentAccessor);
        }

        dungeon.setActive(true);
        dungeon.setArtifactHolderRef(artifactHolderRef);

        int activatedCount = 0;
        for (Ref<EntityStore> spawnerRef : dungeon.getSpawnerRefs()) {
            if (!spawnerRef.isValid()) {
                continue;
            }

            if (activateSpawner(spawnerRef, componentAccessor)) {
                activatedCount++;
            }
        }

        LOGGER.atInfo().log("Dungeon activated: %d/%d spawners triggered",
                activatedCount, dungeon.getSpawnerCount());
    }

    /**
     * Called when a player picks up the relic in a dungeon.
     * Marks them as relic holder and activates the dungeon.
     *
     * @param playerRef The player who picked up the relic
     * @param componentAccessor Component accessor
     * @return true if successful, false if player is not a dungeoneer
     */
    public boolean relicPickedUp(
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        DungeoneerComponent dungeoneer = componentAccessor.getComponent(playerRef, DungeoneerComponent.getComponentType());
        if (dungeoneer == null) {
            LOGGER.atWarning().log("Cannot pick up relic: player has no DungeoneerComponent");
            return false;
        }

        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        if (dungeonRef == null || !dungeonRef.isValid()) {
            LOGGER.atWarning().log("Cannot pick up relic: dungeoneer has no valid dungeon reference");
            return false;
        }

        DungeonComponent dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            LOGGER.atWarning().log("Cannot pick up relic: dungeon component not found");
            return false;
        }

        // Mark player as relic holder
        dungeoneer.setRelicHolder(true);

        // Log the event
        PlayerRef playerRefComp = componentAccessor.getComponent(playerRef, PlayerRef.getComponentType());
        String playerName = playerRefComp != null ? playerRefComp.getUsername() : "Unknown";
        LOGGER.atInfo().log("Player '%s' picked up relic in dungeon '%s'", playerName, dungeon.getDungeonId());

        // Activate the dungeon
        activate(dungeonRef, playerRef, componentAccessor);

        return true;
    }

    /**
     * Called when a relic holder drops or loses the relic.
     * Transfers relic to another player or resets if no one else can hold it.
     *
     * @param playerRef The player who lost the relic
     * @param componentAccessor Component accessor
     */
    public void relicLost(
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        DungeoneerComponent dungeoneer = componentAccessor.getComponent(playerRef, DungeoneerComponent.getComponentType());
        if (dungeoneer == null || !dungeoneer.isRelicHolder()) {
            return;
        }

        Ref<EntityStore> dungeonRef = dungeoneer.getDungeonRef();
        if (dungeonRef == null || !dungeonRef.isValid()) {
            dungeoneer.setRelicHolder(false);
            return;
        }

        DungeonComponent dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            dungeoneer.setRelicHolder(false);
            return;
        }

        // Clear relic holder status
        dungeoneer.setRelicHolder(false);

        PlayerRef playerRefComp = componentAccessor.getComponent(playerRef, PlayerRef.getComponentType());
        String playerName = playerRefComp != null ? playerRefComp.getUsername() : "Unknown";
        LOGGER.atInfo().log("Player '%s' lost relic in dungeon '%s'", playerName, dungeon.getDungeonId());

        // For now, reset the dungeon when relic is lost
        // TODO: Could implement relic transfer to another dungeoneer
        reset(dungeonRef, componentAccessor);
    }

    public void deactivate(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        DungeonComponent dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            LOGGER.atWarning().log("Cannot deactivate: entity has no DungeonComponent");
            return;
        }

        if (!dungeon.isActive()) {
            LOGGER.atFine().log("Dungeon already inactive");
            return;
        }

        int deactivatedCount = 0;
        for (Ref<EntityStore> spawnerRef : dungeon.getSpawnerRefs()) {
            if (!spawnerRef.isValid()) {
                continue;
            }

            if (deactivateSpawner(spawnerRef, componentAccessor)) {
                deactivatedCount++;
            }
        }

        dungeon.setActive(false);

        LOGGER.atInfo().log("Dungeon deactivated: %d spawners despawned", deactivatedCount);
    }

    // deactivate and clear artifact holder (player died or dropped artifact)
    public void reset(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        deactivate(dungeonRef, componentAccessor);
        LOGGER.atInfo().log("Dungeon reset");
    }

    public boolean activateSpawner(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            LOGGER.atWarning().log("Cannot activate: entity has no SpawnerComponent");
            return false;
        }

        String executionId = spawner.getExecutionId();
        if (executionId.isEmpty()) {
            LOGGER.atWarning().log("Spawner has empty executionId, skipping activation");
            return false;
        }

        
        Spawnable spawnable = spawnableRegistry.get(executionId);
        if (spawnable == null) {
            LOGGER.atWarning().log("No spawnable registered for executionId: %s", executionId);
            return false;
        }
        
        spawnable.activate(spawnerRef, componentAccessor);
        
        spawner.setActive(true);

        LOGGER.atFine().log("Activated spawner '%s'", executionId);
        return true;
    }

    public boolean deactivateSpawner(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            LOGGER.atWarning().log("Cannot deactivate: entity has no SpawnerComponent");
            return false;
        }

        String executionId = spawner.getExecutionId();
        if (executionId.isEmpty()) {
            LOGGER.atFine().log("Spawner has empty executionId, nothing to deactivate");
            return false;
        }

        spawner.setActive(false);

        Spawnable spawnable = spawnableRegistry.get(executionId);
        if (spawnable == null) {
            LOGGER.atWarning().log("No spawnable registered for executionId: %s", executionId);
            return false;
        }

        spawnable.deactivate(spawnerRef, componentAccessor);

        LOGGER.atFine().log("Deactivated spawner '%s'", executionId);
        return true;
    }

    public boolean resetSpawner(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            return false;
        }

        String executionId = spawner.getExecutionId();
        if (executionId.isEmpty()) {
            return false;
        }

        Spawnable spawnable = spawnableRegistry.get(executionId);
        if (spawnable == null) {
            return false;
        }

        spawnable.reset(spawnerRef, componentAccessor);
        spawner.setActive(true);

        LOGGER.atFine().log("Reset spawner '%s'", executionId);
        return true;
    }

    // change spawner action type, handles cleanup
    public boolean setSpawnerAction(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull String newExecutionId,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            LOGGER.atWarning().log("Cannot set action: entity has no SpawnerComponent");
            return false;
        }

        spawner.setActive(false);
        String oldExecutionId = spawner.getExecutionId();

        // cleanup old action
        if (!oldExecutionId.isEmpty()) {
            Spawnable oldSpawnable = spawnableRegistry.get(oldExecutionId);
            if (oldSpawnable != null) {
                oldSpawnable.cleanup(spawnerRef, componentAccessor);
                LOGGER.atFine().log("Cleaned up old action '%s'", oldExecutionId);
            }
        }

        spawner.setExecutionId(newExecutionId);

        // register new action
        if (!newExecutionId.isEmpty()) {
            Spawnable newSpawnable = spawnableRegistry.get(newExecutionId);
            if (newSpawnable != null) {
                newSpawnable.register(spawnerRef, componentAccessor);
                LOGGER.atFine().log("Registered new action '%s'", newExecutionId);
                return true;
            } else {
                LOGGER.atWarning().log("No spawnable registered for executionId: %s", newExecutionId);
                return false;
            }
        }

        return true;
    }

    public void cleanupSpawner(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        SpawnerComponent spawner = componentAccessor.getComponent(spawnerRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            return;
        }

        String executionId = spawner.getExecutionId();
        if (!executionId.isEmpty()) {
            Spawnable spawnable = spawnableRegistry.get(executionId);
            if (spawnable != null) {
                spawnable.cleanup(spawnerRef, componentAccessor);
                spawner.setActive(false);
                LOGGER.atFine().log("Cleaned up spawner '%s'", executionId);
            }
        }
    }

    private void registerSpawnerAction(
            @Nonnull Ref<EntityStore> spawnerRef,
            @Nonnull SpawnerComponent spawner,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        String executionId = spawner.getExecutionId();
        if (executionId.isEmpty()) {
            LOGGER.atFine().log("Spawner has no executionId, skipping action registration");
            return;
        }

        Spawnable spawnable = spawnableRegistry.get(executionId);
        if (spawnable == null) {
            LOGGER.atWarning().log("No spawnable registered for executionId: %s", executionId);
            return;
        }

        spawnable.register(spawnerRef, componentAccessor);
    }
}

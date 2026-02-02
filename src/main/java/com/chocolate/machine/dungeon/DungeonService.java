package com.chocolate.machine.dungeon;

import com.chocolate.machine.dungeon.component.DungeonBlockEntry;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.spawnable.Spawnable;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.utils.DungeonFinder;
import com.chocolate.machine.utils.EntityFloodFill;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class DungeonService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DUNGEON_BLOCK_PREFIX = "CM_";
    private static final String STATE_BLOCK_PREFIX = "*" + DUNGEON_BLOCK_PREFIX;
    private static final double BLOCK_SCAN_RADIUS = 200.0;

    private final SpawnableRegistry spawnableRegistry;

    private static boolean isDungeonBlock(@Nonnull String blockId) {
        return blockId.startsWith(DUNGEON_BLOCK_PREFIX) || blockId.startsWith(STATE_BLOCK_PREFIX);
    }

    // *CM_Torch_On -> CM_Torch
    private static String getBaseBlockId(@Nonnull String blockId) {
        if (blockId.startsWith("*")) {
            // Format: *BaseId_StateName - strip leading * and trailing _StateName
            String withoutStar = blockId.substring(1);
            int lastUnderscore = withoutStar.lastIndexOf('_');
            if (lastUnderscore > DUNGEON_BLOCK_PREFIX.length()) {
                // Only strip if there's content after CM_
                return withoutStar.substring(0, lastUnderscore);
            }
            return withoutStar;
        }
        return blockId;
    }

    public DungeonService() {
        this.spawnableRegistry = SpawnableRegistry.getInstance();
    }

    public int registerDungeon(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // Try to get world from component accessor
        World world = componentAccessor.getExternalData().getWorld();
        return registerDungeon(dungeonRef, componentAccessor, world);
    }

    public int registerDungeon(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor,
            @Nullable World world) {

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

        // Crash recovery: if dungeon was active but we're re-registering, reset state
        if (dungeon.isActive()) {
            LOGGER.atWarning().log("Dungeon was active but unregistered - resetting state");
            dungeon.setActive(false);
            dungeon.setArtifactHolderRef(null);
            dungeon.clearDungeoneerRefs();
        }

        // Check for nearby dungeons to merge
        MergeResult mergeResult = checkAndMergeDungeons(dungeonRef, componentAccessor);
        if (!mergeResult.primaryDungeonRef.equals(dungeonRef)) {
            LOGGER.atInfo().log("Dungeon was merged into another, skipping registration");
            return 0;
        }
        if (mergeResult.merged) {
            LOGGER.atInfo().log("Merged with nearby dungeon network");
        }

        // Re-fetch dungeon in case merge modified it
        dungeon = componentAccessor.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            return 0;
        }

        int registeredCount = 0;

        List<Ref<EntityStore>> nearbySpawners = EntityFloodFill.floodFillSpawners(
                dungeonRef, componentAccessor, SpawnerComponent.getComponentType(), BLOCK_SCAN_RADIUS);

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

        // Register dungeon blocks (CM_* blocks)
        int blockCount = 0;
        if (world != null) {
            Vector3d center = dungeonTransform.getPosition();
            blockCount = registerDungeonBlocks(dungeon, world, center);
        } else {
            LOGGER.atWarning().log("Cannot register dungeon blocks: world is null (use registerDungeon with world parameter)");
        }

        // Link entrance
        linkEntrance(dungeonRef, dungeon, componentAccessor);

        dungeon.setRegistered(true);
        LOGGER.atInfo().log("Dungeon registration complete: %d spawners, %d dungeon blocks registered",
                registeredCount, blockCount);

        return registeredCount;
    }

    private boolean linkEntrance(
            @Nonnull Ref<EntityStore> dungeonRef,
            @Nonnull DungeonComponent dungeon,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        String dungeonId = dungeon.getDungeonId();
        if (dungeonId.isEmpty()) {
            LOGGER.atFine().log("Dungeon has no ID, skipping entrance linking");
            return false;
        }

        // Search for entrance entities via flood-fill
        List<Ref<EntityStore>> nearbyEntities = EntityFloodFill.floodFillSpawners(
                dungeonRef, componentAccessor, DungeonEntranceComponent.getComponentType(), BLOCK_SCAN_RADIUS);

        for (Ref<EntityStore> entityRef : nearbyEntities) {
            if (!entityRef.isValid()) continue;

            DungeonEntranceComponent entrance = componentAccessor.getComponent(entityRef,
                    DungeonEntranceComponent.getComponentType());
            if (entrance == null) continue;

            if (entrance.getDungeonId().equals(dungeonId)) {
                dungeon.setEntranceRef(entityRef);
                LOGGER.atInfo().log("Linked entrance to dungeon '%s'", dungeonId);
                return true;
            }
        }

        LOGGER.atWarning().log("No entrance found for dungeon '%s' within radius %.0f",
                dungeonId, BLOCK_SCAN_RADIUS);
        return false;
    }

    public static class MergeResult {
        public final Ref<EntityStore> primaryDungeonRef;
        public final boolean merged;
        public final boolean hasConflict;

        public MergeResult(Ref<EntityStore> primaryDungeonRef, boolean merged, boolean hasConflict) {
            this.primaryDungeonRef = primaryDungeonRef;
            this.merged = merged;
            this.hasConflict = hasConflict;
        }
    }

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

        // Activate dungeon blocks
        int blocksActivated = activateDungeonBlocks(dungeon, componentAccessor);

        LOGGER.atInfo().log("Dungeon activated: %d/%d spawners triggered, %d blocks activated",
                activatedCount, dungeon.getSpawnerCount(), blocksActivated);
    }

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

        // Deactivate dungeon blocks
        int blocksDeactivated = deactivateDungeonBlocks(dungeon, componentAccessor);

        dungeon.setActive(false);

        LOGGER.atInfo().log("Dungeon deactivated: %d spawners despawned, %d blocks deactivated",
                deactivatedCount, blocksDeactivated);
    }

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

        if (!oldExecutionId.isEmpty()) {
            Spawnable oldSpawnable = spawnableRegistry.get(oldExecutionId);
            if (oldSpawnable != null) {
                oldSpawnable.cleanup(spawnerRef, componentAccessor);
                LOGGER.atFine().log("Cleaned up old action '%s'", oldExecutionId);
            }
        }

        spawner.setExecutionId(newExecutionId);

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

    private int registerDungeonBlocks(
            @Nonnull DungeonComponent dungeon,
            @Nonnull World world,
            @Nonnull Vector3d center) {
        int radius = (int) BLOCK_SCAN_RADIUS;
        int registeredCount = 0;

        int minX = (int) center.getX() - radius;
        int maxX = (int) center.getX() + radius;
        int minY = Math.max(0, (int) center.getY() - radius);
        int maxY = Math.min(255, (int) center.getY() + radius);
        int minZ = (int) center.getZ() - radius;
        int maxZ = (int) center.getZ() + radius;

        // Clear existing blocks before re-scanning
        dungeon.clearDungeonBlocks();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    BlockType blockType = chunk.getBlockType(x, y, z);
                    if (blockType == null || blockType.isUnknown()) {
                        continue;
                    }

                    String blockId = blockType.getId();
                    if (blockId != null && isDungeonBlock(blockId)) {
                        // Store the base block ID (strip state prefix/suffix if present)
                        String baseBlockId = getBaseBlockId(blockId);
                        DungeonBlockEntry entry = new DungeonBlockEntry(x, y, z, baseBlockId);
                        dungeon.addDungeonBlock(entry);
                        registeredCount++;

                        LOGGER.atFine().log("Registered dungeon block '%s' (base: '%s') at (%d, %d, %d)",
                                blockId, baseBlockId, x, y, z);
                    }
                }
            }
        }

        if (registeredCount > 0) {
            LOGGER.atInfo().log("Found %d dungeon blocks matching prefix '%s'",
                    registeredCount, DUNGEON_BLOCK_PREFIX);
        }

        return registeredCount;
    }

    private int activateDungeonBlocks(
            @Nonnull DungeonComponent dungeon,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        World world = componentAccessor.getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("Cannot activate dungeon blocks: world is null");
            return 0;
        }

        int activatedCount = 0;
        for (DungeonBlockEntry entry : dungeon.getDungeonBlocks()) {
            if (setBlockState(world, entry.getPosition(), entry.getActiveState())) {
                activatedCount++;
            }
        }

        return activatedCount;
    }

    private int deactivateDungeonBlocks(
            @Nonnull DungeonComponent dungeon,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {

        World world = componentAccessor.getExternalData().getWorld();
        if (world == null) {
            LOGGER.atWarning().log("Cannot deactivate dungeon blocks: world is null");
            return 0;
        }

        int deactivatedCount = 0;
        for (DungeonBlockEntry entry : dungeon.getDungeonBlocks()) {
            if (setBlockState(world, entry.getPosition(), entry.getInactiveState())) {
                deactivatedCount++;
            }
        }

        return deactivatedCount;
    }

    private boolean setBlockState(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull String state) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ()));
        if (chunk == null) {
            LOGGER.atFine().log("Cannot set block state: chunk not loaded at (%d, %d)", pos.getX(), pos.getZ());
            return false;
        }

        BlockType blockType = chunk.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null || blockType.isUnknown()) {
            LOGGER.atFine().log("Cannot set block state: no block at (%d, %d, %d)",
                    pos.getX(), pos.getY(), pos.getZ());
            return false;
        }

        chunk.setBlockInteractionState(pos, blockType, state);
        LOGGER.atFine().log("Set block '%s' at (%d, %d, %d) to state '%s'",
                blockType.getId(), pos.getX(), pos.getY(), pos.getZ(), state);
        return true;
    }
}

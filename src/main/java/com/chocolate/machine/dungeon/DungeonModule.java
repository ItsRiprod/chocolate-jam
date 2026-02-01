package com.chocolate.machine.dungeon;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.component.actions.AxeBladeActionComponent;
import com.chocolate.machine.dungeon.component.actions.HydraulicPressActionComponent;
import com.chocolate.machine.dungeon.component.actions.SkeletonActionComponent;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.dungeon.spawnable.actions.ArcherAction;
import com.chocolate.machine.dungeon.spawnable.actions.ArrowTrap;
import com.chocolate.machine.dungeon.spawnable.actions.AxeBladeTrap;
import com.chocolate.machine.dungeon.spawnable.actions.BruteAction;
import com.chocolate.machine.dungeon.spawnable.actions.GolemAction;
import com.chocolate.machine.dungeon.spawnable.actions.HydraulicPressTrap;
import com.chocolate.machine.dungeon.system.DungeonBossRoomSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.system.System;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

// dungeon module - registers components, systems, and spawnables
public class DungeonModule extends System<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static DungeonModule instance;

    private final DungeonService dungeonService;

    // persistent (serialized)
    private ComponentType<EntityStore, DungeonComponent> dungeonComponentType;
    private ComponentType<EntityStore, DungeoneerComponent> dungeoneerComponentType;
    private ComponentType<EntityStore, DungeonEntranceComponent> dungeonEntranceComponentType;
    private ComponentType<EntityStore, SpawnerComponent> spawnerComponentType;

    // runtime only
    private ComponentType<EntityStore, SkeletonActionComponent> skeletonActionComponentType;
    private ComponentType<EntityStore, AxeBladeActionComponent> axeBladeActionComponentType;
    private ComponentType<EntityStore, HydraulicPressActionComponent> hydraulicPressActionComponentType;

    // resources
    private ResourceType<EntityStore, DungeonBossRoomSystem.PendingDungeoneerResource> pendingDungeoneerResourceType;

    public DungeonModule() {
        instance = this;
        this.dungeonService = new DungeonService();
    }

    public static DungeonModule get() {
        return instance;
    }

    @Override
    public void onSystemRegistered() {
        LOGGER.atInfo().log("Registering dungeon module components...");

        dungeonComponentType = registerComponent(DungeonComponent.class, "Dungeon", DungeonComponent.CODEC);
        DungeonComponent.setComponentType(dungeonComponentType);

        dungeoneerComponentType = registerComponent(DungeoneerComponent.class, "Dungeoneer", DungeoneerComponent.CODEC);
        DungeoneerComponent.setComponentType(dungeoneerComponentType);

        spawnerComponentType = registerComponent(SpawnerComponent.class, "Spawner", SpawnerComponent.CODEC);
        SpawnerComponent.setComponentType(spawnerComponentType);

        dungeonEntranceComponentType = registerComponent(DungeonEntranceComponent.class, "DungeonEntrance", DungeonEntranceComponent.CODEC);
        DungeonEntranceComponent.setComponentType(dungeonEntranceComponentType);

        // Register resources
        pendingDungeoneerResourceType = registerResource(
                DungeonBossRoomSystem.PendingDungeoneerResource.class,
                DungeonBossRoomSystem.PendingDungeoneerResource::new);
        DungeonBossRoomSystem.setPendingResourceType(pendingDungeoneerResourceType);

        registerDefaultSpawnables();

        LOGGER.atInfo().log("Dungeon module registered successfully");
    }

    @Override
    public void onSystemUnregistered() {
        LOGGER.atInfo().log("Unregistering dungeon module...");
        SpawnableRegistry.getInstance().clear();
    }

    private void registerDefaultSpawnables() {
        SpawnableRegistry registry = SpawnableRegistry.getInstance();

        skeletonActionComponentType = registerComponent(SkeletonActionComponent.class, SkeletonActionComponent::new);
        SkeletonActionComponent.setComponentType(skeletonActionComponentType);

        axeBladeActionComponentType = registerComponent(AxeBladeActionComponent.class, AxeBladeActionComponent::new);
        AxeBladeActionComponent.setComponentType(axeBladeActionComponentType);

        hydraulicPressActionComponentType = registerComponent(HydraulicPressActionComponent.class, HydraulicPressActionComponent::new);
        HydraulicPressActionComponent.setComponentType(hydraulicPressActionComponentType);

        // traps
        registry.register(new AxeBladeTrap());
        registry.register(new HydraulicPressTrap());
        registry.register(new ArrowTrap());

        // entities
        registry.register(new GolemAction());
        registry.register(new ArcherAction());
        registry.register(new BruteAction());

        LOGGER.atInfo().log("Registered %d default spawnables", registry.getRegisteredIds().size());
    }

    @Nonnull
    public DungeonService getDungeonService() {
        return dungeonService;
    }

    @Nonnull
    public ComponentType<EntityStore, DungeonComponent> getDungeonComponentType() {
        return dungeonComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, DungeoneerComponent> getDungeoneerComponentType() {
        return dungeoneerComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, SpawnerComponent> getSpawnerComponentType() {
        return spawnerComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, SkeletonActionComponent> getSkeletonActionComponentType() {
        return skeletonActionComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, AxeBladeActionComponent> getAxeBladeActionComponentType() {
        return axeBladeActionComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, HydraulicPressActionComponent> getHydraulicPressActionComponentType() {
        return hydraulicPressActionComponentType;
    }
}

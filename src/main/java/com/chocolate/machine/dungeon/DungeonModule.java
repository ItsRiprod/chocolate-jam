package com.chocolate.machine.dungeon;

import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeonEntranceComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.dungeon.component.SpawnedEntityComponent;
import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.component.actions.BigFreakingHammerComponent;
import com.chocolate.machine.dungeon.component.actions.HydraulicPressActionComponent;
import com.chocolate.machine.dungeon.component.actions.LaserBeamComponent;
import com.chocolate.machine.dungeon.component.actions.LaserTrapActionComponent;
import com.chocolate.machine.dungeon.component.actions.SawBladeComponent;
import com.chocolate.machine.dungeon.component.actions.SkeletonActionComponent;
import com.chocolate.machine.dungeon.spawnable.SpawnableRegistry;
import com.chocolate.machine.dungeon.spawnable.actions.ArcherAction;
import com.chocolate.machine.dungeon.spawnable.actions.HammerTrap;
import com.chocolate.machine.dungeon.spawnable.actions.BeamTrap;
import com.chocolate.machine.dungeon.spawnable.actions.BruteAction;
import com.chocolate.machine.dungeon.spawnable.actions.GolemAction;
import com.chocolate.machine.dungeon.spawnable.actions.HydraulicPressTrap;
import com.chocolate.machine.dungeon.spawnable.actions.LaserTrap;
import com.chocolate.machine.dungeon.spawnable.actions.SawBladeTrap;
import com.chocolate.machine.dungeon.system.DungeonBossRoomSystem;
import com.chocolate.machine.dungeon.interaction.PedestalBlockInteraction;
import com.chocolate.machine.dungeon.interaction.PedestalTriggerInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.system.System;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class DungeonModule extends System<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static DungeonModule instance;

    private final DungeonService dungeonService;

    private ComponentType<EntityStore, DungeonComponent> dungeonComponentType;
    private ComponentType<EntityStore, DungeoneerComponent> dungeoneerComponentType;
    private ComponentType<EntityStore, DungeonEntranceComponent> dungeonEntranceComponentType;
    private ComponentType<EntityStore, SpawnerComponent> spawnerComponentType;

    private ComponentType<EntityStore, SkeletonActionComponent> skeletonActionComponentType;

    private ComponentType<EntityStore, BigFreakingHammerComponent> hammerActionComponent;
    private ComponentType<EntityStore, HydraulicPressActionComponent> hydraulicPressActionComponentType;
    private ComponentType<EntityStore, LaserTrapActionComponent> laserTrapActionComponentType;
    private ComponentType<EntityStore, LaserBeamComponent> laserBeamComponentType;
    private ComponentType<EntityStore, SawBladeComponent> sawBladeComponentType;
    private ComponentType<EntityStore, SpawnedEntityComponent> spawnedEntityComponentType;

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

        spawnedEntityComponentType = registerComponent(SpawnedEntityComponent.class, SpawnedEntityComponent::new);
        SpawnedEntityComponent.setComponentType(spawnedEntityComponentType);

        // Register resources
        pendingDungeoneerResourceType = registerResource(
                DungeonBossRoomSystem.PendingDungeoneerResource.class,
                DungeonBossRoomSystem.PendingDungeoneerResource::new);
        DungeonBossRoomSystem.setPendingResourceType(pendingDungeoneerResourceType);

        Interaction.CODEC.register("CM_PedestalTrigger", PedestalTriggerInteraction.class, PedestalTriggerInteraction.CODEC);
        Interaction.CODEC.register("CM_PedestalBlock", PedestalBlockInteraction.class, PedestalBlockInteraction.CODEC);

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

        hammerActionComponent = registerComponent(BigFreakingHammerComponent.class, "Hammer", BigFreakingHammerComponent.CODEC);
        BigFreakingHammerComponent.setComponentType(hammerActionComponent);

        hydraulicPressActionComponentType = registerComponent(HydraulicPressActionComponent.class, "HydraulicPress", HydraulicPressActionComponent.CODEC);
        HydraulicPressActionComponent.setComponentType(hydraulicPressActionComponentType);

        laserTrapActionComponentType = registerComponent(LaserTrapActionComponent.class, "LaserTrap", LaserTrapActionComponent.CODEC);
        LaserTrapActionComponent.setComponentType(laserTrapActionComponentType);

        laserBeamComponentType = registerComponent(LaserBeamComponent.class, "LaserBeam", LaserBeamComponent.CODEC);
        LaserBeamComponent.setComponentType(laserBeamComponentType);

        sawBladeComponentType = registerComponent(SawBladeComponent.class, "SawBlade", SawBladeComponent.CODEC);
        SawBladeComponent.setComponentType(sawBladeComponentType);

        registry.register(new HammerTrap());
        registry.register(new SawBladeTrap());
        registry.register(new HydraulicPressTrap());
        registry.register(new LaserTrap());
        registry.register(new BeamTrap());

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
    public ComponentType<EntityStore, BigFreakingHammerComponent> getHammerActionComponent() {
        return hammerActionComponent;
    }

    @Nonnull
    public ComponentType<EntityStore, HydraulicPressActionComponent> getHydraulicPressActionComponentType() {
        return hydraulicPressActionComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, LaserTrapActionComponent> getLaserTrapActionComponentType() {
        return laserTrapActionComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, LaserBeamComponent> getLaserBeamComponentType() {
        return laserBeamComponentType;
    }

    @Nonnull
    public ComponentType<EntityStore, SawBladeComponent> getSawBladeComponentType() {
        return sawBladeComponentType;
    }
}

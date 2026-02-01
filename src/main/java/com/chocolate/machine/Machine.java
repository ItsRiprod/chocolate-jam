package com.chocolate.machine;

import com.chocolate.machine.command.ChocolateCommand;
import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.system.DungeonAreaSystem;
import com.chocolate.machine.dungeon.system.DungeonBossRoomSystem;
import com.chocolate.machine.dungeon.system.DungeonDeathSystem;
import com.chocolate.machine.dungeon.system.DungeoneerCleanupSystem;
import com.chocolate.machine.dungeon.system.DungeonTickSystem;
import com.chocolate.machine.dungeon.system.DungeonRegistrationSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class Machine extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DungeonModule dungeonModule;

    public Machine(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Chocolate Machine plugin initializing (version %s)...",
                this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Chocolate Machine...");

        dungeonModule = new DungeonModule();
        EntityStore.REGISTRY.registerSystem(dungeonModule);

        registerDungeonSystems();

        setupCommands();

        LOGGER.atInfo().log("Chocolate Machine setup complete!");
    }

    private void registerDungeonSystems() {
        getEntityStoreRegistry().registerSystem(new DungeonRegistrationSystem());

        getEntityStoreRegistry().registerSystem(new DungeonBossRoomSystem());

        getEntityStoreRegistry().registerSystem(new DungeonAreaSystem());

        getEntityStoreRegistry().registerSystem(new DungeonDeathSystem());

        getEntityStoreRegistry().registerSystem(new DungeoneerCleanupSystem());

        getEntityStoreRegistry().registerSystem(new DungeonTickSystem());
    }

    private void setupCommands() {
        getCommandRegistry().registerCommand(new ChocolateCommand());
    }

    public DungeonModule getDungeonModule() {
        return dungeonModule;
    }
}

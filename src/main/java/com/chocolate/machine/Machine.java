package com.chocolate.machine;

import com.chocolate.machine.command.ChocolateCommand;
import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.system.DungeonAreaSystem;
import com.chocolate.machine.dungeon.system.DungeonBossRoomSystem;
import com.chocolate.machine.dungeon.system.DungeonDeathSystem;
import com.chocolate.machine.dungeon.system.DungeoneerCleanupSystem;
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

        // register dungeon module (components + spawnables)
        dungeonModule = new DungeonModule();
        EntityStore.REGISTRY.registerSystem(dungeonModule);

        // register dungeon systems
        registerDungeonSystems();

        setupCommands();

        LOGGER.atInfo().log("Chocolate Machine setup complete!");
    }

    private void registerDungeonSystems() {
        // Registration system - handles dungeon setup when DungeonComponent spawns
        getEntityStoreRegistry().registerSystem(new DungeonRegistrationSystem());
        LOGGER.atInfo().log("Registered DungeonRegistrationSystem");

        // Boss room system - detects players entering boss room, adds DungeoneerComponent
        getEntityStoreRegistry().registerSystem(new DungeonBossRoomSystem());
        LOGGER.atInfo().log("Registered DungeonBossRoomSystem");

        // Area system - detects players leaving entrance (escape detection)
        getEntityStoreRegistry().registerSystem(new DungeonAreaSystem());
        LOGGER.atInfo().log("Registered DungeonAreaSystem");

        // Death system - intercepts player deaths in dungeon
        getEntityStoreRegistry().registerSystem(new DungeonDeathSystem());
        LOGGER.atInfo().log("Registered DungeonDeathSystem");

        // Cleanup system - handles player disconnect while in dungeon
        getEntityStoreRegistry().registerSystem(new DungeoneerCleanupSystem());
        LOGGER.atInfo().log("Registered DungeoneerCleanupSystem");
    }

    private void setupCommands() {
        getCommandRegistry().registerCommand(new ChocolateCommand());
        LOGGER.atInfo().log("Registered /chocolate command");
    }

    public DungeonModule getDungeonModule() {
        return dungeonModule;
    }
}

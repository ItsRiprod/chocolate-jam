package com.chocolate.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;


/**
 * Main plugin class for modmod.
 */

// add another comment
public class Plugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public Plugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Plugin initializing...", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // setup code

        LOGGER.atInfo().log("Plugin setup complete!");
    }
}

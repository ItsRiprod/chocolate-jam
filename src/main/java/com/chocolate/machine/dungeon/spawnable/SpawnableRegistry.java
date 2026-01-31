package com.chocolate.machine.dungeon.spawnable;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// maps execution IDs to spawnable implementations
public class SpawnableRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final SpawnableRegistry INSTANCE = new SpawnableRegistry();

    private final Map<String, Spawnable> registry = new HashMap<>();

    private SpawnableRegistry() {}

    @Nonnull
    public static SpawnableRegistry getInstance() {
        return INSTANCE;
    }

    public void register(@Nonnull Spawnable spawnable) {
        String id = spawnable.getId();
        if (registry.containsKey(id)) {
            throw new IllegalArgumentException("Spawnable with ID '" + id + "' is already registered");
        }
        registry.put(id, spawnable);
        LOGGER.atInfo().log("Registered spawnable: %s", id);
    }

    public boolean unregister(@Nonnull String id) {
        Spawnable removed = registry.remove(id);
        if (removed != null) {
            LOGGER.atInfo().log("Unregistered spawnable: %s", id);
            return true;
        }
        return false;
    }

    @Nullable
    public Spawnable get(@Nonnull String id) {
        return registry.get(id);
    }

    public boolean isRegistered(@Nonnull String id) {
        return registry.containsKey(id);
    }

    @Nonnull
    public Set<String> getRegisteredIds() {
        return Set.copyOf(registry.keySet());
    }

    public void clear() {
        registry.clear();
        LOGGER.atInfo().log("Cleared spawnable registry");
    }
}

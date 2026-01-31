package com.chocolate.machine.command;

import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public class ChocolateDungeonCommand extends AbstractCommand {

    public ChocolateDungeonCommand() {
        super("dungeon", "Chocolate Machine dungeon management commands");
        addAliases("d", "dn");
        setPermissionGroup(GameMode.Creative);

        addSubCommand(new DungeonInfoCommand());
        addSubCommand(new DungeonToggleCommand());
        addSubCommand(new DungeonRegisterCommand());
        addSubCommand(new DungeonDiscardCommand());
        addSubCommand(new DungeonTriggerCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Chocolate Machine Dungeon Commands:"));
        context.sendMessage(Message.raw("/chocolate dungeon info - Show dungeon info at your location"));
        context.sendMessage(Message.raw("/chocolate dungeon toggle - Toggle dungeon Active state"));
        context.sendMessage(Message.raw("/chocolate dungeon register - Re-register nearest dungeon's spawners"));
        context.sendMessage(Message.raw("/chocolate dungeon discard - Discard/clear dungeon state (for prefab editing)"));
        context.sendMessage(Message.raw("/chocolate dungeon trigger - Become relic holder / simulate escape"));
        return CompletableFuture.completedFuture(null);
    }
}

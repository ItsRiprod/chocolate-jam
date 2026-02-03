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
        addSubCommand(new DungeonDeleteCommand());
        addSubCommand(new DungeonAssignCommand());
        addSubCommand(new DungeonEntranceCommand());
        addSubCommand(new DungeonCleanCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Chocolate Machine Dungeon Commands:"));
        context.sendMessage(Message.raw("/cm d info - Show dungeon info at your location"));
        context.sendMessage(Message.raw("/cm d toggle - Toggle dungeon Active state"));
        context.sendMessage(Message.raw("/cm d register - Re-register nearest dungeon's spawners"));
        context.sendMessage(Message.raw("/cm d discard - Discard/clear dungeon state"));
        context.sendMessage(Message.raw("/cm d trigger - Become relic holder / simulate escape"));
        context.sendMessage(Message.raw("/cm d delete [--nearest] - Delete bossroom"));
        context.sendMessage(Message.raw("/cm d assign [--dungeon_id <id>] - Assign entity as bossroom"));
        context.sendMessage(Message.raw("/cm d entrance [--dungeon_id <id>] [--delete] - Manage entrance"));
        context.sendMessage(Message.raw("/cm d clean - Remove all spawned trap/dungeon entities"));
        return CompletableFuture.completedFuture(null);
    }
}

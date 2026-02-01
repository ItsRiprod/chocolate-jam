package com.chocolate.machine.command;

import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public class ChocolateTrapCommand extends AbstractCommand {

    public ChocolateTrapCommand() {
        super("trap", "Chocolate Machine dungeon management commands");
        addAliases("t", "tr");
        setPermissionGroup(GameMode.Creative);

        addSubCommand(new TrapSetCommand());
        addSubCommand(new TrapToggleCommand());
        addSubCommand(new TrapPlaceCommand());
        addSubCommand(new TrapListCommand());
        addSubCommand(new TrapConfigCommand());

    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Chocolate Machine Commands:"));
        context.sendMessage(Message.raw("/cm t set <executionId> - Set trap type (look at trap)"));
        context.sendMessage(Message.raw("/cm t toggle - Toggle trap Active state"));
        context.sendMessage(Message.raw("/cm t place <executionId> - Place a trap entity"));
        context.sendMessage(Message.raw("/cm t list - List all Trap locations"));
        context.sendMessage(Message.raw("/cm t config - Show/set trap config (look at trap)"));
        return CompletableFuture.completedFuture(null);
    }
}

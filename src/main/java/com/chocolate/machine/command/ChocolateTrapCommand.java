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

    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Chocolate Machine Commands:"));
        context.sendMessage(Message.raw("/chocolate trap set <executionId> - Set trap type (look at trap)"));
        context.sendMessage(Message.raw("/chocolate trap toggle - Toggle trap Active state"));
        context.sendMessage(Message.raw("/chocolate trap place <executionId> - Place a trap entity at your current location"));
        context.sendMessage(Message.raw("/chocolate trap list - List all Trap locations found via flood-fill"));
        return CompletableFuture.completedFuture(null);
    }
}

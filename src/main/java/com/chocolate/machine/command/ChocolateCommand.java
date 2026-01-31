package com.chocolate.machine.command;

import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

public class ChocolateCommand extends AbstractCommand {

    public ChocolateCommand() {
        super("chocolate", "Chocolate Machine dungeon management commands");
        addAliases("choc", "cm");
        setPermissionGroup(GameMode.Creative);

        addSubCommand(new ChocolateDungeonCommand());
        addSubCommand(new ChocolateTrapCommand());
        addSubCommand(new ChocolateSelfCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw("Chocolate Machine Commands:"));
        context.sendMessage(Message.raw("/chocolate dungeon info - Show dungeon info at your location"));
        context.sendMessage(Message.raw("/chocolate dungeon toggle - Toggle dungeon Active state"));
        context.sendMessage(Message.raw("/chocolate dungeon register - Re-register nearest dungeon's spawners"));
        context.sendMessage(Message.raw("/chocolate dungeon discard - Discard/clear dungeon state (for prefab editing)"));
        context.sendMessage(Message.raw("/chocolate dungeon trigger - Become relic holder / simulate escape"));
        context.sendMessage(Message.raw("/chocolate trap set <executionId> - Set trap type (look at trap)"));
        context.sendMessage(Message.raw("/chocolate trap toggle - Toggle trap Active state"));
        context.sendMessage(Message.raw("/chocolate trap place <executionId> - Place a trap entity at your current location"));
        context.sendMessage(Message.raw("/chocolate trap list - List all Trap locations found via flood-fill"));
        context.sendMessage(Message.raw("/chocolate self - Show your current dungeon status"));
        return CompletableFuture.completedFuture(null);
    }
}

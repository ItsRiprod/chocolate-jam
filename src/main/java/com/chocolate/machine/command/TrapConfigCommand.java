package com.chocolate.machine.command;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.component.SpawnerComponent;
import com.chocolate.machine.dungeon.component.actions.HydraulicPressActionComponent;
import com.chocolate.machine.dungeon.component.actions.LaserTrapActionComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

/**
 * Configure trap properties.
 * /cm t config - lists configurable fields for the trap you're looking at
 * /cm t config <field> <value> - sets a field value
 */
public class TrapConfigCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> fieldNameArg;
    private final OptionalArg<String> valueArg;

    public TrapConfigCommand() {
        super("config", "Configure trap properties");
        this.addAliases("c", "cfg");

        this.fieldNameArg = this.withOptionalArg("field", "Field name to configure", ArgTypes.STRING);
        this.valueArg = this.withOptionalArg("value", "New value for the field", ArgTypes.STRING);
    }

    @Override
    public void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Ref<EntityStore> targetRef = TargetUtil.getTargetEntity(ref, store);
        if (targetRef == null) {
            context.sendMessage(Message.raw("No entity in view. Look at a trap entity."));
            return;
        }

        SpawnerComponent spawner = store.getComponent(targetRef, SpawnerComponent.getComponentType());
        if (spawner == null) {
            context.sendMessage(Message.raw("Target entity is not a spawner/trap."));
            return;
        }

        String executionId = spawner.getExecutionId();
        if (executionId == null || executionId.isEmpty()) {
            context.sendMessage(Message.raw("Spawner has no execution ID set."));
            return;
        }

        String fieldName = fieldNameArg.get(context);
        String valueStr = valueArg.get(context);

        switch (executionId) {
            case "press":
                handlePress(context, store, targetRef, fieldName, valueStr);
                break;
            case "laser":
                handleLaser(context, store, targetRef, fieldName, valueStr);
                break;
            default:
                context.sendMessage(Message.raw("Trap type '" + executionId + "' is not configurable."));
                context.sendMessage(Message.raw("Configurable: press, laser"));
                break;
        }
    }

    private void handlePress(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> targetRef, String fieldName, String valueStr) {

        HydraulicPressActionComponent press = store.getComponent(targetRef,
                HydraulicPressActionComponent.getComponentType());

        if (press == null) {
            context.sendMessage(Message.raw("Press component not found. Activate the trap first."));
            return;
        }

        if (fieldName == null || fieldName.isEmpty()) {
            // List all configurable fields
            context.sendMessage(Message.raw("=== Press Config ==="));
            context.sendMessage(Message.raw("  pressAnimDuration: " + press.getPressAnimationDuration()));
            context.sendMessage(Message.raw("  retractAnimDuration: " + press.getRetractAnimationDuration()));
            context.sendMessage(Message.raw("  minCooldown: " + press.getMinCooldown()));
            context.sendMessage(Message.raw("  maxCooldown: " + press.getMaxCooldown()));
            context.sendMessage(Message.raw("  damage: " + press.getDamageAmount()));
            context.sendMessage(Message.raw("  velocityMult: " + press.getVelocityMultiplier()));
            context.sendMessage(Message.raw("Use: /cm t config <field> <value>"));
            return;
        }

        if (valueStr == null || valueStr.isEmpty()) {
            // Show single field
            switch (fieldName) {
                case "pressAnimDuration":
                    context.sendMessage(Message.raw("pressAnimDuration = " + press.getPressAnimationDuration()));
                    break;
                case "retractAnimDuration":
                    context.sendMessage(Message.raw("retractAnimDuration = " + press.getRetractAnimationDuration()));
                    break;
                case "minCooldown":
                    context.sendMessage(Message.raw("minCooldown = " + press.getMinCooldown()));
                    break;
                case "maxCooldown":
                    context.sendMessage(Message.raw("maxCooldown = " + press.getMaxCooldown()));
                    break;
                case "damage":
                    context.sendMessage(Message.raw("damage = " + press.getDamageAmount()));
                    break;
                case "velocityMult":
                    context.sendMessage(Message.raw("velocityMult = " + press.getVelocityMultiplier()));
                    break;
                default:
                    context.sendMessage(Message.raw("Unknown field: " + fieldName));
                    break;
            }
            return;
        }

        // Set field value
        float value;
        try {
            value = Float.parseFloat(valueStr);
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Invalid number: " + valueStr));
            return;
        }

        switch (fieldName) {
            case "pressAnimDuration":
                press.setPressAnimationDuration(value);
                context.sendMessage(Message.raw("Set pressAnimDuration = " + value));
                break;
            case "retractAnimDuration":
                press.setRetractAnimationDuration(value);
                context.sendMessage(Message.raw("Set retractAnimDuration = " + value));
                break;
            case "minCooldown":
                press.setMinCooldown(value);
                context.sendMessage(Message.raw("Set minCooldown = " + value));
                break;
            case "maxCooldown":
                press.setMaxCooldown(value);
                context.sendMessage(Message.raw("Set maxCooldown = " + value));
                break;
            case "damage":
                press.setDamageAmount(value);
                context.sendMessage(Message.raw("Set damage = " + value));
                break;
            case "velocityMult":
                press.setVelocityMultiplier(value);
                context.sendMessage(Message.raw("Set velocityMult = " + value));
                break;
            default:
                context.sendMessage(Message.raw("Unknown field: " + fieldName));
                break;
        }
    }

    private void handleLaser(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> targetRef, String fieldName, String valueStr) {

        LaserTrapActionComponent laser = store.getComponent(targetRef,
                LaserTrapActionComponent.getComponentType());

        if (laser == null) {
            context.sendMessage(Message.raw("Laser component not found. Activate the trap first."));
            return;
        }

        if (fieldName == null || fieldName.isEmpty()) {
            // List all configurable fields
            context.sendMessage(Message.raw("=== Laser Config ==="));
            context.sendMessage(Message.raw("  interval: " + laser.getFireInterval()));
            context.sendMessage(Message.raw("  damage: " + laser.getDamage()));
            context.sendMessage(Message.raw("  speed: " + laser.getSpeed()));
            context.sendMessage(Message.raw("Use: /cm t config <field> <value>"));
            return;
        }

        if (valueStr == null || valueStr.isEmpty()) {
            // Show single field
            switch (fieldName) {
                case "interval":
                    context.sendMessage(Message.raw("interval = " + laser.getFireInterval()));
                    break;
                case "damage":
                    context.sendMessage(Message.raw("damage = " + laser.getDamage()));
                    break;
                case "speed":
                    context.sendMessage(Message.raw("speed = " + laser.getSpeed()));
                    break;
                default:
                    context.sendMessage(Message.raw("Unknown field: " + fieldName));
                    break;
            }
            return;
        }

        // Set field value
        float value;
        try {
            value = Float.parseFloat(valueStr);
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw("Invalid number: " + valueStr));
            return;
        }

        switch (fieldName) {
            case "interval":
                laser.setFireInterval(value);
                context.sendMessage(Message.raw("Set interval = " + value));
                break;
            case "damage":
                laser.setDamage(value);
                context.sendMessage(Message.raw("Set damage = " + value));
                break;
            case "speed":
                laser.setSpeed(value);
                context.sendMessage(Message.raw("Set speed = " + value));
                break;
            default:
                context.sendMessage(Message.raw("Unknown field: " + fieldName));
                break;
        }
    }

    @Nonnull
    public Message getUsageMessage() {
        return Message.raw("Configure trap properties");
    }

    @Nonnull
    public Message getUsageOneLiner() {
        return Message.raw("/cm t config [field] [value]");
    }
}

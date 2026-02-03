package com.chocolate.machine.dungeon.interaction;

import java.util.HashMap;

import javax.annotation.Nonnull;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;

public class PedestalTriggerInteraction extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<PedestalTriggerInteraction> CODEC = BuilderCodec.builder(
            PedestalTriggerInteraction.class, PedestalTriggerInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    private static final String TRIGGERED_SOUND = "SFX_CM_Triggered";
    private static final String[] ARTIFACT_ARMOR = {
            "Armor_Artifact_Head",
            "Armor_Artifact_Chest",
            "Armor_Artifact_Hands",
            "Armor_Artifact_Legs"
    };

    private int soundIndex = -1;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> playerRef = context.getOwningEntity();
        if (playerRef == null || !playerRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        InteractionSyncData clientState = context.getClientState();
        if (clientState == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> dungeonRef = commandBuffer.getStore().getExternalData()
                .getRefFromNetworkId(clientState.entityId);

        if (dungeonRef == null || !dungeonRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        DungeonComponent dungeon = commandBuffer.getComponent(dungeonRef, DungeonComponent.getComponentType());
        if (dungeon == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (dungeon.isTriggered() || dungeon.isActive()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        playSoundAtEntity(dungeonRef, commandBuffer);

        DungeonModule module = DungeonModule.get();
        if (module == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        DungeonService dungeonService = module.getDungeonService();
        World world = commandBuffer.getExternalData().getWorld();

        dungeonService.registerDungeon(dungeonRef, commandBuffer, world);
        dungeonService.activate(dungeonRef, playerRef, commandBuffer);

        dungeon.setArtifactHolderRef(playerRef);
        setupDungeoneer(playerRef, dungeonRef, dungeon, commandBuffer);
        giveArtifactArmor(playerRef, commandBuffer);
        hidePedestalArmor(dungeonRef, commandBuffer);

        dungeon.setTriggered(true);

        context.getState().state = InteractionState.Finished;
    }

    private void playSoundAtEntity(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> commandBuffer) {
        if (soundIndex < 0) {
            soundIndex = SoundEvent.getAssetMap().getIndex(TRIGGERED_SOUND);
        }

        if (soundIndex < 0) {
            return;
        }

        TransformComponent transform = commandBuffer.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d pos = transform.getPosition();
        SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, pos, commandBuffer);
    }

    private void setupDungeoneer(Ref<EntityStore> playerRef, Ref<EntityStore> dungeonRef,
            DungeonComponent dungeon, CommandBuffer<EntityStore> commandBuffer) {

        DungeoneerComponent existingDungeoneer = commandBuffer.getComponent(playerRef,
                DungeoneerComponent.getComponentType());

        if (existingDungeoneer == null) {
            Vector3d spawnPosition = dungeon.getSpawnPosition();
            existingDungeoneer = new DungeoneerComponent(dungeon.getDungeonId(), spawnPosition);
            existingDungeoneer.setDungeonRef(dungeonRef);
            commandBuffer.addComponent(playerRef, DungeoneerComponent.getComponentType(), existingDungeoneer);
            dungeon.addDungeoneerRef(playerRef);
        }

        existingDungeoneer.setRelicHolder(true);
    }

    private void giveArtifactArmor(Ref<EntityStore> playerRef, CommandBuffer<EntityStore> commandBuffer) {
        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        ItemContainer armor = inventory.getArmor();

        for (String armorId : ARTIFACT_ARMOR) {
            ItemStack item = new ItemStack(armorId, 1);
            InventoryHelper.useArmor(armor, item);
        }
    }

    private void hidePedestalArmor(Ref<EntityStore> pedestalRef, CommandBuffer<EntityStore> commandBuffer) {
        ModelComponent modelComponent = commandBuffer.getComponent(pedestalRef, ModelComponent.getComponentType());
        if (modelComponent == null) {
            return;
        }

        Model model = modelComponent.getModel();
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(model.getModelAssetId());
        if (modelAsset == null) {
            return;
        }

        Model emptyModel = Model.createScaledModel(modelAsset, model.getScale(), new HashMap<>());
        commandBuffer.putComponent(pedestalRef, ModelComponent.getComponentType(), new ModelComponent(emptyModel));
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }
}

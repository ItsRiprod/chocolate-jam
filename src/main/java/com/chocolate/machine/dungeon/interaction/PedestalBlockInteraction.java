package com.chocolate.machine.dungeon.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.chocolate.machine.dungeon.DungeonModule;
import com.chocolate.machine.dungeon.DungeonService;
import com.chocolate.machine.dungeon.component.DungeonComponent;
import com.chocolate.machine.dungeon.component.DungeoneerComponent;
import com.chocolate.machine.utils.DungeonFinder;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.npc.util.InventoryHelper;

public class PedestalBlockInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<PedestalBlockInteraction> CODEC = BuilderCodec.builder(
            PedestalBlockInteraction.class, PedestalBlockInteraction::new, SimpleBlockInteraction.CODEC)
            .build();

    private static final String TRIGGERED_SOUND = "SFX_CM_Triggered";
    private static final String[] ARTIFACT_ARMOR = {
            "Armor_Artifact_Head",
            "Armor_Artifact_Chest",
            "Armor_Artifact_Hands",
            "Armor_Artifact_Legs"
    };

    private int soundIndex = -1;

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i pos,
            @Nonnull CooldownHandler cooldownHandler) {

        try {
            Ref<EntityStore> playerRef = context.getEntity();

            if (playerRef == null || !playerRef.isValid()) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());

            Vector3d blockCenter = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            Ref<EntityStore> dungeonRef = DungeonFinder.findNearestDungeon(blockCenter, commandBuffer);

            if (dungeonRef == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            DungeonComponent dungeon = commandBuffer.getComponent(dungeonRef, DungeonComponent.getComponentType());
            if (dungeon == null || dungeon.isTriggered() || dungeon.isActive()) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            playSoundAtPosition(pos, commandBuffer);

            if (playerRefComponent == null || !playerRefComponent.isValid()) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            EventTitleUtil.showEventTitleToPlayer(
                    playerRefComponent,
                    Message.raw("Escape"),
                    Message.raw("Objective Update"),
                    true);

            DungeonModule module = DungeonModule.get();
            if (module == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            DungeonService dungeonService = module.getDungeonService();

            // reset if already registered (same as /cm d register command)
            if (dungeon.isRegistered()) {
                dungeon.reset();
            }

            // register first, then activate
            dungeonService.registerDungeon(dungeonRef, commandBuffer, world);
            dungeonService.activate(dungeonRef, playerRef, commandBuffer);

            dungeon.setArtifactHolderRef(playerRef);
            setupDungeoneer(playerRef, dungeonRef, dungeon, commandBuffer);
            giveArtifactArmor(playerRef, commandBuffer);

            setBlockState(world, pos, "On");

            dungeon.setTriggered(true);
            context.getState().state = InteractionState.Finished;
        } catch (Exception e) {
            context.getState().state = InteractionState.Failed;
            e.printStackTrace();
            return;
        }
    }

    private void playSoundAtPosition(Vector3i pos, CommandBuffer<EntityStore> commandBuffer) {
        if (soundIndex < 0) {
            soundIndex = SoundEvent.getAssetMap().getIndex(TRIGGERED_SOUND);
        }
        if (soundIndex >= 0) {
            Vector3d soundPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, soundPos, commandBuffer);
        }
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
        if (player == null)
            return;

        Inventory inventory = player.getInventory();
        ItemContainer armor = inventory.getArmor();

        for (String armorId : ARTIFACT_ARMOR) {
            ItemStack item = new ItemStack(armorId, 1);
            InventoryHelper.useArmor(armor, item);
        }
    }

    private void setBlockState(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull String state) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return;
        }

        BlockType blockType = chunk.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null || blockType.isUnknown()) {
            return;
        }

        chunk.setBlockInteractionState(pos, blockType, state);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
    }
}

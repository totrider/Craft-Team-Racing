package com.craftteamracing.pickup;

import java.util.ArrayList;
import java.util.List;

import com.craftteamracing.items.HeldItem;
import com.craftteamracing.kart.WrenchItem;
import com.craftteamracing.track.TrackPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A spot where a fruit, a fruit crate or a power-up crate appears while a race is on near it (or while it has a redstone signal). A small marker
 * block, no collision. A power-up crate spot is set to a particular power-up by right-clicking it with the Wrench (it cycles through them; the
 * first stop is CTR's own roulette).
 */
public class PickupSpotBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 8.0, 12.0);

    private final PickupKind kind;

    public PickupSpotBlock(Properties properties, PickupKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PickupKind kind() {
        return kind;
    }

    /** Drawn by {@link com.craftteamracing.client.PickupSpotRenderer}, and only while an editing item is in hand: the marker is a tool for the builder, not part of the scenery. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A power-up crate spot item that carries a power-up sets the spot it is placed as. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && kind == PickupKind.POWERUP_CRATE && level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be) {
            String id = stack.get(com.craftteamracing.ModComponents.CRATE_ITEM.get());
            if (id != null) be.setItemId(id);
        }
    }

    /** What a broken spot drops, and what a middle click picks, keeps the power-up the spot was set to. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (kind == PickupKind.POWERUP_CRATE && params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof PickupSpotBlockEntity be
                && !be.itemId().isEmpty()) {
            for (ItemStack d : drops) {
                if (d.is(com.craftteamracing.ModBlocks.POWERUP_CRATE_SPOT_ITEM.get())) d.set(com.craftteamracing.ModComponents.CRATE_ITEM.get(), be.itemId());
            }
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (kind == PickupKind.POWERUP_CRATE && level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be && !be.itemId().isEmpty()) {
            stack.set(com.craftteamracing.ModComponents.CRATE_ITEM.get(), be.itemId());
        }
        return stack;
    }

    /** The trigger crate gives a redstone signal (the block entity says when). */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == PickupKind.TRIGGER_CRATE;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return kind == PickupKind.TRIGGER_CRATE && level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be && be.emitting() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** A trigger crate that is taken away switches off what it had switched on. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (kind == PickupKind.TRIGGER_CRATE && !state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server
                && level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be) {
            be.setOutput(server, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PickupSpotBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != com.craftteamracing.ModBlockEntities.PICKUP_SPOT.get()) return null;
        return (lvl, pos, st, be) -> PickupSpotBlockEntity.serverTick(lvl, pos, st, (PickupSpotBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem) || (kind != PickupKind.POWERUP_CRATE && kind != PickupKind.TRIGGER_CRATE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !TrackPermissions.canEdit(sp, pos)
                || !(level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be)) {
            return ItemInteractionResult.CONSUME;
        }
        if (kind == PickupKind.TRIGGER_CRATE) {
            // a Redstone Linking Node picked with the Wrench is linked (or unlinked) by the next click here; otherwise the click opens the settings
            BlockPos node = RedstoneLinks.selected(sp.getUUID(), level.dimension());
            if (node != null) {
                // with a node picked: a click links it, a crouching click takes the link away again
                boolean done = sp.isShiftKeyDown() ? be.unlink(node) : be.link(node);
                if (sp.isShiftKeyDown()) sp.displayClientMessage(Component.translatable(done ? "message.craftteamracing.link.unlinked" : "message.craftteamracing.link.not_linked"), true);
                else sp.displayClientMessage(Component.translatable(done ? "message.craftteamracing.link.linked" : "message.craftteamracing.link.already"), true);
            } else {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.craftteamracing.net.OpenTriggerPayload(pos, be.respawnStep(), be.durationStep(), be.uses()));
            }
            return ItemInteractionResult.CONSUME;
        }
        // the Wrench opens a screen with a picture of every power-up to pick from (SetSpotPayload sends the choice back)
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.craftteamracing.net.OpenSpotPayload(pos, be.itemId()));
        return ItemInteractionResult.CONSUME;
    }
}

package com.craftteamracing.pickup;

import com.craftteamracing.kart.WrenchItem;
import com.craftteamracing.track.TrackPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Redstone Linking Node: the far end of a wireless redstone line. Trigger Crate Spots linked to it (Wrench on the node, then Wrench on the spot) switch it on while they give a
 * signal, and it gives the signal off in every direction. A visible little block (unlike the track's invisible nodes: it is part of a redstone build).
 */
public class RedstoneLinkNodeBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);

    public RedstoneLinkNodeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    public static void setPowered(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RedstoneLinkNodeBlock && state.getValue(POWERED) != on) level.setBlock(pos, state.setValue(POWERED, on), 3);
    }

    /** The Wrench picks the node, to be linked to the next Trigger Crate Spot it is used on. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && TrackPermissions.canEdit(sp, pos)) {
            if (sp.isShiftKeyDown()) {
                RedstoneLinks.clear(sp.getUUID());                                  // crouching lets go of the picked node
                sp.displayClientMessage(Component.translatable("message.craftteamracing.link.dropped"), true);
            } else {
                RedstoneLinks.select(sp.getUUID(), level.dimension(), pos);
                sp.displayClientMessage(Component.translatable("message.craftteamracing.link.picked"), true);
            }
        }
        return ItemInteractionResult.CONSUME;
    }
}

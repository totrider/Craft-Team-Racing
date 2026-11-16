package com.ctrcraft.track;

import com.ctrcraft.ModBlocks;
import com.ctrcraft.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A track node (start or checkpoint): invisible, no collision, and only there to mark a place and a direction. It can only be
 * pointed at while holding the Track Tool (and is drawn as a ghost then, see client/TrackGhostRenderer). Its real data (links, ring size)
 * lives in {@link TrackData}; placing registers the node there, breaking removes it.
 */
public class TrackNodeBlock extends Block {
    /** 0-15 in 22.5 degree steps, like a standing sign: the way the node faces. */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    /** On while the node is "active": a Start node while its race is running, a podium node while a racer stands on it. It is a redstone source then (full strength). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);

    private final TrackGraph.Kind kind;

    public TrackNodeBlock(Properties properties, TrackGraph.Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0).setValue(POWERED, false));
    }

    public TrackGraph.Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    /** Switches the node's redstone output on or off (the node keeps its place in the track). */
    public static void setPowered(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrackNodeBlock && state.getValue(POWERED) != on) level.setBlock(pos, state.setValue(POWERED, on), 3);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Only someone holding the Track Tool, a node item (or, for a Start node, the rulebook or the drop table) can point at it (to link, remove or replace it); for everyone else it is not there. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // only a real entity's context can be holding something; the empty context (block state caches, early queries) never is, and must
        // not look the tool up before items are registered
        if (context instanceof EntityCollisionContext && (context.isHoldingItem(ModItems.TRACK_TOOL.get())
                || context.isHoldingItem(ModBlocks.START_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.CHECKPOINT_NODE_ITEM.get())
                || context.isHoldingItem(ModBlocks.PODIUM_1_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.PODIUM_2_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.PODIUM_3_NODE_ITEM.get())
                || (kind == TrackGraph.Kind.START && (context.isHoldingItem(ModItems.RULEBOOK.get()) || context.isHoldingItem(ModItems.DROP_TABLE.get()) || context.isHoldingItem(ModItems.MUSIC_SHEET.get()))))) {
            return SHAPE;
        }
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** A node item put down on a node replaces it, like a block put down on tall grass: the node keeps its links, so you can turn it or change its kind. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() instanceof TrackNodeItem;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (oldState.is(state.getBlock()) && oldState.getValue(ROTATION) == state.getValue(ROTATION)) {
            super.onPlace(state, level, pos, oldState, isMoving);          // only the redstone output changed: the node is the same
            return;
        }
        if (level instanceof ServerLevel server) {
            // put() keeps the links and the ring size of a node already there (a replaced or turned node)
            TrackData data = TrackData.get(server);
            data.graph().put(pos.asLong(), kind, state.getValue(ROTATION));
            data.setDirty();
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // replaced by another node block: the node lives on (its onPlace updates it); anything else removes it and its links
        if (level instanceof ServerLevel server && !state.is(newState.getBlock()) && !(newState.getBlock() instanceof TrackNodeBlock)) {
            TrackData data = TrackData.get(server);
            data.graph().remove(pos.asLong());
            data.setDirty();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }
}

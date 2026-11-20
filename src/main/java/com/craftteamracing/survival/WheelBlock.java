package com.craftteamracing.survival;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A kart wheel as a block, placed on the ground. More wheels can be added to the same block as petals are to pink petals: one lies in the middle, two are stacked, three are two stacked
 * with one leaning against them (on the side the block faces), and four lie in a neat two by two. The kart and the offroad tires are built from wheels.
 */
public class WheelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WheelBlock> CODEC = simpleCodec(WheelBlock::new);
    public static final IntegerProperty WHEELS = IntegerProperty.create("wheels", 1, 4);
    private static final VoxelShape[] SHAPES = {
            Block.box(4.0, 0.0, 4.0, 12.0, 3.0, 12.0),
            Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0),
            Block.box(0.0, 0.0, 4.0, 16.0, 6.0, 12.0),
            Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0)};

    public WheelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WHEELS, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WHEELS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(WHEELS) - 1];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(ModSurvival.WHEEL_ITEM.get()) && state.getValue(WHEELS) < 4 || super.canBeReplaced(state, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) return here.setValue(WHEELS, Math.min(4, here.getValue(WHEELS) + 1));
        BlockState fresh = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return fresh.canSurvive(context.getLevel(), context.getClickedPos()) ? fresh : null;
    }
}

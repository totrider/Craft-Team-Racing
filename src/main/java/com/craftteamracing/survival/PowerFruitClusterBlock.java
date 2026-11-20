package com.craftteamracing.survival;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Power Fruit hanging from the underside of Power Palm fronds. Push a fruit onto the underside of a frond (right click) and it takes hold as a single fruit; it grows to two, three and four
 * over time (a random tick each), and more fruit can be pushed onto it to add to the cluster (as petals are added to a pink petals block). A full cluster of four is picked by hand (right
 * click: four fruit) and goes back to one, so a tree is worth keeping. Broken, it drops the fruit it carries. It stays only under palm fronds.
 */
public class PowerFruitClusterBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 1, 4);
    /** Shears on a cluster stop its growth (and again start it): as a pruned vine stays as it is. */
    public static final BooleanProperty PRUNED = BooleanProperty.create("pruned");
    private static final VoxelShape SHAPE = Block.box(2.0, 8.0, 2.0, 14.0, 16.0, 14.0);

    public PowerFruitClusterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 1).setValue(PRUNED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(PowerFruitClusterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, PRUNED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(ModSurvival.POWER_PALM_LEAVES.get());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModSurvival.POWER_FRUIT.get());
    }

    /** A fruit pushed onto a cluster that has room is placed in it (the cluster is "replaced" by one with a fruit more). */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(ModSurvival.POWER_FRUIT.get()) && state.getValue(AGE) < 4 || super.canBeReplaced(state, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) return here.setValue(AGE, Math.min(4, here.getValue(AGE) + 1));
        BlockState fresh = defaultBlockState();
        return fresh.canSurvive(context.getLevel(), context.getClickedPos()) ? fresh : null;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 4 && !state.getValue(PRUNED);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.SHEARS)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PRUNED, !state.getValue(PRUNED)), 2);
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < 4) return InteractionResult.PASS;
        if (!level.isClientSide) {
            Block.popResource(level, pos, new ItemStack(ModSurvival.POWER_FRUIT.get(), 4));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            level.setBlock(pos, state.setValue(AGE, 1), 2);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

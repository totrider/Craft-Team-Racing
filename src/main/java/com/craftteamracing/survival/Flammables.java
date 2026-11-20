package com.craftteamracing.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The blocks of wood the survival side adds, with the fire behaviour of their vanilla kind (a log, planks, leaves). */
final class Flammables {
    private Flammables() {}
}

class FlammableBlock extends Block {
    private final int encouragement;
    private final int flammability;

    FlammableBlock(BlockBehaviour.Properties properties, int encouragement, int flammability) {
        super(properties);
        this.encouragement = encouragement;
        this.flammability = flammability;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return encouragement;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }
}

class FlammablePillar extends RotatedPillarBlock {
    private final int encouragement;
    private final int flammability;

    FlammablePillar(BlockBehaviour.Properties properties, int encouragement, int flammability) {
        super(properties);
        this.encouragement = encouragement;
        this.flammability = flammability;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return encouragement;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }
}

class FlammableLeaves extends LeavesBlock {
    FlammableLeaves(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }
}

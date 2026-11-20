package com.craftteamracing.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The Power Palm's sapling: it takes root in sand as well as in dirt (the palms grow on beaches). */
public class PowerPalmSaplingBlock extends SaplingBlock {
    public PowerPalmSaplingBlock(TreeGrower grower, BlockBehaviour.Properties properties) {
        super(grower, properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND);
    }
}

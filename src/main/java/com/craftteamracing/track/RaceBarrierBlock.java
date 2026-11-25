package com.craftteamracing.track;

import com.craftteamracing.ModBlocks;
import com.craftteamracing.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Kart Barrier: a block that stops karts and nothing else, and only while the driven kart takes part in a race (the kart's terrain reads it as a wall then, see client/McBlockSource).
 * Players, mobs and other karts out of a race walk and drive through it. It is invisible, like the track nodes, and can only be pointed at (to place beside it, or break it) while holding
 * something for building a track; it is drawn as a ghost then (client/BarrierGhostRenderer), so a long wall of them can be seen and edited.
 */
public class RaceBarrierBlock extends Block {
    public RaceBarrierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Whether the item is one that lets the barrier be seen and pointed at: the track building items and the barrier itself. */
    public static boolean editing(net.minecraft.world.item.ItemStack stack) {
        return stack.is(ModBlocks.RACE_BARRIER_ITEM.get()) || stack.is(ModItems.TRACK_TOOL.get()) || stack.is(ModItems.WRENCH.get()) || stack.is(ModBlocks.START_NODE_ITEM.get())
                || stack.is(ModBlocks.CHECKPOINT_NODE_ITEM.get()) || stack.is(ModBlocks.PODIUM_1_NODE_ITEM.get()) || stack.is(ModBlocks.PODIUM_2_NODE_ITEM.get())
                || stack.is(ModBlocks.PODIUM_3_NODE_ITEM.get()) || stack.is(ModBlocks.FRUIT_SPOT_ITEM.get()) || stack.is(ModBlocks.FRUIT_CRATE_SPOT_ITEM.get())
                || stack.is(ModBlocks.POWERUP_CRATE_SPOT_ITEM.get()) || stack.is(ModBlocks.TRIGGER_CRATE_SPOT_ITEM.get()) || stack.is(ModBlocks.REDSTONE_LINK_NODE_ITEM.get());
    }

    /** Whether whoever the context belongs to holds a block in either hand (the barrier can then be pointed at, to place a block against its face). */
    private static boolean holdingBlock(CollisionContext context) {
        return context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                && (living.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem || living.getOffhandItem().getItem() instanceof net.minecraft.world.item.BlockItem);
    }

    /**
     * Pointable for someone holding a track building item, and for someone holding any block, so that a regular block can be placed against a barrier's face (it is not drawn then, but
     * its outline shows); for everyone else it is not there. It never has a collision of its own: see the block's properties.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // the empty context (block state caches, early queries) is never holding anything, and must not look items up before they are registered
        if (context instanceof EntityCollisionContext && (context.isHoldingItem(ModBlocks.RACE_BARRIER_ITEM.get()) || context.isHoldingItem(ModItems.TRACK_TOOL.get())
                || context.isHoldingItem(ModItems.WRENCH.get()) || context.isHoldingItem(ModBlocks.START_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.CHECKPOINT_NODE_ITEM.get())
                || context.isHoldingItem(ModBlocks.PODIUM_1_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.PODIUM_2_NODE_ITEM.get()) || context.isHoldingItem(ModBlocks.PODIUM_3_NODE_ITEM.get())
                || context.isHoldingItem(ModBlocks.FRUIT_SPOT_ITEM.get()) || context.isHoldingItem(ModBlocks.FRUIT_CRATE_SPOT_ITEM.get()) || context.isHoldingItem(ModBlocks.POWERUP_CRATE_SPOT_ITEM.get())
                || context.isHoldingItem(ModBlocks.TRIGGER_CRATE_SPOT_ITEM.get()) || context.isHoldingItem(ModBlocks.REDSTONE_LINK_NODE_ITEM.get()) || holdingBlock(context))) {
            return Shapes.block();
        }
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}

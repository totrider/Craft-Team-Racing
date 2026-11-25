package com.craftteamracing.pickup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A block item that can be placed in mid-air, like the old "angel block": right-click while pointing at nothing and the block appears three blocks in front of you at most, as if it
 * were against another block. Pointing at a block places it the normal way (against that block's face). Used for the pickup spots, which a track builder often wants to hang in the air
 * above the road (a fruit, a crate), where there is nothing to place them against.
 */
public class AirPlaceBlockItem extends BlockItem {
    /** How far in front of the eyes the block goes at most, and how much closer than the reach it is kept, so that it is always within reach to be picked up again. */
    private static final double MAX_DISTANCE = 3.0;
    private static final double REACH_MARGIN = 0.5;
    private static final double MIN_DISTANCE = 2.0;

    public AirPlaceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Where a block placed in the air goes: the block cell on the line of sight a little before the end of the player's reach. */
    public static BlockPos airTarget(Player player) {
        double distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, player.blockInteractionRange() - REACH_MARGIN));
        Vec3 eye = player.getEyePosition();
        return BlockPos.containing(eye.add(player.getLookAngle().scale(distance)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // a block in reach is the normal placement (useOn); this is only for pointing at nothing
        HitResult pointed = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (pointed.getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = airTarget(player);
        // placed as if the face of the cell itself had been clicked: the cell is replaced, so the block lands exactly there
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = place(new BlockPlaceContext(player, hand, stack, hit));
        if (result.consumesAction()) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }
}

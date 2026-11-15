package com.ctrcraft.track;

import java.util.List;

import com.ctrcraft.DropTableData;
import com.ctrcraft.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drop table (docs/DESIGN_ITEMS.md): holds a power-up table, CTR's roulette or a weighted list of power-ups, apart from the rules of a race, so a track's drops can be swapped without
 * touching its rules. Right-click opens its screen; right-clicking a Start node applies it, as the track's default (TrackPermissions.canEdit) or for the next race only.
 */
public class DropTableItem extends Item {
    public DropTableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.ctrcraft.client.DropTableClient.open(hand, stack.getOrDefault(ModComponents.DROP_TABLE.get(), DropTableData.DEFAULT));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        BlockState state = ctx.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof TrackNodeBlock node) || node.kind() != TrackGraph.Kind.START) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ServerLevel level = (ServerLevel) ctx.getLevel();
        if (RaceManager.isRunning(level, pos.asLong())) {
            sp.displayClientMessage(Component.translatable("message.ctrcraft.rulebook.race_running"), true);
            return InteractionResult.CONSUME;
        }
        DropTableData data = ctx.getItemInHand().getOrDefault(ModComponents.DROP_TABLE.get(), DropTableData.DEFAULT);
        TrackData track = TrackData.get(level);
        if (data.asDefault()) {
            if (!TrackPermissions.canEdit(sp, pos)) {
                sp.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), true);
                return InteractionResult.CONSUME;
            }
            track.setDefaultTable(pos.asLong(), data.table());
            sp.displayClientMessage(Component.translatable("message.ctrcraft.droptable.applied_default"), true);
        } else {
            track.setTemporaryTable(pos.asLong(), data.table());
            sp.displayClientMessage(Component.translatable("message.ctrcraft.droptable.applied_temporary"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DropTableData data = stack.getOrDefault(ModComponents.DROP_TABLE.get(), DropTableData.DEFAULT);
        tooltip.add(data.table().isCtr() ? Component.translatable("tooltip.ctrcraft.droptable.ctr") : Component.translatable("tooltip.ctrcraft.droptable.custom", data.table().weights().size()));
        tooltip.add(Component.translatable(data.asDefault() ? "tooltip.ctrcraft.rulebook.default" : "tooltip.ctrcraft.rulebook.temporary"));
        tooltip.add(Component.translatable("tooltip.ctrcraft.droptable.use"));
    }
}

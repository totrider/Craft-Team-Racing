package com.craftteamracing.track;

import java.util.List;

import com.craftteamracing.ModComponents;
import com.craftteamracing.RulebookData;

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
 * The rulebook (docs/DESIGN_ITEMS.md): holds a rule set. Right-click in the air opens its screen; right-clicking a Start node applies it to that track, as the track's
 * default (who may do that is TrackPermissions.canEdit) or for the next race only (anybody, so a visitor can try their own rules without changing the track). The rules travel with the item
 * when it is copied (a middle click in creative).
 */
public class RulebookItem extends Item {
    public RulebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.craftteamracing.client.RulebookClient.open(hand, stack.getOrDefault(ModComponents.RULEBOOK.get(), RulebookData.DEFAULT));
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
            sp.displayClientMessage(Component.translatable("message.craftteamracing.rulebook.race_running"), true);
            return InteractionResult.CONSUME;
        }
        RulebookData data = ctx.getItemInHand().getOrDefault(ModComponents.RULEBOOK.get(), RulebookData.DEFAULT);
        TrackData track = TrackData.get(level);
        if (data.asDefault()) {
            if (!TrackPermissions.canEdit(sp, pos)) {
                sp.displayClientMessage(Component.translatable("message.craftteamracing.track.no_permission"), true);
                return InteractionResult.CONSUME;
            }
            track.setDefaultRules(pos.asLong(), data.rules());
            track.clearTemporaryRules(pos.asLong());
            sp.displayClientMessage(Component.translatable("message.craftteamracing.rulebook.applied_default"), true);
        } else {
            track.setTemporaryRules(pos.asLong(), data.rules());
            sp.displayClientMessage(Component.translatable("message.craftteamracing.rulebook.applied_temporary"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RulebookData data = stack.getOrDefault(ModComponents.RULEBOOK.get(), RulebookData.DEFAULT);
        tooltip.add(Component.translatable(data.rules().isCtr() ? "tooltip.craftteamracing.rulebook.ctr" : "tooltip.craftteamracing.rulebook.custom"));
        tooltip.add(Component.translatable(data.asDefault() ? "tooltip.craftteamracing.rulebook.default" : "tooltip.craftteamracing.rulebook.temporary"));
        tooltip.add(Component.translatable("tooltip.craftteamracing.rulebook.use"));
    }
}

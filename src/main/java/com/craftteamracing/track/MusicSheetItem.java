package com.craftteamracing.track;

import java.util.List;

import com.craftteamracing.ModComponents;
import com.craftteamracing.MusicSheetData;

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
 * The track music sheet (docs/MUSIC.md): holds the music of a track, a title and the id of a sound. Right-click opens its screen; right-clicking a Start node applies it, as the track's
 * default (TrackPermissions.canEdit) or for the next race only. The sound itself only has to exist on a player's own computer (the original's music is a local resource pack): the title
 * is what every player sees.
 */
public class MusicSheetItem extends Item {
    public MusicSheetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) com.craftteamracing.client.MusicSheetClient.open(hand, stack.getOrDefault(ModComponents.MUSIC_SHEET.get(), MusicSheetData.DEFAULT));
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
        MusicSheetData data = ctx.getItemInHand().getOrDefault(ModComponents.MUSIC_SHEET.get(), MusicSheetData.DEFAULT);
        TrackData track = TrackData.get(level);
        if (data.asDefault()) {
            if (!TrackPermissions.canEdit(sp, pos)) {
                sp.displayClientMessage(Component.translatable("message.craftteamracing.track.no_permission"), true);
                return InteractionResult.CONSUME;
            }
            track.setDefaultMusic(pos.asLong(), data);
            sp.displayClientMessage(Component.translatable("message.craftteamracing.music.applied_default"), true);
        } else {
            track.setTemporaryMusic(pos.asLong(), data);
            sp.displayClientMessage(Component.translatable("message.craftteamracing.music.applied_temporary"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MusicSheetData data = stack.getOrDefault(ModComponents.MUSIC_SHEET.get(), MusicSheetData.DEFAULT);
        tooltip.add(Component.translatable("tooltip.craftteamracing.music.current", com.craftteamracing.client.MusicTitles.display(data)));
        tooltip.add(Component.translatable(data.asDefault() ? "tooltip.craftteamracing.rulebook.default" : "tooltip.craftteamracing.rulebook.temporary"));
        tooltip.add(Component.translatable("tooltip.craftteamracing.music.use"));
    }
}

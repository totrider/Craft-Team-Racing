package com.craftteamracing.track;

import com.craftteamracing.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Track Tool: links track nodes. Right-click a node to select it; right-click another to link the two (or unlink them, if they
 * are already linked); the clicked node becomes the selected one, so you can walk a chain: start, c1, c2, ... start. Sneak + right-click
 * (not on a node) clears the selection. While held, nodes, their direction, their rings and their links are drawn as ghosts.
 */
public class TrackToolItem extends Item {
    /** Furthest apart two nodes may be linked, in blocks. */
    public static final double MAX_LINK_DISTANCE = 192.0;

    public TrackToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockState(pos).getBlock() instanceof com.craftteamracing.spectate.ViewingConsoleBlock) {
            return hookConsole(context, level, pos);
        }
        if (!(level.getBlockState(pos).getBlock() instanceof TrackNodeBlock)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!TrackPermissions.canEdit(player, pos)) {
            player.displayClientMessage(Component.translatable("message.craftteamracing.track.no_permission"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        GlobalPos clicked = GlobalPos.of(server.dimension(), pos);
        GlobalPos selected = stack.get(ModComponents.SELECTED_NODE.get());
        TrackData data = TrackData.get(server);
        TrackGraph graph = data.graph();

        // sneak + right-click on the Start node: its menu (test drive, race, laps, re-record)
        TrackGraph.Node clickedNode = graph.get(pos.asLong());
        if (player.isShiftKeyDown() && clickedNode != null && clickedNode.kind == TrackGraph.Kind.START) {
            TestDriveManager.openMenu(player, server, pos);
            return InteractionResult.CONSUME;
        }

        if (selected == null || !selected.dimension().equals(server.dimension()) || !graph.contains(selected.pos().asLong())) {
            stack.set(ModComponents.SELECTED_NODE.get(), clicked);
            player.displayClientMessage(Component.translatable("message.craftteamracing.track.selected", describe(graph, pos)), true);
            return InteractionResult.CONSUME;
        }
        if (selected.pos().equals(pos)) {
            stack.remove(ModComponents.SELECTED_NODE.get());
            player.displayClientMessage(Component.translatable("message.craftteamracing.track.cleared"), true);
            return InteractionResult.CONSUME;
        }
        if (selected.pos().distSqr(pos) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
            player.displayClientMessage(Component.translatable("message.craftteamracing.track.too_far", (int) MAX_LINK_DISTANCE), true);
            return InteractionResult.FAIL;
        }

        // a podium node is hooked up to a Start node (not linked into the course): either one clicked first
        TrackGraph.Node selNode = graph.get(selected.pos().asLong());
        if (clickedNode != null && selNode != null && (clickedNode.kind.isPodium() || selNode.kind.isPodium())) {
            long podium = clickedNode.kind.isPodium() ? pos.asLong() : selected.pos().asLong();
            long start = clickedNode.kind.isPodium() ? selected.pos().asLong() : pos.asLong();
            TrackGraph.PodiumResult pr = graph.togglePodium(podium, start);
            switch (pr) {
                case HOOKED, UNHOOKED -> {
                    stack.remove(ModComponents.SELECTED_NODE.get());                 // done with this podium node: the next one can be picked at once
                    data.setDirty();
                    int place = graph.get(podium).kind.place();
                    player.displayClientMessage(Component.translatable(pr == TrackGraph.PodiumResult.HOOKED ? "message.craftteamracing.podium.hooked" : "message.craftteamracing.podium.unhooked", place), true);
                }
                default -> player.displayClientMessage(Component.translatable("message.craftteamracing.podium.needs_start"), true);
            }
            return InteractionResult.CONSUME;
        }
        TrackGraph.LinkResult result = graph.toggleLink(selected.pos().asLong(), pos.asLong());
        switch (result) {
            case LINKED, UNLINKED -> {
                data.setDirty();
                stack.set(ModComponents.SELECTED_NODE.get(), clicked);            // walk the chain
                TrackGraph.Summary s = graph.summary(pos.asLong());
                String key = result == TrackGraph.LinkResult.LINKED ? "message.craftteamracing.track.linked" : "message.craftteamracing.track.unlinked";
                player.displayClientMessage(Component.translatable(key, describe(graph, selected.pos()), describe(graph, pos))
                        .append(" ").append(Component.translatable("message.craftteamracing.track.summary", s.starts(), s.checkpoints(),
                                Component.translatable(s.loopClosed() ? "message.craftteamracing.track.loop_closed" : "message.craftteamracing.track.loop_open"))), true);
            }
            case TWO_STARTS -> player.displayClientMessage(Component.translatable("message.craftteamracing.track.two_starts"), true);
            default -> player.displayClientMessage(Component.translatable("message.craftteamracing.track.cleared"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** A viewing console is hooked up to the selected Start node (or unhooked, if it already is). */
    private InteractionResult hookConsole(UseOnContext context, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!TrackPermissions.canEdit(player, pos)) {
            player.displayClientMessage(Component.translatable("message.craftteamracing.track.no_permission"), true);
            return InteractionResult.FAIL;
        }
        GlobalPos selected = context.getItemInHand().get(ModComponents.SELECTED_NODE.get());
        TrackGraph graph = TrackData.get(server).graph();
        TrackGraph.Node node = selected == null || !selected.dimension().equals(server.dimension()) ? null : graph.get(selected.pos().asLong());
        if (node == null || node.kind != TrackGraph.Kind.START || !(level.getBlockEntity(pos) instanceof com.craftteamracing.spectate.ViewingConsoleBlockEntity be)) {
            player.displayClientMessage(Component.translatable("message.craftteamracing.console.needs_start"), true);
            return InteractionResult.CONSUME;
        }
        if (be.linked() && be.start() == selected.pos().asLong()) {
            be.unlink();
            player.displayClientMessage(Component.translatable("message.craftteamracing.console.unhooked"), true);
        } else {
            be.link(selected.pos().asLong());
            player.displayClientMessage(Component.translatable("message.craftteamracing.console.hooked"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModComponents.SELECTED_NODE.get())) {
            if (!level.isClientSide) {
                stack.remove(ModComponents.SELECTED_NODE.get());
                player.displayClientMessage(Component.translatable("message.craftteamracing.track.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static Component describe(TrackGraph graph, BlockPos pos) {
        TrackGraph.Node n = graph.get(pos.asLong());
        String kind = n == null ? "block.craftteamracing.checkpoint_node" : switch (n.kind) {
            case START -> "block.craftteamracing.start_node";
            case PODIUM_1 -> "block.craftteamracing.podium_1_node";
            case PODIUM_2 -> "block.craftteamracing.podium_2_node";
            case PODIUM_3 -> "block.craftteamracing.podium_3_node";
            default -> "block.craftteamracing.checkpoint_node";
        };
        return Component.translatable(kind).append(" (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
    }
}

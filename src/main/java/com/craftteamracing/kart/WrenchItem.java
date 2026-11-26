package com.craftteamracing.kart;

import net.minecraft.world.item.Item;

/**
 * The Wrench: right-click a kart with it to change the kart's settings. For now one setting, whether driving this kart into a Start node's
 * area can start a race (on by default, so a kart you only want to drive round freely can be switched off). The kart's tuning profile
 * is meant to live here too later, see docs/KART_TUNING.md. The behaviour is in {@link KartEntity#interact}.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    /** Crouching with the Wrench on a block that is not a node or a trigger spot (those handle it themselves) lets go of a picked Redstone Linking Node. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && dropPicked(player, context.getLevel())) return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        return super.useOn(context);
    }

    /** Crouching with the Wrench in the air lets go of a picked Redstone Linking Node. */
    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && dropPicked(player, level)) return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        return super.use(level, player, hand);
    }

    /** Lets go of the player's picked node (server side); true when there was one (the client just reports "handled" so the swing shows). */
    private static boolean dropPicked(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level) {
        if (level.isClientSide) return com.craftteamracing.pickup.RedstoneLinks.hasPicked(player.getUUID());
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp) || !com.craftteamracing.pickup.RedstoneLinks.hasPicked(sp.getUUID())) return false;
        com.craftteamracing.pickup.RedstoneLinks.clear(sp.getUUID());
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.craftteamracing.link.dropped"), true);
        return true;
    }

    /** Crouching with the Wrench still uses the block (the spots' and nodes' crouching clicks: unlinking, letting go of a picked node). */
    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return true;
    }
}

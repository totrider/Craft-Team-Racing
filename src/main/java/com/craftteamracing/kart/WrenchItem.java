package com.craftteamracing.kart;

import net.minecraft.world.item.Item;

/**
 * The Wrench: right-click a kart with it to change the kart's settings. For now one setting, whether driving this kart into a Start node's
 * area can start a race (on by default, so a kart you only want to drive round freely can be switched off). The kart's tuning profile
 * is meant to live here too later, see docs/DESIGN_KART_DATA.md. The behaviour is in {@link KartEntity#interact}.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    /** Crouching with the Wrench still uses the block (the spots' and nodes' crouching clicks: unlinking, letting go of a picked node). */
    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return true;
    }
}

package com.craftteamracing.client;

import com.craftteamracing.net.OpenSpotPayload;

import net.minecraft.client.Minecraft;

/**
 * The power-up crate spot's screen (opened by the Wrench) is the drop table's screen ({@link DropTableScreen}) editing the crate's own list: the same grid, weights by place, quantities, several
 * entries of one power-up and quantity by place. With no list of its own the crate uses the drop table. Kept as a name for the network handler.
 */
public final class CrateSpotScreen {
    private CrateSpotScreen() {}

    public static void open(OpenSpotPayload p) {
        Minecraft.getInstance().setScreen(DropTableScreen.forCrate(p.pos(), p.itemId()));
    }
}

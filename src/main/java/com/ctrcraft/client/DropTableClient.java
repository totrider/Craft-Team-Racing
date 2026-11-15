package com.ctrcraft.client;

import com.ctrcraft.DropTableData;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Opens the drop table's screen (kept apart so the item class does not load client code on a server). */
public final class DropTableClient {
    private DropTableClient() {}

    public static void open(InteractionHand hand, DropTableData data) {
        Minecraft.getInstance().setScreen(new DropTableScreen(hand, data));
    }
}

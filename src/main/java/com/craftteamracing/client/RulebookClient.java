package com.craftteamracing.client;

import com.craftteamracing.RulebookData;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Opens the rulebook's screen (kept apart so the item class does not load client code on a server). */
public final class RulebookClient {
    private RulebookClient() {}

    public static void open(InteractionHand hand, RulebookData data) {
        Minecraft.getInstance().setScreen(new RulebookScreen(hand, data));
    }
}

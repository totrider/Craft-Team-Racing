package com.ctrcraft.client;

import com.ctrcraft.MusicSheetData;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Opens the track music screen (kept apart so the item class does not load client code on a server). */
public final class MusicSheetClient {
    private MusicSheetClient() {}

    public static void open(InteractionHand hand, MusicSheetData data) {
        Minecraft.getInstance().setScreen(new MusicSheetScreen(hand, data));
    }
}

package com.craftteamracing.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The bar that fills while a key is held to leave something (a race, a viewing console): a label over a small bar near the bottom middle of the screen. */
public final class HoldBar {
    private HoldBar() {}

    public static void draw(GuiGraphics g, Component label, float progress) {
        Minecraft mc = Minecraft.getInstance();
        int bw = 80;
        int x = g.guiWidth() / 2 - bw / 2;
        int y = g.guiHeight() - 46;
        g.fill(x - 1, y - 1, x + bw + 1, y + 7, 0xFF000000);
        g.fill(x, y, x + Math.round(bw * Math.max(0.0F, Math.min(1.0F, progress))), y + 6, 0xFFE0E0E0);
        g.drawCenteredString(mc.font, label, g.guiWidth() / 2, y - 12, 0xFFFFFF);
    }

    /** The bar of leaving a race, while the leave key is held (see {@link KartDriver#leaveProgress}). */
    public static void renderLeaveRace(GuiGraphics g, DeltaTracker delta) {
        float p = KartDriver.leaveProgress();
        if (p > 0.0F && !Minecraft.getInstance().options.hideGui) draw(g, Component.translatable("hud.craftteamracing.race.leaving"), p);
    }
}

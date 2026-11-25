package com.craftteamracing.client;

import java.util.UUID;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.physics.RevEngine;
import com.craftteamracing.track.RaceClient;
import com.craftteamracing.track.RaceGrid;
import com.craftteamracing.track.RacePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The race HUD, laid out where the original game puts things. CTR draws on a 512 x 216 screen; positions below are those and are scaled to the window
 * (x by width / 512, y by height / 216). The clock and lap times are top left, the first four racers' portraits (here: skin heads) below them, your
 * place in big digits bottom left, the four start lamps top centre. The item and Power Fruit count are in {@link ItemHud}. The one difference: the lap
 * counter, which the original puts top right, sits at the right edge above the driving meters, where the original shows its map.
 */
public final class RaceHud {
    /** Racers whose portrait is red until this time (ms): hurt by a spin or a blast, 0x1e frames of 30 Hz. */
    private static final java.util.Map<UUID, Long> FLASH = new java.util.HashMap<>();

    public static void flash(UUID racer) {
        FLASH.put(racer, System.currentTimeMillis() + 1000L);
    }

    private RaceHud() {}

    private static final int ORANGE = 0xFFFF8A1F;
    private static final int PERIWINKLE = 0xFFB5A8F0;
    private static final int RED = 0xFFE04040;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFD21F;

    private static final ResourceLocation LAMP_RED_ON = lamp("red_on");
    private static final ResourceLocation LAMP_RED_OFF = lamp("red_off");
    private static final ResourceLocation LAMP_GREEN_ON = lamp("green_on");
    private static final ResourceLocation LAMP_GREEN_OFF = lamp("green_off");

    private static ResourceLocation lamp(String name) {
        return ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "textures/gui/lights/" + name + ".png");
    }

    /**
     * The reference screen's scale: GUI pixels per reference pixel across ({@code sx}) and down. The proportions of the elements do not depend on the window's
     * shape: a reference pixel is {@link KartHud#PIXEL_ASPECT} as wide as it is tall (measured on the original's screen), and each element is anchored to the left, the
     * centre or the right edge, as the original's layout is, by where it is on the reference screen.
     */
    private record Screen(float sx, float sy, int w) {
        int x(float refX) {
            if (refX < 170) return Math.round(refX * sx);
            if (refX > 342) return Math.round(w - (512.0F - refX) * sx);
            return cx(refX);
        }

        int cx(float refX) {
            return Math.round(w / 2.0F + (refX - 256.0F) * sx);
        }

        int len(float ref) {
            return Math.round(ref * sx);
        }

        int y(float refY) {
            return Math.round(refY * sy);
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        RacePayload s = RaceClient.state();
        if (s.phase() == RacePayload.OFF || mc.options.hideGui) return;
        Screen sc = new Screen(KartHud.PIXEL_ASPECT * g.guiHeight() / 216.0F, g.guiHeight() / 216.0F, g.guiWidth());
        switch (s.phase()) {
            case RacePayload.GATHER -> {
                String text = s.cooldown() ? Component.translatable("hud.craftteamracing.race.cooldown", Math.max(0, (s.ticksLeft() + 19) / 20)).getString()
                        : Component.translatable("hud.craftteamracing.race.gather", Math.max(0, (s.ticksLeft() + 19) / 20), s.karts()).getString();
                KartHud.outlined(g, mc, text, g.guiWidth() / 2 - (int) (mc.font.width(text) * 1.5F / 2), sc.y(48), 1.5F, YELLOW);          // under the power fruit counter
            }
            case RacePayload.LIGHTS -> {
                if (RaceClient.introMsLeft() > 0) break;          // the camera pans over the grid first: the lamps come after
                lights(g, sc, s.lights());
                if (!SpectateCamera.viewing()) rev(g, mc, sc);          // a viewer does not rev an engine
            }
            case RacePayload.RACING, RacePayload.DONE -> {
                // the green lamp (with the reds) stays up for 960 ms after GO
                if (s.phase() == RacePayload.RACING && s.timeMs() < RaceGrid.GREEN_SHOWN_MS) lights(g, sc, 4);
                racing(g, mc, sc, s);
            }
            default -> { }
        }
    }

    /** The racer this screen is about: the player, or the racer a viewer at a console follows. */
    private static UUID self(Minecraft mc) {
        return SpectateCamera.viewing() ? SpectateCamera.racerId() : mc.player != null ? mc.player.getUUID() : null;
    }

    /** Text at a reference position: big font is 16 px tall on the reference screen, small 8. */
    private static void text(GuiGraphics g, Minecraft mc, String t, float refX, float refY, boolean big, Screen sc, int colour) {
        float scale = (big ? 2.0F : 1.0F) * sc.sy();
        KartHud.outlined(g, mc, t, sc.x(refX), sc.y(refY), scale, colour);
    }

    /** Text that ends at a reference x. */
    private static void textRight(GuiGraphics g, Minecraft mc, String t, float refRight, float refY, boolean big, Screen sc, int colour) {
        float scale = (big ? 2.0F : 1.0F) * sc.sy();
        KartHud.outlined(g, mc, t, sc.x(refRight) - Math.round(mc.font.width(t) * scale), sc.y(refY), scale, colour);
    }

    /** DotLights_Video: four 47 x 31 lamps in a row from x 162, their bottom edge at y 72. */
    private static void lights(GuiGraphics g, Screen sc, int lit) {
        int lw = sc.len(47);
        int lh = Math.round(lw * 31.0F / 47.0F);
        int x0 = sc.cx(162);
        int y0 = sc.y(72) - lh;
        for (int i = 0; i < 4; i++) {
            boolean green = i == 3;
            boolean on = green ? lit >= 4 : i < lit;
            ResourceLocation tex = green ? (on ? LAMP_GREEN_ON : LAMP_GREEN_OFF) : (on ? LAMP_RED_ON : LAMP_RED_OFF);
            GuiTextures.drawFit(g, tex, x0 + i * lw, y0, lw, lh);
        }
    }

    /** The grid rev: a bar to the top of the engine's range, red from the acceleration speed up (where a start boost is earned). */
    private static void rev(GuiGraphics g, Minecraft mc, Screen sc) {
        RevEngine rev = KartDriver.rev();
        int w = g.guiWidth();
        int y = sc.y(72) + 14;
        String hint = Component.translatable("hud.craftteamracing.race.hold").getString();
        g.drawString(mc.font, hint, w / 2 - mc.font.width(hint) / 2, y - 12, 0xFFCFC8E0, true);
        if (rev == null) return;
        int barW = 160;
        int x = w / 2 - barW / 2;
        double top = rev.accelSpeed() + 4096.0;
        int redFrom = (int) (barW * rev.accelSpeed() / top);
        g.fill(x - 2, y - 2, x + barW + 2, y + 10, 0xFF14101C);
        g.fill(x, y, x + barW, y + 8, 0xFF2A2634);
        g.fill(x + redFrom, y, x + barW, y + 8, 0xFF5A1C24);
        int len = (int) (barW * Math.min(1.0, rev.level() / top));
        int colour = rev.state() == RevEngine.State.BUSY ? 0xFF777777 : rev.state() == RevEngine.State.READY ? 0xFFFF4040 : 0xFFF2F2F2;
        g.fill(x, y + 1, x + len, y + 7, colour);
    }

    /** The CTR clock: minutes, seconds and hundredths, 0:25:59. */
    private static String clock(int ms) {
        int cs = Math.max(0, ms) / 10;
        return String.format("%d:%02d:%02d", cs / 6000, cs / 100 % 60, cs % 100);
    }

    private static String suffix(int place) {
        if (place % 100 >= 11 && place % 100 <= 13) return "TH";
        return switch (place % 10) {
            case 1 -> "ST";
            case 2 -> "ND";
            case 3 -> "RD";
            default -> "TH";
        };
    }

    /**
     * The track bar (a stand-in for the original's map): a black line at the top left, over the clock, for one lap of the track, start line at the left, with a small box of each
     * racer's head where they are on it; yours is bigger and drawn last. The top middle is the item box and the power fruit, the bottom the hotbar, health and the driving meters.
     */
    /** The clock, the lap times and the portraits are this much lower than the original's (reference units), to leave room for the track bar above them. */
    private static final int LEFT_SHIFT = 6;
    /** The clock, the lap rows and the portraits are lower by this much again, so the big time does not run into the "TIME" label above it. */
    private static final int CLOCK_GAP = 3;
    /** And a little more between the big time and the lap rows (and the portraits below them). */
    private static final int LAP_GAP = 3;
    /** A few pixels more between the clock's rows and the first portrait. */
    private static final int PORTRAIT_GAP = 4;

    private static void trackBar(GuiGraphics g, Minecraft mc, Screen sc, RacePayload s) {
        if (s.barIds().isEmpty()) return;
        int x0 = sc.x(20);
        int width = Math.round(184 * sc.sx());          // twice as long as the clock is wide
        int y = sc.y(4.5F) + 2;
        int head = Math.max(8, Math.round(6.5F * sc.sy()));
        int thick = Math.max(2, Math.round(sc.sy() * 0.5F));
        g.fill(x0 - 1, y - thick / 2 - 1, x0 + width + 1, y + thick / 2 + thick % 2 + 1, 0x66FFFFFF);
        g.fill(x0, y - thick / 2, x0 + width, y + thick / 2 + thick % 2, 0xFF000000);
        int tick = Math.max(2, thick);
        g.fill(x0 - tick / 2, y - head / 2, x0 + tick / 2 + 1, y + head / 2, 0xFFFFFFFF);                  // the start line
        g.fill(x0 + width - tick / 2, y - head / 2, x0 + width + tick / 2 + 1, y + head / 2, 0xFFFFFFFF);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = s.barIds().size() - 1; i >= 0; i--) {
                UUID id = s.barIds().get(i);
                boolean me = id.equals(self(mc));
                if (me != (pass == 1)) continue;
                int size = me ? head + Math.max(2, Math.round(sc.sy() * 0.6F)) : head;
                int cx = x0 + Math.round(s.barPos().get(i) * width);
                int left = cx - size / 2;
                int top = y - size / 2;
                g.fill(left - 1, top - 1, left + size + 1, top + size + 1, me ? 0xFFFFD21F : 0x99000000);
                PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
                PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
                PlayerFaceRenderer.draw(g, skin, left, top, size);
                Long until = FLASH.get(id);
                if (until != null && System.currentTimeMillis() < until) g.fill(left, top, left + size, top + size, 0x90FF1010);
            }
        }
    }

    /** "Wrong way" and "checkpoint skipped", under the item box in the middle; each can be switched off in the client config. */
    private static void warnings(GuiGraphics g, Minecraft mc, Screen sc, RacePayload s) {
        boolean wrong = s.wrongWay() && com.craftteamracing.CtrClientConfig.WRONG_WAY_WARNING.get();
        boolean skipped = s.skipped() && com.craftteamracing.CtrClientConfig.SKIPPED_CHECKPOINT_WARNING.get();
        if (!wrong && !skipped) return;
        boolean blink = (System.currentTimeMillis() / 350) % 2 == 0;
        String head = Component.translatable(wrong ? "hud.craftteamracing.race.wrong_way" : "hud.craftteamracing.race.skipped").getString();
        int y = sc.y(52);
        if (blink) KartHud.outlined(g, mc, head, g.guiWidth() / 2 - Math.round(mc.font.width(head) * 2.2F / 2), y, 2.2F, RED);
    }

    private static void racing(GuiGraphics g, Minecraft mc, Screen sc, RacePayload s) {
        boolean done = s.phase() == RacePayload.DONE;
        int ms = s.timeMs();
        if (!done) ms += (int) Math.min(RaceClient.sinceUpdateMs(), 400);

        // UI_Clock: "TIME" small orange at (20, 8), the time big orange at (20, 16), a row for each lap below
        text(g, mc, Component.translatable("hud.craftteamracing.race.time").getString(), 20, 8 + LEFT_SHIFT, false, sc, ORANGE);
        text(g, mc, clock(ms), 20, 16 + LEFT_SHIFT + CLOCK_GAP, true, sc, ORANGE);
        int finished = s.lapTimes().size();
        int sum = 0;
        for (int t : s.lapTimes()) sum += t;
        for (int n = 1; n <= Math.min(s.laps(), finished + (done ? 0 : 1)); n++) {
            int lapMs = n <= finished ? s.lapTimes().get(n - 1) : ms - sum;
            float y = 8 + 8 * n + 16 + LEFT_SHIFT + CLOCK_GAP + LAP_GAP;
            text(g, mc, "L" + n, 20, y, false, sc, RED);
            text(g, mc, clock(lapMs), 46, y, false, sc, PERIWINKLE);
        }

        // UI_Rank: the first four racers, a portrait at (20, 57 + 27 i) and the place beside it
        int row = Math.round(27 * sc.sy());
        int head = Math.round(24 * sc.sy());
        for (int i = 0; i < s.order().size(); i++) {
            UUID id = s.order().get(i);
            int top = sc.y(57 + LEFT_SHIFT + CLOCK_GAP + LAP_GAP) + PORTRAIT_GAP + i * row;
            g.fill(sc.x(20) - 1, top - 1, sc.x(20) + head + 1, top + head + 1, 0x99000000);      // the original's dark, half-transparent box; nobody is marked
            PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
            PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
            PlayerFaceRenderer.draw(g, skin, sc.x(20), top, head);
            Long until = FLASH.get(id);
            if (until != null && System.currentTimeMillis() < until) g.fill(sc.x(20), top, sc.x(20) + head, top + head, 0x90FF1010);      // damageColorTimer: the icon drawn red
            // the place sits on the top right corner of the box, its right edge a pixel or so beyond it (the original's), not beside it
            String placeText = Integer.toString(i + 1);
            float numScale = sc.sy();
            int numW = Math.round((mc.font.width(placeText) - 1) * numScale);
            KartHud.outlined(g, mc, placeText, sc.x(20) + head + Math.max(1, Math.round(sc.sy() * 0.2F)) - numW, top - Math.round(1.5F * sc.sy()), numScale, WHITE);
        }

        trackBar(g, mc, sc, s);
        warnings(g, mc, sc, s);
        String debug = RaceDebugClient.info();
        if (debug != null) KartHud.outlined(g, mc, debug, g.guiWidth() - 6 - mc.font.width(debug), sc.y(112), 1.0F, WHITE);          // the debug view's line of text about the racer's progress

        // UI_DrawPosSuffix: the place in big digits, centred at (50, 191), its suffix big orange at (70, 171)
        String place = Integer.toString(done ? s.finishPlace() : s.place());
        float digit = 6.0F * sc.sy();
        int dw = Math.round(mc.font.width(place) * digit);
        KartHud.outlined(g, mc, place, sc.x(50) - dw / 2, sc.y(191) - Math.round(4 * digit), digit, ORANGE);
        text(g, mc, suffix(done ? s.finishPlace() : s.place()), 70 + (place.length() - 1) * 6, 171, true, sc, ORANGE);

        // the lap counter: the original has it top right; here, above the driving meters at the right edge (where the original shows its map)
        String lap = Component.translatable("hud.craftteamracing.race.lapcount", Math.min(s.lap(), s.laps()), s.laps()).getString();
        int right = g.guiWidth() - 12;
        int top = g.guiHeight() - 70 - 80;
        float lapScale = 2.2F;
        KartHud.outlined(g, mc, Component.translatable("hud.craftteamracing.race.lapword").getString(),
                right - Math.round(mc.font.width(Component.translatable("hud.craftteamracing.race.lapword").getString()) * 1.4F), top, 1.4F, PERIWINKLE);
        KartHud.outlined(g, mc, lap, right - Math.round(mc.font.width(lap) * lapScale), top + 14, lapScale, PERIWINKLE);
        if (done) {
            String fin = Component.translatable("hud.craftteamracing.race.finished", s.finishPlace()).getString();
            KartHud.outlined(g, mc, fin, g.guiWidth() / 2 - Math.round(mc.font.width(fin) * 2.0F / 2), sc.y(70), 2.0F, YELLOW);
        }
    }
}

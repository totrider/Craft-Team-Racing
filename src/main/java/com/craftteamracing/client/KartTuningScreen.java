package com.craftteamracing.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.craftteamracing.net.SetDebugTuningPayload;
import com.craftteamracing.net.SetTuningPayload;
import com.craftteamracing.physics.KartStats;
import com.craftteamracing.tuning.TuningClient;
import com.craftteamracing.tuning.TuningProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The kart's tuning (opened from the Wrench's settings): the profiles the server offers as a list on the left (the original game's four engine classes and whatever data packs add),
 * and for the one picked its numbers as bars on the right, with a tick for what the kart has now. "Use" sends the choice; the server checks it against its own list.
 * An operator also has a "Debug" entry: its bars are sliders (any numbers the simulation can run on) that can be used on the kart at once, and exported as a data pack file.
 */
public class KartTuningScreen extends Screen {
    private static final int ROWS = 8;
    private static final int ROW_H = 22;
    private static final String DEBUG = "debug";
    /** The debug sliders: label key, lowest, highest. */
    private static final Object[][] RANGES = {
            { "screen.craftteamracing.tuning.accel", 100, 1500 }, { "screen.craftteamracing.tuning.speed", 6000, 30000 }, { "screen.craftteamracing.tuning.turn", 5, 80 },
            { "screen.craftteamracing.tuning.turn_decrease", 0, 20000 }, { "screen.craftteamracing.tuning.response", 1000, 12000 }, { "screen.craftteamracing.tuning.drift", 0, 60 },
            { "screen.craftteamracing.tuning.weight", 50, 800 } };

    private final Screen parent;
    private final int kartId;
    private final Consumer<String> onUsed;
    private final List<TuningProfile> profiles = new ArrayList<>();
    private final boolean operator;
    private String current;
    private String picked;
    private int scroll;
    private final int[] debug = new int[7];

    public KartTuningScreen(Screen parent, int kartId, String current, Consumer<String> onUsed) {
        super(Component.translatable("screen.craftteamracing.tuning.title"));
        this.parent = parent;
        this.kartId = kartId;
        this.onUsed = onUsed;
        this.profiles.addAll(TuningClient.profiles());
        this.operator = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
        if (operator) profiles.add(new TuningProfile(DEBUG, Component.translatable("screen.craftteamracing.tuning.debug").getString(), Component.translatable("screen.craftteamracing.tuning.debug_hint").getString(), false, KartStats.BALANCED));
        TuningProfile now = TuningClient.resolve(current);
        this.current = now.id().startsWith(TuningProfile.DEBUG_PREFIX) ? DEBUG : now.id();
        this.picked = this.current;
        loadDebug(now.stats());
    }

    private void loadDebug(KartStats s) {
        debug[0] = s.accel();
        debug[1] = s.speed();
        debug[2] = s.turnRate();
        debug[3] = s.turnDecrease();
        debug[4] = s.turnInputDelay();
        debug[5] = s.driftTurnBase();
        debug[6] = s.weight();
    }

    private KartStats debugStats() {
        return new KartStats(debug[0], debug[1], debug[2], debug[3], debug[4], debug[5], debug[6]);
    }

    private int left() {
        return width / 2 - 210;
    }

    private boolean debugPicked() {
        return DEBUG.equals(picked);
    }

    private TuningProfile profile(String id) {
        if (DEBUG.equals(id)) return new TuningProfile(DEBUG, Component.translatable("screen.craftteamracing.tuning.debug").getString(), Component.translatable("screen.craftteamracing.tuning.debug_hint").getString(), false, debugStats());
        for (TuningProfile p : profiles) {
            if (p.id().equals(id)) return p;
        }
        return TuningProfile.FALLBACK;
    }

    /** A debug slider: a stat from its lowest to its highest number. */
    private final class DebugSlider extends AbstractSliderButton {
        private final int index;
        private final int lo;
        private final int hi;
        private final String key;

        DebugSlider(int x, int y, int w, int index) {
            super(x, y, w, 16, Component.empty(), 0.0);
            this.index = index;
            this.key = (String) RANGES[index][0];
            this.lo = (int) RANGES[index][1];
            this.hi = (int) RANGES[index][2];
            this.value = Math.max(0.0, Math.min(1.0, (debug[index] - lo) / (double) (hi - lo)));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key).append(": " + (lo + (int) Math.round(value * (hi - lo)))));
        }

        @Override
        protected void applyValue() {
            debug[index] = lo + (int) Math.round(value * (hi - lo));
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        int top = 34;
        int x = left();
        int max = Math.max(0, profiles.size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll));
        for (int i = 0; i < ROWS && scroll + i < profiles.size(); i++) {
            TuningProfile p = profiles.get(scroll + i);
            Component label = Component.literal((p.id().equals(current) ? "✔ " : "") + p.name());
            Button b = Button.builder(label, btn -> {
                picked = p.id();
                init();
            }).bounds(x, top + i * ROW_H, 140, ROW_H - 2).build();
            b.active = !p.id().equals(picked);                     // the picked one is shown pressed
            addRenderableWidget(b);
        }
        if (max > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                scroll--;
                init();
            }).bounds(x, top + ROWS * ROW_H + 2, 68, 18).build()).active = scroll > 0;
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                scroll++;
                init();
            }).bounds(x + 72, top + ROWS * ROW_H + 2, 68, 18).build()).active = scroll < max;
        }
        if (debugPicked()) {
            for (int i = 0; i < 7; i++) addRenderableWidget(new DebugSlider(left() + 160, 66 + i * 20, 244, i));
        }
        int y = top + ROWS * ROW_H + 26;
        Button use = Button.builder(Component.translatable("screen.craftteamracing.tuning.use"), b -> use()).bounds(width / 2 - 102, y, 100, 20).build();
        use.active = debugPicked() || (!picked.equals(current) && !profiles.isEmpty());
        addRenderableWidget(use);
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.tuning.back"), b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
        if (debugPicked()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.tuning.export"), b -> export()).bounds(width / 2 + 106, y, 100, 20).build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (profiles.size() > ROWS && mouseX < left() + 145) {
            scroll -= (int) Math.signum(scrollY);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void use() {
        if (debugPicked()) {
            PacketDistributor.sendToServer(new SetDebugTuningPayload(kartId, debug[0], debug[1], debug[2], debug[3], debug[4], debug[5], debug[6]));
            current = DEBUG;
            onUsed.accept(TuningProfile.debugId(debugStats()));
        } else {
            PacketDistributor.sendToServer(new SetTuningPayload(kartId, picked));
            current = picked;
            onUsed.accept(picked);
        }
        onClose();
    }

    /** Writes the debug numbers as a data pack entry (data/<namespace>/craftteamracing/kart_tuning/<name>.json) into the game folder's craftteamracing-tuning-exports. */
    private void export() {
        KartStats s = debugStats();
        String json = "{\n  \"name\": \"Custom\",\n  \"description\": \"Exported from the debug tuning.\",\n  \"official\": false,\n  \"enabled\": true,\n  \"stats\": {\n"
                + "    \"accel\": " + s.accel() + ",\n    \"speed\": " + s.speed() + ",\n    \"turn_rate\": " + s.turnRate() + ",\n    \"turn_decrease\": " + s.turnDecrease() + ",\n"
                + "    \"turn_input_delay\": " + s.turnInputDelay() + ",\n    \"drift_turn_base\": " + s.driftTurnBase() + ",\n    \"weight\": " + s.weight() + "\n  }\n}\n";
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("craftteamracing-tuning-exports");
        Path file = dir.resolve("tuning_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("message.craftteamracing.tuning.exported", file.toString()), false);
        } catch (IOException e) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("message.craftteamracing.tuning.export_failed", e.getMessage()), false);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** A dim panel behind the numbers, so they read against whatever is behind the screen. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = left() + 150;
        g.fill(x, 30, x + 270, 34 + ROWS * ROW_H, 0xA0000000);
    }

    private void bar(GuiGraphics g, int x, int y, String key, int value, int currentValue, int lo, int hi) {
        g.drawString(font, Component.translatable(key), x, y, 0xC0C0C0, false);
        int bx = x + 112;
        int bw = 80;
        g.fill(bx, y + 2, bx + bw, y + 8, 0xFF303030);
        double f = Math.max(0.04, Math.min(1.0, (value - lo) / (double) (hi - lo)));
        g.fill(bx, y + 2, bx + (int) Math.round(bw * f), y + 8, 0xFFE8B030);
        double c = Math.max(0.0, Math.min(1.0, (currentValue - lo) / (double) (hi - lo)));
        g.fill(bx + (int) Math.round((bw - 1) * c), y, bx + (int) Math.round((bw - 1) * c) + 1, y + 10, 0xFFFFFFFF);                // what the kart has now
        g.drawString(font, Integer.toString(value), bx + bw + 6, y, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        TuningProfile p = profile(picked);
        int x = left() + 160;
        int y = 36;
        g.drawString(font, p.name(), x, y, 0xFFD040, false);
        if (debugPicked()) {
            g.drawString(font, Component.translatable("screen.craftteamracing.tuning.debug_hint"), x, y + 11, 0xFF9060, false);
            return;
        }
        KartStats s = p.stats();
        KartStats now = profile(current).stats();
        g.drawString(font, Component.translatable(p.official() ? "screen.craftteamracing.tuning.official" : "screen.craftteamracing.tuning.custom"), x, y + 11, p.official() ? 0x60D060 : 0x60C0F0, false);
        y += 26;
        for (FormattedCharSequence line : font.split(Component.literal(p.description()), 250)) {
            g.drawString(font, line, x, y, 0xA0A0A0, false);
            y += 10;
        }
        y = Math.max(y + 6, 36 + 26 + 36);
        bar(g, x, y, "screen.craftteamracing.tuning.accel", s.accel(), now.accel(), 350, 650);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.speed", s.speed(), now.speed(), 12500, 14500);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.turn", s.turnRate(), now.turnRate(), 20, 34);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.turn_decrease", s.turnDecrease(), now.turnDecrease(), 3000, 10000);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.response", s.turnInputDelay(), now.turnInputDelay(), 3500, 6000);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.drift", s.driftTurnBase(), now.driftTurnBase(), 3, 20);
        bar(g, x, y += 14, "screen.craftteamracing.tuning.weight", s.weight(), now.weight(), 180, 320);
        g.drawString(font, Component.translatable("screen.craftteamracing.tuning.tick"), x, y + 20, 0x808080, false);
    }
}

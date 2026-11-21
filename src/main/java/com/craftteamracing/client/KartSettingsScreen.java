package com.craftteamracing.client;

import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.net.KartSettingsPayload;
import com.craftteamracing.net.OpenKartSettingsPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The kart's settings, opened by the Wrench: whether it can start races, and the switches of the upgrades it has (the others are greyed out until the upgrade item is used on it). */
public class KartSettingsScreen extends Screen {
    private final int kartId;
    private boolean canStart;
    private final int upgrades;
    private boolean offroad;
    private boolean softBumper;
    private boolean darkMask;
    private boolean floater;
    private boolean paddle;
    private boolean skis;
    private int spring;                  // tenths, 0 to 10
    private int gravity;                 // tenths, 0 to 10
    private String tuning;

    public static void open(OpenKartSettingsPayload p) {
        Minecraft.getInstance().setScreen(new KartSettingsScreen(p));
    }

    private KartSettingsScreen(OpenKartSettingsPayload p) {
        super(Component.translatable("screen.craftteamracing.kart.title"));
        this.kartId = p.kartId();
        this.canStart = p.canStartRaces();
        this.tuning = p.tuning();
        this.upgrades = p.upgrades();
        this.darkMask = p.darkMask();
        this.floater = (p.upgrades() & KartEntity.UP_FLOATER_ON) != 0;
        this.paddle = (p.upgrades() & KartEntity.UP_PADDLE_ON) != 0;
        this.skis = (p.upgrades() & KartEntity.UP_SKIS_ON) != 0;
        this.spring = (p.upgrades() & KartEntity.UP_SPRING_UNLOCKED) == 0 ? 0 : ((p.upgrades() >> KartEntity.UP_SPRING_SHIFT) & 15) == 0 && (p.upgrades() & KartEntity.UP_SPRING_ON) != 0 ? 5 : (p.upgrades() >> KartEntity.UP_SPRING_SHIFT) & 15;
        this.gravity = (p.upgrades() >> KartEntity.UP_GRAVITY_SHIFT) & 15;
        this.offroad = (p.upgrades() & KartEntity.UP_OFFROAD_ON) != 0;
        this.softBumper = (p.upgrades() & KartEntity.UP_BUMPER_ON) != 0;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.craftteamracing.rulebook.on" : "screen.craftteamracing.rulebook.off");
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = 28;
        // the tuning is the first option: its own screen (the profiles and their numbers) opens from here, and sends its choice by itself
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.kart.tuning", com.craftteamracing.tuning.TuningClient.resolve(tuning).name()),
                b -> minecraft.setScreen(new KartTuningScreen(this, kartId, tuning, id -> tuning = id))).bounds(x, y, 200, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.kart.races", onOff(canStart)), b -> {
            canStart = !canStart;
            b.setMessage(Component.translatable("screen.craftteamracing.kart.races", onOff(canStart)));
        }).bounds(x, y, 200, 20).build());
        upgradeButton(x, y += 24, "screen.craftteamracing.kart.offroad", KartEntity.UP_OFFROAD_UNLOCKED, () -> offroad, v -> offroad = v, "screen.craftteamracing.kart.offroad_hint");
        upgradeButton(x, y += 24, "screen.craftteamracing.kart.soft_bumper", KartEntity.UP_BUMPER_UNLOCKED, () -> softBumper, v -> softBumper = v, "screen.craftteamracing.kart.bumper_hint");
        upgradeButton(x, y += 24, "screen.craftteamracing.kart.floater", KartEntity.UP_FLOATER_UNLOCKED, () -> floater, v -> floater = v, "screen.craftteamracing.kart.floater_hint");
        upgradeButton(x, y += 24, "screen.craftteamracing.kart.paddle", KartEntity.UP_PADDLE_UNLOCKED, () -> paddle, v -> paddle = v, "screen.craftteamracing.kart.paddle_hint");
        upgradeButton(x, y += 24, "screen.craftteamracing.kart.skis", KartEntity.UP_SKIS_UNLOCKED, () -> skis, v -> skis = v, "screen.craftteamracing.kart.skis_hint");
        boolean hasSpring = (upgrades & KartEntity.UP_SPRING_UNLOCKED) != 0;
        StrengthSlider springSlider = new StrengthSlider(x, y += 24, 200, 20, hasSpring, "screen.craftteamracing.kart.spring", () -> spring, v -> spring = v);
        springSlider.setTooltip(Tooltip.create(Component.translatable(hasSpring ? "screen.craftteamracing.kart.spring_hint" : "screen.craftteamracing.kart.locked_hint")));
        addRenderableWidget(springSlider);
        boolean hasGravity = (upgrades & KartEntity.UP_GRAVITY_UNLOCKED) != 0;
        StrengthSlider slider = new StrengthSlider(x, y += 24, 200, 20, hasGravity, "screen.craftteamracing.kart.gravity", () -> gravity, v -> gravity = v);
        slider.setTooltip(Tooltip.create(Component.translatable(hasGravity ? "screen.craftteamracing.kart.gravity_hint" : "screen.craftteamracing.kart.locked_hint")));
        addRenderableWidget(slider);
        addRenderableWidget(Button.builder(maskLabel(), b -> {
            darkMask = !darkMask;
            b.setMessage(maskLabel());
        }).bounds(x, y += 24, 200, 20).tooltip(Tooltip.create(Component.translatable("screen.craftteamracing.kart.mask_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(x, y += 30, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(x + 102, y, 98, 20).build());
    }

    /** A strength upgrade's slider: 0 to 100 % in steps of 10 (the Low Gravity Module, the Jump Spring). */
    private final class StrengthSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String key;
        private final java.util.function.IntSupplier get;
        private final java.util.function.IntConsumer set;

        StrengthSlider(int x, int y, int w, int h, boolean installed, String key, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            super(x, y, w, h, Component.empty(), get.getAsInt() / 10.0);
            this.key = key;
            this.get = get;
            this.set = set;
            this.active = installed;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, active ? Component.literal(get.getAsInt() * 10 + "%") : Component.translatable("screen.craftteamracing.kart.not_installed")));
        }

        @Override
        protected void applyValue() {
            set.accept((int) Math.round(value * 10.0));
            value = get.getAsInt() / 10.0;
        }
    }

    private Component maskLabel() {
        // the names of the original's masks only when its textures are in use (a pack made from the player's own disc)
        boolean named = OriginalPacks.textures();
        return Component.translatable("screen.craftteamracing.kart.mask", Component.translatable(darkMask ? (named ? "screen.craftteamracing.kart.mask_dark" : "screen.craftteamracing.kart.mask_dark_plain")
                : (named ? "screen.craftteamracing.kart.mask_light" : "screen.craftteamracing.kart.mask_light_plain")));
    }

    private void upgradeButton(int x, int y, String key, int unlockBit, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, String hint) {
        boolean has = (upgrades & unlockBit) != 0;
        Component label = Component.translatable(key, has ? onOff(get.getAsBoolean()) : Component.translatable("screen.craftteamracing.kart.not_installed"));
        Button b = Button.builder(label, btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.translatable(key, onOff(get.getAsBoolean())));
        }).bounds(x, y, 200, 20).tooltip(Tooltip.create(Component.translatable(has ? hint : "screen.craftteamracing.kart.locked_hint"))).build();
        b.active = has;                                   // greyed out until the upgrade is installed
        addRenderableWidget(b);
    }

    private void save() {
        PacketDistributor.sendToServer(new KartSettingsPayload(kartId, canStart, darkMask, (offroad ? KartEntity.UP_OFFROAD_ON : 0) | (softBumper ? KartEntity.UP_BUMPER_ON : 0) | (floater ? KartEntity.UP_FLOATER_ON : 0) | (paddle ? KartEntity.UP_PADDLE_ON : 0) | (skis ? KartEntity.UP_SKIS_ON : 0), gravity, spring));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
    }
}

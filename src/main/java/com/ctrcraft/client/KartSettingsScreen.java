package com.ctrcraft.client;

import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.net.KartSettingsPayload;
import com.ctrcraft.net.OpenKartSettingsPayload;

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
    private boolean spring;
    private int gravity;                 // tenths, 0 to 10

    public static void open(OpenKartSettingsPayload p) {
        Minecraft.getInstance().setScreen(new KartSettingsScreen(p));
    }

    private KartSettingsScreen(OpenKartSettingsPayload p) {
        super(Component.translatable("screen.ctrcraft.kart.title"));
        this.kartId = p.kartId();
        this.canStart = p.canStartRaces();
        this.upgrades = p.upgrades();
        this.darkMask = p.darkMask();
        this.floater = (p.upgrades() & KartEntity.UP_FLOATER_ON) != 0;
        this.spring = (p.upgrades() & KartEntity.UP_SPRING_ON) != 0;
        this.gravity = (p.upgrades() >> KartEntity.UP_GRAVITY_SHIFT) & 15;
        this.offroad = (p.upgrades() & KartEntity.UP_OFFROAD_ON) != 0;
        this.softBumper = (p.upgrades() & KartEntity.UP_BUMPER_ON) != 0;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.ctrcraft.rulebook.on" : "screen.ctrcraft.rulebook.off");
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.kart.races", onOff(canStart)), b -> {
            canStart = !canStart;
            b.setMessage(Component.translatable("screen.ctrcraft.kart.races", onOff(canStart)));
        }).bounds(x, y, 200, 20).build());
        upgradeButton(x, y += 24, "screen.ctrcraft.kart.offroad", KartEntity.UP_OFFROAD_UNLOCKED, () -> offroad, v -> offroad = v, "screen.ctrcraft.kart.offroad_hint");
        upgradeButton(x, y += 24, "screen.ctrcraft.kart.soft_bumper", KartEntity.UP_BUMPER_UNLOCKED, () -> softBumper, v -> softBumper = v, "screen.ctrcraft.kart.bumper_hint");
        upgradeButton(x, y += 24, "screen.ctrcraft.kart.floater", KartEntity.UP_FLOATER_UNLOCKED, () -> floater, v -> floater = v, "screen.ctrcraft.kart.floater_hint");
        upgradeButton(x, y += 24, "screen.ctrcraft.kart.spring", KartEntity.UP_SPRING_UNLOCKED, () -> spring, v -> spring = v, "screen.ctrcraft.kart.spring_hint");
        boolean hasGravity = (upgrades & KartEntity.UP_GRAVITY_UNLOCKED) != 0;
        GravitySlider slider = new GravitySlider(x, y += 24, 200, 20, hasGravity);
        slider.setTooltip(Tooltip.create(Component.translatable(hasGravity ? "screen.ctrcraft.kart.gravity_hint" : "screen.ctrcraft.kart.locked_hint")));
        addRenderableWidget(slider);
        addRenderableWidget(Button.builder(maskLabel(), b -> {
            darkMask = !darkMask;
            b.setMessage(maskLabel());
        }).bounds(x, y += 24, 200, 20).tooltip(Tooltip.create(Component.translatable("screen.ctrcraft.kart.mask_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.done"), b -> save()).bounds(x, y += 30, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.cancel"), b -> onClose()).bounds(x + 102, y, 98, 20).build());
    }

    /** The Low Gravity Module's strength: 0 to 100 % in steps of 10. */
    private final class GravitySlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        GravitySlider(int x, int y, int w, int h, boolean installed) {
            super(x, y, w, h, Component.empty(), gravity / 10.0);
            this.active = installed;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.ctrcraft.kart.gravity",
                    active ? Component.literal(gravity * 10 + "%") : Component.translatable("screen.ctrcraft.kart.not_installed")));
        }

        @Override
        protected void applyValue() {
            gravity = (int) Math.round(value * 10.0);
            value = gravity / 10.0;
        }
    }

    private Component maskLabel() {
        return Component.translatable("screen.ctrcraft.kart.mask", Component.translatable(darkMask ? "screen.ctrcraft.kart.mask_dark" : "screen.ctrcraft.kart.mask_light"));
    }

    private void upgradeButton(int x, int y, String key, int unlockBit, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, String hint) {
        boolean has = (upgrades & unlockBit) != 0;
        Component label = Component.translatable(key, has ? onOff(get.getAsBoolean()) : Component.translatable("screen.ctrcraft.kart.not_installed"));
        Button b = Button.builder(label, btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Component.translatable(key, onOff(get.getAsBoolean())));
        }).bounds(x, y, 200, 20).tooltip(Tooltip.create(Component.translatable(has ? hint : "screen.ctrcraft.kart.locked_hint"))).build();
        b.active = has;                                   // greyed out until the upgrade is installed
        addRenderableWidget(b);
    }

    private void save() {
        PacketDistributor.sendToServer(new KartSettingsPayload(kartId, canStart, darkMask, (offroad ? KartEntity.UP_OFFROAD_ON : 0) | (softBumper ? KartEntity.UP_BUMPER_ON : 0) | (floater ? KartEntity.UP_FLOATER_ON : 0) | (spring ? KartEntity.UP_SPRING_ON : 0), gravity));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
    }
}

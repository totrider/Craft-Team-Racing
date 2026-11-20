package com.craftteamracing.client;

import com.craftteamracing.RulebookData;
import com.craftteamracing.items.PowerupTable;
import com.craftteamracing.items.RuleSet;
import com.craftteamracing.net.RulebookSavePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The rulebook's screen: the toggles of a RuleSet, a cycle for the top speed cap, and whether applying it sets the track's default or only the next race. The drop table (which power-ups
 * crates give) is not here: it has an item of its own, so it can be swapped without touching the rules. Saving sends the rules to the server, which writes them on the rulebook in that hand.
 */
public class RulebookScreen extends Screen {
    private static final int[] SPEEDS = { 0, 100, 90, 80, 70, 60, 50 };

    private final InteractionHand hand;
    /** The power-up table the book came with, kept as it is (an older rulebook may hold one). */
    private final PowerupTable table;
    private boolean fruit;
    private boolean fruitCrates;
    private boolean powerupCrates;
    private boolean boostPads;
    private boolean driverDamage;
    private boolean hunger;
    private boolean offroad;
    private boolean softBumper;
    private int floater;
    private int spring;
    private int lowGravity;
    private boolean paddle;
    private boolean asDefault;
    private int maxSpeed;

    public RulebookScreen(InteractionHand hand, RulebookData data) {
        super(Component.translatable("screen.craftteamracing.rulebook.title"));
        this.hand = hand;
        RuleSet r = data.rules();
        table = r.powerups();
        fruit = r.fruit();
        fruitCrates = r.fruitCrates();
        powerupCrates = r.powerupCrates();
        boostPads = r.boostPads();
        driverDamage = r.driverDamage();
        hunger = r.hunger();
        offroad = r.offroad();
        softBumper = r.softBumper();
        floater = r.floater();
        spring = r.spring();
        lowGravity = r.lowGravity();
        paddle = r.paddle();
        maxSpeed = r.maxSpeedPercent();
        asDefault = data.asDefault();
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.craftteamracing.rulebook.on" : "screen.craftteamracing.rulebook.off");
    }

    private void toggle(int x, int y, String key, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(Component.translatable(key, onOff(get.getAsBoolean())), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(Component.translatable(key, onOff(get.getAsBoolean())));
        }).bounds(x, y, 145, 20).build());
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        int right = width / 2 + 5;
        toggle(left, 24, "screen.craftteamracing.rulebook.fruit", () -> fruit, v -> fruit = v);
        toggle(right, 24, "screen.craftteamracing.rulebook.fruit_crates", () -> fruitCrates, v -> fruitCrates = v);
        toggle(left, 48, "screen.craftteamracing.rulebook.powerup_crates", () -> powerupCrates, v -> powerupCrates = v);
        toggle(right, 48, "screen.craftteamracing.rulebook.boost_pads", () -> boostPads, v -> boostPads = v);
        toggle(left, 72, "screen.craftteamracing.rulebook.driver_damage", () -> driverDamage, v -> driverDamage = v);
        toggle(right, 72, "screen.craftteamracing.rulebook.hunger", () -> hunger, v -> hunger = v);
        toggle(left, 96, "screen.craftteamracing.rulebook.offroad", () -> offroad, v -> offroad = v);
        toggle(right, 96, "screen.craftteamracing.rulebook.soft_bumper", () -> softBumper, v -> softBumper = v);
        addRenderableWidget(Button.builder(floaterLabel(), b -> {
            floater = (floater + 1) % 3;
            b.setMessage(floaterLabel());
        }).bounds(left, 120, 145, 20).build());
        addRenderableWidget(Button.builder(springLabel(), b -> {
            spring = (spring + 1) % 11;
            b.setMessage(springLabel());
        }).bounds(right, 120, 145, 20).build());
        toggle(right, 144, "screen.craftteamracing.rulebook.paddle", () -> paddle, v -> paddle = v);
        addRenderableWidget(Button.builder(gravityLabel(), b -> {
            lowGravity = (lowGravity + 1) % 11;
            b.setMessage(gravityLabel());
        }).bounds(left, 144, 145, 20).build());
        addRenderableWidget(Button.builder(speedLabel(), b -> {
            int at = 0;
            for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == maxSpeed) at = i;
            maxSpeed = SPEEDS[(at + 1) % SPEEDS.length];
            b.setMessage(speedLabel());
        }).bounds(left, 168, 145, 20).build());
        addRenderableWidget(Button.builder(defaultLabel(), b -> {
            asDefault = !asDefault;
            b.setMessage(defaultLabel());
        }).bounds(right, 168, 145, 20).build());
        int bottom = Math.max(height - 28, 196);
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(left, bottom, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(right, bottom, 145, 20).build());
    }

    private Component floaterLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.floater", Component.translatable("screen.craftteamracing.rulebook.floater_" + floater));
    }

    private Component springLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.spring", spring == 0 ? Component.translatable("screen.craftteamracing.rulebook.off") : Component.literal(spring * 10 + "%"));
    }

    private Component gravityLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.low_gravity", lowGravity == 0 ? Component.translatable("screen.craftteamracing.rulebook.off") : Component.literal(lowGravity * 10 + "%"));
    }

    private Component speedLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.speed_cap", maxSpeed == 0 ? Component.translatable("screen.craftteamracing.rulebook.none") : Component.literal(maxSpeed + "%"));
    }

    private Component defaultLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.apply_as", Component.translatable(asDefault ? "screen.craftteamracing.rulebook.as_default" : "screen.craftteamracing.rulebook.as_temporary"));
    }

    private void save() {
        RuleSet rules = new RuleSet(table, fruit, fruitCrates, powerupCrates, boostPads, maxSpeed, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle);
        PacketDistributor.sendToServer(new RulebookSavePayload(hand.ordinal(), new RulebookData(rules, asDefault)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
    }
}

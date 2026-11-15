package com.ctrcraft.client;

import com.ctrcraft.RulebookData;
import com.ctrcraft.items.PowerupTable;
import com.ctrcraft.items.RuleSet;
import com.ctrcraft.net.RulebookSavePayload;

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
    private boolean spring;
    private int lowGravity;
    private boolean asDefault;
    private int maxSpeed;

    public RulebookScreen(InteractionHand hand, RulebookData data) {
        super(Component.translatable("screen.ctrcraft.rulebook.title"));
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
        maxSpeed = r.maxSpeedPercent();
        asDefault = data.asDefault();
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.ctrcraft.rulebook.on" : "screen.ctrcraft.rulebook.off");
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
        toggle(left, 24, "screen.ctrcraft.rulebook.fruit", () -> fruit, v -> fruit = v);
        toggle(right, 24, "screen.ctrcraft.rulebook.fruit_crates", () -> fruitCrates, v -> fruitCrates = v);
        toggle(left, 48, "screen.ctrcraft.rulebook.powerup_crates", () -> powerupCrates, v -> powerupCrates = v);
        toggle(right, 48, "screen.ctrcraft.rulebook.boost_pads", () -> boostPads, v -> boostPads = v);
        toggle(left, 72, "screen.ctrcraft.rulebook.driver_damage", () -> driverDamage, v -> driverDamage = v);
        toggle(right, 72, "screen.ctrcraft.rulebook.hunger", () -> hunger, v -> hunger = v);
        toggle(left, 96, "screen.ctrcraft.rulebook.offroad", () -> offroad, v -> offroad = v);
        toggle(right, 96, "screen.ctrcraft.rulebook.soft_bumper", () -> softBumper, v -> softBumper = v);
        addRenderableWidget(Button.builder(floaterLabel(), b -> {
            floater = (floater + 1) % 3;
            b.setMessage(floaterLabel());
        }).bounds(left, 120, 145, 20).build());
        toggle(right, 120, "screen.ctrcraft.rulebook.spring", () -> spring, v -> spring = v);
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
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.done"), b -> save()).bounds(left, bottom, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.cancel"), b -> onClose()).bounds(right, bottom, 145, 20).build());
    }

    private Component floaterLabel() {
        return Component.translatable("screen.ctrcraft.rulebook.floater", Component.translatable("screen.ctrcraft.rulebook.floater_" + floater));
    }

    private Component gravityLabel() {
        return Component.translatable("screen.ctrcraft.rulebook.low_gravity", lowGravity == 0 ? Component.translatable("screen.ctrcraft.rulebook.off") : Component.literal(lowGravity * 10 + "%"));
    }

    private Component speedLabel() {
        return Component.translatable("screen.ctrcraft.rulebook.speed_cap", maxSpeed == 0 ? Component.translatable("screen.ctrcraft.rulebook.none") : Component.literal(maxSpeed + "%"));
    }

    private Component defaultLabel() {
        return Component.translatable("screen.ctrcraft.rulebook.apply_as", Component.translatable(asDefault ? "screen.ctrcraft.rulebook.as_default" : "screen.ctrcraft.rulebook.as_temporary"));
    }

    private void save() {
        RuleSet rules = new RuleSet(table, fruit, fruitCrates, powerupCrates, boostPads, maxSpeed, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
        PacketDistributor.sendToServer(new RulebookSavePayload(hand.ordinal(), new RulebookData(rules, asDefault)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
    }
}

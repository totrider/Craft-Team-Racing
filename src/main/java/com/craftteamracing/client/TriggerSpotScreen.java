package com.craftteamracing.client;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import com.craftteamracing.net.OpenTriggerPayload;
import com.craftteamracing.net.SetTriggerPayload;
import com.craftteamracing.pickup.TriggerTime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Trigger Crate Spot's settings (opened by the Wrench): the respawn timer, how long the redstone signal lasts, and how many times it can be used. */
public class TriggerSpotScreen extends Screen {
    private final BlockPos pos;
    private int respawn;
    private int duration;
    private int uses;

    public static void open(OpenTriggerPayload p) {
        Minecraft.getInstance().setScreen(new TriggerSpotScreen(p));
    }

    private TriggerSpotScreen(OpenTriggerPayload p) {
        super(Component.translatable("screen.craftteamracing.trigger.title"));
        this.pos = p.pos();
        this.respawn = p.respawn();
        this.duration = p.duration();
        this.uses = p.uses();
    }

    /** A slider of whole steps (0 to max). */
    private static final class Steps extends AbstractSliderButton {
        private final int max;
        private final IntConsumer onChange;
        private final IntFunction<Component> label;

        Steps(int x, int y, int w, int max, int start, IntFunction<Component> label, IntConsumer onChange) {
            super(x, y, w, 20, Component.empty(), (double) start / max);
            this.max = max;
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        private int step() {
            return (int) Math.round(value * max);
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(label.apply(step()));
        }

        @Override
        protected void applyValue() {
            value = step() / (double) max;
            onChange.accept(step());
        }
    }

    @Override
    protected void init() {
        int w = 220;
        int x = width / 2 - w / 2;
        int y = height / 2 - 48;
        addRenderableWidget(new Steps(x, y, w, TriggerTime.MAX_STEP, respawn,
                s -> Component.translatable("screen.craftteamracing.trigger.respawn", TriggerTime.label(s, "screen.craftteamracing.trigger.default")), s -> respawn = s));
        addRenderableWidget(new Steps(x, y + 26, w, TriggerTime.MAX_STEP, duration,
                s -> Component.translatable("screen.craftteamracing.trigger.duration", TriggerTime.label(s, "screen.craftteamracing.trigger.always")), s -> duration = s));
        addRenderableWidget(new Steps(x, y + 52, w, TriggerTime.MAX_USES, uses,
                s -> Component.translatable("screen.craftteamracing.trigger.uses", s == 0 ? Component.translatable("screen.craftteamracing.trigger.infinite") : Component.literal(Integer.toString(s))),
                s -> uses = s));
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(width / 2 - 102, y + 84, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(width / 2 + 2, y + 84, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new SetTriggerPayload(pos, respawn, duration, uses));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFFFFF);
    }
}

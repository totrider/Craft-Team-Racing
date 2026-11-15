package com.ctrcraft.client;

import java.util.LinkedHashSet;
import java.util.Set;

import com.ctrcraft.items.CrateItems;
import com.ctrcraft.items.PowerupRegistry;
import com.ctrcraft.net.OpenSpotPayload;
import com.ctrcraft.net.SetSpotPayload;
import com.ctrcraft.pickup.ItemManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The power-up crate spot's screen (opened by the Wrench): a grid with the picture of every power-up (the HUD icons, the original's when a pack has them). Click to select or deselect
 * a power-up: the crate gives one of the selected ones at random and its icon cycles between them; nothing selected is CTR's drop table (the "?" cell clears the selection).
 */
public class CrateSpotScreen extends Screen {
    private static final int COLUMNS = 5;
    private static final int CELL_W = 52;
    private static final int CELL_H = 40;

    private final BlockPos pos;
    private final Set<String> selected = new LinkedHashSet<>();

    public static void open(OpenSpotPayload p) {
        Minecraft.getInstance().setScreen(new CrateSpotScreen(p.pos(), p.itemId()));
    }

    private CrateSpotScreen(BlockPos pos, String current) {
        super(Component.translatable("screen.ctrcraft.spot.title"));
        this.pos = pos;
        this.selected.addAll(CrateItems.parse(current));
    }

    /** A cell: a flat button that draws a power-up's icon (or a question mark for the general drop table) and an outline when it is selected. */
    private final class Cell extends Button {
        private final String id;

        Cell(int x, int y, String id, Component tip) {
            super(x, y, CELL_W - 4, CELL_H - 4, Component.empty(), b -> {
                if (id.isEmpty()) selected.clear();
                else if (!selected.add(id)) selected.remove(id);
            }, DEFAULT_NARRATION);
            this.id = id;
            setTooltip(Tooltip.create(tip));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            boolean on = id.isEmpty() ? selected.isEmpty() : selected.contains(id);
            if (id.isEmpty()) {
                g.drawCenteredString(font, "?", getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, 0xFFFFFF);
            } else {
                GuiTextures.drawFit(g, GuiTextures.powerIcon(id), getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6);
                if (com.ctrcraft.items.PowerupRegistry.stackCount(id) > 1) KartHud.outlined(g, minecraft, Integer.toString(com.ctrcraft.items.PowerupRegistry.stackCount(id)), getX() + 4, getY() + 4, 1.5F, 0xFFFFFFFF);
                if (!on) g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x66000000);          // not selected: dimmed
            }
            if (on) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFFD040);
        }
    }

    @Override
    protected void init() {
        java.util.List<String> ids = PowerupRegistry.allIds();
        int cells = ids.size() + 1;
        int rows = (cells + COLUMNS - 1) / COLUMNS;
        int left = width / 2 - COLUMNS * CELL_W / 2 + 2;
        int top = 34;
        for (int i = 0; i < cells; i++) {
            String id = i == 0 ? "" : ids.get(i - 1);
            Component tip = i == 0 ? Component.translatable("screen.ctrcraft.spot.roulette") : ItemManager.nameOf(id);
            addRenderableWidget(new Cell(left + (i % COLUMNS) * CELL_W, top + (i / COLUMNS) * CELL_H, id, tip));
        }
        int y = top + rows * CELL_H + 6;
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.done"), b -> save()).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.rulebook.cancel"), b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new SetSpotPayload(pos, CrateItems.join(selected)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.ctrcraft.spot.hint"), width / 2, 22, 0xA0A0A0);
    }
}

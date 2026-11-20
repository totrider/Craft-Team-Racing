package com.craftteamracing.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import com.craftteamracing.DropTableData;
import com.craftteamracing.items.HeldItem;
import com.craftteamracing.items.ItemTables;
import com.craftteamracing.items.PowerupRegistry;
import com.craftteamracing.items.PowerupTable;
import com.craftteamracing.net.DropTableSavePayload;
import com.craftteamracing.pickup.ItemManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drop table's screen: a grid with the picture of every power-up and its weight (left click adds one, right click takes one, crouching changes it by five; scroll the wheel when there
 * are more power-ups than fit). CTR's roulette (the place-based drops) is one switch away; touching a cell while it is on starts a custom list from CTR's shares. Hover for the name.
 */
public class DropTableScreen extends Screen {
    private static final int COLUMNS = 6;
    private static final int CELL_W = 48;
    private static final int CELL_H = 40;
    private static final int VISIBLE_ROWS = 4;

    private final InteractionHand hand;
    private boolean custom;
    private boolean asDefault;
    private int rowOffset;
    private final Map<String, Integer> weights = new LinkedHashMap<>();

    public DropTableScreen(InteractionHand hand, DropTableData data) {
        super(Component.translatable("screen.craftteamracing.droptable.title"));
        this.hand = hand;
        this.custom = !data.table().isCtr();
        this.asDefault = data.asDefault();
        for (String id : PowerupRegistry.rouletteIds()) weights.put(id, custom ? data.table().weights().getOrDefault(id, 0) : 0);
    }

    /** A cell of the grid: the icon and its weight; its two mouse buttons change the weight. */
    private final class Cell extends Button {
        private final String id;

        Cell(int x, int y, String id) {
            super(x, y, CELL_W - 4, CELL_H - 4, Component.empty(), b -> { }, DEFAULT_NARRATION);
            this.id = id;
            setTooltip(Tooltip.create(ItemManager.nameOf(id)));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!active || !visible || (button != 0 && button != 1) || !clicked(mx, my)) return false;
            playDownSound(minecraft.getSoundManager());
            if (!custom) {
                custom = true;
                startFromCtr();
            }
            int step = hasShiftDown() ? 5 : 1;
            weights.put(id, Math.max(0, Math.min(99, weights.get(id) + (button == 0 ? step : -step))));
            return true;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            GuiTextures.drawIcon(g, GuiTextures.powerIcon(id), getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6);
                if (com.craftteamracing.items.PowerupRegistry.stackCount(id) > 1) KartHud.outlined(g, minecraft, Integer.toString(com.craftteamracing.items.PowerupRegistry.stackCount(id)), getX() + 4, getY() + 4, 1.5F, 0xFFFFFFFF);
            int w = weights.get(id);
            if (custom) {
                if (w == 0) g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x99000000);     // left out: dimmed
                String text = Integer.toString(w);
                g.drawString(font, text, getX() + getWidth() - 3 - font.width(text), getY() + getHeight() - 10, w > 0 ? 0xFFFFFF : 0x909090, true);
            } else {
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x55000000);                  // CTR's roulette: the grid is only a preview
            }
        }
    }

    @Override
    protected void init() {
        int left = width / 2 - COLUMNS * CELL_W / 2;
        addRenderableWidget(Button.builder(tableLabel(), b -> {
            custom = !custom;
            if (custom && weights.values().stream().noneMatch(w -> w > 0)) startFromCtr();
            b.setMessage(tableLabel());
        }).bounds(left, 22, 142, 20).build());
        addRenderableWidget(Button.builder(defaultLabel(), b -> {
            asDefault = !asDefault;
            b.setMessage(defaultLabel());
        }).bounds(left + 146, 22, 142, 20).build());
        java.util.List<String> ids = new java.util.ArrayList<>(weights.keySet());
        int rows = (ids.size() + COLUMNS - 1) / COLUMNS;
        rowOffset = Math.max(0, Math.min(rowOffset, rows - VISIBLE_ROWS));
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int i = (r + rowOffset) * COLUMNS + c;
                if (i >= ids.size()) continue;
                addRenderableWidget(new Cell(left + c * CELL_W + 2, 48 + r * CELL_H, ids.get(i)));
            }
        }
        int y = 48 + VISIBLE_ROWS * CELL_H + 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
    }

    private Component tableLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.table", Component.translatable(custom ? "screen.craftteamracing.rulebook.table_custom" : "screen.craftteamracing.rulebook.table_ctr"));
    }

    private Component defaultLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.apply_as", Component.translatable(asDefault ? "screen.craftteamracing.rulebook.as_default" : "screen.craftteamracing.rulebook.as_temporary"));
    }

    /** A custom list starts as CTR's shares for a driver in the middle of a field of eight (a sample of its draws), so there is something to change. */
    private void startFromCtr() {
        Random rnd = new Random(7);
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < 4000; i++) counts.merge(ItemTables.draw(8, 4, 1, rnd).id(), 1, Integer::sum);
        for (String id : weights.keySet()) weights.put(id, Math.max(0, Math.round(counts.getOrDefault(id, 0) / 40.0F)));
        if (weights.values().stream().noneMatch(w -> w > 0)) weights.replaceAll((k, v) -> 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int rows = (weights.size() + COLUMNS - 1) / COLUMNS;
        if (rows > VISIBLE_ROWS && scrollY != 0) {
            int next = Math.max(0, Math.min(rows - VISIBLE_ROWS, rowOffset - (int) Math.signum(scrollY)));
            if (next != rowOffset) {
                rowOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void save() {
        PowerupTable table = PowerupTable.ctr();
        if (custom) {
            Map<String, Integer> w = new LinkedHashMap<>();
            weights.forEach((id, n) -> {
                if (n > 0) w.put(id, n);
            });
            if (!w.isEmpty()) table = PowerupTable.custom(w);
        }
        PacketDistributor.sendToServer(new DropTableSavePayload(hand.ordinal(), new DropTableData(table, asDefault)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int rows = (weights.size() + COLUMNS - 1) / COLUMNS;
        if (rows > VISIBLE_ROWS) {
            // a scroll bar beside the grid
            int x = width / 2 + COLUMNS * CELL_W / 2 + 4;
            int h = VISIBLE_ROWS * CELL_H - 4;
            g.fill(x, 48, x + 4, 48 + h, 0x55FFFFFF);
            int barH = Math.max(8, h * VISIBLE_ROWS / rows);
            int barY = 48 + (h - barH) * rowOffset / Math.max(1, rows - VISIBLE_ROWS);
            g.fill(x, barY, x + 4, barY + barH, 0xFFFFD040);
        }
        g.drawCenteredString(font, Component.translatable("screen.craftteamracing.droptable.hint"), width / 2, 48 + VISIBLE_ROWS * CELL_H + 28, 0xA0A0A0);
    }
}

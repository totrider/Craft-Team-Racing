package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.craftteamracing.items.CrateItems;
import com.craftteamracing.items.PowerupRegistry;
import com.craftteamracing.net.OpenSpotPayload;
import com.craftteamracing.net.SetSpotPayload;
import com.craftteamracing.pickup.ItemManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The power-up crate spot's screen (opened by the Wrench): a grid with the picture of every power-up (the HUD icons, the original's when a pack has them). Click to select or deselect
 * a power-up: the crate gives one of the selected ones at random and its icon cycles between them; nothing selected is CTR's drop table (the "?" cell clears the selection).
 * A selected power-up has a small options mark in its corner: click it (a power-up that has just been selected is open already) and its quantity is set under the grid, one number or,
 * with "random range" on, a minimum and a maximum. The number is drawn over the icon: nothing for one, "2" for two, "2-4" for two to four.
 */
public class CrateSpotScreen extends Screen {
    private static final int COLUMNS = 5;
    private static final int CELL_W = 52;
    private static final int CELL_H = 40;
    private static final int LIMIT = CrateItems.Entry.LIMIT;

    private final BlockPos pos;
    /** The selected power-ups, in the order chosen: {minimum, maximum} each. */
    private final Map<String, int[]> selected = new LinkedHashMap<>();
    /** The power-up whose quantity the panel under the grid is setting. */
    private String focus;
    /** The power-ups whose quantity is a random range (the toggle is on), also while the two numbers happen to be equal. */
    private final java.util.Set<String> rangeOn = new java.util.HashSet<>();
    private Slider minSlider;
    private Slider maxSlider;

    public static void open(OpenSpotPayload p) {
        Minecraft.getInstance().setScreen(new CrateSpotScreen(p.pos(), p.itemId()));
    }

    private CrateSpotScreen(BlockPos pos, String current) {
        super(Component.translatable("screen.craftteamracing.spot.title"));
        this.pos = pos;
        for (CrateItems.Entry e : CrateItems.entries(current)) {
            selected.put(e.id(), new int[] { e.min(), e.max() });
            if (e.max() > e.min()) rangeOn.add(e.id());
        }
    }

    /** A cell: a flat button that draws a power-up's icon (or a question mark for the general drop table), an outline when it is selected and its options mark. */
    private final class Cell extends Button {
        private final String id;

        Cell(int x, int y, String id, Component tip) {
            super(x, y, CELL_W - 4, CELL_H - 4, Component.empty(), b -> {
                if (id.isEmpty()) {
                    selected.clear();
                    rangeOn.clear();
                    focus = null;
                } else if (selected.containsKey(id)) {
                    selected.remove(id);
                    rangeOn.remove(id);
                    if (id.equals(focus)) focus = null;
                } else {
                    selected.put(id, new int[] { 1, 1 });
                    focus = id;                                   // a power-up just chosen is open for its quantity
                }
                CrateSpotScreen.this.rebuild();
            }, DEFAULT_NARRATION);
            this.id = id;
            setTooltip(Tooltip.create(tip));
        }

        private boolean onMark(double mx, double my) {
            return !id.isEmpty() && selected.containsKey(id) && mx >= getX() + getWidth() - 13 && mx < getX() + getWidth() && my >= getY() && my < getY() + 13;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (active && visible && button == 0 && isMouseOver(mx, my) && onMark(mx, my)) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                focus = id;
                CrateSpotScreen.this.rebuild();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            boolean on = id.isEmpty() ? selected.isEmpty() : selected.containsKey(id);
            if (id.isEmpty()) {
                g.drawCenteredString(font, "?", getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, 0xFFFFFF);
            } else {
                GuiTextures.drawIcon(g, GuiTextures.powerIcon(id), getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6);
                int[] q = selected.get(id);
                if (q != null) {
                    String label = new CrateItems.Entry(id, q[0], q[1]).label();
                    if (!label.isEmpty()) KartHud.outlined(g, minecraft, label, getX() + 4, getY() + 4, 1.2F, 0xFFFFFFFF);
                } else {
                    g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x66000000);          // not selected: dimmed
                }
                if (q != null) {
                    // the options mark: three little bars, lit when this is the power-up being set
                    boolean open = id.equals(focus);
                    boolean over = onMark(mouseX, mouseY);
                    int x = getX() + getWidth() - 11;
                    int y = getY() + 3;
                    g.fill(x - 1, y - 1, x + 9, y + 8, open ? 0xFFFFD040 : over ? 0xFFE0E0E0 : 0xAA000000);
                    int bar = open ? 0xFF202020 : 0xFFFFFFFF;
                    for (int i = 0; i < 3; i++) g.fill(x + 1, y + 1 + i * 2, x + 8, y + 2 + i * 2, bar);
                }
            }
            if (on) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFFD040);
        }
    }

    /** A slider of whole numbers, 1 to LIMIT. */
    private static final class Slider extends AbstractSliderButton {
        private final java.util.function.IntSupplier get;
        private final java.util.function.IntConsumer set;
        private final java.util.function.IntFunction<Component> label;

        Slider(int x, int y, int w, java.util.function.IntSupplier get, java.util.function.IntConsumer set, java.util.function.IntFunction<Component> label) {
            super(x, y, w, 20, Component.empty(), (get.getAsInt() - 1) / (double) (LIMIT - 1));
            this.get = get;
            this.set = set;
            this.label = label;
            updateMessage();
        }

        private int step() {
            return 1 + (int) Math.round(value * (LIMIT - 1));
        }

        /** Moves the handle to the number the settings hold (another slider changed it). */
        void sync() {
            value = (get.getAsInt() - 1) / (double) (LIMIT - 1);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(active ? get.getAsInt() : step()));
        }

        @Override
        protected void applyValue() {
            set.accept(step());
        }
    }

    private void rebuild() {
        init();
    }

    @Override
    protected void init() {
        clearWidgets();
        minSlider = null;
        maxSlider = null;
        List<String> ids = PowerupRegistry.crateIds();
        int cells = ids.size() + 1;
        int rows = (cells + COLUMNS - 1) / COLUMNS;
        int left = width / 2 - COLUMNS * CELL_W / 2 + 2;
        int top = 34;
        for (int i = 0; i < cells; i++) {
            String id = i == 0 ? "" : ids.get(i - 1);
            Component tip = i == 0 ? Component.translatable("screen.craftteamracing.spot.roulette") : ItemManager.nameOf(id);
            addRenderableWidget(new Cell(left + (i % COLUMNS) * CELL_W, top + (i / COLUMNS) * CELL_H, id, tip));
        }
        int y = top + rows * CELL_H + 6;
        if (focus != null && selected.containsKey(focus)) {
            int[] q = selected.get(focus);
            int x = width / 2 - 150;
            boolean ranged = rangeOn.contains(focus);
            minSlider = addRenderableWidget(new Slider(x, y + 12, ranged ? 110 : 190, () -> q[0], v -> {
                q[0] = v;
                q[1] = ranged ? Math.max(q[1], v) : v;
                if (maxSlider != null) maxSlider.sync();
            }, v -> Component.translatable(ranged ? "screen.craftteamracing.spot.min" : "screen.craftteamracing.spot.quantity", v)));
            Button rangeToggle = Button.builder(Component.translatable(ranged ? "screen.craftteamracing.spot.range_on" : "screen.craftteamracing.spot.range_off"), b -> {
                if (ranged) {
                    rangeOn.remove(focus);
                    q[1] = q[0];
                } else {
                    rangeOn.add(focus);
                    if (q[0] >= LIMIT) q[0] = LIMIT - 1;
                    q[1] = q[0] + 1;
                }
                rebuild();
            }).bounds(x + (ranged ? 114 : 194), y + 12, ranged ? 72 : 106, 20).tooltip(Tooltip.create(Component.translatable("screen.craftteamracing.spot.range_hint"))).build();
            addRenderableWidget(rangeToggle);
            if (ranged) {
                maxSlider = addRenderableWidget(new Slider(x + 190, y + 12, 110, () -> q[1], v -> q[1] = Math.max(v, q[0]), v -> Component.translatable("screen.craftteamracing.spot.max", v)));
            }
            y += 38;
        } else {
            y += 14;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
    }

    private void save() {
        List<CrateItems.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, int[]> e : selected.entrySet()) entries.add(new CrateItems.Entry(e.getKey(), e.getValue()[0], e.getValue()[1]));
        PacketDistributor.sendToServer(new SetSpotPayload(pos, CrateItems.joinEntries(entries)));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.craftteamracing.spot.hint"), width / 2, 22, 0xA0A0A0);
        if (focus != null && selected.containsKey(focus)) {
            int rows = (PowerupRegistry.crateIds().size() + 1 + COLUMNS - 1) / COLUMNS;
            int y = 34 + rows * CELL_H + 6;
            g.drawString(font, Component.translatable("screen.craftteamracing.spot.quantity_of", ItemManager.nameOf(focus)), width / 2 - 150, y, 0xFFD040, false);
        }
    }
}

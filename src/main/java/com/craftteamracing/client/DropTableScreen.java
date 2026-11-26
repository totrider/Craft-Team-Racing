package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.craftteamracing.DropTableData;
import com.craftteamracing.items.CrateItems;
import com.craftteamracing.items.HeldItem;
import com.craftteamracing.items.ItemTables;
import com.craftteamracing.items.PowerupRegistry;
import com.craftteamracing.items.PowerupTable;
import com.craftteamracing.net.DropTableSavePayload;
import com.craftteamracing.pickup.ItemManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drop table's screen, built like the power-up crate spot's ({@link CrateSpotScreen}): a grid with the picture of every power-up; click to put one in the list or take it out, and the
 * options mark in a listed one's corner opens its settings under the grid: its weight (how often it comes up against the others) and its quantity, one number or a random range. The quantity is
 * drawn over the icon, the weight in the corner. Alt + click adds another entry of the same power-up (its own weight and quantity: a bomb once as one and once as three); right click takes an
 * added entry away. Under the settings, "quantity by place" makes a range lean low for a driver in front and high for one at the back, between the two percentages set for the first and the last
 * place. The classic roulette (the place-based drops) is one switch away; touching a cell while it is on starts a custom list from its shares.
 */
public class DropTableScreen extends Screen {
    private static final int COLUMNS = 6;
    private static final int CELL_W = 48;
    private static final int CELL_H = 40;
    private static final int VISIBLE_ROWS = 2;
    private static final int GRID_TOP = 48;
    private static final int LIMIT = CrateItems.Entry.LIMIT;
    /** The weight at both ends for a power-up the classic roulette never gives, when it is put in the list. */
    private static final int START_WEIGHT = 5;

    /** One entry of the list: a power-up with its weight and quantity. Copies are extra entries of a power-up that is listed already. */
    private static final class Row {
        final String id;
        /** The weight for the first place and for the last. */
        int weight;
        int weightLast;
        int min;
        int max;
        boolean ranged;
        final boolean copy;

        Row(String id, int weight, int weightLast, int min, int max, boolean copy) {
            this.id = id;
            this.weight = weight;
            this.weightLast = weightLast;
            this.min = min;
            this.max = max;
            this.ranged = max > min;
            this.copy = copy;
        }

        boolean listed() {
            return weight > 0 || weightLast > 0;
        }

        CrateItems.Entry entry() {
            return new CrateItems.Entry(id, min, max);
        }
    }

    /** Whether this edits a power-up crate spot's own list (the same screen as the drop table's, without its "apply as" switch and with the drop table as the alternative to a list). */
    private final boolean crate;
    private final java.util.function.BiConsumer<PowerupTable, Boolean> onSave;
    private boolean custom;
    private boolean asDefault;
    private int rowOffset;
    private final List<Row> rows = new ArrayList<>();
    /** Entries the grid cannot show (an id with no picture here, from a data pack): kept as they are. */
    private final Map<String, Integer> extra = new LinkedHashMap<>();
    private final Map<String, Integer> extraLast = new LinkedHashMap<>();
    private Button doneButton;
    private boolean quantityByPlace;
    private int biasLow;
    private int biasHigh;
    /** The entry whose settings the panel under the grid is showing. */
    private Row focus;
    private int bottomY;
    private Slider minSlider;
    private Slider maxSlider;

    public DropTableScreen(InteractionHand hand, DropTableData data) {
        this(Component.translatable("screen.craftteamracing.droptable.title"), false, data.table(), data.asDefault(),
                (t, d) -> PacketDistributor.sendToServer(new DropTableSavePayload(hand.ordinal(), new DropTableData(t, d))));
    }

    /** The screen of a power-up crate spot (opened by the Wrench): the crate's own list, or the drop table when it has none. */
    public static DropTableScreen forCrate(net.minecraft.core.BlockPos pos, String itemId) {
        PowerupTable own = CrateItems.table(itemId);
        return new DropTableScreen(Component.translatable("screen.craftteamracing.spot.title"), true, own == null ? PowerupTable.ctr() : own, false,
                (t, d) -> PacketDistributor.sendToServer(new com.craftteamracing.net.SetSpotPayload(pos, CrateItems.fromTable(t))));
    }

    private DropTableScreen(Component title, boolean crate, PowerupTable table, boolean asDefaultIn, java.util.function.BiConsumer<PowerupTable, Boolean> onSave) {
        super(title);
        this.crate = crate;
        this.onSave = onSave;
        this.custom = !table.isCtr();
        this.asDefault = asDefaultIn;
        this.biasLow = 0;
        this.biasHigh = 100;
        for (String id : PowerupRegistry.crateIds()) rows.add(new Row(id, 0, 0, 1, 1, false));
        if (custom) {
            for (Map.Entry<String, Integer> e : table.orderedWeights().entrySet()) {
                int last = table.orderedLastWeights().getOrDefault(e.getKey(), e.getValue());
                List<CrateItems.Entry> parsed = CrateItems.entries(e.getKey());          // also reads the old three-bomb and three-missile ids as a quantity of three
                if (parsed.isEmpty() || !PowerupRegistry.crateIds().contains(parsed.get(0).id())) {
                    extra.put(e.getKey(), e.getValue());
                    extraLast.put(e.getKey(), last);
                } else {
                    put(parsed.get(0).id(), parsed.get(0).min(), parsed.get(0).max(), e.getValue(), last);
                }
            }
            quantityByPlace = table.quantityByPlace();
            biasLow = table.biasLow();
            biasHigh = table.biasHigh();
        }
    }

    /** An entry into the list: the power-up's own row if that is still empty, else a copy right after its last row. */
    private void put(String id, int min, int max, int weight, int weightLast) {
        int last = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.id.equals(id)) continue;
            last = i;
            if (!r.copy && !r.listed()) {
                r.weight = weight;
                r.weightLast = weightLast;
                r.min = min;
                r.max = max;
                r.ranged = max > min;
                return;
            }
        }
        rows.add(last + 1, new Row(id, weight, weightLast, min, max, true));
    }

    /** A power-up put in the list is the plain single power-up with the classic roulette's own weights for it (first-place set, last-place set), open for its settings. */
    private void listWithDefaults(Row row) {
        int first = shares(ItemTables.Set.RACE1).getOrDefault(row.id, 0);
        int last = shares(ItemTables.Set.RACE4).getOrDefault(row.id, 0);
        if (first == 0 && last == 0) first = last = START_WEIGHT;             // one the classic sets never give
        row.weight = first;
        row.weightLast = last;
        row.min = 1;
        row.max = 1;
        row.ranged = false;
        focus = row;
    }

    /** A cell: a flat button with a power-up's icon, an outline when it is in the list, its quantity over the icon, its weight and its options mark. */
    private final class Cell extends Button {
        private final Row row;

        Cell(int x, int y, Row row) {
            super(x, y, CELL_W - 4, CELL_H - 4, Component.empty(), b -> { }, DEFAULT_NARRATION);
            this.row = row;
            setTooltip(Tooltip.create(ItemManager.nameOf(row.id)));
        }

        private boolean onMark(double mx, double my) {
            return custom && row.listed() && mx >= getX() + getWidth() - 13 && mx < getX() + getWidth() && my >= getY() && my < getY() + 13;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!active || !visible || (button != 0 && button != 1) || !clicked(mx, my)) return false;
            playDownSound(minecraft.getSoundManager());
            if (!custom) {
                custom = true;
                if (crate) {
                    if (button == 0) listWithDefaults(row);                   // a crate's own list starts with the power-up that was clicked
                } else {
                    startFromCtr();
                }
                rebuild();
                return true;
            }
            boolean ctrl = hasControlDown();
            if (ctrl && button == 0) {                                   // Ctrl + click: a power-up into the list, or another entry of one that is there
                if (!row.listed()) {
                    listWithDefaults(row);
                } else {
                    Row added = new Row(row.id, row.weight, row.weightLast, row.min, row.max, true);
                    rows.add(rows.indexOf(row) + 1, added);
                    focus = added;
                }
            } else if (ctrl && button == 1) {                            // Ctrl + right click: an added entry goes away, a power-up's own is taken out of the list
                if (row.copy) {
                    rows.remove(row);
                } else {
                    row.weight = 0;
                    row.weightLast = 0;
                    row.min = 1;
                    row.max = 1;
                    row.ranged = false;
                }
                if (focus == row) focus = null;
            } else if (button == 0 && row.listed()) {                    // a plain click (or its options mark) opens the settings; it never adds or removes
                focus = row;
            }
            rebuild();
            return true;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            GuiTextures.drawIcon(g, GuiTextures.powerIcon(row.id), getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6);
            if (!custom) {
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x55000000);          // CTR's roulette: the grid is only a preview
                return;
            }
            if (row.listed()) {
                String label = row.entry().label();
                if (!label.isEmpty()) KartHud.outlined(g, minecraft, label, getX() + 4, getY() + 4, 1.2F, 0xFFFFFFFF);
                String text = row.weight == row.weightLast ? Integer.toString(row.weight) : row.weight + "-" + row.weightLast;
                g.drawString(font, text, getX() + getWidth() - 3 - font.width(text), getY() + getHeight() - 10, 0xFFFFFF, true);
                boolean open = row == focus;
                boolean over = onMark(mouseX, mouseY);
                int x = getX() + getWidth() - 11;
                int y = getY() + 3;
                g.fill(x - 1, y - 1, x + 9, y + 8, open ? 0xFFFFD040 : over ? 0xFFE0E0E0 : 0xAA000000);
                int bar = open ? 0xFF202020 : 0xFFFFFFFF;
                for (int i = 0; i < 3; i++) g.fill(x + 1, y + 1 + i * 2, x + 8, y + 2 + i * 2, bar);
                g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFFD040);
            } else {
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0x66000000);          // not in the list: dimmed
            }
        }
    }

    /** A slider of whole numbers from lo to hi. */
    private static final class Slider extends AbstractSliderButton {
        private final int lo;
        private final int hi;
        private final java.util.function.IntSupplier get;
        private final java.util.function.IntConsumer set;
        private final java.util.function.IntFunction<Component> label;

        Slider(int x, int y, int w, int lo, int hi, java.util.function.IntSupplier get, java.util.function.IntConsumer set, java.util.function.IntFunction<Component> label) {
            super(x, y, w, 20, Component.empty(), (get.getAsInt() - lo) / (double) (hi - lo));
            this.lo = lo;
            this.hi = hi;
            this.get = get;
            this.set = set;
            this.label = label;
            updateMessage();
        }

        private int step() {
            return lo + (int) Math.round(value * (hi - lo));
        }

        /** Moves the handle to the number the settings hold (another slider changed it). */
        void sync() {
            value = (get.getAsInt() - lo) / (double) (hi - lo);
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
        rebuildWidgets();
    }

    @Override
    protected void init() {
        minSlider = null;
        maxSlider = null;
        int left = width / 2 - COLUMNS * CELL_W / 2;
        addRenderableWidget(Button.builder(tableLabel(), b -> {
            custom = !custom;
            if (custom && !crate && rows.stream().noneMatch(Row::listed)) startFromCtr();
            focus = null;
            rebuild();
        }).bounds(left, 22, crate ? 288 : 142, 20).build());
        if (!crate) {
            addRenderableWidget(Button.builder(defaultLabel(), b -> {
                asDefault = !asDefault;
                b.setMessage(defaultLabel());
            }).bounds(left + 146, 22, 142, 20).build());
        }
        int gridRows = (rows.size() + COLUMNS - 1) / COLUMNS;
        rowOffset = Math.max(0, Math.min(rowOffset, gridRows - VISIBLE_ROWS));
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int i = (r + rowOffset) * COLUMNS + c;
                if (i >= rows.size()) continue;
                addRenderableWidget(new Cell(left + c * CELL_W + 2, GRID_TOP + r * CELL_H, rows.get(i)));
            }
        }
        int y = GRID_TOP + VISIBLE_ROWS * CELL_H + 4;
        int x = width / 2 - 150;
        if (custom && focus != null && rows.contains(focus) && focus.listed()) {
            Row f = focus;
            y += 12;
            addRenderableWidget(new Slider(x, y, 148, 0, 99, () -> f.weight, v -> f.weight = v, v -> Component.translatable("screen.craftteamracing.droptable.weight_first", v)))
                    .setTooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.weight_tip")));
            addRenderableWidget(new Slider(x + 152, y, 148, 0, 99, () -> f.weightLast, v -> f.weightLast = v, v -> Component.translatable("screen.craftteamracing.droptable.weight_last", v)))
                    .setTooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.weight_tip")));
            y += 22;
            minSlider = addRenderableWidget(new Slider(x, y, f.ranged ? 110 : 190, 1, LIMIT, () -> f.min, v -> {
                f.min = v;
                f.max = f.ranged ? Math.max(f.max, v) : v;
                if (maxSlider != null) maxSlider.sync();
            }, v -> Component.translatable(f.ranged ? "screen.craftteamracing.spot.min" : "screen.craftteamracing.spot.quantity", v)));
            addRenderableWidget(Button.builder(Component.translatable(f.ranged ? "screen.craftteamracing.spot.range_on" : "screen.craftteamracing.spot.range_off"), b -> {
                if (f.ranged) {
                    f.ranged = false;
                    f.max = f.min;
                } else {
                    f.ranged = true;
                    if (f.min >= LIMIT) f.min = LIMIT - 1;
                    f.max = f.min + 1;
                }
                rebuild();
            }).bounds(x + (f.ranged ? 114 : 194), y, f.ranged ? 72 : 106, 20).tooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.range_hint"))).build());
            if (f.ranged) {
                maxSlider = addRenderableWidget(new Slider(x + 190, y, 110, 1, LIMIT, () -> f.max, v -> f.max = Math.max(v, f.min), v -> Component.translatable("screen.craftteamracing.spot.max", v)));
            }
            y += 24;
        } else if (custom) {
            y += 4;
        }
        if (custom) {
            addRenderableWidget(Button.builder(quantityLabel(), b -> {
                quantityByPlace = !quantityByPlace;
                rebuild();
            }).bounds(x, y, 120, 20).tooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.qty_place_tip"))).build());
            Slider first = addRenderableWidget(new Slider(x + 124, y, 86, 0, 100, () -> biasLow, v -> biasLow = v, v -> Component.translatable("screen.craftteamracing.droptable.first", v)));
            Slider last = addRenderableWidget(new Slider(x + 214, y, 86, 0, 100, () -> biasHigh, v -> biasHigh = v, v -> Component.translatable("screen.craftteamracing.droptable.last", v)));
            first.active = quantityByPlace;
            last.active = quantityByPlace;
            first.setTooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.bias_tip")));
            last.setTooltip(Tooltip.create(Component.translatable("screen.craftteamracing.droptable.bias_tip")));
            y += 24;
        }
        bottomY = y;
        doneButton = addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.done"), b -> save()).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftteamracing.rulebook.cancel"), b -> onClose()).bounds(width / 2 + 2, y, 100, 20).build());
    }

    private Component tableLabel() {
        if (crate) {
            return Component.translatable("screen.craftteamracing.spot.source", Component.translatable(custom ? "screen.craftteamracing.spot.source_own" : "screen.craftteamracing.spot.source_table"));
        }
        return Component.translatable("screen.craftteamracing.rulebook.table", Component.translatable(custom ? "screen.craftteamracing.rulebook.table_custom" : "screen.craftteamracing.rulebook.table_ctr"));
    }

    private Component quantityLabel() {
        return Component.translatable("screen.craftteamracing.droptable.qty_place", Component.translatable(quantityByPlace ? "screen.craftteamracing.rulebook.on" : "screen.craftteamracing.rulebook.off"));
    }

    private Component defaultLabel() {
        return Component.translatable("screen.craftteamracing.rulebook.apply_as", Component.translatable(asDefault ? "screen.craftteamracing.rulebook.as_default" : "screen.craftteamracing.rulebook.as_temporary"));
    }

    /**
     * A custom list starts as the classic roulette's first-place set (the weights for the first place) and last-place set (for the last), as percentages of their slots: the places between are
     * worked out from the two (the original has a set for each band of places, so they are close, not the same). The three-bomb and three-missile stacks become a bomb and a missile at quantity three.
     */
    private void startFromCtr() {
        rows.removeIf(r -> r.copy);
        Map<String, Integer> first = shares(ItemTables.Set.RACE1);
        Map<String, Integer> last = shares(ItemTables.Set.RACE4);
        for (Row r : rows) {
            r.weight = first.getOrDefault(r.id, 0);
            r.weightLast = last.getOrDefault(r.id, 0);
            r.min = 1;
            r.max = 1;
            r.ranged = false;
        }
        // the three-bomb and three-missile stacks are in the middle sets only (not in the first-place or the last-place one): a bomb and a missile at quantity three, weighted by the straight line
        // that fits their share through the four sets, so they are there for both ends
        for (String stack : new String[] { HeldItem.BOMB_3X.id(), HeldItem.MISSILE_3X.id() }) {
            int[] ends = fitted(stack);
            put(stack.equals(HeldItem.BOMB_3X.id()) ? HeldItem.BOMB.id() : HeldItem.MISSILE.id(), 3, 3, ends[0], ends[1]);
        }
        if (rows.stream().noneMatch(Row::listed)) rows.forEach(r -> r.weight = r.weightLast = 1);
        focus = null;
    }

    /** An item's weights for the first and the last place as the least-squares line through its share of the four classic sets (first, second, third and last place bands), at least 1 at each end. */
    private static int[] fitted(String id) {
        ItemTables.Set[] sets = { ItemTables.Set.RACE1, ItemTables.Set.RACE2, ItemTables.Set.RACE3, ItemTables.Set.RACE4 };
        double[] y = new double[4];
        double mean = 0;
        for (int i = 0; i < 4; i++) {
            int[] entries = sets[i].entries();
            int n = 0;
            for (int ctrId : entries) if (HeldItem.byCtrId(ctrId).id().equals(id)) n++;
            y[i] = n * 100.0 / entries.length;
            mean += y[i] / 4;
        }
        double num = 0;
        double den = 0;
        for (int i = 0; i < 4; i++) {
            double x = i / 3.0 - 0.5;
            num += x * (y[i] - mean);
            den += x * x;
        }
        double slope = num / den;
        return new int[] { (int) Math.max(1, Math.round(mean - slope * 0.5)), (int) Math.max(1, Math.round(mean + slope * 0.5)) };
    }

    /** The percent of a classic set's slots each item holds. */
    private static Map<String, Integer> shares(ItemTables.Set set) {
        int[] entries = set.entries();
        Map<String, Integer> out = new java.util.HashMap<>();
        for (int ctrId : entries) out.merge(HeldItem.byCtrId(ctrId).id(), 1, Integer::sum);
        out.replaceAll((k, v) -> Math.max(1, Math.round(v * 100.0F / entries.length)));
        return out;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int gridRows = (rows.size() + COLUMNS - 1) / COLUMNS;
        if (gridRows > VISIBLE_ROWS && scrollY != 0) {
            int next = Math.max(0, Math.min(gridRows - VISIBLE_ROWS, rowOffset - (int) Math.signum(scrollY)));
            if (next != rowOffset) {
                rowOffset = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    /** Whether the list gives something at the first place and at the last (a custom list with nothing to draw at one end cannot be saved). */
    private boolean valid() {
        int first = extra.values().stream().mapToInt(Integer::intValue).sum();
        int last = extraLast.values().stream().mapToInt(Integer::intValue).sum();
        for (Row r : rows) {
            if (!r.listed()) continue;
            first += r.weight;
            last += r.weightLast;
        }
        return first > 0 && last > 0;
    }

    private void save() {
        PowerupTable table = PowerupTable.ctr();
        if (custom) {
            if (!valid()) return;
            Map<String, Integer> first = new LinkedHashMap<>();
            Map<String, Integer> last = new LinkedHashMap<>();
            for (Row r : rows) {
                if (!r.listed()) continue;
                first.merge(r.entry().text(), r.weight, Integer::sum);
                last.merge(r.entry().text(), r.weightLast, Integer::sum);
            }
            extra.forEach(first::putIfAbsent);
            extraLast.forEach(last::putIfAbsent);
            table = PowerupTable.custom(first, last, quantityByPlace, biasLow, biasHigh);
        }
        onSave.accept(table, asDefault);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (doneButton != null) doneButton.active = !custom || valid();
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int gridRows = (rows.size() + COLUMNS - 1) / COLUMNS;
        if (gridRows > VISIBLE_ROWS) {
            // a scroll bar beside the grid
            int x = width / 2 + COLUMNS * CELL_W / 2 + 4;
            int h = VISIBLE_ROWS * CELL_H - 4;
            g.fill(x, GRID_TOP, x + 4, GRID_TOP + h, 0x55FFFFFF);
            int barH = Math.max(8, h * VISIBLE_ROWS / gridRows);
            int barY = GRID_TOP + (h - barH) * rowOffset / Math.max(1, gridRows - VISIBLE_ROWS);
            g.fill(x, barY, x + 4, barY + barH, 0xFFFFD040);
        }
        int y = GRID_TOP + VISIBLE_ROWS * CELL_H + 4;
        if (custom && focus != null && rows.contains(focus) && focus.listed()) {
            g.drawString(font, Component.translatable("screen.craftteamracing.droptable.settings_of", ItemManager.nameOf(focus.id)), width / 2 - 150, y, 0xFFD040, false);
        }
        int hy = bottomY + 24;
        java.util.List<net.minecraft.util.FormattedCharSequence> hint = font.split(Component.translatable("screen.craftteamracing.droptable.hint"), 300);
        int widest = 0;
        for (net.minecraft.util.FormattedCharSequence line : hint) widest = Math.max(widest, font.width(line));
        g.fill(width / 2 - widest / 2 - 4, hy - 3, width / 2 + widest / 2 + 4, hy + hint.size() * 10 + 1, 0xA0000000);          // a dark panel: the text stays readable over any background
        for (net.minecraft.util.FormattedCharSequence line : hint) {
            g.drawString(font, line, width / 2 - font.width(line) / 2, hy, 0xD0D0D0, false);
            hy += 10;
        }
    }
}

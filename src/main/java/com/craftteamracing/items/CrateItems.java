package com.craftteamracing.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A power-up crate spot's setting: none (the drop table) or a list of entries of its own, kept as one comma-separated string. It is a small drop table of the crate's own (the same model as
 * {@link PowerupTable}): an entry is {@code id}, {@code id*n} (always n) or {@code id*min-max} (a random number from min to max), n being 1 to {@link Entry#LIMIT}; no quantity is one. After
 * the quantity an entry may carry its weights, {@code @first-last} (or {@code @n} for the same at both ends): the chance of the entry is its weight over the total at the driver's place, the
 * weight for a place between the first and the last in proportion; no weights is 1 at both ends (an even pick). The same power-up may be listed several times. A trailing {@code |low-high}
 * switches "quantity by place" on, with the percentages for the first and the last place ({@link PowerupTable}). An older single id is a list of one, and the old three-bomb and three-missile ids
 * are read as {@code bomb*3} and {@code missile*3}. No Minecraft imports.
 */
public final class CrateItems {
    private CrateItems() {}

    /** One entry of a crate and how many of it a kart gets: from {@code min} to {@code max} (the same: exactly that many), with its weight for the first and the last place. */
    public record Entry(String id, int min, int max, int first, int last) {
        public static final int LIMIT = 10;
        public static final int WEIGHT_LIMIT = 99;

        public Entry {
            min = Math.max(1, Math.min(LIMIT, min));
            max = Math.max(min, Math.min(LIMIT, max));
            first = Math.max(0, Math.min(WEIGHT_LIMIT, first));
            last = Math.max(0, Math.min(WEIGHT_LIMIT, last));
        }

        public Entry(String id, int min, int max) {
            this(id, min, max, 1, 1);
        }

        /** How many this crate gives this time (even across the range). */
        public int roll(Random random) {
            return min == max ? min : min + random.nextInt(max - min + 1);
        }

        /** What is drawn over the icon: nothing for one, "2" for exactly two, "2-4" for two to four. */
        public String label() {
            return max > min ? min + "-" + max : min > 1 ? Integer.toString(min) : "";
        }

        /** The id and quantity: an entry of a {@link PowerupTable} is keyed by this. */
        public String text() {
            return id + (max > min ? "*" + min + "-" + max : min > 1 ? "*" + min : "");
        }

        /** The id, the quantity and the weights, as a crate keeps it. */
        public String full() {
            return text() + (first == 1 && last == 1 ? "" : first == last ? "@" + first : "@" + first + "-" + last);
        }
    }

    /** The quantity-by-place setting of a crate's own list (off: the count is even across a range). */
    public record Settings(boolean byPlace, int low, int high) {
        public static final Settings NONE = new Settings(false, 0, 100);
    }

    private static int[] pair(String text, int fallbackA, int fallbackB) {
        try {
            int dash = text.indexOf('-');
            int a = Integer.parseInt(dash >= 0 ? text.substring(0, dash).trim() : text.trim());
            int b = dash >= 0 ? Integer.parseInt(text.substring(dash + 1).trim()) : a;
            return new int[] { a, b };
        } catch (NumberFormatException e) {
            return new int[] { fallbackA, fallbackB };
        }
    }

    public static List<Entry> entries(String joined) {
        List<Entry> out = new ArrayList<>();
        if (joined == null) return out;
        int bar = joined.indexOf('|');
        for (String part : (bar >= 0 ? joined.substring(0, bar) : joined).split(",")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            int first = 1;
            int last = 1;
            int at = text.indexOf('@');
            if (at >= 0) {
                int[] w = pair(text.substring(at + 1), 1, 1);
                first = w[0];
                last = w[1];
                text = text.substring(0, at).trim();
            }
            String id = text;
            int min = 1;
            int max = 1;
            int star = text.indexOf('*');
            if (star >= 0) {
                id = text.substring(0, star).trim();
                int[] q = pair(text.substring(star + 1), 1, 1);
                min = q[0];
                max = q[1];
            }
            if (id.equals(HeldItem.BOMB_3X.id())) {                 // the old fixed stacks
                id = HeldItem.BOMB.id();
                min = max = 3;
            } else if (id.equals(HeldItem.MISSILE_3X.id())) {
                id = HeldItem.MISSILE.id();
                min = max = 3;
            }
            if (id.isEmpty()) continue;
            out.add(new Entry(id, min, max, first, last));
        }
        return out;
    }

    /** The quantity-by-place setting of a crate's list. */
    public static Settings settings(String joined) {
        int bar = joined == null ? -1 : joined.indexOf('|');
        if (bar < 0) return Settings.NONE;
        int[] b = pair(joined.substring(bar + 1), 0, 100);
        return new Settings(true, Math.max(0, Math.min(100, b[0])), Math.max(0, Math.min(100, b[1])));
    }

    /** The ids only, in order (an id listed twice is there twice). */
    public static List<String> parse(String joined) {
        return entries(joined).stream().map(Entry::id).toList();
    }

    public static String join(Collection<String> ids) {
        return String.join(",", ids);
    }

    public static String joinEntries(Collection<Entry> entries) {
        return entries.stream().map(Entry::full).reduce((a, b) -> a + "," + b).orElse("");
    }

    /** A list and its setting as a crate keeps them. */
    public static String compose(Collection<Entry> entries, Settings settings) {
        String body = joinEntries(entries);
        return body.isEmpty() || !settings.byPlace() ? body : body + "|" + settings.low() + "-" + settings.high();
    }

    public static String normalize(String joined) {
        return compose(entries(joined), settings(joined));
    }

    /** The crate's own list as a drop table (the same model, so a crate draws like the table does), or null when it has none to give: the general drop table then. */
    public static PowerupTable table(String joined) {
        List<Entry> list = entries(joined);
        if (list.isEmpty()) return null;
        Map<String, Integer> first = new LinkedHashMap<>();
        Map<String, Integer> last = new LinkedHashMap<>();
        for (Entry e : list) {
            first.merge(e.text(), e.first(), Integer::sum);
            last.merge(e.text(), e.last(), Integer::sum);
        }
        Settings s = settings(joined);
        try {
            return PowerupTable.custom(first, last, s.byPlace(), s.low(), s.high());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A table as a crate keeps it (the empty string for CTR's roulette: the crate then uses the general drop table). */
    public static String fromTable(PowerupTable table) {
        if (table.isCtr()) return "";
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : table.orderedWeights().entrySet()) {
            List<Entry> parsed = entries(e.getKey());
            if (parsed.isEmpty()) continue;
            Entry p = parsed.get(0);
            list.add(new Entry(p.id(), p.min(), p.max(), e.getValue(), table.orderedLastWeights().getOrDefault(e.getKey(), e.getValue())));
        }
        return compose(list, new Settings(table.quantityByPlace(), table.biasLow(), table.biasHigh()));
    }

    /** The entry a crate shows now: the list is cycled, one every second and a half. */
    public static Entry shownEntry(String joined, long ticks) {
        List<Entry> list = entries(joined);
        return list.isEmpty() ? null : list.get((int) ((ticks / 30) % list.size()));
    }

    /** The id a crate shows now ("" for none). */
    public static String shown(String joined, long ticks) {
        Entry e = shownEntry(joined, ticks);
        return e == null ? "" : e.id();
    }

    /** What a kart gets from the crate, drawn like the drop table does for a kart in place {@code rank} (0 first) of {@code field}: null for the general drop table. */
    public static PowerupTable.Drop pickDrop(String joined, int field, int rank, int lapIndex, Random random) {
        PowerupTable table = table(joined);
        return table == null ? null : table.drawDrop(field, rank, lapIndex, random);
    }
}

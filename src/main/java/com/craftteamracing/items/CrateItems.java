package com.craftteamracing.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * A power-up crate spot's setting: none (CTR's roulette) or a list of power-ups, each with a quantity, kept as one comma-separated string. An entry is {@code id}, {@code id*n} (always n)
 * or {@code id*min-max} (a random number from min to max), n being 1 to {@link Entry#LIMIT}; no quantity is one. An older single id is a list of one, and the old three-bomb and
 * three-missile ids are read as {@code bomb*3} and {@code missile*3}. No Minecraft imports.
 */
public final class CrateItems {
    private CrateItems() {}

    /** One power-up of a crate and how many of it a kart gets: from {@code min} to {@code max} (the same: exactly that many). */
    public record Entry(String id, int min, int max) {
        public static final int LIMIT = 10;

        public Entry {
            min = Math.max(1, Math.min(LIMIT, min));
            max = Math.max(min, Math.min(LIMIT, max));
        }

        /** How many this crate gives this time. */
        public int roll(Random random) {
            return min == max ? min : min + random.nextInt(max - min + 1);
        }

        /** What is drawn over the icon: nothing for one, "2" for exactly two, "2-4" for two to four. */
        public String label() {
            return max > min ? min + "-" + max : min > 1 ? Integer.toString(min) : "";
        }

        public String text() {
            return id + (max > min ? "*" + min + "-" + max : min > 1 ? "*" + min : "");
        }
    }

    public static List<Entry> entries(String joined) {
        List<Entry> out = new ArrayList<>();
        if (joined == null) return out;
        for (String part : joined.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            String id = text;
            int min = 1;
            int max = 1;
            int star = text.indexOf('*');
            if (star >= 0) {
                id = text.substring(0, star).trim();
                String q = text.substring(star + 1).trim();
                try {
                    int dash = q.indexOf('-');
                    min = Integer.parseInt(dash >= 0 ? q.substring(0, dash).trim() : q);
                    max = dash >= 0 ? Integer.parseInt(q.substring(dash + 1).trim()) : min;
                } catch (NumberFormatException e) {
                    min = 1;
                    max = 1;
                }
            }
            if (id.equals(HeldItem.BOMB_3X.id())) {                 // the old fixed stacks
                id = HeldItem.BOMB.id();
                min = max = 3;
            } else if (id.equals(HeldItem.MISSILE_3X.id())) {
                id = HeldItem.MISSILE.id();
                min = max = 3;
            }
            if (id.isEmpty()) continue;
            final String found = id;
            if (out.stream().noneMatch(e -> e.id().equals(found))) out.add(new Entry(id, min, max));
        }
        return out;
    }

    /** The ids only, in order. */
    public static List<String> parse(String joined) {
        return entries(joined).stream().map(Entry::id).toList();
    }

    public static String join(Collection<String> ids) {
        return String.join(",", ids);
    }

    public static String joinEntries(Collection<Entry> entries) {
        return entries.stream().map(Entry::text).reduce((a, b) -> a + "," + b).orElse("");
    }

    public static String normalize(String joined) {
        return joinEntries(entries(joined));
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

    /** What a kart gets from the crate: one of the list at random, or null for the general drop table. */
    public static Entry pickEntry(String joined, Random random) {
        List<Entry> list = entries(joined);
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    /** The id a kart gets from the crate: one of the list at random, or "" for the general drop table. */
    public static String pick(String joined, Random random) {
        Entry e = pickEntry(joined, random);
        return e == null ? "" : e.id();
    }
}

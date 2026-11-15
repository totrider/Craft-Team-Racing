package com.ctrcraft.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/** A power-up crate spot's setting: none (CTR's roulette) or a list of power-up ids, kept as one comma-separated string (so an older single id is a list of one). No Minecraft imports. */
public final class CrateItems {
    private CrateItems() {}

    public static List<String> parse(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null) return out;
        for (String part : joined.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        return out;
    }

    public static String join(Collection<String> ids) {
        return String.join(",", ids);
    }

    public static String normalize(String joined) {
        return join(parse(joined));
    }

    /** The id a crate shows now: the list is cycled, one every second and a half. */
    public static String shown(String joined, long ticks) {
        List<String> ids = parse(joined);
        return ids.isEmpty() ? "" : ids.get((int) ((ticks / 30) % ids.size()));
    }

    /** The id a kart gets from the crate: one of the list at random, or "" for the general drop table. */
    public static String pick(String joined, Random random) {
        List<String> ids = parse(joined);
        return ids.isEmpty() ? "" : ids.get(random.nextInt(ids.size()));
    }
}

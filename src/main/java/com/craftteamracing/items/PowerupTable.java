package com.craftteamracing.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What a power-up crate can give when it is not set to anything itself (the drop table): either CTR's own rule ({@link #ctr()}: the item set follows the driver's place, {@link ItemTables};
 * the baseline, exactly as the original has it) or a weighted list of entries that a rule set swaps in. An entry key is an item id with an optional quantity, in the notation of
 * {@link CrateItems} ({@code bomb}, {@code bomb*3}, {@code bomb*1-3}), so the same item can be listed several times with different quantities. Ids are namespaced strings for data pack items.
 * No Minecraft imports.
 *
 * <p>An entry's weight is a pair, one for the first place and one for the last: a driver in between gets the weight in proportion to their place (the original's sets also change with the
 * place, in steps; the pair is the simple stand-in). A chance is the entry's weight over the total of all weights at that place; the same two numbers make it flat. On top of that
 * {@code quantityByPlace} makes an entry with a quantity range (1-3) lean towards the low end for a driver in front and the high end for one at the back: the first place is centred at
 * {@code biasLow} and the last at {@code biasHigh} (percent of the range), every count staying possible. With it off the count is even across the range.
 */
public final class PowerupTable {
    private static final PowerupTable CTR = new PowerupTable(null, null, false, 0, 100);

    /** What a draw gave: the item id and how many (1 for a single; the classic stacks are their own ids and count 1). */
    public record Drop(String id, int count) {}

    /** The weights for the first place. */
    private final Map<String, Integer> weights;
    /** The weights for the last place (the same keys). */
    private final Map<String, Integer> lastWeights;
    private final boolean quantityByPlace;
    private final int biasLow;
    private final int biasHigh;

    private PowerupTable(Map<String, Integer> weights, Map<String, Integer> lastWeights, boolean quantityByPlace, int biasLow, int biasHigh) {
        this.weights = weights;
        this.lastWeights = lastWeights;
        this.quantityByPlace = quantityByPlace;
        this.biasLow = biasLow;
        this.biasHigh = biasHigh;
    }

    /** The original game's roulette: by place, from the sets in {@link ItemTables}. */
    public static PowerupTable ctr() {
        return CTR;
    }

    /** A flat table: every entry with its weight (an entry with weight 2 comes up twice as often as one with weight 1). Zero weights are dropped. */
    public static PowerupTable custom(Map<String, Integer> weights) {
        return custom(weights, weights, false, 0, 100);
    }

    public static PowerupTable custom(Map<String, Integer> weights, boolean quantityByPlace, int biasLow, int biasHigh) {
        return custom(weights, weights, quantityByPlace, biasLow, biasHigh);
    }

    /** {@code last} holds the weight for the last place of the entries of {@code first} (an entry it does not name is the same at both ends); an entry with 0 at both ends is dropped. */
    public static PowerupTable custom(Map<String, Integer> first, Map<String, Integer> last, boolean quantityByPlace, int biasLow, int biasHigh) {
        Map<String, Integer> f = new LinkedHashMap<>();
        Map<String, Integer> l = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(first.keySet());
        keys.addAll(last.keySet());
        for (String k : keys) {
            int a = Math.max(0, first.getOrDefault(k, last.getOrDefault(k, 0)));
            int b = Math.max(0, last.getOrDefault(k, first.getOrDefault(k, 0)));
            if (a > 0 || b > 0) {
                f.put(k, a);
                l.put(k, b);
            }
        }
        if (f.isEmpty()) throw new IllegalArgumentException("a power-up table needs at least one item with a weight above 0");
        if (f.values().stream().mapToInt(Integer::intValue).sum() <= 0 || l.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            throw new IllegalArgumentException("a power-up table needs something to give at the first and at the last place");
        }
        int lo = Math.max(0, Math.min(100, biasLow));
        int hi = Math.max(0, Math.min(100, biasHigh));
        return new PowerupTable(f, l, quantityByPlace, lo, hi);
    }

    public boolean isCtr() {
        return weights == null;
    }

    /** The first place's weights of a custom table (empty for CTR's). */
    public Map<String, Integer> weights() {
        return weights == null ? Map.of() : Map.copyOf(weights);
    }

    /** The first place's weights in their saved order (the screen keeps them as they were). */
    public Map<String, Integer> orderedWeights() {
        return weights == null ? Map.of() : java.util.Collections.unmodifiableMap(weights);
    }

    /** The last place's weights, in the same order. */
    public Map<String, Integer> orderedLastWeights() {
        return lastWeights == null ? Map.of() : java.util.Collections.unmodifiableMap(lastWeights);
    }

    /** Whether any entry weighs differently at the two ends. */
    public boolean weightsByPlace() {
        return weights != null && !weights.equals(lastWeights);
    }

    public boolean quantityByPlace() {
        return quantityByPlace;
    }

    public int biasLow() {
        return biasLow;
    }

    public int biasHigh() {
        return biasHigh;
    }

    /** The item id drawn for a kart in place {@code rank} (0 first) of {@code field}, on lap {@code lapIndex}. */
    public String draw(int field, int rank, int lapIndex, Random random) {
        return drawDrop(field, rank, lapIndex, random).id();
    }

    /** How far down the field a place is: 0 for the first, 1 for the last (0 for a field of one). */
    private static double fraction(int field, int rank) {
        return field <= 1 ? 0.0 : Math.max(0.0, Math.min(1.0, rank / (double) (field - 1)));
    }

    /** The draw with its quantity: CTR's roulette always gives a count of 1 (its stacks are ids of their own). */
    public Drop drawDrop(int field, int rank, int lapIndex, Random random) {
        if (weights == null) return new Drop(ItemTables.draw(field, rank, lapIndex, random).id(), 1);
        double r = fraction(field, rank);
        double total = 0.0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) total += at(e.getKey(), r);
        double roll = random.nextDouble() * total;
        String key = null;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            double w = at(e.getKey(), r);
            if (w <= 0) continue;
            key = e.getKey();                                                           // the last one with weight is the fallback for rounding
            roll -= w;
            if (roll < 0) break;
        }
        if (key == null) throw new IllegalStateException("unreachable");
        if (key.indexOf('*') < 0) return new Drop(key, 1);                                  // a plain id (the classic stacks are their own ids)
        List<CrateItems.Entry> parsed = CrateItems.entries(key);
        if (parsed.isEmpty()) return new Drop(key.substring(0, key.indexOf('*')).trim(), 1);
        CrateItems.Entry e = parsed.get(0);
        return new Drop(e.id(), count(e, r, random));
    }

    /** An entry's weight for a place {@code r} of the way down the field. */
    public double weightAt(String key, double r) {
        return weights == null ? 0.0 : at(key, r);
    }

    private double at(String key, double r) {
        int a = weights.getOrDefault(key, 0);
        int b = lastWeights.getOrDefault(key, a);
        return a + (b - a) * r;
    }

    /** How many of a ranged entry: even across the range, or leaning with the place when {@link #quantityByPlace}. */
    private int count(CrateItems.Entry e, double r, Random random) {
        if (e.min() == e.max()) return e.min();
        if (!quantityByPlace) return e.roll(random);
        double centre = (biasLow + r * (biasHigh - biasLow)) / 100.0;
        int span = e.max() - e.min();
        double[] w = new double[span + 1];
        double sum = 0.0;
        for (int i = 0; i <= span; i++) {
            w[i] = Math.max(0.05, 1.0 - Math.abs(i / (double) span - centre));
            sum += w[i];
        }
        double pick = random.nextDouble() * sum;
        for (int i = 0; i <= span; i++) {
            pick -= w[i];
            if (pick < 0) return e.min() + i;
        }
        return e.max();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PowerupTable t && java.util.Objects.equals(weights, t.weights) && java.util.Objects.equals(lastWeights, t.lastWeights) && quantityByPlace == t.quantityByPlace
                && biasLow == t.biasLow && biasHigh == t.biasHigh;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(weights, lastWeights, quantityByPlace, biasLow, biasHigh);
    }
}

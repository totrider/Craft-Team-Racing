package com.craftteamracing;

import java.util.LinkedHashMap;
import java.util.Map;

import com.craftteamracing.items.PowerupTable;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A power-up table as data (kept out of {@code items}, which has no Minecraft or library imports): a plain map of entries and weights (no entries: CTR's roulette), or an object with the
 * {@code weights} (the first place's), {@code last_weights} (the last place's; an entry it does not name weighs the same) and the quantity-by-place settings ({@code quantity_by_place},
 * {@code bias_low}, {@code bias_high}). A table with the same weight at both ends and no quantity bias is written as the plain map.
 */
public final class PowerupTableCodec {
    private PowerupTableCodec() {}

    private record Body(Map<String, Integer> weights, Map<String, Integer> lastWeights, boolean quantityByPlace, int biasLow, int biasHigh) {}

    private static final Codec<Body> BODY = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("weights").forGetter(Body::weights),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("last_weights", Map.of()).forGetter(Body::lastWeights),
            Codec.BOOL.optionalFieldOf("quantity_by_place", false).forGetter(Body::quantityByPlace),
            Codec.INT.optionalFieldOf("bias_low", 0).forGetter(Body::biasLow),
            Codec.INT.optionalFieldOf("bias_high", 100).forGetter(Body::biasHigh)).apply(i, Body::new));

    public static final Codec<PowerupTable> CODEC = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.INT), BODY).flatXmap(e -> {
        try {
            Map<String, Integer> first = e.map(m -> m, Body::weights);
            Map<String, Integer> last = e.map(m -> Map.<String, Integer>of(), Body::lastWeights);
            boolean any = first.values().stream().anyMatch(v -> v > 0) || last.values().stream().anyMatch(v -> v > 0);
            if (!any) return DataResult.success(PowerupTable.ctr());
            return DataResult.success(e.map(m -> PowerupTable.custom(new LinkedHashMap<>(m)), b -> PowerupTable.custom(new LinkedHashMap<>(b.weights()), new LinkedHashMap<>(b.lastWeights()),
                    b.quantityByPlace(), b.biasLow(), b.biasHigh())));
        } catch (IllegalArgumentException ex) {
            return DataResult.error(ex::getMessage);
        }
    }, t -> DataResult.success(!t.isCtr() && (t.quantityByPlace() || t.weightsByPlace())
            ? Either.<Map<String, Integer>, Body>right(new Body(t.orderedWeights(), t.orderedLastWeights(), t.quantityByPlace(), t.biasLow(), t.biasHigh()))
            : Either.<Map<String, Integer>, Body>left(t.orderedWeights())));
}

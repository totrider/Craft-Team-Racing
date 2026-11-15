package com.ctrcraft;

import java.util.Map;

import com.ctrcraft.items.PowerupTable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a drop table item holds (the data component {@link ModComponents#DROP_TABLE}): a power-up table (CTR's roulette or a weighted list) and whether applying it sets the track's default. */
public record DropTableData(PowerupTable table, boolean asDefault) {
    public static final DropTableData DEFAULT = new DropTableData(PowerupTable.ctr(), false);

    /** A table as data: no entries is CTR's roulette, else item ids with weights (an id with weight 0 is left out). */
    public static final Codec<PowerupTable> TABLE_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).flatXmap(map -> {
        try {
            Map<String, Integer> weights = map.entrySet().stream().filter(e -> e.getValue() > 0)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
            return DataResult.success(weights.isEmpty() ? PowerupTable.ctr() : PowerupTable.custom(weights));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }, t -> DataResult.success(t.weights()));

    public static final Codec<DropTableData> CODEC = RecordCodecBuilder.create(i -> i.group(
            TABLE_CODEC.fieldOf("table").forGetter(DropTableData::table),
            Codec.BOOL.optionalFieldOf("as_default", false).forGetter(DropTableData::asDefault)).apply(i, DropTableData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DropTableData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}

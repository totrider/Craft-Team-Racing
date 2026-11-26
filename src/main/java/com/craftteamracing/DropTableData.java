package com.craftteamracing;

import com.craftteamracing.items.PowerupTable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a drop table item holds (the data component {@link ModComponents#DROP_TABLE}): a power-up table (CTR's roulette or a weighted list) and whether applying it sets the track's default. */
public record DropTableData(PowerupTable table, boolean asDefault) {
    public static final DropTableData DEFAULT = new DropTableData(PowerupTable.ctr(), false);

    /** A table as data: see {@link PowerupTableCodec}. */
    public static final Codec<PowerupTable> TABLE_CODEC = PowerupTableCodec.CODEC;

    public static final Codec<DropTableData> CODEC = RecordCodecBuilder.create(i -> i.group(
            TABLE_CODEC.fieldOf("table").forGetter(DropTableData::table),
            Codec.BOOL.optionalFieldOf("as_default", false).forGetter(DropTableData::asDefault)).apply(i, DropTableData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DropTableData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}

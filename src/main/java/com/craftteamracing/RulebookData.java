package com.craftteamracing;

import java.util.Map;

import com.craftteamracing.items.PowerupTable;
import com.craftteamracing.items.RuleSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a rulebook item holds (the data component {@link ModComponents#RULEBOOK}): a rule set and whether applying it sets the track's default (otherwise it is for the next race only).
 * Also the codecs of a rule set, for the saved track data and the network.
 */
public record RulebookData(RuleSet rules, boolean asDefault) {
    public static final RulebookData DEFAULT = new RulebookData(RuleSet.CTR, false);

    private record Raw(Map<String, Integer> table, boolean fruit, boolean fruitCrates, boolean powerupCrates, boolean boostPads, int maxSpeed, boolean driverDamage, boolean hunger, boolean offroad, boolean softBumper, int floater, int spring, int lowGravity, boolean paddle) {}

    /** The spring rule is a strength; a rulebook saved when it was a switch (a boolean) loads as 50 % or off. */
    private static final Codec<Integer> SPRING_CODEC = Codec.either(Codec.INT, Codec.BOOL).xmap(e -> e.map(i -> i, b -> b ? 5 : 0), com.mojang.datafixers.util.Either::left);

    private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("table", Map.of()).forGetter(Raw::table),
            Codec.BOOL.optionalFieldOf("fruit", true).forGetter(Raw::fruit),
            Codec.BOOL.optionalFieldOf("fruit_crates", true).forGetter(Raw::fruitCrates),
            Codec.BOOL.optionalFieldOf("powerup_crates", true).forGetter(Raw::powerupCrates),
            Codec.BOOL.optionalFieldOf("boost_pads", true).forGetter(Raw::boostPads),
            Codec.INT.optionalFieldOf("max_speed", 0).forGetter(Raw::maxSpeed),
            Codec.BOOL.optionalFieldOf("driver_damage", false).forGetter(Raw::driverDamage),
            Codec.BOOL.optionalFieldOf("hunger", false).forGetter(Raw::hunger),
            Codec.BOOL.optionalFieldOf("offroad", false).forGetter(Raw::offroad),
            Codec.BOOL.optionalFieldOf("soft_bumper", false).forGetter(Raw::softBumper),
            Codec.INT.optionalFieldOf("floater", 0).forGetter(Raw::floater),
            SPRING_CODEC.optionalFieldOf("spring", 0).forGetter(Raw::spring),
            Codec.INT.optionalFieldOf("low_gravity", 0).forGetter(Raw::lowGravity),
            Codec.BOOL.optionalFieldOf("paddle", false).forGetter(Raw::paddle)).apply(i, Raw::new));

    /** A rule set as data: the power-up table is CTR's (no "table" entry) or a flat list of item ids and weights. */
    public static final Codec<RuleSet> RULESET_CODEC = RAW.flatXmap(raw -> {
        try {
            Map<String, Integer> weights = raw.table().entrySet().stream().filter(e -> e.getValue() > 0)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
            PowerupTable table = weights.isEmpty() ? PowerupTable.ctr() : PowerupTable.custom(weights);
            return DataResult.success(new RuleSet(table, raw.fruit(), raw.fruitCrates(), raw.powerupCrates(), raw.boostPads(), raw.maxSpeed(), raw.driverDamage(), raw.hunger(), raw.offroad(), raw.softBumper(), raw.floater(), raw.spring(), raw.lowGravity(), raw.paddle()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }, rs -> DataResult.success(new Raw(rs.powerups().weights(), rs.fruit(), rs.fruitCrates(), rs.powerupCrates(), rs.boostPads(), rs.maxSpeedPercent(), rs.driverDamage(), rs.hunger(), rs.offroad(), rs.softBumper(), rs.floater(), rs.spring(), rs.lowGravity(), rs.paddle())));

    public static final Codec<RulebookData> CODEC = RecordCodecBuilder.create(i -> i.group(
            RULESET_CODEC.fieldOf("rules").forGetter(RulebookData::rules),
            Codec.BOOL.optionalFieldOf("as_default", false).forGetter(RulebookData::asDefault)).apply(i, RulebookData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RulebookData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, RuleSet> RULES_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(RULESET_CODEC);
}

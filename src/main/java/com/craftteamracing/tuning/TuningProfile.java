package com.craftteamracing.tuning;

import com.craftteamracing.physics.KartClass;
import com.craftteamracing.physics.KartStats;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A kart tuning profile: an id (the datapack entry's, {@code craftteamracing:balanced}), a name and description to show, whether it is one of the original game's engine classes
 * ("official"), and the numbers. Defined by {@code data/<namespace>/craftteamracing/kart_tuning/<name>.json} (see {@link TuningManager}).
 */
public record TuningProfile(String id, String name, String description, boolean official, KartStats stats) {
    /** What a kart without a valid profile is tuned as: the original's Balanced class. */
    public static final TuningProfile FALLBACK = new TuningProfile("craftteamracing:balanced", "Balanced", "", true, KartClass.BALANCED.stats());

    /** The debug profile's id prefix: the id carries the seven numbers ("debug:480,13140,28,4080,5000,14,256"), set by an operator from the tuning screen. */
    public static final String DEBUG_PREFIX = "debug:";

    public static String debugId(KartStats s) {
        return DEBUG_PREFIX + s.accel() + "," + s.speed() + "," + s.turnRate() + "," + s.turnDecrease() + "," + s.turnInputDelay() + "," + s.driftTurnBase() + "," + s.weight();
    }

    /** The profile a debug id stands for, or null when it is not one (or its numbers cannot be run). */
    public static TuningProfile parseDebug(String id) {
        if (id == null || !id.startsWith(DEBUG_PREFIX)) return null;
        String[] parts = id.substring(DEBUG_PREFIX.length()).split(",");
        if (parts.length != 7) return null;
        try {
            int[] v = new int[7];
            for (int i = 0; i < 7; i++) v[i] = Integer.parseInt(parts[i].trim());
            KartStats stats = new KartStats(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
            return stats.valid() ? new TuningProfile(id, "Debug", "Numbers set by an operator for testing.", false, stats) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TuningProfile> STREAM_CODEC = StreamCodec.of(TuningProfile::write, TuningProfile::read);

    private static void write(RegistryFriendlyByteBuf buf, TuningProfile p) {
        ByteBufCodecs.STRING_UTF8.encode(buf, p.id);
        ByteBufCodecs.STRING_UTF8.encode(buf, p.name);
        ByteBufCodecs.STRING_UTF8.encode(buf, p.description);
        buf.writeBoolean(p.official);
        KartStats s = p.stats;
        buf.writeVarInt(s.accel());
        buf.writeVarInt(s.speed());
        buf.writeVarInt(s.turnRate());
        buf.writeVarInt(s.turnDecrease());
        buf.writeVarInt(s.turnInputDelay());
        buf.writeVarInt(s.driftTurnBase());
        buf.writeVarInt(s.weight());
    }

    private static TuningProfile read(RegistryFriendlyByteBuf buf) {
        String id = ByteBufCodecs.STRING_UTF8.decode(buf);
        String name = ByteBufCodecs.STRING_UTF8.decode(buf);
        String description = ByteBufCodecs.STRING_UTF8.decode(buf);
        boolean official = buf.readBoolean();
        KartStats stats = new KartStats(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        return new TuningProfile(id, name, description, official, stats);
    }
}

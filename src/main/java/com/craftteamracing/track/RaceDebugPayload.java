package com.craftteamracing.track;

import java.util.ArrayList;
import java.util.List;

import com.craftteamracing.CraftTeamRacingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, to a racer who asked for it (/ctr debug rings, /ctr debug path): how the server sees this racer's progress. Every ring of the course with what it is to the racer's line
 * (passed, the last one, the one that counts next, or none of those), and the line of the course the racer is nearest to with the point on it.
 */
public record RaceDebugPayload(List<Ring> rings, double[] seg, double[] point, String info) implements CustomPacketPayload {
    public static final int OTHER = 0;
    public static final int PASSED = 1;
    public static final int HEAD = 2;
    public static final int NEXT = 3;

    public record Ring(long pos, int rotation, float[] ext, int state) {}

    public static final Type<RaceDebugPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "race_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaceDebugPayload> STREAM_CODEC = StreamCodec.of(RaceDebugPayload::write, RaceDebugPayload::read);

    private static void write(RegistryFriendlyByteBuf b, RaceDebugPayload p) {
        b.writeVarInt(p.rings.size());
        for (Ring r : p.rings) {
            b.writeLong(r.pos());
            b.writeByte(r.rotation());
            for (float f : r.ext()) b.writeFloat(f);
            b.writeByte(r.state());
        }
        for (double d : p.seg) b.writeDouble(d);
        for (double d : p.point) b.writeDouble(d);
        b.writeUtf(p.info, 300);
    }

    private static RaceDebugPayload read(RegistryFriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 512);
        List<Ring> rings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long pos = b.readLong();
            int rot = b.readByte();
            float[] ext = new float[6];
            for (int k = 0; k < 6; k++) ext[k] = b.readFloat();
            rings.add(new Ring(pos, rot, ext, b.readByte()));
        }
        double[] seg = new double[6];
        for (int i = 0; i < 6; i++) seg[i] = b.readDouble();
        double[] point = new double[3];
        for (int i = 0; i < 3; i++) point[i] = b.readDouble();
        return new RaceDebugPayload(rings, seg, point, b.readUtf(300));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RaceDebugPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.craftteamracing.client.RaceDebugClient.receive(p));
    }
}

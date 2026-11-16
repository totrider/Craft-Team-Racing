package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, to every racer: where the race is and how this racer is doing. Sent when something changes and every few ticks while
 * racing. {@code placeSeq} changes when the server has put the racer on the grid ({@code gx..gyaw}); the client driver then places its sim.
 */
public record RacePayload(int phase, int ticksLeft, int lights, int laps, int lap, int place, int field, int timeMs, int lastLapMs,
                          int bestLapMs, boolean hold, int placeSeq, double gx, double gy, double gz, float gyaw, int finishPlace, int karts,
                          List<UUID> order, List<Integer> lapTimes, List<UUID> barIds, List<Float> barPos, boolean podium, boolean wrongWay, boolean skipped, boolean cooldown)
        implements CustomPacketPayload {
    public static final int OFF = 0;
    public static final int GATHER = 1;
    public static final int LIGHTS = 2;
    public static final int RACING = 3;
    public static final int DONE = 4;

    public static final Type<RacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RacePayload> STREAM_CODEC = StreamCodec.of(RacePayload::write, RacePayload::read);

    public static RacePayload off() {
        return new RacePayload(OFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), false, false, false, false);
    }

    private static void write(RegistryFriendlyByteBuf b, RacePayload p) {
        b.writeVarInt(p.phase);
        b.writeVarInt(p.ticksLeft);
        b.writeVarInt(p.lights);
        b.writeVarInt(p.laps);
        b.writeVarInt(p.lap);
        b.writeVarInt(p.place);
        b.writeVarInt(p.field);
        b.writeVarInt(p.timeMs);
        b.writeVarInt(p.lastLapMs);
        b.writeVarInt(p.bestLapMs);
        b.writeBoolean(p.hold);
        b.writeVarInt(p.placeSeq);
        b.writeDouble(p.gx);
        b.writeDouble(p.gy);
        b.writeDouble(p.gz);
        b.writeFloat(p.gyaw);
        b.writeVarInt(p.finishPlace);
        b.writeVarInt(p.karts);
        b.writeVarInt(p.order.size());
        for (UUID id : p.order) b.writeUUID(id);
        b.writeVarInt(p.lapTimes.size());
        for (int t : p.lapTimes) b.writeVarInt(t);
        b.writeBoolean(p.podium);
        b.writeBoolean(p.wrongWay);
        b.writeBoolean(p.skipped);
        b.writeBoolean(p.cooldown);
        b.writeVarInt(p.barIds.size());
        for (int i = 0; i < p.barIds.size(); i++) {
            b.writeUUID(p.barIds.get(i));
            b.writeFloat(p.barPos.get(i));
        }
    }

    private static RacePayload read(RegistryFriendlyByteBuf b) {
        int phase = b.readVarInt(), ticksLeft = b.readVarInt(), lights = b.readVarInt(), laps = b.readVarInt(), lap = b.readVarInt();
        int place = b.readVarInt(), field = b.readVarInt(), timeMs = b.readVarInt(), lastLapMs = b.readVarInt(), bestLapMs = b.readVarInt();
        boolean hold = b.readBoolean();
        int placeSeq = b.readVarInt();
        double gx = b.readDouble(), gy = b.readDouble(), gz = b.readDouble();
        float gyaw = b.readFloat();
        int finishPlace = b.readVarInt(), karts = b.readVarInt();
        int n = Math.min(b.readVarInt(), 16);
        List<UUID> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) order.add(b.readUUID());
        int m = Math.min(b.readVarInt(), 64);
        List<Integer> lapTimes = new ArrayList<>(m);
        for (int i = 0; i < m; i++) lapTimes.add(b.readVarInt());
        boolean podium = b.readBoolean();
        boolean wrongWay = b.readBoolean();
        boolean skipped = b.readBoolean();
        boolean cooldown = b.readBoolean();
        int k = Math.min(b.readVarInt(), 32);
        List<UUID> barIds = new ArrayList<>(k);
        List<Float> barPos = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            barIds.add(b.readUUID());
            barPos.add(b.readFloat());
        }
        return new RacePayload(phase, ticksLeft, lights, laps, lap, place, field, timeMs, lastLapMs, bestLapMs, hold, placeSeq, gx, gy, gz, gyaw,
                finishPlace, karts, order, lapTimes, barIds, barPos, podium, wrongWay, skipped, cooldown);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RaceClient.update(payload));
    }
}

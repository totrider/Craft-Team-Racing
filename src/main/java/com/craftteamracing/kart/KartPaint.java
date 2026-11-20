package com.craftteamracing.kart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a kart item is painted with (the data component {@code craftteamracing:kart_paint}): the frame's colour (the tub and struts) and the seat's, as RGB. No component: the kart's own colours. */
public record KartPaint(int frame, int seat) {
    public static final KartPaint DEFAULT = new KartPaint(KartEntity.DEFAULT_COLOUR, KartEntity.DEFAULT_SEAT_COLOUR);

    public static final Codec<KartPaint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("frame").forGetter(KartPaint::frame),
            Codec.INT.fieldOf("seat").forGetter(KartPaint::seat)).apply(i, KartPaint::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, KartPaint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, KartPaint::frame, ByteBufCodecs.INT, KartPaint::seat, KartPaint::new);

    public KartPaint withFrame(int rgb) {
        return new KartPaint(rgb, seat);
    }

    public KartPaint withSeat(int rgb) {
        return new KartPaint(frame, rgb);
    }

    /**
     * Dyes mixed into a colour the way vanilla mixes them into leather armour (the average of the colours, brightness kept): {@code current} counts as one of the colours unless it is the part's
     * unpainted {@code base}. Used by the crafting recipe and by dyeing a placed kart, so both give the same colour.
     */
    public static int mix(int current, int base, int... dyes) {
        int[] total = new int[3];
        int maxSum = 0;
        int count = 0;
        java.util.List<Integer> all = new java.util.ArrayList<>();
        if ((current & 0xFFFFFF) != (base & 0xFFFFFF)) all.add(current);
        for (int d : dyes) all.add(d);
        for (int c : all) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            maxSum += Math.max(r, Math.max(g, b));
            total[0] += r;
            total[1] += g;
            total[2] += b;
            count++;
        }
        if (count == 0) return current;
        int r = total[0] / count, g = total[1] / count, b = total[2] / count;
        float avgMax = (float) maxSum / count;
        float maxAvg = Math.max(r, Math.max(g, b));
        if (maxAvg == 0) return 0;
        r = (int) (r * avgMax / maxAvg);
        g = (int) (g * avgMax / maxAvg);
        b = (int) (b * avgMax / maxAvg);
        return (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8) | Math.min(b, 255);
    }
}

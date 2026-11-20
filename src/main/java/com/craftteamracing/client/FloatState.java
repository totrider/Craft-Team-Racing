package com.craftteamracing.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.craftteamracing.kart.FloatSurface;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.track.RaceClient;

import net.minecraft.util.Mth;

/** A kart riding on water (or lava) with the Floater: its pose tilts back a little, as a boat's bow lifts, and it lays no skid marks. Client side, per kart, eased. */
public final class FloatState {
    private FloatState() {}

    /** How far the nose lifts, in degrees. */
    private static final float TILT = 14.0F;
    private static final Map<KartEntity, float[]> TILTS = new WeakHashMap<>();

    /** The Floater the kart has: in a race the rules' (for the racers), otherwise its own. */
    public static int mode(KartEntity kart) {
        return RaceClient.active() && kart.getControllingPassenger() != null ? RaceClient.rules().floater() : kart.floaterMode();
    }

    /** The kart is on the surface of a fluid it floats on (not up in the air over it, not deep under it). */
    public static boolean onFluid(KartEntity kart) {
        int mode = mode(kart);
        if (mode <= 0) return false;
        double top = FloatSurface.top(kart.level(), kart.getX(), kart.getY() + 0.05, kart.getZ(), mode);
        return !Double.isNaN(top) && kart.getY() > top - FloatSurface.SINK - 0.5 && kart.getY() < top - FloatSurface.SINK + 0.25;
    }

    /** Every client tick of the kart: ease the tilt towards its target. */
    public static void tick(KartEntity kart) {
        float[] t = TILTS.computeIfAbsent(kart, k -> new float[2]);
        t[0] = t[1];
        t[1] += ((onFluid(kart) ? TILT : 0.0F) - t[1]) * 0.15F;
    }

    public static float tilt(KartEntity kart, float partialTick) {
        float[] t = TILTS.get(kart);
        return t == null ? 0.0F : Mth.lerp(partialTick, t[0], t[1]);
    }
}

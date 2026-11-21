package com.craftteamracing.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.craftteamracing.kart.FloatSurface;
import com.craftteamracing.kart.KartEntity;
import com.craftteamracing.track.RaceClient;

import net.minecraft.util.Mth;

/** A kart riding on water (or lava) with the Floater: its pose tilts back a little, as a boat's bow lifts, its wheels fold down flat, and it lays no skid marks. Client side, per kart, eased. */
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
        tickFold(kart);
    }

    /** How fast the wheels fold, per tick (the tilt's 0.15 is a little slower: the wheels go about an eighth faster). */
    private static final float FOLD_RATE = 0.17F;
    /** Per kart: the fold last tick and now, 0 to 1, and where it is heading (1 folded, 0 as they drive). */
    private static final Map<KartEntity, float[]> FOLDS = new WeakHashMap<>();

    /** Whether there is solid ground right under the kart (not water, not air). */
    private static boolean onGround(KartEntity kart) {
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(kart.getX(), kart.getY() - 0.15, kart.getZ());
        net.minecraft.world.level.block.state.BlockState state = kart.level().getBlockState(below);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(kart.level(), below).isEmpty();
    }

    private static void tickFold(KartEntity kart) {
        float[] f = FOLDS.computeIfAbsent(kart, k -> new float[3]);
        // the wheels fold when the kart settles on the water and unfold when it settles on ground; in the air (a hop off the water, a drift that lifts it) they stay as they are
        if (mode(kart) <= 0) f[2] = 0.0F;
        else if (onFluid(kart)) f[2] = 1.0F;
        else if (onGround(kart)) f[2] = 0.0F;
        f[0] = f[1];
        f[1] += (f[2] - f[1]) * FOLD_RATE;
    }

    /** How far the wheels are folded down, 0 (as they drive) to 1 (lying flat, facing down). */
    public static float fold(KartEntity kart, float partialTick) {
        float[] f = FOLDS.get(kart);
        return f == null ? 0.0F : Mth.clamp(Mth.lerp(partialTick, f[0], f[1]), 0.0F, 1.0F);
    }

    public static float tilt(KartEntity kart, float partialTick) {
        float[] t = TILTS.get(kart);
        return t == null ? 0.0F : Mth.lerp(partialTick, t[0], t[1]);
    }
}

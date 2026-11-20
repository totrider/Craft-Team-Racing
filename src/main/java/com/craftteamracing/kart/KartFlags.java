package com.craftteamracing.kart;

import com.craftteamracing.physics.KartSim;

/**
 * What other players (and the particle and sound code) need to know about a kart, packed in one int that the driver's client sends
 * and the entity syncs: effect bits, the wheelie frame (5 bits), the turbo flame size (4 bits), the tumble (7 bits) and the airborne pose (3 bits).
 */
public final class KartFlags {
    private KartFlags() {}

    public static final int DRIFTING = 1;
    public static final int BOOSTING = 2;
    public static final int AIRBORNE = 4;
    public static final int BACK_SKID = 8;
    public static final int FRONT_SKID = 16;
    public static final int WALL_RUB = 32;
    public static final int BACKFIRE = 64;
    /** Burning past the sacred fire cap: only a super turbo pad does that in CTR, and we have none yet. */
    public static final int ULTRA = 128;
    /** The bits a client may set (anything else is dropped by the server). */
    public static final int MASK = 0xFFFFFFFF;

    public static int encode(KartSim sim) {
        int f = 0;
        if (sim.state == KartSim.State.DRIFTING) f |= DRIFTING;
        if (sim.reserves() > 0) f |= BOOSTING;
        if (!sim.grounded()) f |= AIRBORNE;
        int skids = sim.skids();
        if ((skids & 1) != 0) f |= BACK_SKID;
        if ((skids & 2) != 0) f |= FRONT_SKID;
        if (sim.againstWall()) f |= WALL_RUB;
        if (sim.failedBoost() > 0) f |= BACKFIRE;
        f |= (sim.wheelieCode() & 31) << 8;
        f |= (sim.fireSize() & 15) << 13;
        f |= (sim.flameFade() & 7) << 17;
        if (sim.ultraFire()) f |= ULTRA;
        int tumble = sim.tumbleCode();
        if (tumble == 0) tumble = sim.squishCode();                                // the field's values below 64 are free: the squash (2, 8 to 15) and the hazard wobble (1)
        if (tumble == 0 && sim.burning()) tumble = BURN_CODE;
        if (tumble == 0 && sim.wobbling()) tumble = WOBBLE_CODE;
        f |= (tumble & 127) << 20;
        f |= (sim.airTiltCode() & 7) << 27;
        return f;
    }

    public static int wheelie(int flags) {
        return (flags >> 8) & 31;
    }

    /** The airborne pose: 0 none, 1 to 4 (see {@link com.craftteamracing.physics.AirTilt}). */
    public static int airTilt(int flags) {
        return (flags >> 27) & 7;
    }

    /** What the exhaust pipes show (bits 30 and 31): 0 no smoke, 1 bright additive, 2 dark (see {@link com.craftteamracing.physics.KartSim#exhaustMode}). */
    public static int exhaust(int flags) {
        return (flags >>> 30) & 3;
    }

    public static int withExhaust(int flags, int mode) {
        return (flags & 0x3FFFFFFF) | ((mode & 3) << 30);
    }

    public static int fireSize(int flags) {
        return (flags >> 13) & 15;
    }

    /** How far the flame has faded, 0 (full) to 7. */
    public static int fade(int flags) {
        return (flags >> 17) & 7;
    }

    /** The value the tumble field holds while the kart wobbles (the Clock, a rain cloud's slow): the tumble's own values all have bit 64 set. */
    public static final int WOBBLE_CODE = 1;
    /** The value of the same field while the kart is on fire. */
    public static final int BURN_CODE = 3;

    /** 0 when the kart is not tumbling (after an explosion), else 64 | the turn of its flip in 64ths of a revolution. */
    public static int tumble(int flags) {
        int t = (flags >> 20) & 127;
        return t >= 64 ? t : 0;
    }

    /** Whether the kart is wobbling: the hazard timer of the original (the Clock, the rain cloud's slow, a squish) rocks it and shakes its wheels. */
    public static boolean wobble(int flags) {
        int t = (flags >> 20) & 127;
        return t == WOBBLE_CODE || t == 2;
    }

    public static boolean burning(int flags) {
        return ((flags >> 20) & 127) == BURN_CODE;
    }

    /** How tall the kart is drawn: 1, or flat while a turbo item's squash holds it (code 2), and the overshoots of its spring back (codes 8 to 15). */
    public static float squishScale(int flags) {
        int t = (flags >> 20) & 127;
        if (t == 2) return 0.08F;
        if (t >= 8 && t < 16) return RECOVER[t - 8];
        return 1.0F;
    }

    private static final float[] RECOVER = { 0.35F, 0.8F, 1.25F, 1.3F, 1.12F, 0.95F, 1.04F, 1.0F };

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }
}

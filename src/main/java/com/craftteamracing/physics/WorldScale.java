package com.craftteamracing.physics;

/**
 * The speed scale of the world, 0.5 to 1: how fast everything may move, as one number for the whole mod. 1 is the original's pace; at 0.5 everything moves half as far in a second
 * (the kart, the weapons that fly, a jump's height and length, a turn's radius), which is what a track half the size, and a server with half the chunk loading, would want. It works by
 * changing how many lu (the physics' own units) make a block, so every number of the original's physics stays untouched; the kart's own size in blocks does not change, so the sizes
 * that belong to the kart (its collision sphere, the bump radius between karts) are written in lu as the base size over the scale.
 *
 * <p>There is one knob with two places that set it: the server's setting ({@link #global()}), and a track's rulebook, which has the same range (50 to 100 percent) and
 * applies to the race's participants only. The rulebook's value is the default of the server's; a rulebook that sets none (0) leaves the server's in force. No Minecraft imports.
 */
public final class WorldScale {
    private WorldScale() {}

    public static final double MIN = 0.5;
    /** The rulebook's range, percent of the server's scale. */
    private static volatile double global = 1.0;
    private static volatile int track = 0;
    private static volatile double speed = 1.0;
    private static volatile boolean shrinkJumps = true;

    /** Whether a lower speed lowers and shortens the jumps and takes air time off them (the default; off: the same arc at a smaller size, in the same time). */
    public static boolean shrinkJumps() {
        return shrinkJumps;
    }

    public static void setShrinkJumps(boolean on) {
        shrinkJumps = on;
    }

    /**
     * What the hop's launch speed is multiplied by, and the air's gravity too: with jumps shrinking (the default) gravity stays as strong in blocks as at full speed, so the lower
     * speed of a ramp jump gives a lower, shorter jump with less air time, and a plain hop is given its full-speed launch so it stays the same. Off, the whole arc is the full-speed
     * one at the scale's size, in the same time (floaty beside the kart, which is drawn at its full size).
     */
    public static double hopFactor() {
        return shrinkJumps ? 1.0 / speed : 1.0;
    }

    /** The scale in force. */
    public static double speed() {
        return speed;
    }

    /** The server's own setting. */
    public static double global() {
        return global;
    }

    public static void setGlobal(double s) {
        global = Math.max(MIN, Math.min(1.0, s));
        speed = effective(global, track);
    }

    /** The race's rulebook pace, percent (50 to 100); 0 when there is no race, or a rulebook that leaves the server's in force. */
    public static void setTrack(int percent) {
        track = percent <= 0 ? 0 : Math.max(50, Math.min(100, percent));
        speed = effective(global, track);
    }

    public static int track() {
        return track;
    }

    public static double effective(double global, int trackPercent) {
        return trackPercent <= 0 ? global : Math.max(MIN, Math.min(1.0, trackPercent / 100.0));
    }

    /** Back to the original's pace: the server's setting and the track's. */
    public static void reset() {
        global = 1.0;
        track = 0;
        speed = 1.0;
        shrinkJumps = true;
    }
}

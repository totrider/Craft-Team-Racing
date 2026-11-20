package com.craftteamracing.physics;

/**
 * A kart's tuning: the numbers a profile sets (the MetaPhys class stats of NTSC-U CTR, zGlobal_DATA.c:7122-7189, plus the collision weight, :7240). The four engine classes of
 * {@link KartClass} are the official ones; a datapack can define more (docs/KART_TUNING.md). No Minecraft imports.
 *
 * @param accel          Accel_ClassStat: engine push per frame
 * @param speed          Speed_ClassStat: top speed
 * @param turnRate       TurnRate: steer strength at full lock
 * @param turnDecrease   TurnDecreaseRate: top speed lost at full steer
 * @param turnInputDelay TurnInputDelay: steering response per frame
 * @param driftTurnBase  DriftTurnBase: the powerslide's base turn
 * @param weight         COLLISION_WEIGHT: how hard the kart pushes in a bump
 */
public record KartStats(int accel, int speed, int turnRate, int turnDecrease, int turnInputDelay, int driftTurnBase, int weight) {
    /** Balanced: what a kart without a (valid) profile has. */
    public static final KartStats BALANCED = KartClass.BALANCED.stats();

    /** Whether every number is one the simulation can run on (a datapack may be wrong; nothing here may divide by zero or run backwards). */
    public boolean valid() {
        return accel >= 1 && accel <= 5000 && speed >= 1000 && speed <= 60000 && turnRate >= 1 && turnRate <= 200 && turnDecrease >= 0 && turnDecrease <= 60000
                && turnInputDelay >= 1 && turnInputDelay <= 60000 && driftTurnBase >= 0 && driftTurnBase <= 200 && weight >= 1 && weight <= 4096;
    }
}

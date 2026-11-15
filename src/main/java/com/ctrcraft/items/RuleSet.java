package com.ctrcraft.items;

/**
 * The rules of a race, set on a race track's start (applied with the rulebook item, see docs/DESIGN_ITEMS.md). The default, {@link #CTR},
 * is the original game's experience. No Minecraft imports.
 *
 * @param powerups        what power-up crates give
 * @param fruit           loose power fruit are spawned
 * @param fruitCrates     fruit crates are spawned
 * @param powerupCrates   power-up (weapon) crates are spawned
 * @param boostPads       boost blocks act on karts
 * @param maxSpeedPercent a cap on the top speed as a percentage of the kart class's, 0 for none
 * @param driverDamage    a racing driver can lose health (to any damage: vanilla sources and the weapons' explosions alike); off by default, the karts react to hits as the rules say
 *                        but the driver's health is never touched
 * @param hunger          a racing driver's hunger drains as in vanilla; off by default (the food bar holds still)
 * @param offroad         every racing kart ignores rough terrain (grass, dirt, sand, snow, mud drive like asphalt), whatever the karts' own upgrades are set to; off by default
 * @param softBumper      every racing kart does no collision damage to the entities it bumps into, whatever the karts' own upgrades are set to; off by default
 *                        (in a race the track's rules always override the karts' own settings of these two upgrades)
 * @param floater         every racing kart floats: 0 not, 1 on water, 2 on water and lava (the Floater / Obsidian Floater upgrades); off by default, and the karts' own upgrades do not count in a race
 * @param spring          every racing kart can jump with the Jump Spring key; off by default
 * @param lowGravity      every racing kart falls slower, 0 (off) to 10 tenths (the Low Gravity Module's strength); off by default
 */
public record RuleSet(PowerupTable powerups, boolean fruit, boolean fruitCrates, boolean powerupCrates, boolean boostPads, int maxSpeedPercent, boolean driverDamage, boolean hunger,
                      boolean offroad, boolean softBumper, int floater, boolean spring, int lowGravity) {
    /** The original game's rules. */
    public static final RuleSet CTR = new RuleSet(PowerupTable.ctr(), true, true, true, true, 0, false, false, false, false, 0, false, 0);

    public RuleSet {
        if (powerups == null) throw new IllegalArgumentException("powerups");
        if (floater < 0 || floater > 2) throw new IllegalArgumentException("floater is 0 (off), 1 (water) or 2 (water and lava)");
        if (lowGravity < 0 || lowGravity > 10) throw new IllegalArgumentException("lowGravity is 0 to 10 tenths");
        if (maxSpeedPercent < 0 || maxSpeedPercent > 100) throw new IllegalArgumentException("maxSpeedPercent is 0 (no cap) to 100");
    }

    public RuleSet withPowerups(PowerupTable t) {
        return new RuleSet(t, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withFruit(boolean on) {
        return new RuleSet(powerups, on, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withFruitCrates(boolean on) {
        return new RuleSet(powerups, fruit, on, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withPowerupCrates(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, on, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withBoostPads(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, on, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withMaxSpeedPercent(int percent) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, percent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withDriverDamage(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, on, hunger, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withHunger(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, on, offroad, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withOffroad(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, on, softBumper, floater, spring, lowGravity);
    }

    public RuleSet withSoftBumper(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, on, floater, spring, lowGravity);
    }

    public RuleSet withFloater(int mode) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, mode, spring, lowGravity);
    }

    public RuleSet withSpring(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, on, lowGravity);
    }

    public RuleSet withLowGravity(int tenths) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, tenths);
    }

    public boolean isCtr() {
        return equals(CTR);
    }
}

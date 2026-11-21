package com.craftteamracing.items;

/**
 * The rules of a race, set on a race track's start (applied with the rulebook item, see docs/DESIGN_ITEMS.md). The default, {@link #CTR},
 * is the original game's experience. No Minecraft imports.
 *
 * @param powerups        what power-up crates give
 * @param fruit           loose power fruit are spawned
 * @param fruitCrates     fruit crates are spawned
 * @param powerupCrates   power-up (weapon) crates are spawned
 * @param boostPads       boost blocks act on karts
 * @param speedPercent the race's pace, 50 to 100 percent like the server's speed scale, which it replaces for the race's participants; 0 leaves the server's (see WorldScale)
 * @param driverDamage    a racing driver can lose health (to any damage: vanilla sources and the weapons' explosions alike); off by default, the karts react to hits as the rules say
 *                        but the driver's health is never touched
 * @param hunger          a racing driver's hunger drains as in vanilla; off by default (the food bar holds still)
 * @param offroad         every racing kart ignores rough terrain (grass, dirt, sand, snow, mud drive like asphalt), whatever the karts' own upgrades are set to; off by default
 * @param softBumper      every racing kart does no collision damage to the entities it bumps into, whatever the karts' own upgrades are set to; off by default
 *                        (in a race the track's rules always override the karts' own settings of these two upgrades)
 * @param floater         every racing kart floats: 0 not, 1 on water, 2 on water and lava (the Floater / Obsidian Floater upgrades); off by default, and the karts' own upgrades do not count in a race
 * @param spring          every racing kart has the Jump Spring's double jump at this strength, 0 (off) to 10 tenths (5 is the spring's own high jump); off by default
 * @param paddle         every racing kart's floating is at full speed on the water instead of the water's slowdown (the karts' own Paddles do not count in a race); off by default
 * @param skis           every racing kart drives on top of powder snow (the Snow Skis upgrade); the karts' own Snow Skis do not count in a race; off by default
 * @param lowGravity      every racing kart falls slower, 0 (off) to 10 tenths (the Low Gravity Module's strength); off by default
 */
public record RuleSet(PowerupTable powerups, boolean fruit, boolean fruitCrates, boolean powerupCrates, boolean boostPads, int speedPercent, boolean driverDamage, boolean hunger,
                      boolean offroad, boolean softBumper, int floater, int spring, int lowGravity, boolean paddle, boolean skis) {
    /** The original game's rules. */
    public static final RuleSet CTR = new RuleSet(PowerupTable.ctr(), true, true, true, true, 0, false, false, false, false, 0, 0, 0, false, false);

    public RuleSet {
        if (powerups == null) throw new IllegalArgumentException("powerups");
        if (floater < 0 || floater > 2) throw new IllegalArgumentException("floater is 0 (off), 1 (water) or 2 (water and lava)");
        if (spring < 0 || spring > 10) throw new IllegalArgumentException("spring is 0 to 10 tenths");
        if (lowGravity < 0 || lowGravity > 10) throw new IllegalArgumentException("lowGravity is 0 to 10 tenths");
        if (speedPercent != 0 && (speedPercent < 50 || speedPercent > 100)) throw new IllegalArgumentException("speedPercent is 0 (the server speed scale) or 50 to 100");
    }

    public RuleSet withPowerups(PowerupTable t) {
        return new RuleSet(t, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withFruit(boolean on) {
        return new RuleSet(powerups, on, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withFruitCrates(boolean on) {
        return new RuleSet(powerups, fruit, on, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withPowerupCrates(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, on, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withBoostPads(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, on, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withSpeedPercent(int percent) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, percent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withDriverDamage(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, on, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withHunger(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, on, offroad, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withOffroad(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, on, softBumper, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withSoftBumper(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, on, floater, spring, lowGravity, paddle, skis);
    }

    public RuleSet withFloater(int mode) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, mode, spring, lowGravity, paddle, skis);
    }

    public RuleSet withSpring(int tenths) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, tenths, lowGravity, paddle, skis);
    }

    public RuleSet withLowGravity(int tenths) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, tenths, paddle, skis);
    }

    public RuleSet withPaddle(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, on, skis);
    }

    public RuleSet withSkis(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, speedPercent, driverDamage, hunger, offroad, softBumper, floater, spring, lowGravity, paddle, on);
    }

    public boolean isCtr() {
        return equals(CTR);
    }
}

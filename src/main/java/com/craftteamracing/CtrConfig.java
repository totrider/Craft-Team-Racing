package com.craftteamracing;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings (config/craftteamracing-server.toml in the world's serverconfig folder). */
public final class CtrConfig {
    private CtrConfig() {}

    /** Who may place and break track nodes, link them and run test drives. A claims mod (Open Parties and Claims) hooks in later. */
    public enum EditPolicy { CREATIVE_OR_OPERATOR, EVERYONE }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<EditPolicy> TRACK_EDITING;
    public static final ModConfigSpec.IntValue RACE_MIN_RACERS;
    public static final ModConfigSpec.DoubleValue PICKUP_TRACK_RADIUS;
    public static final ModConfigSpec.BooleanValue ENTITY_CONTACT;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE_PLAYERS;
    public static final ModConfigSpec.DoubleValue ENTITY_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue WEAPON_SPEED_SCALE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_TUNING;
    public static final ModConfigSpec.BooleanValue POWER_PALMS;
    public static final ModConfigSpec.DoubleValue POWER_PALMS_PER_CHUNK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TRACK_EDITING = builder
                .comment("Who may edit race tracks (place and break track nodes, link them with the Track Tool).",
                        "CREATIVE_OR_OPERATOR: players in creative mode or with operator rights. EVERYONE: anyone holding the items.")
                .defineEnum("trackEditing", EditPolicy.CREATIVE_OR_OPERATOR);
        RACE_MIN_RACERS = builder
                .comment("How many karts have to be inside a Start node's area for a race to start by itself (the countdown then begins).")
                .defineInRange("raceMinRacers", 1, 1, 16);
        PICKUP_TRACK_RADIUS = builder
                .comment("A fruit, crate or power-up spot belongs to a track when it is within this many blocks of the track's line (the links between its nodes);",
                        "its pickup then appears while a race on that track is on. A spot with a redstone signal is always on.")
                .defineInRange("pickupTrackRadius", 96.0, 8.0, 512.0);
        builder.push("entities");
        ENTITY_CONTACT = builder
                .comment("Karts bump into other entities (mobs, boats, minecarts, players on foot): the kart bounces and the entity is thrown.",
                        "Kart against kart is the classic kart bump and is not affected by this.")
                .define("contact", true);
        ENTITY_DAMAGE = builder
                .comment("A kart hurts the living entities it hits at speed (the driver gets the credit). Needs contact to be on.")
                .define("damage", true);
        ENTITY_DAMAGE_PLAYERS = builder
                .comment("Players on foot can be hurt too. Still needs the server's PvP to be on, and follows teams and claims rules for harming players.")
                .define("damagePlayers", true);
        ENTITY_DAMAGE_SCALE = builder
                .comment("Multiplier on the damage: 1 is about 1 heart for every 2 blocks/s of speed over 6 (12 hp, 6 hearts, at top speed).")
                .defineInRange("damageScale", 1.0, 0.0, 10.0);
        builder.pop();
        WEAPON_SPEED_SCALE = builder
                .comment("Multiplier on how fast the Bomb rolls and how far a Potion is thrown (its horizontal speed). 1 is the original's value in the kart's own scale",
                        "(the bomb 96 units a frame against a kart's 78, a potion 120): the world is 50 units to a block like the kart, so these are fast beside crates and",
                        "potions drawn a block big. Lower it to slow them down; it does not change the kart.")
                .defineInRange("weaponSpeedScale", 1.0, 0.1, 2.0);
        DISABLED_TUNING = builder
                .comment("Kart tuning profiles that cannot be picked (ids; craftteamracing ones may be written without the namespace, e.g. \"speed\"). Data packs can define more profiles and turn any off too",
                        "(data/<namespace>/craftteamracing/kart_tuning/<name>.json, see docs/KART_TUNING.md). A kart whose profile is off is tuned as Balanced; if every profile is off, as the original Balanced.")
                .defineListAllowEmpty("disabledTuningProfiles", java.util.List.of(), () -> "", o -> o instanceof String);
        builder.push("worldgen");
        POWER_PALMS = builder
                .comment("Power Palm trees grow on beaches in newly generated chunks. Turn off to stop them (saplings and planted palms still work; chunks already generated keep theirs).")
                .define("powerPalms", true);
        POWER_PALMS_PER_CHUNK = builder
                .comment("About how many Power Palms are tried per beach chunk (a palm needs sand and room, so fewer end up standing). 0 is none, 1 is the default.")
                .defineInRange("powerPalmsPerChunk", 1.0, 0.0, 8.0);
        builder.pop();
        SPEC = builder.build();
    }
}

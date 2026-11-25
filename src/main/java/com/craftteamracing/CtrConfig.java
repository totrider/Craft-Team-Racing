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
    public static final ModConfigSpec.BooleanValue KEEP_COURSE_LOADED;
    public static final ModConfigSpec.BooleanValue KEEP_WEAPONS_LOADED;
    public static final ModConfigSpec.BooleanValue ENTITY_CONTACT;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE_PLAYERS;
    public static final ModConfigSpec.DoubleValue ENTITY_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue SPEED_SCALE;
    public static final ModConfigSpec.BooleanValue SHRINK_JUMPS;
    public static final ModConfigSpec.DoubleValue WEAPON_SPEED_SCALE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_TUNING;
    public static final ModConfigSpec.BooleanValue POWER_PALMS;
    public static final ModConfigSpec.DoubleValue POWER_PALMS_PER_CHUNK;
    public static final ModConfigSpec.BooleanValue OASES;
    public static final ModConfigSpec.IntValue OASIS_RARITY;

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
        KEEP_COURSE_LOADED = builder
                .comment("Keep the chunks along a track (its nodes and the links between them) loaded and running from the moment a race gathers until it ends, so the pickup spots along the whole track keep their crates",
                        "and the track's redstone keeps working whoever is near. Costs server time on a long track; switch off if a claims or chunk-loading mod already does this.")
                .define("keepCourseLoaded", true);
        KEEP_WEAPONS_LOADED = builder
                .comment("Keep the chunks round a placed or flying weapon (TNT and Nitro crates, Potions, Bombs, Missiles, Warp Orbs) loaded for as long as it exists, so a trap does not vanish when nobody is near it and a missile or orb",
                        "does not freeze in the air at the edge of the loaded world.")
                .define("keepWeaponsLoaded", true);
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
        SPEED_SCALE = builder
                .comment("How fast everything may move, in percent (50 to 100): the kart, the weapons that fly, and with them a jump's height and length and a turn's radius, in the open world too.",
                        "100 is the original's pace. Lower it for a weaker server (less ground to load a second) or a smaller track; the kart itself keeps its size. A track's rulebook has the same range and replaces this for the race's participants; this is its default.",
                        "Operators can change it while playing with /ctr speed <percent> to find the right value.")
                .defineInRange("speedScale", 100, 50, 100);
        SHRINK_JUMPS = builder
                .comment("Whether a lower speedScale also lowers and shortens the jumps (on, the default): with less speed you get less height, less air time and less distance off a ramp, as gravity",
                        "takes over sooner; a plain hop stays as it is. The hang-time boost on landing follows the time in the air, so a shorter jump gives less of it.",
                        "Off: the jump keeps its shape, the same arc at the scale's smaller size in the same time (floaty beside the kart). Operators: /ctr speed shrinkjumps <true|false>.")
                .define("shrinkJumps", true);
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
                .comment("Power Palm trees grow on beaches, and on desert sand or jungle ground within a few blocks of water, in newly generated chunks. Turn off to stop them (saplings and planted palms still work; chunks already generated keep theirs).")
                .define("powerPalms", true);
        POWER_PALMS_PER_CHUNK = builder
                .comment("About how many Power Palms are tried per beach chunk (a palm needs sand and room, so fewer end up standing). 0 is none, 1 is the default.")
                .defineInRange("powerPalmsPerChunk", 1.0, 0.0, 8.0);
        OASES = builder
                .comment("Small oases (a pond, a grass rim and a few Power Palms) in newly generated deserts. Turn off to stop them (chunks already generated keep theirs). Power Palms on desert sand beside water follow the powerPalms setting above.")
                .define("oases", true);
        OASIS_RARITY = builder
                .comment("How rare an oasis is: about one in this many desert chunks. Higher is rarer; 40 is the default.")
                .defineInRange("oasisRarity", 40, 1, 1000);
        builder.pop();
        SPEC = builder.build();
    }
}

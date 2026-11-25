package com.craftteamracing;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings (config/craftteamracing-client.toml): what this player hears and sees, nothing that changes the race. */
public final class CtrClientConfig {
    private CtrClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPIN_OUT_SOUND;
    public static final ModConfigSpec.BooleanValue ANIMATED_BOOST_PADS;
    public static final ModConfigSpec.BooleanValue PODIUM_CAMERA;
    public static final ModConfigSpec.BooleanValue WRONG_WAY_WARNING;
    public static final ModConfigSpec.BooleanValue SKIPPED_CHECKPOINT_WARNING;
    public static final ModConfigSpec.BooleanValue MOTION_BLUR;
    public static final ModConfigSpec.BooleanValue GRID_WHEN_IDLE;
    public static final ModConfigSpec.BooleanValue SHIELD_DARK_LAYER;
    public static final ModConfigSpec.BooleanValue HIDE_JADE_IN_RACE;
    public static final ModConfigSpec.BooleanValue SHOW_BARRIERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SPIN_OUT_SOUND = builder
                .comment("Play the mod's own spin-out sound when your kart spins out. The original game has no such sound, so it is off by default.")
                .define("spinOutSound", false);
        ANIMATED_BOOST_PADS = builder
                .comment("The arrows on the boost pads run the way they point (an animated texture). Off: they stand still. Changing it reloads the resources.")
                .define("animatedBoostPads", true);
        PODIUM_CAMERA = builder
                .comment("Move the camera round the front of your kart (or over the whole podium, when you are not on it) while the podium is on display after a race.")
                .define("podiumCamera", true);
        WRONG_WAY_WARNING = builder
                .comment("Show \"WRONG WAY\" on screen while you drive the wrong way round the course.")
                .define("wrongWayWarning", true);
        SKIPPED_CHECKPOINT_WARNING = builder
                .comment("Show a warning on screen when you have skipped a checkpoint (nothing counts until you go back to it).")
                .define("skippedCheckpointWarning", true);
        MOTION_BLUR = builder
                .comment("The screen blur while the Clock holds you (and the echo when you use it). Turn it off if screen motion makes you feel sick: the white flash then stays, the blur goes.")
                .define("motionBlur", true);
        GRID_WHEN_IDLE = builder
                .comment("Draw the start grid of a finished track whenever nobody is racing on it (not only while a track item is in hand). It is hidden once a race reaches the lights.")
                .define("gridWhenIdle", true);
        HIDE_JADE_IN_RACE = builder
                .comment("With Jade installed, hide its pop-up while you take part in a race (from the gathering to the finish), so it does not get in the way of the race's own screen.")
                .define("hideJadeInRace", true);
        SHOW_BARRIERS = builder
                .comment("Draw the Kart Barriers (the invisible walls that stop karts in a race) all the time, as a cyan ghost, not only while you hold a track building item. /ctr barriers switches it.")
                .define("showBarriers", false);
        SHIELD_DARK_LAYER = builder
                .comment("The original's held shield bubble has a dark layer under its colour (the thrown one has none): it darkens the picture behind the bubble. Turn it off to have the held shield look",
                        "like the thrown one (a bright colour layer only).")
                .define("shieldDarkLayer", true);
        SPEC = builder.build();
    }
}

package com.ctrcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings (config/ctrcraft-client.toml): what this player hears and sees, nothing that changes the race. */
public final class CtrClientConfig {
    private CtrClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPIN_OUT_SOUND;
    public static final ModConfigSpec.BooleanValue ANIMATED_BOOST_PADS;
    public static final ModConfigSpec.BooleanValue PODIUM_CAMERA;
    public static final ModConfigSpec.BooleanValue WRONG_WAY_WARNING;
    public static final ModConfigSpec.BooleanValue SKIPPED_CHECKPOINT_WARNING;

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
        SPEC = builder.build();
    }
}

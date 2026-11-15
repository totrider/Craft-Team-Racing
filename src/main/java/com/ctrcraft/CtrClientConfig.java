package com.ctrcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings (config/ctrcraft-client.toml): what this player hears and sees, nothing that changes the race. */
public final class CtrClientConfig {
    private CtrClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPIN_OUT_SOUND;
    public static final ModConfigSpec.BooleanValue ANIMATED_BOOST_PADS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SPIN_OUT_SOUND = builder
                .comment("Play the mod's own spin-out sound when your kart spins out. The original game has no such sound, so it is off by default.")
                .define("spinOutSound", false);
        ANIMATED_BOOST_PADS = builder
                .comment("The arrows on the boost pads run the way they point (an animated texture). Off: they stand still. Changing it reloads the resources.")
                .define("animatedBoostPads", true);
        SPEC = builder.build();
    }
}

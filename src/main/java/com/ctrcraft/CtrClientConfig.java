package com.ctrcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings (config/ctrcraft-client.toml): what this player hears and sees, nothing that changes the race. */
public final class CtrClientConfig {
    private CtrClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPIN_OUT_SOUND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SPIN_OUT_SOUND = builder
                .comment("Play the mod's own spin-out sound when your kart spins out. The original game has no such sound, so it is off by default.")
                .define("spinOutSound", false);
        SPEC = builder.build();
    }
}

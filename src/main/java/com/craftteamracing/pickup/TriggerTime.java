package com.craftteamracing.pickup;

import net.minecraft.network.chat.Component;

/**
 * The step scale of the trigger crate's timers: 1 to 10 game ticks, then 1 to 60 seconds, then 2 to 5 minutes, and step 0 for the setting's own special value (a redstone
 * duration of "always", a respawn of the usual crate delay).
 */
public final class TriggerTime {
    private TriggerTime() {}

    public static final int MAX_STEP = 10 + 60 + 4;
    public static final int MAX_USES = 64;
    /** A respawn at step 0: the crates' own delay (PickupKind#respawnTicks). */
    public static final int DEFAULT_RESPAWN = 31;

    /** The step in game ticks (step 0 is 0: the caller's special value). */
    public static long ticks(int step) {
        if (step <= 0) return 0;
        if (step <= 10) return step;
        if (step <= 70) return (step - 10) * 20L;
        return (step - 69) * 1200L;
    }

    public static Component label(int step, String zeroKey) {
        if (step <= 0) return Component.translatable(zeroKey);
        if (step <= 10) return Component.translatable("screen.craftteamracing.trigger.ticks", step);
        if (step <= 70) return Component.translatable("screen.craftteamracing.trigger.seconds", step - 10);
        return Component.translatable("screen.craftteamracing.trigger.minutes", step - 69);
    }
}

package com.craftteamracing.client;

import com.craftteamracing.pickup.BombEntity;

/** The missile's exhaust (emSet_Missile, drawn in single player in the original): a bright puff behind its nozzle every tick (the mod's own particle: nothing from vanilla, so a resource pack's look is all there is). */
public final class MissileFx {
    private MissileFx() {}

    public static void tick(BombEntity missile) {
        if (!missile.level().isClientSide) return;
        Fx.play("missile_exhaust", missile.getX(), missile.getY(), missile.getZ(), missile.getYRot(), 0.0F, missile.tickCount);
    }
}

package com.craftteamracing.pickup;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Tracking Missile power-up: the same flying explosive as the Bomb ({@code RB_MovingExplosive_ThTick} serves both, launched by the same branch of
 * {@code VehPickupItem_ShootNow}), with the missile's numbers from the tables: it flies at 80 lu a frame ({@code 5 >> 8} of the heading's unit vector), 96 with ten power fruit
 * (which also makes it turn faster and blast wider); it turns towards its target by 0x40 of 4096 per frame (0x80 charged) where a bomb turns by 4; it falls twice as hard
 * (gravity {@code elapsed << 3}); it never rolls, is never fired backwards and the use key does not detonate it. Without a target it flies straight. Not done: the warning
 * that shows a driver a missile is chasing it ({@code thTrackingMe}), the rocket's own sound.
 */
public class MissileEntity extends BombEntity {
    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean isMissile() {
        return true;
    }

    @Override
    protected double baseSpeed() {
        return powered() ? 96.0 : 80.0;
    }

    @Override
    protected double turnPerFrame() {
        return (powered() ? 128.0 : 64.0) * TURN_UNIT;
    }

    @Override
    protected double gravity() {
        return 8.0;
    }
}

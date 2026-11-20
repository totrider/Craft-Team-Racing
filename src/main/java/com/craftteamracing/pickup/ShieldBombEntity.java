package com.craftteamracing.pickup;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

import com.craftteamracing.kart.KartEntity;

/**
 * A thrown Power Shield (RB_ShieldDark_ThTick_Grow, SHIELD_FLAG_SHOOT): the bubble leaves the kart as a small moving explosive (the same {@code RB_MovingExplosive_ThTick} as a bomb,
 * model DYNAMIC_SHIELD or SHIELD_GREEN at a quarter scale), straight ahead at the bomb's 96 lu a frame with no target, its owner spared for 10 frames; it blasts as a missile does
 * (sound 0x4c, the plain blast). Green, or blue when the shield was the charged one.
 */
public class ShieldBombEntity extends BombEntity {
    /** The thrown shield goes along the kart's heading (RB_ShieldDark_ThTick_Grow: tw->rotY = player->angle), not along its model. */
    @Override
    protected float launchYaw(com.craftteamracing.kart.KartEntity kart) {
        return kart.getYRot();
    }

    private static final EntityDataAccessor<Boolean> DATA_BLUE = SynchedEntityData.defineId(ShieldBombEntity.class, EntityDataSerializers.BOOLEAN);

    public ShieldBombEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLUE, false);
    }

    public boolean blue() {
        return entityData.get(DATA_BLUE);
    }

    public void setBlue(boolean blue) {
        entityData.set(DATA_BLUE, blue);
    }

    @Override
    protected UUID chooseTarget(ServerPlayer driver, KartEntity from) {
        return null;                       // tw->driverTarget = NULL
    }

    /** tw->parentSafetyFrames = 10. */
    @Override
    protected int ownerSparedTicks() {
        return 7;
    }

    @Override
    protected boolean missileSound() {
        return true;
    }
}

package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.ctrcraft.pickup.BombEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The rolling bomb's sound (and the missile's flight sound, 0x4b): PlaySound3D_Flags(0x48) every frame it moves, a loop that follows the bomb until it explodes (RB_MovingExplosive_ThTick). */
public final class BombRollSound extends AbstractTickableSoundInstance {
    private final BombEntity bomb;

    private BombRollSound(BombEntity bomb, net.minecraft.sounds.SoundEvent sound) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.bomb = bomb;
        this.looping = true;
        this.delay = 0;
        this.volume = 4.0F;                 // a variable-range sound carries 16 blocks times its volume: 64 blocks, as the original's 3D sound carries far
        this.x = bomb.getX();
        this.y = bomb.getY();
        this.z = bomb.getZ();
    }

    @Override
    public void tick() {
        if (bomb.isRemoved()) {
            stop();
            return;
        }
        this.x = bomb.getX();
        this.y = bomb.getY();
        this.z = bomb.getZ();
    }

    public static void start(BombEntity bomb, net.minecraft.sounds.SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(new BombRollSound(bomb, sound));
    }
}

package com.craftteamracing.spectate;

import com.craftteamracing.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A viewing console's link: the Start node (packed block position) whose race it shows, or none. */
public class ViewingConsoleBlockEntity extends BlockEntity {
    private boolean linked;
    private long start;

    public ViewingConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIEWING_CONSOLE.get(), pos, state);
    }

    public boolean linked() {
        return linked;
    }

    public long start() {
        return start;
    }

    public void link(long startPos) {
        this.linked = true;
        this.start = startPos;
        setChanged();
    }

    public void unlink() {
        this.linked = false;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("linked", linked);
        tag.putLong("start", start);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linked = tag.getBoolean("linked");
        start = tag.getLong("start");
    }
}

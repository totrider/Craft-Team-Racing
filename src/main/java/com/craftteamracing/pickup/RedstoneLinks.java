package com.craftteamracing.pickup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The Redstone Linking Node a player has picked with the Wrench, waiting for the Trigger Crate Spot to be linked to (the next Wrench click on one). Not saved: it is a gesture. */
public final class RedstoneLinks {
    private RedstoneLinks() {}

    private static final Map<UUID, GlobalPos> SELECTED = new HashMap<>();

    public static void select(UUID player, ResourceKey<Level> dimension, BlockPos pos) {
        SELECTED.put(player, GlobalPos.of(dimension, pos));
    }

    /** The picked node if it is in this dimension (it stays picked). */
    public static BlockPos selected(UUID player, ResourceKey<Level> dimension) {
        GlobalPos g = SELECTED.get(player);
        return g != null && g.dimension().equals(dimension) ? g.pos() : null;
    }

    public static void clear(UUID player) {
        SELECTED.remove(player);
    }
}

package com.ctrcraft.kart;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/** Where a kart with the Floater rides: the surface of the water (and of lava, for the Obsidian Floater), a little below its top, so the kart sits IN the water as a boat does. */
public final class FloatSurface {
    private FloatSurface() {}

    /** How far below the surface the kart's feet are. */
    public static final double SINK = 0.18;

    /** A fluid a kart floats on: water, and lava for the Obsidian Floater (mode 2). */
    public static boolean floats(FluidState fluid, int mode) {
        return mode >= 1 && (fluid.is(FluidTags.WATER) || (mode == 2 && fluid.is(FluidTags.LAVA)));
    }

    /** The top of the fluid column that contains the point (NaN when the point is not in a fluid the kart floats on). */
    public static double top(Level level, double x, double y, double z, int mode) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!floats(level.getFluidState(p), mode)) return Double.NaN;
        for (int i = 0; i < 24; i++) {
            p.setY(p.getY() + 1);
            if (!floats(level.getFluidState(p), mode)) {
                p.setY(p.getY() - 1);
                break;
            }
        }
        return p.getY() + level.getFluidState(p).getHeight(level, p);
    }
}

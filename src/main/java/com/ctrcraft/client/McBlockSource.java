package com.ctrcraft.client;

import java.util.List;

import com.ctrcraft.ModTags;
import com.ctrcraft.compat.CopycatCompat;
import com.ctrcraft.terrain.BlockSource;
import com.ctrcraft.terrain.Surface;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The terrain model's view of Minecraft: block collision boxes, and block tags for what a surface is. */
public final class McBlockSource implements BlockSource {
    /** The Floater for the kart driving here (set by KartDriver each step): 0 none, 1 water is a floor for it, 2 lava is too. */
    public static volatile int floaterMode;

    private final Level level;
    private final Entity context;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public McBlockSource(Level level, Entity context) {
        this.level = level;
        this.context = context;
    }

    @Override
    public void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor visitor) {
        // inset a hair so only the blocks inside the region count, not those touching it
        AABB region = new AABB(x0 + 1e-4, y0, z0 + 1e-4, x1 - 1e-4, y1, z1 - 1e-4);
        for (VoxelShape shape : level.getBlockCollisions(context, region)) {
            shape.forAllBoxes(visitor::box);
        }
        if (floaterMode > 0) {
            // the water (and lava, for the Obsidian Floater) is a floor for a floating kart: each water cell is a box up to the water's surface (a full cell under more water)
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1 - 1e-4); bx++) {
                for (int by = (int) Math.floor(y0); by <= (int) Math.floor(y1 - 1e-4); by++) {
                    for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1 - 1e-4); bz++) {
                        at.set(bx, by, bz);
                        var fluid = level.getFluidState(at);
                        if (!floatsOn(fluid)) continue;
                        double h = floatsOn(level.getFluidState(at.set(bx, by + 1, bz))) ? 1.0 : fluid.getHeight(level, at.set(bx, by, bz));
                        visitor.box(bx, by, bz, bx + 1, by + h, bz + 1);
                    }
                }
            }
        }
    }

    /** A fluid the kart floats on with the Floater it has: water, and lava for the Obsidian one. */
    private static boolean floatsOn(net.minecraft.world.level.material.FluidState fluid) {
        return fluid.is(net.minecraft.tags.FluidTags.WATER) || (floaterMode == 2 && fluid.is(net.minecraft.tags.FluidTags.LAVA));
    }

    @Override
    public int surface(int bx, int by, int bz) {
        pos.set(bx, by, bz);
        if (floaterMode > 0 && floatsOn(level.getFluidState(pos))) return Surface.ASPHALT;          // the Floater has no water slowdown
        BlockState state = level.getBlockState(pos);
        int code;
        // a copycat (Create, Copycats+) is the block it copies
        List<BlockState> copied = state.hasBlockEntity() ? CopycatCompat.materials(level, pos) : List.of();
        if (copied.isEmpty()) {
            code = classify(state);
        } else {
            code = Surface.ASPHALT;
            for (BlockState material : copied) {
                int c = classify(material);
                code |= c & Surface.PADS;
                if ((code & 63) == Surface.ASPHALT) {
                    code = (code & Surface.PADS) | (c & 63);
                }
            }
        }
        // a boost plate lying on this block (it has no collision, so it is not the ground itself) boosts too
        if ((code & Surface.PADS) == 0) {
            code |= padOf(level.getBlockState(pos.set(bx, by + 1, bz)));
        }
        return code;
    }

    /** One block's surface code from the {@code ctrcraft:surface/*} and {@code ctrcraft:turbo_pad} tags. */
    private static int classify(BlockState state) {
        int code;
        if (state.is(ModTags.SURFACE_ICE)) code = Surface.ICE;
        else if (state.is(ModTags.SURFACE_MUD)) code = Surface.MUD;
        else if (state.is(ModTags.SURFACE_SNOW)) code = Surface.SNOW;
        else if (state.is(ModTags.SURFACE_ICY_ROAD)) code = Surface.ICY_ROAD;
        else if (state.is(ModTags.SURFACE_SLOW_DIRT)) code = Surface.SLOW_DIRT;
        else if (state.is(ModTags.SURFACE_DIRT)) code = Surface.DIRT;
        else if (state.is(ModTags.SURFACE_WATER)) code = Surface.WATER;
        else if (state.is(ModTags.SURFACE_SIDESLIP)) code = Surface.SIDESLIP;
        else code = Surface.ASPHALT;
        return code | padOf(state);
    }

    /** The pad bit of a block: a turbo pad, a super turbo pad, or none. */
    private static int padOf(BlockState state) {
        return state.is(ModTags.SUPER_TURBO_PAD) ? Surface.SUPER_PAD : state.is(ModTags.TURBO_PAD) ? Surface.PAD : 0;
    }
}

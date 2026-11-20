package com.craftteamracing.client;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Render types that ignore the depth buffer: what a builder needs to see through the pickups standing on the markers (spot blocks, node lines), within a few blocks. */
public final class OnTopTypes extends RenderType {
    private OnTopTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean affectsCrumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sort, setup, clear);
    }

    /** Lines drawn over everything. */
    public static final RenderType LINES = create("craftteamracing_lines_on_top", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Block models (the block atlas, cut out) drawn over everything. */
    public static final RenderType BLOCKS = create("craftteamracing_blocks_on_top", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432, true, false,
            CompositeState.builder()
                    .setLightmapState(LIGHTMAP)
                    .setShaderState(RENDERTYPE_CUTOUT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true));

    /** How near (blocks) a marker has to be to be drawn on top. */
    public static final double RADIUS = 7.0;
}

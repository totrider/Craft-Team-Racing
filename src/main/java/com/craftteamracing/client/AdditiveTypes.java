package com.craftteamracing.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Render types that ADD to the screen (the console's additive blend: the colour times its alpha is added to what is behind), for the boost flame. */
public final class AdditiveTypes extends RenderType {
    private AdditiveTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean affectsCrumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sort, setup, clear);
    }

    private static final RenderStateShard.TransparencyStateShard ADD = new RenderStateShard.TransparencyStateShard("craftteamracing_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** The block atlas, lit by nothing, added to the screen, both faces, not written to the depth buffer (so flames do not hide each other or what is behind them). */
    public static final RenderType BLOCK_ATLAS = create("craftteamracing_additive_atlas", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(ADD)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    private static final java.util.Map<net.minecraft.resources.ResourceLocation, RenderType> ADDITIVE_OF = new java.util.HashMap<>();

    /** Added to the screen, both faces, from a picture of its own (not the block atlas): the same state as {@link #BLOCK_ATLAS}. */
    public static RenderType additive(net.minecraft.resources.ResourceLocation texture) {
        return ADDITIVE_OF.computeIfAbsent(texture, t -> create("craftteamracing_additive_" + t, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(ADD)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));
    }

    private static final RenderStateShard.TransparencyStateShard SUBTRACT = new RenderStateShard.TransparencyStateShard("craftteamracing_subtractive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_REVERSE_SUBTRACT);
    }, () -> {
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** Lines (the rain's streaks): gradient lines added to the screen, and taken from it. */
    public static final RenderType LINES_ADD = create("craftteamracing_lines_add", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.empty()))
                    .setTransparencyState(ADD)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType LINES_SUBTRACT = create("craftteamracing_lines_subtract", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.empty()))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** The same, but the picture is TAKEN from the screen (the console's subtractive blend: the flame's outer shell, which is what leaves the red and orange). */
    public static final RenderType BLOCK_ATLAS_SUBTRACT = create("craftteamracing_subtractive_atlas", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /** The particle atlas, taken from the screen (the console's subtractive blend): the dark exhaust smoke. */
    public static final RenderType PARTICLE_ATLAS_SUBTRACT = create("craftteamracing_subtractive_particles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_PARTICLES, false, false))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /** The two blends again with no depth test: drawn over whatever is there, whether it is in front of or behind it (the shield bubble is drawn over the kart it is round). */
    public static final RenderType BLOCK_ATLAS_NODEPTH = create("craftteamracing_additive_atlas_nodepth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(ADD)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));
    public static final RenderType BLOCK_ATLAS_SUBTRACT_NODEPTH = create("craftteamracing_subtractive_atlas_nodepth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /** The two blends with back faces culled: only the outside of a closed model is drawn (the shield's cone), or, mirrored, only its inside. */
    public static final RenderType BLOCK_ATLAS_CULL = create("craftteamracing_additive_atlas_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(ADD)
                    .setCullState(CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));
    public static final RenderType BLOCK_ATLAS_SUBTRACT_CULL = create("craftteamracing_subtractive_atlas_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    /** Additive, no depth test, back faces culled: the sheen's far side is not added on top of its near side. */
    public static final RenderType BLOCK_ATLAS_NODEPTH_CULL = create("craftteamracing_additive_atlas_nodepth_cull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(ADD)
                    .setCullState(CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));
}

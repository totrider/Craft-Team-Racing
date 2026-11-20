package com.craftteamracing.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The super turbo's blue flame. A tint multiplies the flame's colours, and an orange flame multiplied by blue is a dark green (orange has almost no blue in it to keep). So the blue flame is
 * the same flame, picture and models, with the red and the blue of its picture swapped (and the green a little down): the orange of the fire becomes a bright blue, its yellow a cyan white,
 * its white stays white. The swapped picture is made at run time from the picture the flame's own quads use, whoever's it is (the jar's or a pack's), and the quads are drawn with their
 * coordinates turned from the block atlas's to that picture's own.
 */
public final class BlueFlame {
    private BlueFlame() {}

    private static final Map<ResourceLocation, ResourceLocation> MADE = new HashMap<>();
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final RandomSource RANDOM = RandomSource.create();

    /** The blue copy of a sprite's picture (a texture of the block atlas: its name is {@code namespace:path}, the file {@code textures/path.png}), or null if it cannot be made. */
    private static ResourceLocation swapped(ResourceLocation sprite) {
        if (MADE.containsKey(sprite)) return MADE.get(sprite);
        ResourceLocation id = null;
        var res = Minecraft.getInstance().getResourceManager().getResource(sprite.withPath("textures/" + sprite.getPath() + ".png"));
        if (res.isPresent()) {
            try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
                NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
                for (int y = 0; y < src.getHeight(); y++) {
                    for (int x = 0; x < src.getWidth(); x++) {
                        int c = src.getPixelRGBA(x, y);                            // ABGR
                        int a = (c >>> 24) & 255;
                        int r = c & 255;
                        int g = (c >> 8) & 255;
                        int b = (c >> 16) & 255;
                        int nr = b;
                        int ng = (int) (g * 0.82F);
                        int nb = r;
                        out.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
                    }
                }
                id = ResourceLocation.fromNamespaceAndPath("craftteamracing", "dynamic/blue_flame/" + sprite.getNamespace() + "/" + sprite.getPath().replace('.', '_'));
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
            } catch (java.io.IOException | RuntimeException e) {
                id = null;
            }
        }
        MADE.put(sprite, id);
        return id;
    }

    /** Draws the model's quads added to the screen in blue; false if the model has no picture to swap (the caller then draws it as before). */
    public static boolean draw(BakedModel model, PoseStack pose, MultiBufferSource buffers, float r, float g, float b, float a) {
        boolean any = false;
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : model.getQuads(null, side, RANDOM)) {
                var sprite = quad.getSprite();
                ResourceLocation blue = swapped(sprite.contents().name());
                if (blue == null) continue;
                VertexConsumer vc = buffers.getBuffer(AdditiveTypes.additive(blue));
                int[] v = quad.getVertices();
                int stride = v.length / 4;
                float u0 = sprite.getU0();
                float u1 = sprite.getU1();
                float v0 = sprite.getV0();
                float v1 = sprite.getV1();
                for (int i = 0; i < 4; i++) {
                    float x = Float.intBitsToFloat(v[i * stride]);
                    float y = Float.intBitsToFloat(v[i * stride + 1]);
                    float z = Float.intBitsToFloat(v[i * stride + 2]);
                    float u = (Float.intBitsToFloat(v[i * stride + 4]) - u0) / (u1 - u0);
                    float w = (Float.intBitsToFloat(v[i * stride + 5]) - v0) / (v1 - v0);
                    vc.addVertex(pose.last().pose(), x, y, z).setColor(r, g, b, a).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
                }
                any = true;
            }
        }
        return any;
    }

    /** Forget the swapped pictures (a resource pack change can replace the flame). */
    public static void clear() {
        for (ResourceLocation id : MADE.values()) {
            if (id != null) Minecraft.getInstance().getTextureManager().release(id);
        }
        MADE.clear();
    }
}

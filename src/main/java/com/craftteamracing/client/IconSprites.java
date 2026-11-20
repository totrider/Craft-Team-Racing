package com.craftteamracing.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Minecraft's own sprites (the totem of undying, the clock) as HUD pictures, outlined at run time: the mod does not copy them, so the black outline that every power-up sprite gets
 * (tools/sprite_outline.py) is added here, to the sprite read from the game, and the result is the same 96 x 64 texture that the mod's own sprites are baked into: the outlined sprite,
 * 4 x 3 texels a sprite pixel (a reference pixel is 0.7361 as wide as it is tall, so a 3 : 2 picture shows 1.104 : 1 on the screen and the sprite shows square), centred.
 * A source of another resolution (a resource pack's) works as a multiple of 16: the outline is as many texels thick as the sprite's pixels are wide.
 */
public final class IconSprites {
    private IconSprites() {}

    private static final int W = 96;
    private static final int H = 64;
    private static final Map<ResourceLocation, ResourceLocation> BUILT = new HashMap<>();

    /** The outlined HUD picture of a sprite texture (e.g. {@code minecraft:textures/item/clock_00.png}), or the sprite itself if it cannot be read. */
    public static ResourceLocation outlined(ResourceLocation source) {
        ResourceLocation done = BUILT.get(source);
        if (done != null) return done;
        ResourceLocation result = build(source);
        BUILT.put(source, result);
        return result;
    }

    private static ResourceLocation build(ResourceLocation source) {
        Minecraft mc = Minecraft.getInstance();
        var res = mc.getResourceManager().getResource(source);
        if (res.isEmpty()) return source;
        try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
            ResourceLocation made = finish(src, ResourceLocation.fromNamespaceAndPath("craftteamracing", "dynamic/icon/" + source.getNamespace() + "/" + source.getPath().replace('.', '_')));
            return made == null ? source : made;
        } catch (java.io.IOException | RuntimeException e) {
            return source;
        }
    }

    private static final ResourceLocation POTION = ResourceLocation.withDefaultNamespace("textures/item/potion.png");
    private static final ResourceLocation POTION_LIQUID = ResourceLocation.withDefaultNamespace("textures/item/potion_overlay.png");

    /**
     * Minecraft's own potion bottle as the Beaker's HUD picture, green or red (the red one is N. Brio's charged beaker): the bottle's glass over its liquid layer tinted the beaker's colour,
     * outlined here. The 3D flask in the world is the mod's own model.
     */
    public static ResourceLocation potion(boolean red) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("craftteamracing", "dynamic/icon/potion_" + (red ? "red" : "green"));
        ResourceLocation done = BUILT.get(id);
        if (done != null) return done;
        ResourceLocation result = GuiTextures.unknownPower();
        Minecraft mc = Minecraft.getInstance();
        var bottle = mc.getResourceManager().getResource(POTION);
        var liquid = mc.getResourceManager().getResource(POTION_LIQUID);
        if (bottle.isPresent() && liquid.isPresent()) {
            try (InputStream a = bottle.get().open(); InputStream b = liquid.get().open(); NativeImage glass = NativeImage.read(a); NativeImage fluid = NativeImage.read(b)) {
                if (glass.getWidth() != fluid.getWidth() || glass.getHeight() != fluid.getHeight()) throw new IllegalStateException();
                int tr = red ? 222 : 62;
                int tg = red ? 52 : 200;
                int tb = red ? 40 : 60;
                try (NativeImage mix = new NativeImage(glass.getWidth(), glass.getHeight(), true)) {
                    for (int y = 0; y < mix.getHeight(); y++) {
                        for (int x = 0; x < mix.getWidth(); x++) {
                            int l = fluid.getPixelRGBA(x, y);                                                // ABGR
                            int la = (l >>> 24) & 255;
                            int lr = ((l & 255) * tr) / 255;
                            int lg = (((l >> 8) & 255) * tg) / 255;
                            int lb = (((l >> 16) & 255) * tb) / 255;
                            int g = glass.getPixelRGBA(x, y);
                            int ga = (g >>> 24) & 255;
                            int oa = ga + la * (255 - ga) / 255;                                           // the glass over the liquid
                            int or = oa == 0 ? 0 : ((g & 255) * ga + lr * la * (255 - ga) / 255) / oa;
                            int og = oa == 0 ? 0 : (((g >> 8) & 255) * ga + lg * la * (255 - ga) / 255) / oa;
                            int ob = oa == 0 ? 0 : (((g >> 16) & 255) * ga + lb * la * (255 - ga) / 255) / oa;
                            mix.setPixelRGBA(x, y, (oa << 24) | (Math.min(255, ob) << 16) | (Math.min(255, og) << 8) | Math.min(255, or));
                        }
                    }
                    ResourceLocation made = finish(mix, id);
                    if (made != null) result = made;
                }
            } catch (java.io.IOException | RuntimeException e) {
                result = GuiTextures.unknownPower();
            }
        }
        BUILT.put(id, result);
        return result;
    }

    /** The outlined 96 x 64 HUD picture of a 16-pixel-multiple sprite, registered as a texture under {@code id}; null if the sprite is not square or a multiple of 16. */
    private static ResourceLocation finish(NativeImage src, ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        int w = src.getWidth();
        int h = src.getHeight();
        if (w != h || w % 16 != 0) return null;
        int f = w / 16;                                    // texels of the source per sprite pixel
        int n = 16 + 2;                                    // the outlined sprite's size in sprite pixels
        NativeImage out = new NativeImage(W, H, true);
        int ox = (W - n * 4) / 2;
        int oy = (H - n * 3) / 2;
        for (int cy = 0; cy < H; cy++) {
            for (int cx = 0; cx < W; cx++) {
                out.setPixelRGBA(cx, cy, 0);
                int px = Math.floorDiv(cx - ox, 4);       // the outlined sprite's pixel
                int py = Math.floorDiv(cy - oy, 3);
                if (px < 0 || py < 0 || px >= n || py >= n) continue;
                int sx = px - 1;
                int sy = py - 1;
                if (solid(src, sx, sy, f, w)) {
                    out.setPixelRGBA(cx, cy, src.getPixelRGBA(Math.min(w - 1, sx * f + f / 2), Math.min(w - 1, sy * f + f / 2)));
                    continue;
                }
                boolean next = false;
                for (int dy = -1; dy <= 1 && !next; dy++) {
                    for (int dx = -1; dx <= 1 && !next; dx++) next = solid(src, sx + dx, sy + dy, f, w);
                }
                if (next) out.setPixelRGBA(cx, cy, 0xFF000000);            // ABGR: opaque black
            }
        }
        mc.getTextureManager().register(id, new DynamicTexture(out));
        GuiTextures.setSize(id, W, H);
        return id;
    }

    private static boolean solid(NativeImage img, int sx, int sy, int f, int w) {
        if (sx < 0 || sy < 0 || sx >= 16 || sy >= 16) return false;
        return ((img.getPixelRGBA(Math.min(w - 1, sx * f + f / 2), Math.min(w - 1, sy * f + f / 2)) >>> 24) & 255) > 0;
    }

    /** Forget the built pictures (a resource pack change can replace the sprites). */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : BUILT.values()) {
            if (id.getNamespace().equals("craftteamracing") && id.getPath().startsWith("dynamic/")) mc.getTextureManager().release(id);
        }
        BUILT.clear();
    }
}

package com.craftteamracing.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Drawing HUD pictures of any size: a resource pack may replace the mod's icons with ones of another size or shape (the original game's are
 * 32 x 48), so a picture is fitted into its box by its own proportions, whatever they are. Sizes are read from the PNG header, once.
 */
public final class GuiTextures {
    private GuiTextures() {}

    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    private static final int[] NONE = new int[0];

    /** The picture's size (width, height), or null when the pack has no such texture. */
    public static int[] size(ResourceLocation tex) {
        int[] s = SIZES.computeIfAbsent(tex, GuiTextures::read);
        return s.length == 0 ? null : s;
    }

    private static int[] read(ResourceLocation tex) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return NONE;
        try (InputStream in = res.get().open()) {
            byte[] head = in.readNBytes(24);
            if (head.length < 24) return NONE;
            int w = ((head[16] & 255) << 24) | ((head[17] & 255) << 16) | ((head[18] & 255) << 8) | (head[19] & 255);
            int h = ((head[20] & 255) << 24) | ((head[21] & 255) << 16) | ((head[22] & 255) << 8) | (head[23] & 255);
            return w > 0 && h > 0 ? new int[] { w, h } : NONE;
        } catch (java.io.IOException e) {
            return NONE;
        }
    }

    /** Forget what was read (a resource pack change can replace the pictures). */
    public static void clear() {
        SIZES.clear();
        IconSprites.clear();
        BlueFlame.clear();
    }

    /** Records the size of a picture that is not a resource (a texture the mod builds at run time, {@link IconSprites}). */
    static void setSize(ResourceLocation tex, int w, int h) {
        SIZES.put(tex, new int[] { w, h });
    }

    /** The picture of a power-up that has none of its own: a question mark (textures/gui/power/unknown.png; a pack may replace it). */
    private static final ResourceLocation UNKNOWN_POWER = ResourceLocation.fromNamespaceAndPath("craftteamracing", "textures/gui/power/unknown.png");

    /** The mask preference of the kart this client last drove (the icons everywhere follow it). */
    private static boolean darkMask;

    /** The HUD icon of a power-up id, or the question mark when there is none. */
    private static final ResourceLocation GOOD_TOTEM = ResourceLocation.withDefaultNamespace("textures/item/totem_of_undying.png");
    private static final ResourceLocation CLOCK = ResourceLocation.withDefaultNamespace("textures/item/clock_00.png");
    private static final ResourceLocation ROCKET = ResourceLocation.withDefaultNamespace("textures/item/firework_rocket.png");

    static ResourceLocation unknownPower() {
        return UNKNOWN_POWER;
    }

    public static ResourceLocation powerIcon(String id) {
        return powerIcon(id, false);
    }

    /**
     * The HUD icon of a power-up. {@code charged} (ten power fruit) asks for the charged picture where there is one: a pack's {@code <id>_charged.png}, else the mod's (the red beaker, the
     * blue slime, the creeper crate). The stacks (three bombs, three missiles) have no pictures of their own: they show the single one, the count being drawn over it.
     */
    public static ResourceLocation powerIcon(String id, boolean charged) {
        if (id.endsWith("_3x")) id = id.substring(0, id.length() - 3);
        if (charged) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                ResourceLocation tex = rl.withPath("textures/gui/power/" + rl.getPath() + "_charged.png");
                if (size(tex) != null) return tex;
            }
            if (id.equals(com.craftteamracing.items.HeldItem.POTION.id())) return IconSprites.potion(true);
        }
        // the mask is the light one (Aku Aku) or the dark one (Uka Uka) as the player's own kart is set to
        boolean mask = id.equals(com.craftteamracing.items.HeldItem.MASK.id());
        if (mask) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.getVehicle() instanceof com.craftteamracing.kart.KartEntity kart) darkMask = kart.darkMask();
            if (darkMask) id = id + "_dark";
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            ResourceLocation tex = rl.withPath("textures/gui/power/" + rl.getPath() + ".png");
            if (size(tex) != null) return tex;           // a resource pack's own picture of it
        }
        // without one: the good totem is Minecraft's totem of undying and the clock its golden clock (the game's own sprites, outlined here, not copied); the evil totem is the mod's own
        if (mask && !darkMask) return IconSprites.outlined(GOOD_TOTEM);
        if (id.equals(com.craftteamracing.items.HeldItem.CLOCK.id())) return IconSprites.outlined(CLOCK);
        if (id.equals(com.craftteamracing.items.HeldItem.POTION.id())) return IconSprites.potion(false);
        if (id.equals(com.craftteamracing.items.HeldItem.MISSILE.id())) return IconSprites.outlined(ROCKET);          // the Rocket (the original's missile): Minecraft's firework rocket
        return UNKNOWN_POWER;
    }

    private static final ResourceLocation POWER_FRUIT_SPRITE = ResourceLocation.fromNamespaceAndPath("craftteamracing", "item/power_fruit_sprite");

    /**
     * Draws the Power Fruit centred on (cx, cy) in a square of {@code size} pixels: the animated spinning fruit of the pickup (the {@code item/power_fruit_sprite} of a resource
     * pack, a strip of frames the block atlas plays) when there is one, otherwise the static HUD picture {@code fallback}. The sprite's frame is much larger than the
     * fruit in it, so it is drawn twice the box to make the fruit as large as the static picture.
     */
    public static void drawPowerFruit(GuiGraphics g, ResourceLocation fallback, int cx, int cy, int size) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(POWER_FRUIT_SPRITE);
        if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
            drawIcon(g, fallback, cx - size / 2, cy - size / 2, size, size);
            return;
        }
        int s = size * 2;
        g.blit(cx - s / 2, cy - s / 2, 0, s, s, sprite);
    }

    /** Draws the picture over exactly the given rectangle (stretched to it): for pictures whose proportions are the original's reference pixels, not the window's. */
    public static boolean drawStretched(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        if (size(tex) == null) return false;
        g.blit(tex, x, y, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
        return true;
    }

    /** The shape on the screen of the original's 3 : 2 icons (48 x 32 reference pixels, a reference pixel 0.7361 as wide as tall); the mod's own 96 x 64 icons are made for it. */
    public static final float ORIGINAL_ICON_ASPECT = 1.5F * 0.7361F;

    /** Draws a power-up icon as large as fits in the box, centred, as it is shown in the game: a 3 : 2 icon (the original's shape, the mod's own 96 x 64) at the shape that gives, any other by its own proportions. */
    public static boolean drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int boxW, int boxH) {
        int[] s = size(tex);
        if (s == null) return false;
        float aspect = Math.abs(s[0] / (float) s[1] - 1.5F) < 0.05F ? ORIGINAL_ICON_ASPECT : s[0] / (float) s[1];
        int w = Math.max(1, Math.round(Math.min(boxW, boxH * aspect)));
        int h = Math.max(1, Math.round(w / aspect));
        g.blit(tex, x + (boxW - w) / 2, y + (boxH - h) / 2, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
        return true;
    }

    /** Draws the picture as large as fits in the box at (x, y) of boxW x boxH, centred, keeping its proportions. Returns false if there is none. */
    public static boolean drawFit(GuiGraphics g, ResourceLocation tex, int x, int y, int boxW, int boxH) {
        int[] s = size(tex);
        if (s == null) return false;
        float k = Math.min(boxW / (float) s[0], boxH / (float) s[1]);
        int w = Math.max(1, Math.round(s[0] * k));
        int h = Math.max(1, Math.round(s[1] * k));
        g.blit(tex, x + (boxW - w) / 2, y + (boxH - h) / 2, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
        return true;
    }
}

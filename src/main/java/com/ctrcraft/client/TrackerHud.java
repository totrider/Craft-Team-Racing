package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The warning a driver gets when a missile homes in on them (UI_RaceHud, the tracker): four corner brackets round the kart on the screen that flash red and white with a beep
 * ({@code OtherFX 0x56}) at a rate that follows the missile's distance (every 5 frames under 100 of the distance unit, 10 under 200, 30 beyond), and two orange wings either side
 * of the kart that open out with the distance. When the lock is made the brackets animate in (8 frames, {@code trackerAnim1}), when the missile is gone they animate out (11 frames,
 * {@code trackerAnim2}). Sizes are in the original's picture pixels (it is a 512 x 240 screen here as in the rest of the HUD: see ItemHud) and the animation tables are the game's.
 * The server sends the distance every tick while a missile is chasing us ({@link com.ctrcraft.pickup.TrackerPayload}).
 */
public final class TrackerHud {
    private TrackerHud() {}

    /** data.trackerAnim1: (x, y) per frame of the lock-on animation, 0x1000 = 1. */
    private static final int[] ANIM_LOCK = { 0x1000, 0x1000, 0xe66, 0x10cc, 0xccc, 0x1199, 0x10d3, 0xe66, 0x14d9, 0xb33, 0x13b1, 0x12b5, 0x1000, 0x1a38, 0x8bb, 0x1063 };
    /** data.trackerAnim2: the animation after the missile is gone. */
    private static final int[] ANIM_END = { 0xd09, 0x199, 0x1a12, 0x199, 0x2000, 0x199, 0x2000, 0x199, 0x2000, 0x199, 0x2000, 0x555, 0x2000, 0xc44, 0x2000, 0x1000, 0x2000, 0x1000, 0x1692, 0x13f8, 0xccc,
            0x1666 };
    private static final int LOCK_FRAMES = 8;
    private static final int END_FRAMES = 12;

    private static long lastUpdateMs;
    private static int distance;
    private static boolean tracking;
    private static boolean targeted;
    private static int timer;
    private static long frameDone;

    /** A missile is homing in on us at this distance. */
    public static void update(int dist) {
        lastUpdateMs = System.currentTimeMillis();
        distance = dist;
    }

    private static void step(long now) {
        tracking = now - lastUpdateMs < 120L;
        if (timer == 0 && !tracking) {
            targeted = false;
            return;
        }
        if (tracking) {
            if (!targeted && timer == 0) {
                targeted = true;
                timer = LOCK_FRAMES;
            }
        } else if (timer == 0 && targeted) {
            targeted = false;
            timer = END_FRAMES;
        }
        if (timer != 0) timer--;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) return;
        long now = System.currentTimeMillis();
        long frame = now * 30L / 1000L;
        if (frame != frameDone) {
            frameDone = frame;
            step(now);
        }
        if (timer == 0 && !tracking) return;
        int[] anim = tracking ? ANIM_LOCK : ANIM_END;
        if ((timer * 2 + 1) >= anim.length) return;                    // the original reads past its table on the first frame of the end animation: nothing is drawn
        int ax = anim[timer * 2];
        int ay = anim[timer * 2 + 1];

        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 at = mc.player.getVehicle().getPosition(partial).add(0.0, 0.5, 0.0);
        float[] p = project(mc, at, g.guiWidth(), g.guiHeight());
        if (p == null) return;
        float sy = g.guiHeight() / 216.0F;
        float sx = KartHud.PIXEL_ASPECT * sy;

        // the beep: every 5, 10 or 30 frames by the distance, and the brackets are white on that frame, red on the others
        int beepRate = distance > 200 ? 30 : distance > 100 ? 10 : 5;
        boolean white = false;
        if (tracking && frame % beepRate == 0) {
            white = true;
            if (frame != lastBeep) {
                lastBeep = frame;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TRACKER_BEEP.get(), 1.0F));
            }
        }

        // the wings: a border triangle and a fill triangle on each side, orange to yellow with white at the inner tip (drawn with culling off: the left one is wound the other way)
        float halfH = ay * 7 / 4096.0F;
        float outer = distance + (ax >> 8);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        for (int side = -1; side <= 1; side += 2) {
            float x0 = p[0] + side * outer * sx;
            float x1 = p[0] + side * distance * sx;
            float yTop = p[1] - halfH * sy;
            float yMid = p[1];
            float yLow = p[1] + halfH * sy;
            tri(bb, m, x0, yTop, 0xFF9C6900, x1, yMid, 0xFFFFFFFF, x0, yMid, 0xFFFFFF00);
            tri(bb, m, x0, yMid, 0xFF322B01, x1, yMid, 0xFF5B5B00, x0, yLow, 0xFFFFBB00);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // the lock-on reticle: an equal-sided square of corner brackets round the kart with a dim black square inside it and a cross in the middle, red, or white on the beep, every shape
        // outlined in black (UI_TrackerBG: the lock-on icon mirrored into the four corners, the black backing and the cross)
        // measured on the original's picture: the box is square, a corner bracket's arms are a fifth of its side long and 5.5 % of it thick, the cross reaches a fifth of the side either
        // way from the middle, and the black stroke is about 2 % of the side
        float half = 17.0F * sy * ((ax + ay) / 2.0F) / 4096.0F;
        float side = half * 2.0F;
        float arm = side * 0.2F;
        float th = Math.max(2.0F, side * 0.055F);
        float o = Math.max(1.0F, side * 0.025F);
        float reach = side * 0.2F;
        int colour = white ? 0xFFFFFFFF : 0xFFFF2020;
        g.fill(Math.round(p[0] - half), Math.round(p[1] - half), Math.round(p[0] + half), Math.round(p[1] + half), 0x80000000);
        java.util.List<float[]> parts = new java.util.ArrayList<>();
        for (int sxs = -1; sxs <= 1; sxs += 2) {
            for (int sys = -1; sys <= 1; sys += 2) {
                float cx = p[0] + sxs * half;
                float cy = p[1] + sys * half;
                parts.add(new float[] { cx - sxs * arm, cy, cx + sxs * th, cy + sys * th });                 // the arm along x, with the corner
                parts.add(new float[] { cx, cy - sys * arm, cx + sxs * th, cy + sys * th });                 // the arm along y
            }
        }
        parts.add(new float[] { p[0] - reach, p[1] - th / 2.0F, p[0] + reach, p[1] + th / 2.0F });
        parts.add(new float[] { p[0] - th / 2.0F, p[1] - reach, p[0] + th / 2.0F, p[1] + reach });
        // the stroke goes round the whole of the shape: all the black first, then all the colour on top, so where two pieces meet (the cross, the corners) there is one outline
        for (float[] r : parts) fillRect(g, r, o, 0xFF000000);
        for (float[] r : parts) fillRect(g, r, 0.0F, colour);
    }

    private static void fillRect(GuiGraphics g, float[] r, float grow, int colour) {
        g.fill(Math.round(Math.min(r[0], r[2]) - grow), Math.round(Math.min(r[1], r[3]) - grow), Math.round(Math.max(r[0], r[2]) + grow), Math.round(Math.max(r[1], r[3]) + grow), colour);
    }

    /** A filled rectangle with a black stroke of {@code o} pixels round it. */
    private static void outlined(GuiGraphics g, float xa, float ya, float xb, float yb, float o, int colour) {
        float x0 = Math.min(xa, xb);
        float x1 = Math.max(xa, xb);
        float y0 = Math.min(ya, yb);
        float y1 = Math.max(ya, yb);
        g.fill(Math.round(x0 - o), Math.round(y0 - o), Math.round(x1 + o), Math.round(y1 + o), 0xFF000000);
        g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), colour);
    }

    private static long lastBeep = -1;

    private static void rect(GuiGraphics g, float xa, float ya, float xb, float yb, int colour) {
        g.fill(Math.round(Math.min(xa, xb)), Math.round(Math.min(ya, yb)), Math.round(Math.max(xa, xb)), Math.round(Math.max(ya, yb)), colour);
    }

    private static void tri(BufferBuilder bb, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        vertex(bb, m, x0, y0, c0);
        vertex(bb, m, x1, y1, c1);
        vertex(bb, m, x2, y2, c2);
    }

    private static void vertex(BufferBuilder bb, Matrix4f m, float x, float y, int argb) {
        bb.addVertex(m, x, y, 0.0F).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, 200);
    }

    /** The screen position (gui pixels) of a world point, or null when it is behind the camera. */
    private static float[] project(Minecraft mc, Vec3 world, int w, int h) {
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 d = world.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double z = d.x * look.x + d.y * look.y + d.z * look.z;
        if (z < 0.05) return null;
        double x = -(d.x * left.x + d.y * left.y + d.z * left.z);          // to the right
        double y = d.x * up.x + d.y * up.y + d.z * up.z;
        double t = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = w / (double) h;
        return new float[] { (float) (w / 2.0 + x / (z * t * aspect) * w / 2.0), (float) (h / 2.0 - y / (z * t) * h / 2.0) };
    }
}

package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.pickup.WarpOrbEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Two whole-screen effects, drawn over the world (not the HUD) with vanilla's own shaders, from copies of the frame:
 * <ul>
 * <li><b>The Clock's echo</b> (DISPLAY_Blur_Main, MainFrame.c): the original draws the screen back over the racer's viewport, enlarged a few pixels all round (the enlargement pulses
 * with |sin(frame x 100)|), half see-through, for as long as the clock holds the racer (7.7 s) and for a second for the racer who used it; and a flat white flash over every racer's
 * screen for 4 frames when it is used. Here it is drawn as a zoom blur (the frame at several growing zooms, averaged): at this frame rate a single enlarged copy reads as a ghost.</li>
 * <li><b>The orb's lens</b>: a stand-in, NOT in the original's code: round the Warp Orb the picture is drawn pulled in towards the orb, like light bending round something heavy, darker
 * near it. A ring of triangles whose corners keep their place but sample the frame from further out.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class ScreenWarp {
    private ScreenWarp() {}

    private static TextureTarget snapshot;       // the frame as it is, for the lens
    private static final int BLUR_TAPS = 7;
    private static int echoTicks;
    private static int echoTotal;
    private static int flashTicks;

    /** The Clock holds the racer: the echo runs for {@code ticks}. */
    public static void echo(int ticks) {
        if (!com.craftteamracing.CtrClientConfig.MOTION_BLUR.get()) return;
        echoTicks = Math.max(echoTicks, ticks);
        echoTotal = Math.max(echoTotal, ticks);
    }

    /** The white flash of a Clock being used: 4 game frames, 3 ticks. */
    public static void flash() {
        flashTicks = 3;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (echoTicks > 0 && --echoTicks == 0) echoTotal = 0;
        if (flashTicks > 0) flashTicks--;
    }

    private static boolean broken;

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (broken || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        try {
            drawEffects(event);
        } catch (RuntimeException e) {
            broken = true;                          // a screen effect must never take the game down: it is off for the rest of the session
            CraftTeamRacingMod.LOGGER.warn("The screen effects (clock echo, orb lens) failed and are switched off: {}", e.toString());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private static void drawEffects(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        java.util.List<float[]> lenses = lenses(mc, event, partial);
        boolean echo = echoTicks > 0;
        if (!echo && lenses.isEmpty() && flashTicks == 0) return;

        if (echo || !lenses.isEmpty()) {
            snapshot = ensure(snapshot, main);
            copy(main, snapshot);
        }

        if (echo) {
            // the zoom blur: the frame drawn again and again a little larger (a running average over the taps, each at 1 / (k + 1) of what is there), so everything streaks outwards from the
            // middle instead of showing one ghost. The largest zoom pulses as the original's enlargement does (4 to 5 of 256 pixels a side).
            float time = (mc.level.getGameTime() + partial) * 100.0F / 4096.0F * (float) (Math.PI * 2.0);
            float reach = 0.040F + 0.012F * Math.abs(Mth.sin(time * 4.0F));
            float strength = Math.min(1.0F, Math.min(echoTicks, echoTotal - echoTicks + 1) / 8.0F);
            begin();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, snapshot.getColorTextureId());
            for (int k = 1; k <= BLUR_TAPS; k++) {
                float zoom = 1.0F + reach * strength * k / BLUR_TAPS;
                float lo = 0.5F - 0.5F / zoom;
                float hi = 0.5F + 0.5F / zoom;
                int a = Math.round(255.0F / (k + 1));
                BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bb.addVertex(0, 0, 0).setUv(lo, lo).setColor(255, 255, 255, a);
                bb.addVertex(1, 0, 0).setUv(hi, lo).setColor(255, 255, 255, a);
                bb.addVertex(1, 1, 0).setUv(hi, hi).setColor(255, 255, 255, a);
                bb.addVertex(0, 1, 0).setUv(lo, hi).setColor(255, 255, 255, a);
                BufferUploader.drawWithShader(bb.buildOrThrow());
            }
            end();
        }

        if (!lenses.isEmpty()) {
            begin();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, snapshot.getColorTextureId());
            float aspect = main.width / (float) main.height;
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (float[] l : lenses) lens(bb, l[0], l[1], l[2], aspect, l[3]);
            BufferUploader.drawWithShader(bb.buildOrThrow());
            end();
        }

        if (flashTicks > 0) {
            begin();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int a = Math.round(0.55F * flashTicks / 3.0F * 255.0F);
            bb.addVertex(0, 0, 0).setColor(255, 255, 255, a);
            bb.addVertex(1, 0, 0).setColor(255, 255, 255, a);
            bb.addVertex(1, 1, 0).setColor(255, 255, 255, a);
            bb.addVertex(0, 1, 0).setColor(255, 255, 255, a);
            BufferUploader.drawWithShader(bb.buildOrThrow());
            end();
        }

    }

    /** The lens ring of each orb in view: {u, v, radius (in screen widths), strength}. */
    private static java.util.List<float[]> lenses(Minecraft mc, RenderLevelStageEvent event, float partial) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        org.joml.Vector3f right = event.getCamera().getLeftVector().negate(new org.joml.Vector3f());
        for (WarpOrbEntity orb : mc.level.getEntitiesOfClass(WarpOrbEntity.class, mc.player.getBoundingBox().inflate(48.0))) {
            Vec3 c = orb.getPosition(partial).add(0.0, com.craftteamracing.client.WarpOrbRenderer.centreHeight(orb, partial), 0.0);
            Vec3 d = c.subtract(cam);
            if (d.lengthSqr() > 48.0 * 48.0 || occluded(mc, cam, c)) continue;
            Vector4f p = new Vector4f((float) d.x, (float) d.y, (float) d.z, 1.0F).mul(view).mul(proj);
            if (p.w <= 0.1F) continue;
            float u = p.x / p.w * 0.5F + 0.5F;
            float v = p.y / p.w * 0.5F + 0.5F;
            Vector4f e = new Vector4f((float) d.x + right.x * 3.2F, (float) d.y + right.y * 3.2F, (float) d.z + right.z * 3.2F, 1.0F).mul(view).mul(proj);
            if (e.w <= 0.1F) continue;
            float r = Math.abs(e.x / e.w * 0.5F + 0.5F - u);
            if (r < 0.01F || u < -r || u > 1 + r || v < -r * 2 || v > 1 + r * 2) continue;
            out.add(new float[] { u, v, Math.min(r, 0.45F), 1.0F - (float) Math.sqrt(d.lengthSqr()) / 52.0F });
        }
        return out;
    }

    private static boolean occluded(Minecraft mc, Vec3 from, Vec3 to) {
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getLocation().distanceTo(from) < to.distanceTo(from) - 1.0;
    }

    private static final int RINGS = 6;
    private static final int SPOKES = 28;

    /** A ring of triangles round (cu, cv): each corner stays in place but samples the frame from further out, so the picture is drawn pulled in; darker near the orb, clear at the rim and at the middle. */
    private static void lens(BufferBuilder bb, float cu, float cv, float radius, float aspect, float strength) {
        float[][] pos = new float[(RINGS + 1) * SPOKES][];
        for (int i = 0; i <= RINGS; i++) {
            float f = i / (float) RINGS;
            float pull = 1.0F + 1.1F * (1.0F - f) * (1.0F - f);
            float dark = 0.72F + 0.28F * Mth.clamp((f - 0.3F) / 0.6F, 0.0F, 1.0F);
            float alpha = smooth(0.18F, 0.45F, f) * smooth(1.0F, 0.72F, f) * strength;
            for (int j = 0; j < SPOKES; j++) {
                double a = j * Math.PI * 2.0 / SPOKES;
                float dx = (float) Math.cos(a);
                float dy = (float) Math.sin(a) * aspect;
                float rho = radius * f;
                pos[i * SPOKES + j] = new float[] { cu + dx * rho, cv + dy * rho, cu + dx * rho * pull, cv + dy * rho * pull, dark, alpha };
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SPOKES; j++) {
                int j2 = (j + 1) % SPOKES;
                for (int k : new int[] { i * SPOKES + j, i * SPOKES + j2, (i + 1) * SPOKES + j2, (i + 1) * SPOKES + j }) {
                    float[] v = pos[k];
                    int c = Math.round(v[4] * 255.0F);
                    bb.addVertex(v[0], v[1], 0.0F).setUv(v[2], v[3]).setColor(c, c, c, Math.round(Mth.clamp(v[5], 0.0F, 1.0F) * 255.0F));
                }
            }
        }
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static TextureTarget ensure(TextureTarget t, RenderTarget like) {
        if (t == null) return new TextureTarget(like.width, like.height, false, Minecraft.ON_OSX);
        if (t.width != like.width || t.height != like.height) t.resize(like.width, like.height, Minecraft.ON_OSX);
        return t;
    }

    private static void copy(RenderTarget from, TextureTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        from.bindWrite(false);
    }

    private static Matrix4f savedProjection;
    private static VertexSorting savedSorting;

    /** A screen-space drawing state: the unit square is the screen, no depth, ordinary alpha blending. */
    private static void begin() {
        savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, 1.0F, 0.0F, 1.0F, -1.0F, 1.0F), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    private static void end() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
    }
}

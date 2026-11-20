package com.craftteamracing.client;

import java.util.HashMap;
import org.joml.Vector3f;
import java.util.Map;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.kart.KartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The Power Shield's bubble: the model {@code item/pickup_shield} drawn see-through in green or blue, scaled by the game's tables (RB_ShieldDark_ThTick_Grow: eight frames of growth,
 * then a six frame pulse; RB_ShieldDark_ThTick_Pop: eleven frames of shrinking), each in 4096ths of the resting scale 1792, with the green one fading out in its last 60 frames. A game
 * frame is two thirds of a tick.
 */
public final class ShieldBubble {
    private ShieldBubble() {}

    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/pickup_shield"));
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final RandomSource RANDOM = RandomSource.create();

    /** R231.shieldGrowScale (x and z, y). */
    private static final int[][] GROW = { { 977, 1835 }, { 1792, 2936 }, { 2205, 2095 }, { 2335, 1254 }, { 1884, 1612 }, { 1433, 1971 }, { 1612, 1881 }, { 1792, 1792 } };
    /** R231.shieldPopScale. */
    private static final int[][] POP = { { 2150, 1612 }, { 2419, 1433 }, { 2508, 1344 }, { 2329, 1523 }, { 1792, 1792 }, { 1254, 2150 }, { 896, 2419 }, { 716, 2508 }, { 537, 2150 }, { 358, 1254 }, { 179, 537 } };
    /** R231.shieldPulseScale. */
    private static final int[][] PULSE = { { 1845, 1756 }, { 1899, 1720 }, { 1971, 1684 }, { 1899, 1720 }, { 1845, 1756 }, { 1792, 1792 } };
    private static final float REST = 1792.0F;
    /** The bubble round a kart is this wide at rest, blocks (the kart is about a block). */
    private static final float DIAMETER = 1.7F;
    private static final float MODEL_DIAMETER = 12.0F / 16.0F;

    /** The original's four layers (the local pack): the blue and green colour layers and the highlight are added to the screen, the dark layer is taken from it. Empty in the jar. */
    private static final ModelResourceLocation BLUE = layer("pickup_shield_blue");
    private static final ModelResourceLocation GREEN = layer("pickup_shield_green");
    private static final ModelResourceLocation DARK = layer("pickup_shield_dark");
    /** The sheen's eight visible frames of its sweep, each baked by the pack already turned and wrapped onto the bubble's surface (tools/extract_ctr_textures.py: an approximation of the footage). */
    private static final ModelResourceLocation[] SHEEN = new ModelResourceLocation[8];
    static {
        for (int i = 0; i < SHEEN.length; i++) SHEEN[i] = layer("pickup_shield_sheen_" + i);
    }
    public static final ModelResourceLocation[] LAYERS = java.util.stream.Stream.concat(java.util.stream.Stream.of(BLUE, GREEN, DARK), java.util.Arrays.stream(SHEEN)).toArray(ModelResourceLocation[]::new);
    private static final java.util.Map<BakedModel, Boolean> HAS_QUADS = new java.util.IdentityHashMap<>();

    private static ModelResourceLocation layer(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CraftTeamRacingMod.MODID, "item/" + name));
    }

    private static boolean has(BakedModel model) {
        return HAS_QUADS.computeIfAbsent(model, m -> {
            for (Direction side : SIDES) {
                RANDOM.setSeed(42L);
                if (!m.getQuads(null, side, RANDOM).isEmpty()) return true;
            }
            return false;
        });
    }

    /**
     * The original's bubble, placed by the tuning state (DebugCommands.shield*: the right pose is still being found against footage): the model's own origin on the kart's origin, turned,
     * scaled (by the game's opening tables too, in the turned axes) and moved as set, then turned with the kart ({@code yawDegrees}) or to face the camera. The layers' strength is
     * {@code intensity}, {@code highlight} the highlight's turn about the model's y in degrees (NaN: hidden); {@code forceDepthTest} draws with the depth test on whatever the depth setting says (the thrown shield). The pose is in world axes at the kart's place. False when no pack has the model.
     */
    public static boolean drawOriginal(PoseStack pose, MultiBufferSource buffers, net.minecraft.client.Camera camera, net.minecraft.world.phys.Vec3 worldPos, float yawDegrees, float sx, float sy, boolean blue, float intensity, float highlight, boolean forceDepthTest) {
        var models = Minecraft.getInstance().getModelManager();
        BakedModel body = models.getModel(blue ? BLUE : GREEN);
        if (!has(body)) return false;
        BakedModel dark = models.getModel(DARK);
        float[] rot = DebugCommands.shieldRot;
        float[] k = DebugCommands.shieldScale;
        float[] off = DebugCommands.shieldOffset;
        int faces = DebugCommands.shieldFaces;
        float tx = DebugCommands.shieldTable ? sx : 1.0F;
        float ty = DebugCommands.shieldTable ? sy : 1.0F;
        pose.pushPose();
        if (!forceDepthTest && DebugCommands.shieldDepth == 3) {
            // "shift": the depth test is on, but the whole bubble is first scaled about the camera so every point is nearer by about the kart's radius: it looks the same (a scale about the
            // camera does not move a point on the screen) but lies in front of the kart, so the kart does not hide it while blocks and entities nearer than that still do, pixel by pixel
            net.minecraft.world.phys.Vec3 c = camera.getPosition().subtract(worldPos);
            double d = c.length();
            if (d > DebugCommands.shieldShift + 0.5) {
                float f = (float) ((d - DebugCommands.shieldShift) / d);
                pose.translate(c.x, c.y, c.z);
                pose.scale(f, f, f);
                pose.translate(-c.x, -c.y, -c.z);
            }
        }
        // the offset is in the KART's frame (x across, y up, z along it), so it keeps the cone's middle on the kart's middle whatever way the camera looks; only then is the cone turned
        double theta = Math.toRadians(180.0F - yawDegrees);
        double ox = off[0] * Math.cos(theta) + off[2] * Math.sin(theta);
        double oz = -off[0] * Math.sin(theta) + off[2] * Math.cos(theta);
        if (DebugCommands.shieldCamera) {
            pose.translate(ox, off[1], oz);
            pose.mulPose(facing(camera, worldPos.add(ox, off[1], oz)));
        } else {
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yawDegrees));
            pose.translate(off[0], off[1], off[2]);
        }
        pose.scale(tx * k[0], ty * k[1], tx * k[2]);
        if (faces == 1) pose.scale(-1.0F, 1.0F, 1.0F);                          // mirrored: the culling then keeps the inside
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot[2]));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot[1]));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot[0]));
        pose.pushPose();
        pose.translate(-0.5, -0.5, -0.5);
        boolean noDepth = !forceDepthTest && DebugCommands.shieldDepth != 3 && (DebugCommands.shieldDepth == 2 || (DebugCommands.shieldDepth == 0 && !occluded(camera, worldPos.add(0.0, off[1], 0.0))));
        net.minecraft.client.renderer.RenderType subtract = noDepth ? AdditiveTypes.BLOCK_ATLAS_SUBTRACT_NODEPTH : faces == 2 ? AdditiveTypes.BLOCK_ATLAS_SUBTRACT : AdditiveTypes.BLOCK_ATLAS_SUBTRACT_CULL;
        net.minecraft.client.renderer.RenderType add = noDepth ? AdditiveTypes.BLOCK_ATLAS_NODEPTH : faces == 2 ? AdditiveTypes.BLOCK_ATLAS : AdditiveTypes.BLOCK_ATLAS_CULL;
        if (!forceDepthTest && com.craftteamracing.CtrClientConfig.SHIELD_DARK_LAYER.get()) draw(dark, pose, buffers.getBuffer(subtract), Math.min(intensity, 0.75F));       // the thrown shield is the colour model alone: no dark layer
        draw(body, pose, buffers.getBuffer(add), intensity);
        pose.popPose();
        if (!forceDepthTest && DebugCommands.sheenOn && (!Float.isNaN(highlight) || DebugCommands.sheenAngle >= 0.0F)) {      // and no sheen
            // the sheen: one of eight models, each the sweep's frame already turned and wrapped onto the bubble's surface, so it only ever shows on the bubble (the footage's look)
            float angle = DebugCommands.sheenAngle >= 0.0F ? DebugCommands.sheenAngle : highlight;
            int frame = Math.max(0, Math.min(SHEEN.length - 1, Math.round((angle - 270.0F) / 22.5F)));
            BakedModel shine = models.getModel(SHEEN[frame]);
            if (has(shine)) {
                boolean both = DebugCommands.shieldFaces == 2;
                net.minecraft.client.renderer.RenderType shineType = noDepth ? (both ? AdditiveTypes.BLOCK_ATLAS_NODEPTH : AdditiveTypes.BLOCK_ATLAS_NODEPTH_CULL) : (both ? AdditiveTypes.BLOCK_ATLAS : AdditiveTypes.BLOCK_ATLAS_CULL);
                pose.pushPose();
                pose.translate(-0.5, -0.5, -0.5);
                draw(shine, pose, buffers.getBuffer(shineType), intensity);
                pose.popPose();
            }
        }
        pose.popPose();
        return true;
    }

    /**
     * Whether a block stands between the camera and the bubble's middle (more than the bubble's own reach in front of it). The bubble is drawn over the ground it sinks into, as the
     * footage shows, but not through a wall: then the depth test is on.
     */
    private static boolean occluded(net.minecraft.client.Camera camera, net.minecraft.world.phys.Vec3 centre) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        net.minecraft.world.phys.Vec3 from = camera.getPosition();
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, centre, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getLocation().distanceTo(from) < centre.distanceTo(from) - 1.0;
    }

    /**
     * The game's turn for a model flagged "always point north": about the VERTICAL axis only, toward the camera (the angle from the object's place seen from the camera, in the horizontal
     * plane): the model's z points at the camera horizontally and its y stays up. Not a billboard about the line of sight.
     */
    private static org.joml.Quaternionf facing(net.minecraft.client.Camera camera, net.minecraft.world.phys.Vec3 worldPos) {
        net.minecraft.world.phys.Vec3 d = camera.getPosition().subtract(worldPos);
        if (DebugCommands.shieldSphere) {
            // a full billboard: z along the line to the camera, y as nearly the camera's up as it can be (a circle seen from any height)
            Vector3f back = new Vector3f((float) d.x, (float) d.y, (float) d.z);
            if (back.lengthSquared() > 1.0E-8F) {
                back.normalize();
                Vector3f r = new Vector3f(camera.getUpVector()).cross(back);
                if (r.lengthSquared() > 1.0E-8F) {
                    r.normalize();
                    Vector3f u = new Vector3f(back).cross(r);
                    return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(r, u, back));
                }
            }
        }
        return new org.joml.Quaternionf().rotationY((float) Math.atan2(d.x, d.z));
    }

    private static void draw(BakedModel model, PoseStack pose, VertexConsumer vc, float i) {
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : model.getQuads(null, side, RANDOM)) {
                vc.putBulkData(pose.last(), quad, i, i, i, 1.0F, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /** Per kart: what the bubble was last doing and the tick it started, so the frames can be counted. */
    private static final Map<Integer, float[]> STATE = new HashMap<>();

    /** Draws the bubble round a kart (the pose is in the baked model's frame: the kart's origin is at (0.5, 0, 0.5)). */
    public static void drawKart(KartEntity kart, PoseStack pose, MultiBufferSource buffers, float partialTick, net.minecraft.client.Camera camera) {
        int s = kart.shield();
        if (s == 0 && DebugCommands.shieldShow) s = 41;
        if (s == 0) {
            STATE.remove(kart.getId());
            return;
        }
        float[] st = STATE.computeIfAbsent(kart.getId(), k -> new float[] { 0, 0, 0 });          // {phase (1 active, 2 pop), start tick, blue}
        float phase = s == -2 ? 2 : 1;
        float now = kart.tickCount + partialTick;
        if (st[0] != phase) {
            st[0] = phase;
            st[1] = now;
        }
        if (s == -1) st[2] = 1;
        else if (s > 0) st[2] = 0;
        boolean blue = st[2] > 0;
        float frames = (now - st[1]) * 1.5F;
        int f = (int) frames;
        float sx;
        float sy;
        float alpha = 0.22F;
        if (phase == 2) {
            int[] t = POP[Math.min(10, f)];
            sx = t[0] / REST;
            sy = t[1] / REST;
        } else if (DebugCommands.shieldHold >= 0) {
            int[] t = DebugCommands.shieldHold < GROW.length ? GROW[DebugCommands.shieldHold] : PULSE[(DebugCommands.shieldHold - GROW.length) % PULSE.length];
            sx = t[0] / REST;
            sy = t[1] / REST;
        } else if (f < GROW.length) {
            sx = GROW[f][0] / REST;
            sy = GROW[f][1] / REST;
        } else {
            int[] t = PULSE[(int) (now * 1.5F) % PULSE.length];
            sx = t[0] / REST;
            sy = t[1] / REST;
        }
        if (phase == 1 && s > 0 && s <= 40) alpha *= s / 41.0F;                // the green one fades in its last 60 frames
        // the original's layers (RB_ShieldDark_ThTick_Grow, the GTE depth cue toward black: colour x (1 - alphaScale / 0x1000)): the body and the sheen are born with alphaScale 0 (full),
        // the dark layer with 0x400 (three quarters); in the green one's last 60 frames all three get alphaScale 0x400 rising to 0x1000, so they fade OUT to nothing
        float intensity = 1.0F;
        if (phase == 1 && s > 0 && s <= 40) intensity = 0.75F * s / 41.0F;
        if (DebugCommands.shieldIntensity >= 0.0F) intensity = DebugCommands.shieldIntensity;
        // the highlight turns 0x100 (22.5 degrees) a frame from 0xc00, for 8 frames, then is hidden for 30 (highlightTimer)
        float shine = sheenAngle(frames);
        if (drawOriginal(pose, buffers, camera, kart.getPosition(partialTick), kart.renderYawDegrees(partialTick), sx, sy, blue, intensity, shine, false)) return;
        draw(pose, buffers, 0.0F, 0.55F, 0.0F, sx, sy, blue, alpha);
    }

    /**
     * The sheen's turn in degrees (270 to 427, eight steps of 22.5) at {@code frames} game frames (30 a second), NaN while it is hidden. The game steps once a frame and hides it for 30 frames
     * after the eighth; {@code DebugCommands.sheenSpeed} slows the sweep (the baked pivot makes it travel faster than the footage): each step lasts 1 / speed frames.
     */
    public static float sheenAngle(float frames) {
        float speed = Math.max(0.05F, DebugCommands.sheenSpeed);
        float visible = 8.0F / speed;
        float cycle = frames % (visible + 30.0F);
        return cycle < visible ? 270.0F + 22.5F * Math.min(7, (int) (cycle * speed)) : Float.NaN;
    }

    /** Draws a bubble of the given squash at a place in the current pose. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float sx, float sy, boolean blue, float alpha) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        float k = DIAMETER / MODEL_DIAMETER;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(k * sx, k * sy, k * sx);
        pose.translate(-0.5, -0.5, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float r = blue ? 0.45F : 0.4F;
        float g = blue ? 0.7F : 1.0F;
        float b = blue ? 1.0F : 0.5F;
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : model.getQuads(null, side, RANDOM)) {
                vc.putBulkData(pose.last(), quad, r, g, b, alpha, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
    }
}

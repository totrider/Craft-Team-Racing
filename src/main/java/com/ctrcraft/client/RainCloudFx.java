package com.ctrcraft.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartScale;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The red beaker's rain cloud over a kart (RB_RainCloud_Init / _ThTick / _FadeAway, RedBeaker_RenderRain): the cloud model, 23 animation frames one a game frame, floats 0x80 units above the
 * kart and follows it halfway each frame; it turns about the vertical axis to face the camera (not tipped). Under it falls the rain: 30 short streaks (a gradient line from nothing at the top to bright at
 * its lower end, 40 units long, falling 40 units a frame) scattered in a box 256 wide and deep and 510 deep below the cloud, drawn twice, added in a blue white and taken away in white
 * (the rain's two passes, draw modes 0xe1000a20 and 0xe1000a40), fading out with the distance from the camera. When the cloud ends it shrinks 0x100 a frame and the streaks thin by two a
 * frame. Models are the pack's (the jar has a stand-in cloud); only the local driver's own cloud is shown for now.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class RainCloudFx {
    private RainCloudFx() {}

    public static final int FRAMES = 23;
    public static final ModelResourceLocation[] CLOUD = new ModelResourceLocation[FRAMES];
    /** The cloud's blended texels (subtracted from the screen), the opaque ones are in CLOUD. */
    public static final ModelResourceLocation[] CLOUD_SUB = new ModelResourceLocation[FRAMES];

    static {
        for (int i = 0; i < FRAMES; i++) {
            CLOUD[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_rain_cloud_" + i));
            CLOUD_SUB[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_rain_cloud_sub_" + i));
        }
    }

    private static final double UNITS = KartScale.LU_PER_BLOCK;
    /** The rain's box, in the units the footage shows: it stays within the kart's own bounds, about half the size the raw numbers (256 units across) give. */
    private static final double RAIN_UNITS = UNITS * 3.0;
    private static final int STREAKS = 30;
    /** The driver instance's scale, 0xccc / 0x1000, which the cloud's scale follows. */
    private static final double SCALE = 0xccc / 4096.0;
    private static final double EASE = 0.6;
    /** How much of the way to the kart the cloud goes each tick: more than the original's (which leaves a frame's travel of lag), so it keeps closer to the kart. */
    private static final double POS_EASE = 0.88;
    /** The streaks' scattered start points in a box of 256 x 512 x 256 units (a fixed pattern, as the original's seeded generator gives), wrapping as they fall. */
    private static final double[][] POINTS = new double[STREAKS][3];
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final RandomSource RANDOM = RandomSource.create();

    static {
        Random r = new Random(0x30125400L);
        for (double[] p : POINTS) {
            p[0] = r.nextInt(256);
            p[1] = r.nextInt(256) * 2;
            p[2] = r.nextInt(256);
        }
    }

    private static final class State {
        Vec3 pos;
        Vec3 prev;
        double scale = 1.0;          // an instance is born at its model's own size, then goes halfway to the kart's each frame
        double rain = STREAKS;
        boolean fading;
        double age;

        State(Vec3 at) {
            pos = at;
            prev = at;
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STATES.clear();
            return;
        }
        KartEntity ridden = mc.player.getVehicle() instanceof KartEntity k ? k : null;
        if (ridden != null && KartDriver.cloudActive() && !STATES.containsKey(ridden.getId())) {
            STATES.put(ridden.getId(), new State(ridden.position().add(0.0, 128.0 / UNITS, 0.0)));
        }
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            State s = entry.getValue();
            Entity kart = mc.level.getEntity(entry.getKey());
            if (kart == null || kart.isRemoved()) {
                it.remove();
                continue;
            }
            s.fading = !(ridden != null && ridden.getId() == entry.getKey() && KartDriver.cloudActive());
            // each game frame (1.5 to a tick) the cloud goes halfway to the kart's place plus 5/128 of its scale up, and to its scale: the driver's instance is drawn at 0xccc of 0x1000 (0.8,
            // VEH_BIRTH_DRIVER_INSTANCE_SCALE), so the cloud settles at 0.8 of its model's size and 0.8 x 160 = 128 units over the kart, not 160. The ease is 0.6 a tick, which leaves the same
            // steady lag (one game frame's travel) as halving every frame does.
            Vec3 target = kart.position().add(0.0, 128.0 / UNITS, 0.0);
            s.prev = s.pos;
            s.pos = s.pos.add(target.subtract(s.pos).scale(POS_EASE));
            s.age += 1.0;
            if (s.fading) {
                s.scale -= 0.094;                      // 0x100 of 0x1000 a frame
                s.rain -= 3.0;                         // frameCount - 2 a frame
                if (s.scale <= 0.0) it.remove();
            } else {
                s.scale += (SCALE - s.scale) * EASE;
                s.rain = STREAKS;
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || STATES.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (State s : STATES.values()) {
            if (s.scale <= 0.0) continue;
            Vec3 at = s.prev.lerp(s.pos, partial);
            double frames = (s.age + partial) * 1.5;
            // the cloud: its opaque texels as they are (the black middle), then its blended ones taken from the screen (the soft edge): every texture of it is in the subtractive blend
            int frame = (int) frames % FRAMES;
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            float k = (float) s.scale;
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(cam.x - at.x, cam.z - at.z)));          // about the vertical axis only: the panels face the camera, as the shield's cone does
            pose.scale(k, k, k);
            pose.translate(-0.5, -0.5, -0.5);
            VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            drawModel(mc.getModelManager().getModel(CLOUD[frame]), pose, solid);
            buffers.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            VertexConsumer take = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS_SUBTRACT);
            drawModel(mc.getModelManager().getModel(CLOUD_SUB[frame]), pose, take);
            buffers.endBatch(AdditiveTypes.BLOCK_ATLAS_SUBTRACT);
            pose.popPose();
            // the rain
            double dist = cam.distanceTo(at) * UNITS;
            if (dist >= 0xC00) continue;
            int fade = dist < 0x400 ? 255 : 255 - (((int) (dist - 0x400) >> 3) & 255);
            float top = (fade - (fade >> 2)) / 255.0F;
            float full = fade / 255.0F;
            double scroll = -40.0 * frames;
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            PoseStack.Pose p = pose.last();
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer lines = buffers.getBuffer(pass == 0 ? AdditiveTypes.LINES_ADD : AdditiveTypes.LINES_SUBTRACT);
                float r = pass == 0 ? top : full;
                float g = pass == 0 ? top : full;
                float b = full;
                int count = (int) Math.max(0, Math.min(STREAKS, Math.round(s.rain)));
                for (int i = 0; i < count; i++) {
                    double x = Math.floorMod((long) POINTS[i][0], 256L) - 128.0;
                    double z = Math.floorMod((long) POINTS[i][2], 256L) - 128.0;
                    // the wrap in y is the box's 510 units: from the top (just under the cloud) down to its bottom
                    double y0 = Math.floorMod((long) Math.floor(POINTS[i][1] + scroll), 512L) - 512.0;
                    double y1 = y0 - 40.0;
                    if (y1 < -512.0) y1 += 512.0;            // the original skips a streak that wraps over its two ends; here it simply carries on from the top
                    if (y1 > y0) continue;
                    line(p, lines, x / RAIN_UNITS, y0 / RAIN_UNITS, z / RAIN_UNITS, x / RAIN_UNITS, y1 / RAIN_UNITS, z / RAIN_UNITS, r, g, b);
                }
            }
            pose.popPose();
            buffers.endBatch(AdditiveTypes.LINES_ADD);
            buffers.endBatch(AdditiveTypes.LINES_SUBTRACT);
        }
    }

    private static void drawModel(BakedModel model, PoseStack pose, VertexConsumer vc) {
        for (Direction side : SIDES) {
            RANDOM.setSeed(42L);
            for (var quad : model.getQuads(null, side, RANDOM)) {
                vc.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /** A line from nothing (black adds and takes away nothing) at its first end to the colour at its second. */
    private static void line(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b) {
        float nx = (float) (x1 - x0);
        float ny = (float) (y1 - y0);
        float nz = (float) (z1 - z0);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) return;
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(0.0F, 0.0F, 0.0F, 1.0F).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, 1.0F).setNormal(pose, nx, ny, nz);
    }
}

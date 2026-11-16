package com.ctrcraft.client;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.CtrClientConfig;
import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModBlocks;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.track.NodeGeometry;
import com.ctrcraft.track.PodiumScenePayload;
import com.ctrcraft.track.RaceClient;
import com.ctrcraft.track.TrackClient;
import com.ctrcraft.track.TrackSnapshotPayload.NodeView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The podium camera: while the podium is on display the camera drifts across the front of the racer's own kart, looking at the driver. A racer who is not on a podium sees all three places: the
 * camera moves round the group and its look-at point glides from the third place to the second and the first, so there is never a cut however far apart the nodes are. Both stay inside the
 * world the player has loaded (the server only sends chunks round the player, so a podium too far away has no shot) and are pulled in by blocks like a third person camera, or give up for the
 * normal view when the space is too tight. Client side only; switched off with the {@code podiumCamera} client setting. While a podium node is held, the area the camera sweeps is drawn.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class PodiumCamera {
    private PodiumCamera() {}

    /** How far from the kart the camera is, and how it sweeps across the front: half the arc in degrees, a full sweep in seconds. */
    public static final double RADIUS = 4.5;
    public static final double SWEEP_DEGREES = 50.0;
    public static final double SWEEP_SECONDS = 14.0;
    /** The camera is this high above the point it looks at (the driver's middle, FOCUS_Y over the kart's floor). */
    public static final double HEIGHT = 1.0;
    public static final double FOCUS_Y = 0.9;
    /** A shot closer than this to what it looks at is not taken. */
    private static final double MIN_DISTANCE = 1.8;

    private static List<PodiumScenePayload.Spot> scene = List.of();
    private static long sceneAt;
    private static long ownAt;
    private static long suppressedUntil;

    private static Marker camera;
    private static Entity previous;
    private static CameraType previousType;
    private static Vec3 smooth;
    private static float yawNow;
    private static float pitchNow;

    public static void setScene(List<PodiumScenePayload.Spot> spots) {
        scene = spots;
        sceneAt = System.currentTimeMillis();
    }

    private static boolean unlimited(Minecraft mc) {
        return mc.player == null || mc.level == null;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (unlimited(mc) || !RaceClient.active()) {
            scene = List.of();
            ownAt = 0;
            stop(mc);
            return;
        }
        if (!RaceClient.podium()) ownAt = 0;
        else if (ownAt == 0) ownAt = System.currentTimeMillis();
        boolean mine = RaceClient.podium() && mc.player.getVehicle() instanceof KartEntity;
        boolean spectate = !RaceClient.podium() && !scene.isEmpty() && mc.player.getVehicle() instanceof KartEntity;
        if (!CtrClientConfig.PODIUM_CAMERA.get() || !(mine || spectate) || System.currentTimeMillis() < suppressedUntil) {
            stop(mc);
            return;
        }
        double[] pose = mine ? ownShot(mc) : groupShot(mc);
        if (pose == null) {
            suppressedUntil = System.currentTimeMillis() + 2000L;          // no good shot here: the normal view, and not tried again for a moment
            stop(mc);
            return;
        }
        if (camera == null) {
            camera = new Marker(EntityType.MARKER, mc.level);
            previous = mc.getCameraEntity();
            previousType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
            smooth = new Vec3(pose[0], pose[1], pose[2]);
            yawNow = (float) pose[3];
            pitchNow = (float) pose[4];
        }
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        // the camera eases to the shot (a block that comes between pulls it in, and it eases back out)
        smooth = smooth.add(new Vec3(pose[0], pose[1], pose[2]).subtract(smooth).scale(0.2));
        yawNow = Mth.rotLerp(0.2F, yawNow, (float) pose[3]);
        pitchNow = Mth.lerp(0.2F, pitchNow, (float) pose[4]);
        camera.moveTo(smooth.x, smooth.y, smooth.z, yawNow, pitchNow);
        camera.setOldPosAndRot();
    }

    private static void stop(Minecraft mc) {
        if (camera == null) return;
        if (mc.getCameraEntity() == camera) mc.setCameraEntity(previous != null ? previous : mc.player);
        if (previousType != null) mc.options.setCameraType(previousType);
        camera = null;
        previous = null;
        previousType = null;
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (camera == null) return;
        event.setYaw(yawNow);
        event.setPitch(pitchNow);
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (camera != null) event.setCanceled(true);
    }

    // ---- the shots ----

    /** Looking from the front arc of the racer's own kart: {x, y, z, yaw, pitch}, or null when there is no room. */
    private static double[] ownShot(Minecraft mc) {
        Entity kart = mc.player.getVehicle();
        Vec3 focus = new Vec3(kart.getX(), kart.getY() + FOCUS_Y, kart.getZ());
        double phase = (System.currentTimeMillis() - ownAt) / 1000.0 / SWEEP_SECONDS * Math.PI * 2.0;
        double base = kart.getYRot() + SWEEP_DEGREES * Math.sin(phase);
        return bestShot(mc, focus, base, kart.getYRot(), RADIUS, HEIGHT);
    }

    /** Over all three places: the camera goes round the group and looks from the third place to the second and the first. */
    private static double[] groupShot(Minecraft mc) {
        List<PodiumScenePayload.Spot> spots = new ArrayList<>(scene);
        if (spots.isEmpty()) return null;
        Vec3 centre = Vec3.ZERO;
        double fx = 0.0;
        double fz = 0.0;
        for (PodiumScenePayload.Spot s : spots) {
            centre = centre.add(s.x(), s.y() + FOCUS_Y, s.z());
            fx += -Math.sin(Math.toRadians(s.yaw()));
            fz += Math.cos(Math.toRadians(s.yaw()));
        }
        centre = centre.scale(1.0 / spots.size());
        double radius = 0.0;
        for (PodiumScenePayload.Spot s : spots) radius = Math.max(radius, centre.distanceTo(new Vec3(s.x(), s.y() + FOCUS_Y, s.z())));
        double facing = Math.toDegrees(Math.atan2(-fx, fz));
        // only a podium inside what this client has loaded can be seen: the chunks (and the winners) are sent round the player, not round the camera
        double reach = (mc.options.getEffectiveRenderDistance() - 1) * 16.0;
        if (mc.player.position().distanceTo(centre) + radius > reach) return null;
        double t = (System.currentTimeMillis() - sceneAt) / 1000.0;
        double base = facing + 35.0 * Math.sin(t / SWEEP_SECONDS * Math.PI * 2.0);
        // the look-at point goes from the third place to the first over eight seconds and stays on the winner
        double u = Mth.clamp(t / 8.0, 0.0, 1.0);
        u = u * u * (3.0 - 2.0 * u) * 2.0;
        Vec3 look = lookPoint(spots, u);
        double distance = Math.max(7.0, radius * 1.5 + 5.0);
        return bestShot(mc, look.multiply(1.0, 1.0, 1.0), base, facing, distance, 2.0 + radius * 0.1, centre);
    }

    /** The point on the way from the third place (u = 0) through the second (1) to the first (2). */
    private static Vec3 lookPoint(List<PodiumScenePayload.Spot> spots, double u) {
        PodiumScenePayload.Spot third = null;
        PodiumScenePayload.Spot second = null;
        PodiumScenePayload.Spot first = null;
        for (PodiumScenePayload.Spot s : spots) {
            if (s.place() == 3) third = s;
            else if (s.place() == 2) second = s;
            else first = s;
        }
        List<Vec3> order = new ArrayList<>();
        for (PodiumScenePayload.Spot s : new PodiumScenePayload.Spot[] { third, second, first }) if (s != null) order.add(new Vec3(s.x(), s.y() + FOCUS_Y, s.z()));
        if (order.size() == 1) return order.get(0);
        double pos = Mth.clamp(u / 2.0, 0.0, 1.0) * (order.size() - 1);
        int i = Math.min((int) pos, order.size() - 2);
        return order.get(i).lerp(order.get(i + 1), pos - i);
    }

    private static double[] bestShot(Minecraft mc, Vec3 look, double baseYaw, double facing, double distance, double height) {
        return bestShot(mc, look, baseYaw, facing, distance, height, look);
    }

    /**
     * The best place for the camera: round {@code centre} at {@code baseYaw}, {@code distance} away and {@code height} up, looking at {@code look}. A block in the way pulls it in (like third
     * person); when that leaves it too close, other angles and a shorter distance are tried, then none. Returns {x, y, z, yaw, pitch}.
     */
    private static double[] bestShot(Minecraft mc, Vec3 look, double baseYaw, double facing, double distance, double height, Vec3 centre) {
        double[] offsets = { 0.0, 25.0, -25.0, 50.0, -50.0 };
        double[] scales = { 1.0, 0.7 };
        double[] best = null;
        double bestLen = 0.0;
        for (double scale : scales) {
            for (double off : offsets) {
                double yaw = baseYaw + off;
                Vec3 want = centre.add(-Math.sin(Math.toRadians(yaw)) * distance * scale, height, Math.cos(Math.toRadians(yaw)) * distance * scale);
                Vec3 from = centre;
                var hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                Vec3 at = want;
                if (hit.getType() == HitResult.Type.BLOCK) {
                    Vec3 dir = want.subtract(from).normalize();
                    at = hit.getLocation().subtract(dir.scale(0.3));
                }
                double len = at.distanceTo(from);
                if (len > bestLen) {
                    bestLen = len;
                    best = pose(at, look);
                }
                if (len >= distance * scale * 0.9) return pose(at, look);          // a clear shot: no need to look further
            }
        }
        return bestLen >= MIN_DISTANCE ? best : null;
    }

    private static double[] pose(Vec3 at, Vec3 look) {
        double dx = look.x - at.x;
        double dy = look.y - at.y;
        double dz = look.z - at.z;
        return new double[] { at.x, at.y, at.z, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) };
    }

    // ---- the area, drawn while a podium node is held ----

    private static boolean holdsPodium(Minecraft mc) {
        for (ItemStack stack : new ItemStack[] { mc.player.getMainHandItem(), mc.player.getOffhandItem() }) {
            if (stack.is(ModBlocks.PODIUM_1_NODE_ITEM.get()) || stack.is(ModBlocks.PODIUM_2_NODE_ITEM.get()) || stack.is(ModBlocks.PODIUM_3_NODE_ITEM.get())) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void drawArea(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (unlimited(mc) || !holdsPodium(mc)) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        for (NodeView n : TrackClient.nodes()) {
            if (!n.kind().isPodium()) continue;
            float[] c = switch (n.kind()) {
                case PODIUM_1 -> new float[] { 1.0F, 0.85F, 0.2F };
                case PODIUM_2 -> new float[] { 0.8F, 0.85F, 0.9F };
                default -> new float[] { 0.8F, 0.5F, 0.25F };
            };
            double cx = NodeGeometry.x(n.pos()) + 0.5;
            double cy = NodeGeometry.y(n.pos()) + FOCUS_Y;
            double cz = NodeGeometry.z(n.pos()) + 0.5;
            double yaw = Math.toDegrees(Math.atan2(-NodeGeometry.facingX(n.rotation()), NodeGeometry.facingZ(n.rotation())));
            double px = 0;
            double py = 0;
            double pz = 0;
            for (int i = 0; i <= 20; i++) {
                double a = yaw + SWEEP_DEGREES * (i / 10.0 - 1.0);
                double x = cx - Math.sin(Math.toRadians(a)) * RADIUS;
                double y = cy + HEIGHT;
                double z = cz + Math.cos(Math.toRadians(a)) * RADIUS;
                if (i > 0) line(p, lines, px, py, pz, x, y, z, c[0], c[1], c[2], 0.9F);
                if (i == 0 || i == 20) line(p, lines, cx, cy, cz, x, y, z, c[0], c[1], c[2], 0.5F);          // the edges of the sweep, back to the kart
                px = x;
                py = y;
                pz = z;
            }
            line(p, lines, cx, cy, cz, cx - Math.sin(Math.toRadians(yaw)) * RADIUS, cy + HEIGHT, cz + Math.cos(Math.toRadians(yaw)) * RADIUS, c[0], c[1], c[2], 0.35F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) return;
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}

package com.craftteamracing.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.track.ChunkKeeper;
import com.craftteamracing.track.NodeGeometry;
import com.craftteamracing.track.TrackClient;
import com.craftteamracing.track.TrackSnapshotPayload.NodeView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * {@code /ctr debug chunks}: an overview of the chunks a race on the tracks this client knows would keep loaded (see {@link ChunkKeeper}). Every chunk is a tile on one flat level, the height of
 * the course (so the tiles do not depend on where you stand or fly): <b>green</b> the chunks the course runs through (loaded and fully running), <b>yellow</b> the ring round them (loaded, blocks and
 * block entities running, so pickup spots there work). Tall posts mark the corners of the green ones, as the chunk borders of vanilla's F3 + G do. Worked out from the nodes this client knows, so it
 * shows what a race would hold, whether or not one is on and whatever the server's {@code keepCourseLoaded} says.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class ChunkDebugRenderer {
    private ChunkDebugRenderer() {}

    public static volatile boolean enabled = false;

    /** How many chunks round the player are drawn. */
    private static final int DRAW_RADIUS = 40;
    /** The tiles float this far above the average height of the course's nodes, and the posts reach this far below and above that level. */
    private static final double LIFT = 3.0;
    private static final double POST_DOWN = 24.0;
    private static final double POST_UP = 64.0;

    private static Set<Long> course = Set.of();
    private static Set<Long> ring = Set.of();
    private static Set<Long> corners = Set.of();
    private static double level;
    private static long computedAt;

    private static void compute() {
        Map<Long, NodeView> byPos = new HashMap<>();
        List<Long> podiums = new ArrayList<>();
        double sumY = 0;
        for (NodeView n : TrackClient.nodes()) {
            byPos.put(n.pos(), n);
            sumY += NodeGeometry.y(n.pos());
            if (n.kind().isPodium()) podiums.add(n.pos());
        }
        level = byPos.isEmpty() ? 0 : sumY / byPos.size() + LIFT;
        course = ChunkKeeper.courseChunks(new ArrayList<>(byPos.keySet()), pos -> {
            NodeView n = byPos.get(pos);
            return n == null || n.links() == null ? new long[0] : n.links();
        }, podiums);
        Set<Long> around = new HashSet<>();
        Set<Long> cs = new HashSet<>();
        for (long c : course) {
            ChunkPos cp = new ChunkPos(c);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) around.add(ChunkPos.asLong(cp.x + dx, cp.z + dz));
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) cs.add(ChunkPos.asLong(cp.x + dx, cp.z + dz));
        }
        around.removeAll(course);
        ring = around;
        corners = cs;
        computedAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (System.currentTimeMillis() - computedAt > 1000L) compute();
        if (course.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        ChunkPos me = mc.player.chunkPosition();
        // the tiles first (one render type at a time), then the outlines and the posts
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (long c : ring) tile(p, quads, new ChunkPos(c), me, 1.0F, 0.85F, 0.2F, 0.16F);
        for (long c : course) tile(p, quads, new ChunkPos(c), me, 0.3F, 1.0F, 0.4F, 0.26F);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (long c : ring) outline(p, lines, new ChunkPos(c), me, 1.0F, 0.85F, 0.2F, 0.7F);
        for (long c : course) outline(p, lines, new ChunkPos(c), me, 0.3F, 1.0F, 0.4F, 1.0F);
        for (long c : corners) {
            ChunkPos cp = new ChunkPos(c);
            if (far(cp, me)) continue;
            double x = cp.x * 16.0;
            double z = cp.z * 16.0;
            line(p, lines, x, level - POST_DOWN, z, x, level + POST_UP, z, 0.3F, 1.0F, 0.4F, 0.85F);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static boolean far(ChunkPos cp, ChunkPos me) {
        return Math.abs(cp.x - me.x) > DRAW_RADIUS || Math.abs(cp.z - me.z) > DRAW_RADIUS;
    }

    private static void tile(PoseStack.Pose p, VertexConsumer quads, ChunkPos cp, ChunkPos me, float r, float g, float b, float a) {
        if (far(cp, me)) return;
        float x0 = cp.getMinBlockX() + 0.05F;
        float z0 = cp.getMinBlockZ() + 0.05F;
        float x1 = cp.getMinBlockX() + 15.95F;
        float z1 = cp.getMinBlockZ() + 15.95F;
        float y = (float) level;
        quads.addVertex(p, x0, y, z0).setColor(r, g, b, a);
        quads.addVertex(p, x0, y, z1).setColor(r, g, b, a);
        quads.addVertex(p, x1, y, z1).setColor(r, g, b, a);
        quads.addVertex(p, x1, y, z0).setColor(r, g, b, a);
    }

    private static void outline(PoseStack.Pose p, VertexConsumer lines, ChunkPos cp, ChunkPos me, float r, float g, float b, float a) {
        if (far(cp, me)) return;
        double x0 = cp.getMinBlockX() + 0.05;
        double z0 = cp.getMinBlockZ() + 0.05;
        double x1 = cp.getMinBlockX() + 15.95;
        double z1 = cp.getMinBlockZ() + 15.95;
        line(p, lines, x0, level, z0, x1, level, z0, r, g, b, a);
        line(p, lines, x1, level, z0, x1, level, z1, r, g, b, a);
        line(p, lines, x1, level, z1, x0, level, z1, r, g, b, a);
        line(p, lines, x0, level, z1, x0, level, z0, r, g, b, a);
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

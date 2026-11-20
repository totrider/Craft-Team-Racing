package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.net.RaceDebugRequestPayload;
import com.craftteamracing.track.NodeGeometry;
import com.craftteamracing.track.RaceDebugPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The race debug view: {@code /ctr debug rings} draws every ring of the course whether or not a track item is held (the ones the server counts as passed in green, the last one flashing, the one
 * that counts next in orange, the rest dim) and {@code /ctr debug path} draws a line from the kart to the line of the course the server thinks it is driving along, with that line picked out.
 * Both show a line of text about the racer's progress. They only change what this client draws; the server sends the data while the player has one of them on.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class RaceDebugClient {
    private RaceDebugClient() {}

    private static volatile boolean rings;
    private static volatile boolean path;
    private static volatile boolean nodes;

    /** Whether the track's nodes, start grid and pickup spots are drawn without an editing item in hand (/ctr debug nodes), also during a race. */
    public static boolean nodes() {
        return nodes;
    }
    private static volatile RaceDebugPayload last;
    private static volatile long lastAt;
    private static int resend;

    public static String info() {
        return (rings || path) && last != null && System.currentTimeMillis() - lastAt < 2000 ? last.info() : null;
    }

    public static void receive(RaceDebugPayload p) {
        last = p;
        lastAt = System.currentTimeMillis();
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean ringsToggle) {
        if (ringsToggle) rings = !rings;
        else path = !path;
        PacketDistributor.sendToServer(new RaceDebugRequestPayload(rings, path));
        resend = 0;
        boolean on = ringsToggle ? rings : path;
        ctx.getSource().sendSuccess(() -> Component.literal((ringsToggle ? "Debug view of the checkpoints: " : "Debug view of the line driven along: ") + (on ? "on" : "off")), false);
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("debug").then(Commands.literal("rings").executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("path").executes(ctx -> toggle(ctx, false)))
                .then(Commands.literal("nodes").executes(ctx -> {
                    nodes = !nodes;
                    ctx.getSource().sendSuccess(() -> Component.literal("Track nodes, start grid and pickup spots drawn without an item in hand: " + (nodes ? "on" : "off")), false);
                    return 1;
                }))));
    }

    /** While a view is on, the request is repeated now and then (the server forgets it with a restart or a new race). */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!(rings || path) || Minecraft.getInstance().player == null) return;
        if (++resend >= 40) {
            resend = 0;
            PacketDistributor.sendToServer(new RaceDebugRequestPayload(rings, path));
        }
    }

    private static final float[][] COLOURS = {
            { 0.45F, 0.45F, 0.5F },          // other
            { 0.2F, 1.0F, 0.3F },            // passed
            { 1.0F, 1.0F, 0.2F },            // last (flashes)
            { 1.0F, 0.55F, 0.1F },           // next
    };

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        RaceDebugPayload d = last;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.level == null || mc.player == null || !(rings || path) || System.currentTimeMillis() - lastAt > 2000) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float flash = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() * 0.012);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        if (rings) {
            for (RaceDebugPayload.Ring r : d.rings()) {
                float[] c = COLOURS[Math.max(0, Math.min(3, r.state()))];
                float a = r.state() == RaceDebugPayload.HEAD ? flash : r.state() == RaceDebugPayload.OTHER ? 0.5F : 1.0F;
                // the head flashes between yellow and white so it can be told from the passed ones
                float[] col = r.state() == RaceDebugPayload.HEAD && flash > 0.8F ? new float[] { 1.0F, 1.0F, 1.0F } : c;
                box(p, lines, r.pos(), r.rotation(), r.ext(), col[0], col[1], col[2], a);
            }
        }
        if (path) {
            double[] s = d.seg();
            double[] pt = d.point();
            line(p, lines, s[0], s[1], s[2], s[3], s[4], s[5], 0.2F, 0.9F, 1.0F, 1.0F);          // the line of the course the racer is nearest to
            Vec3 kart = mc.player.getVehicle() != null ? mc.player.getVehicle().position() : mc.player.position();
            line(p, lines, kart.x, kart.y + 0.4, kart.z, pt[0], pt[1], pt[2], 1.0F, 0.2F, 1.0F, 1.0F);          // from the kart to the point of it
            line(p, lines, pt[0], pt[1] - 0.5, pt[2], pt[0], pt[1] + 1.5, pt[2], 1.0F, 0.2F, 1.0F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, long key, int rot, float[] ext, float r, float g, float b, float a) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double lat = (i & 1) == 0 ? ext[NodeGeometry.LEFT] : -ext[NodeGeometry.RIGHT];
            double up = (i & 2) == 0 ? ext[NodeGeometry.UP] : -ext[NodeGeometry.DOWN];
            double fwd = (i & 4) == 0 ? ext[NodeGeometry.FRONT] : -ext[NodeGeometry.BACK];
            c[i] = NodeGeometry.world(key, rot, lat, up, fwd);
        }
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i ^ bit;
                if (j > i) line(pose, vc, c[i][0], c[i][1], c[i][2], c[j][0], c[j][1], c[j][2], r, g, b, a);
            }
        }
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

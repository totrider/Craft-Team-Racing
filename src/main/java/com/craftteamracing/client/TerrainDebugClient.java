package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.terrain.TerrainField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * {@code /ctr debug terrain} draws the ground the kart's physics sees around the driven kart: the smooth surface it rides on, in half-block cells coloured by how steep it is, and the
 * collision boxes it would hit as walls as red outlines. It reads the very field the simulation uses, rebuilt every tick around the kart, so what is drawn is what the kart collides with.
 * <ul>
 *   <li><b>green:</b> flat or a gentle slope (under 20 degrees);</li>
 *   <li><b>yellow:</b> a slope of 20 to 35 degrees;</li>
 *   <li><b>orange:</b> 35 to 45 degrees, the steepest ramp the surface ever makes (a one block step, or a staircase of full blocks along its axis);</li>
 *   <li><b>no colour:</b> no ground the kart could stand on (a cliff, a gap, or ground more than a step above it);</li>
 *   <li><b>red outline:</b> a collision box the surface does not cover, a wall;</li>
 *   <li><b>magenta cross and line:</b> the wall the kart last pressed into (for a second and a half), and the way it pushed back.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class TerrainDebugClient {
    private TerrainDebugClient() {}

    private static volatile boolean on;

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("debug").then(Commands.literal("terrain").executes(ctx -> {
            on = !on;
            ctx.getSource().sendSuccess(() -> Component.literal(on
                    ? "Terrain view on (rules " + TerrainField.REVISION + "). While driving: green flat or gentle, yellow 20-35 degrees, orange 35-45 degrees (the steepest ramp), nothing where there is no ground, red outlines are walls."
                    : "Terrain view off"), false);
            return 1;
        }))));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!on || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        TerrainField field = KartDriver.terrainField();
        Minecraft mc = Minecraft.getInstance();
        if (field == null || mc.level == null || mc.player == null || !(mc.player.getVehicle() instanceof com.craftteamracing.kart.KartEntity kart)) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();

        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        double x0 = field.windowX0();
        double z0 = field.windowZ0();
        int n = field.cellCount();
        double cell = TerrainField.CELL;
        double lift = 0.03;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double cx = x0 + (i + 0.5) * cell;
                double cz = z0 + (j + 0.5) * cell;
                double centre = field.surface(cx, cz);
                if (Double.isNaN(centre)) continue;
                double[] normal = field.normal(cx, cz);
                double degrees = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, normal[1]))));
                float r;
                float g;
                float b;
                if (degrees < 20) {
                    r = 0.2F;
                    g = 0.9F;
                    b = 0.3F;
                } else if (degrees < 35) {
                    r = 0.95F;
                    g = 0.9F;
                    b = 0.2F;
                } else {
                    r = 1.0F;
                    g = 0.5F;
                    b = 0.1F;
                }
                double xa = x0 + i * cell;
                double za = z0 + j * cell;
                double[] h = {
                        corner(field, xa, za, centre), corner(field, xa, za + cell, centre), corner(field, xa + cell, za + cell, centre), corner(field, xa + cell, za, centre) };
                quad(p, quads, xa, h[0] + lift, za, xa, h[1] + lift, za + cell, xa + cell, h[2] + lift, za + cell, xa + cell, h[3] + lift, za, r, g, b, 0.38F);
            }
        }

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        field.forEachWallBox(kart.getX(), kart.getZ(), 8.0, bx -> outline(p, lines, bx));
        logWalls(field, kart);
        logHit(field);
        // the wall the kart last pressed into, for a second and a half: a magenta cross at the touching point and a line along the way the wall pushed back
        com.craftteamracing.physics.KartSim sim = KartDriver.activeSim();
        if (sim != null && sim.lastWallContact() != null && System.currentTimeMillis() - sim.lastWallContactMillis() < 1500L) {
            var contact = sim.lastWallContact();
            double u = 1.0 / com.craftteamracing.kart.KartScale.luPerBlock();
            double[] at = { contact.point().x * u, contact.point().y * u, contact.point().z * u };
            double[] tip = { at[0] + contact.normal().x, at[1] + contact.normal().y, at[2] + contact.normal().z };
            magenta(p, lines, at, tip);
            for (int axis = 0; axis < 3; axis++) {
                double[] a = at.clone();
                double[] b = at.clone();
                a[axis] -= 0.25;
                b[axis] += 0.25;
                magenta(p, lines, a, b);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.debugQuads());
        buffers.endBatch(RenderType.lines());
    }

    private static long lastHitLogged;

    /** Each time the kart presses into a wall, one log line (WALLHIT): where, which way it pushed back, whether the point lies on the ground surface (a steep slope) or on a box, and the walls near it. */
    private static void logHit(TerrainField field) {
        com.craftteamracing.physics.KartSim sim = KartDriver.activeSim();
        if (sim == null || sim.lastWallContact() == null || sim.lastWallContactMillis() <= lastHitLogged) return;
        lastHitLogged = sim.lastWallContactMillis();
        var c = sim.lastWallContact();
        double u = 1.0 / com.craftteamracing.kart.KartScale.luPerBlock();
        double px = c.point().x * u;
        double py = c.point().y * u;
        double pz = c.point().z * u;
        double surface = field.surface(px, pz);
        double[] n = field.normal(px, pz);
        java.util.List<double[]> near = new java.util.ArrayList<>();
        field.forEachWallBox(px, pz, 0.6, near::add);
        StringBuilder sb = new StringBuilder();
        for (double[] b : near) {
            sb.append(String.format(" [wall box x %.0f..%.0f y %.1f..%.1f z %.0f..%.0f]", b[0], b[3], b[1], b[4], b[2], b[5]));
        }
        com.craftteamracing.CraftTeamRacingMod.LOGGER.info(String.format("WALLHIT at (%.2f, %.2f, %.2f) pushed back along (%.2f, %.2f, %.2f), depth %.3f; ground surface there %.2f (point is %.2f above it), its normal y %.2f; %d wall boxes within 0.6:%s",
                px, py, pz, c.normal().x, c.normal().y, c.normal().z, c.depth() * u, surface, py - surface, n[1], near.size(), sb));
    }

    private static long lastLog;

    /** Once a second, writes to the game log what the field sees round the kart: the reference height and the nearest wall boxes with the ground the field has under each (latest.log, lines starting \"TERRAIN\"). */
    private static void logWalls(TerrainField field, com.craftteamracing.kart.KartEntity kart) {
        long now = System.currentTimeMillis();
        if (now - lastLog < 1000L) return;
        lastLog = now;
        java.util.List<double[]> boxes = new java.util.ArrayList<>();
        field.forEachWallBox(kart.getX(), kart.getZ(), 2.5, boxes::add);
        boxes.sort(java.util.Comparator.comparingDouble(b -> Math.hypot(Math.max(Math.max(b[0] - kart.getX(), kart.getX() - b[3]), 0), Math.max(Math.max(b[2] - kart.getZ(), kart.getZ() - b[5]), 0))));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, boxes.size()); i++) {
            double[] b = boxes.get(i);
            double px = Math.max(b[0], Math.min(kart.getX(), b[3]));
            double pz = Math.max(b[2], Math.min(kart.getZ(), b[5]));
            sb.append(String.format(" [box x %.0f..%.0f y %.1f..%.1f z %.0f..%.0f: surface at nearest point %.2f, cell height there %.2f]", b[0], b[3], b[1], b[4], b[2], b[5], field.surface(px, pz), field.cellHeight(px, pz)));
        }
        StringBuilder counts = new StringBuilder();
        int cx = (int) Math.floor(kart.getX());
        int cz = (int) Math.floor(kart.getZ());
        for (int z = cz - 4; z <= cz + 4; z++) {
            for (int x = cx - 4; x <= cx + 4; x++) {
                int n = field.columnBoxCount(x, z);
                counts.append(n < 0 ? '#' : (char) ('0' + Math.min(9, n)));
            }
            counts.append('/');
        }
        com.craftteamracing.CraftTeamRacingMod.LOGGER.info("TERRAIN grid: accepted ground height above " + (int) Math.floor(field.ref()) + ", block rows north to south (x west to east) " + field.heightGrid(kart.getX(), kart.getZ(), 4, Math.floor(field.ref())) + " boxes per column " + counts);
        com.craftteamracing.CraftTeamRacingMod.LOGGER.info(String.format("TERRAIN rules %s: kart (%.2f, %.2f, %.2f) ref %.2f own cell %.2f; %d walls within 2.5 blocks:%s", TerrainField.REVISION.substring(0, 1), kart.getX(), kart.getY(), kart.getZ(), field.ref(), field.cellHeight(kart.getX(), kart.getZ()), boxes.size(), sb));
    }

    private static double corner(TerrainField field, double x, double z, double fallback) {
        double s = field.surface(x, z);
        return Double.isNaN(s) ? fallback : s;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4,
            float r, float g, float b, float a) {
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
        vc.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(r, g, b, a);
    }

    private static void outline(PoseStack.Pose pose, VertexConsumer vc, double[] b) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            c[i] = new double[] { (i & 1) == 0 ? b[0] : b[3], (i & 2) == 0 ? b[1] : b[4], (i & 4) == 0 ? b[2] : b[5] };
        }
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i ^ bit;
                if (j > i) line(pose, vc, c[i], c[j]);
            }
        }
    }

    private static void magenta(PoseStack.Pose pose, VertexConsumer vc, double[] a, double[] b) {
        float nx = (float) (b[0] - a[0]);
        float ny = (float) (b[1] - a[1]);
        float nz = (float) (b[2] - a[2]);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) return;
        vc.addVertex(pose, (float) a[0], (float) a[1], (float) a[2]).setColor(1.0F, 0.2F, 1.0F, 1.0F).setNormal(pose, nx / len, ny / len, nz / len);
        vc.addVertex(pose, (float) b[0], (float) b[1], (float) b[2]).setColor(1.0F, 0.2F, 1.0F, 1.0F).setNormal(pose, nx / len, ny / len, nz / len);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc, double[] a, double[] b) {
        float nx = (float) (b[0] - a[0]);
        float ny = (float) (b[1] - a[1]);
        float nz = (float) (b[2] - a[2]);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) return;
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose, (float) a[0], (float) a[1], (float) a[2]).setColor(1.0F, 0.15F, 0.15F, 1.0F).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) b[0], (float) b[1], (float) b[2]).setColor(1.0F, 0.15F, 0.15F, 1.0F).setNormal(pose, nx, ny, nz);
    }
}

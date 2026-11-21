package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the Kart Barriers near the player as translucent cyan boxes with an outline, only while the player holds something for building a track (the barrier itself, the Track Tool,
 * a node block, a spot block or the Wrench) or the nodes debug view is on. Faces between two barriers are left out, so a wall of them reads as one slab.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class BarrierGhostRenderer {
    private BarrierGhostRenderer() {}

    private static final int RADIUS = 14;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(PickupSpotRenderer.holdingEditingItem(mc.player) || RaceDebugClient.nodes())) return;
        BlockPos centre = mc.player.blockPosition();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        java.util.List<long[]> faces = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!mc.level.getBlockState(at).is(ModBlocks.RACE_BARRIER.get())) continue;
                    for (Direction face : Direction.values()) {
                        next.set(at).move(face);
                        if (!mc.level.getBlockState(next).is(ModBlocks.RACE_BARRIER.get())) faces.add(new long[] { at.asLong(), face.ordinal() });
                    }
                }
            }
        }
        if (faces.isEmpty()) return;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        // one render type at a time: asking the buffer source for another type ends the batch of the one before, so all the quads are written, then all the lines
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (long[] f : faces) quad(p, quads, BlockPos.of(f[0]), Direction.values()[(int) f[1]]);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (long[] f : faces) edges(p, lines, BlockPos.of(f[0]), Direction.values()[(int) f[1]]);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static double[][] corners(BlockPos at, Direction face) {
        double e = 0.002;
        double x0 = at.getX() - (face == Direction.WEST ? e : 0);
        double x1 = at.getX() + 1 + (face == Direction.EAST ? e : 0);
        double y0 = at.getY() - (face == Direction.DOWN ? e : 0);
        double y1 = at.getY() + 1 + (face == Direction.UP ? e : 0);
        double z0 = at.getZ() - (face == Direction.NORTH ? e : 0);
        double z1 = at.getZ() + 1 + (face == Direction.SOUTH ? e : 0);
        return switch (face) {
            case UP -> new double[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
            case DOWN -> new double[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
            case NORTH -> new double[][] { { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
            case SOUTH -> new double[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
            case WEST -> new double[][] { { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
            default -> new double[][] { { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
        };
    }

    /** One face of a barrier: a translucent quad pushed out a hair. */
    private static void quad(PoseStack.Pose pose, VertexConsumer quads, BlockPos at, Direction face) {
        for (double[] v : corners(at, face)) {
            quads.addVertex(pose, (float) v[0], (float) v[1], (float) v[2]).setColor(0.2F, 0.85F, 0.95F, 0.22F);
        }
    }

    /** The four edges of that face. */
    private static void edges(PoseStack.Pose pose, VertexConsumer lines, BlockPos at, Direction face) {
        double[][] c = corners(at, face);
        for (int i = 0; i < 4; i++) {
            double[] a = c[i];
            double[] b = c[(i + 1) % 4];
            float nx = (float) (b[0] - a[0]);
            float ny = (float) (b[1] - a[1]);
            float nz = (float) (b[2] - a[2]);
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            lines.addVertex(pose, (float) a[0], (float) a[1], (float) a[2]).setColor(0.3F, 0.95F, 1.0F, 0.9F).setNormal(pose, nx / len, ny / len, nz / len);
            lines.addVertex(pose, (float) b[0], (float) b[1], (float) b[2]).setColor(0.3F, 0.95F, 1.0F, 0.9F).setNormal(pose, nx / len, ny / len, nz / len);
        }
    }
}

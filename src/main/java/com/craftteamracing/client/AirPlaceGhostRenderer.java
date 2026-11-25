package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.pickup.AirPlaceBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Shows where a spot will land when it is placed in mid-air: while an {@link AirPlaceBlockItem} is in hand and the player points at nothing within reach (pointing at a block places it
 * against that block the usual way, with the usual outline), a translucent box with an outline marks the cell {@link AirPlaceBlockItem#airTarget} picks. Red when something is in the way.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class AirPlaceGhostRenderer {
    private AirPlaceGhostRenderer() {}

    private static boolean holding(Player player) {
        return player.getMainHandItem().getItem() instanceof AirPlaceBlockItem || player.getOffhandItem().getItem() instanceof AirPlaceBlockItem;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null || mc.screen != null || !holding(player)) return;
        if (player.pick(player.blockInteractionRange(), 1.0F, false).getType() != HitResult.Type.MISS) return;
        BlockPos pos = AirPlaceBlockItem.airTarget(player);
        boolean free = mc.level.getBlockState(pos).canBeReplaced();
        float r = free ? 0.3F : 1.0F;
        float g = free ? 1.0F : 0.25F;
        float b = free ? 0.6F : 0.25F;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        float e = 0.003F;
        float x0 = pos.getX() - e;
        float y0 = pos.getY() - e;
        float z0 = pos.getZ() - e;
        float x1 = pos.getX() + 1 + e;
        float y1 = pos.getY() + 1 + e;
        float z1 = pos.getZ() + 1 + e;
        float[][] c = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
        int[][] faces = { { 0, 1, 2, 3 }, { 4, 7, 6, 5 }, { 0, 4, 5, 1 }, { 1, 5, 6, 2 }, { 2, 6, 7, 3 }, { 3, 7, 4, 0 } };
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (int[] f : faces) for (int i : f) quads.addVertex(p, c[i][0], c[i][1], c[i][2]).setColor(r, g, b, 0.2F);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
        for (int[] ed : edges) {
            float[] a = c[ed[0]];
            float nx = c[ed[1]][0] - a[0];
            float ny = c[ed[1]][1] - a[1];
            float nz = c[ed[1]][2] - a[2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            lines.addVertex(p, a[0], a[1], a[2]).setColor(r, g, b, 0.95F).setNormal(p, nx / len, ny / len, nz / len);
            lines.addVertex(p, c[ed[1]][0], c[ed[1]][1], c[ed[1]][2]).setColor(r, g, b, 0.95F).setNormal(p, nx / len, ny / len, nz / len);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}

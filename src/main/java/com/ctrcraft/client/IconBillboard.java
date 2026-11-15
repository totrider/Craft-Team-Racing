package com.ctrcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws a power-up's HUD icon in the world, turned to face the camera: over a crate that always gives that power-up, so the builder can see what it is from a distance. */
public final class IconBillboard {
    private IconBillboard() {}

    /** The icon of a power-up id, standing on the origin of the pose stack: {@code lift} blocks up, {@code height} blocks tall (the width keeps the picture's proportions). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, String itemIds, double lift, float height) {
        // a list of power-ups cycles, one every second and a half
        String itemId = com.ctrcraft.items.CrateItems.shown(itemIds, Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime());
        if (itemId.isEmpty()) return;
        ResourceLocation tex = GuiTextures.powerIcon(itemId);
        int[] size = GuiTextures.size(tex);
        if (size == null) return;
        float w = height * size[0] / size[1] * 0.7361F;
        int count = com.ctrcraft.items.PowerupRegistry.stackCount(itemId);
        pose.pushPose();
        pose.translate(0.0, lift, 0.0);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        vertex(vc, p, -w / 2, 0.0F, 0.0F, 1.0F);
        vertex(vc, p, w / 2, 0.0F, 1.0F, 1.0F);
        vertex(vc, p, w / 2, height, 1.0F, 0.0F);
        vertex(vc, p, -w / 2, height, 0.0F, 0.0F);
        if (count > 1) {
            // the count, in the game's font, at the picture's top left corner
            pose.pushPose();
            pose.translate(-w / 2 + 0.02F, height - 0.02F, 0.01F);
            float s = height * 0.025F;
            pose.scale(s, -s, s);
            Minecraft mc = Minecraft.getInstance();
            mc.font.drawInBatch8xOutline(net.minecraft.network.chat.Component.literal(Integer.toString(count)).getVisualOrderText(), 0.0F, 0.0F, 0xFFFFFFFF, 0xFF000000,
                    pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    /** How far the icon on a face of the crate reaches (of the face), and how far it stands off it, in blocks. */
    private static final float FACE_SIZE = 0.6F;
    private static final float FACE_OFFSET = 0.004F;

    /**
     * The crate's six faces each get the power-up's icon in the middle, where the "?" is: six flat quads a hair off the faces of the crate (an unseen, slightly larger box would be the same
     * six quads and would need a model of its own). The pose is the crate's, its box from (0, 0, 0) to (1, 1, 1); a list of power-ups cycles.
     */
    public static void drawFaces(PoseStack pose, MultiBufferSource buffers, String itemIds, long ticks, int light) {
        String itemId = com.ctrcraft.items.CrateItems.shown(itemIds, ticks);
        if (itemId.isEmpty()) return;
        ResourceLocation tex = GuiTextures.powerIcon(itemId);
        int[] size = GuiTextures.size(tex);
        if (size == null) return;
        // the original's pictures are drawn pre-squashed (a reference pixel is 0.7361 as wide as it is tall), so the picture on a face is that much narrower than its texel ratio says
        float aspect = size[0] / (float) size[1] * 0.7361F;
        float hw = FACE_SIZE / 2 * (aspect >= 1.0F ? 1.0F : aspect);
        float hh = FACE_SIZE / 2 * (aspect >= 1.0F ? 1.0F / aspect : 1.0F);
        float e = FACE_OFFSET;
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        //          centre                 right (u)            up (v)               normal
        face(vc, p, 0.5F, 0.5F, 1 + e, 1, 0, 0, 0, 1, 0, 0, 0, 1, hw, hh, light);
        face(vc, p, 0.5F, 0.5F, -e, -1, 0, 0, 0, 1, 0, 0, 0, -1, hw, hh, light);
        face(vc, p, 1 + e, 0.5F, 0.5F, 0, 0, -1, 0, 1, 0, 1, 0, 0, hw, hh, light);
        face(vc, p, -e, 0.5F, 0.5F, 0, 0, 1, 0, 1, 0, -1, 0, 0, hw, hh, light);
        face(vc, p, 0.5F, 1 + e, 0.5F, -1, 0, 0, 0, 0, 1, 0, 1, 0, hw, hh, light);
        face(vc, p, 0.5F, -e, 0.5F, 1, 0, 0, 0, 0, 1, 0, -1, 0, hw, hh, light);
        int count = com.ctrcraft.items.PowerupRegistry.stackCount(itemId);
        if (count > 1) {
            String text = Integer.toString(count);
            badge(pose, buffers, text, 0.5F, 0.5F, 1 + e, 1, 0, 0, 0, 1, 0, 0, 0, 1, hw, hh, light);
            badge(pose, buffers, text, 0.5F, 0.5F, -e, -1, 0, 0, 0, 1, 0, 0, 0, -1, hw, hh, light);
            badge(pose, buffers, text, 1 + e, 0.5F, 0.5F, 0, 0, -1, 0, 1, 0, 1, 0, 0, hw, hh, light);
            badge(pose, buffers, text, -e, 0.5F, 0.5F, 0, 0, 1, 0, 1, 0, -1, 0, 0, hw, hh, light);
            badge(pose, buffers, text, 0.5F, 1 + e, 0.5F, -1, 0, 0, 0, 0, 1, 0, 1, 0, hw, hh, light);
            badge(pose, buffers, text, 0.5F, -e, 0.5F, 1, 0, 0, 0, 0, 1, 0, -1, 0, hw, hh, light);
        }
    }

    /** The count on a face, in the game's font, at the icon's top left corner (the same frame as {@link #face}: centre, right, up, normal). */
    private static void badge(PoseStack pose, MultiBufferSource buffers, String text, float cx, float cy, float cz, float ux, float uy, float uz, float vx, float vy, float vz,
                              float nx, float ny, float nz, float hw, float hh, int light) {
        pose.pushPose();
        pose.last().pose().mul(new org.joml.Matrix4f(ux, uy, uz, 0, vx, vy, vz, 0, nx, ny, nz, 0, cx + nx * 0.002F, cy + ny * 0.002F, cz + nz * 0.002F, 1));
        float s = 0.012F;
        pose.scale(s, -s, s);
        Minecraft.getInstance().font.drawInBatch8xOutline(net.minecraft.network.chat.Component.literal(text).getVisualOrderText(), -hw / s, -hh / s, 0xFFFFFFFF, 0xFF000000,
                pose.last().pose(), buffers, light);
        pose.popPose();
    }

    private static void face(VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float cz, float ux, float uy, float uz, float vx, float vy, float vz, float nx, float ny, float nz,
                             float hw, float hh, int light) {
        float[][] corner = { { -1, -1, 0, 1 }, { 1, -1, 1, 1 }, { 1, 1, 1, 0 }, { -1, 1, 0, 0 } };
        for (float[] c : corner) {
            float x = cx + ux * c[0] * hw + vx * c[1] * hh;
            float y = cy + uy * c[0] * hw + vy * c[1] * hh;
            float z = cz + uz * c[0] * hw + vz * c[1] * hh;
            vc.addVertex(p, x, y, z).setColor(-1).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v) {
        vc.addVertex(p, x, y, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0.0F, 0.0F, 1.0F);
    }
}

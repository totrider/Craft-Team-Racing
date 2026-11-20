package com.craftteamracing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Vec3i;

/**
 * Draws a baked quad of an OBJ model with the normals of its vertices. Vanilla's own call for this ({@code putBulkData}) writes the quad's face direction as the normal of every
 * vertex, so a flask or a fruit comes out faceted; here each vertex keeps its own (smooth with vanilla lighting; some shader packs light it in facets anyway, see the README: known issues). A quad without vertex normals (a plain JSON model) is drawn the vanilla way.
 */
public final class SmoothQuads {
    private SmoothQuads() {}

    public static void put(PoseStack.Pose pose, VertexConsumer consumer, BakedQuad quad, int light) {
        int[] v = quad.getVertices();
        boolean hasNormals = false;
        for (int i = 0; i < 4; i++) {
            hasNormals |= (v[i * 8 + 7] & 0xFFFFFF) != 0;
        }
        if (!hasNormals) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
            return;
        }
        Vec3i dir = quad.getDirection().getNormal();
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            int packed = v[o + 7];
            float nx = ((byte) packed) / 127.0F;
            float ny = ((byte) (packed >> 8)) / 127.0F;
            float nz = ((byte) (packed >> 16)) / 127.0F;
            if (nx == 0.0F && ny == 0.0F && nz == 0.0F) {
                nx = dir.getX();
                ny = dir.getY();
                nz = dir.getZ();
            }
            consumer.addVertex(pose, Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2])).setColor(255, 255, 255, 255)
                    .setUv(Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5])).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}

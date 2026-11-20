package com.craftteamracing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The lightning round the Warp Orb (the End Crystal, in the stand-in's words): a few jagged bolts that jump from its middle in random directions and are redrawn every other tick, as thin
 * crossed ribbons of vanilla's lightning render type (bright core, blue halo), a stand-in for the electric flicker the original orb has. The pose is at the orb's middle.
 */
public final class OrbLightning {
    private OrbLightning() {}

    private static final int RANDOM_BOLTS = 5;
    private static final int SEGMENTS = 9;
    /** How far the bolts reach sideways and up, blocks: the original's star is about 7 across. */
    private static final float REACH = 4.6F;

    /** {@code groundDistance}: how far below the orb's middle the ground is; one bolt always runs down to it, and the others spread out as far as the original's star does. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, long seed, float groundDistance) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        RandomSource random = RandomSource.create(seed);
        Matrix4f m = pose.last().pose();
        // always one to the ground (and a second, now and then), the first bolts of the flicker
        bolt(vc, m, random, new Vector3f(0.0F, -1.0F, 0.0F), groundDistance, true);
        if (random.nextInt(3) != 0) bolt(vc, m, random, new Vector3f(random.nextFloat() - 0.5F, -1.0F, random.nextFloat() - 0.5F).normalize(), groundDistance * 1.02F, true);
        for (int b = 0; b < RANDOM_BOLTS; b++) {
            if (random.nextInt(4) == 0) continue;                              // not every bolt every flicker
            Vector3f dir = new Vector3f(random.nextFloat() * 2.0F - 1.0F, random.nextFloat() * 1.6F - 0.5F, random.nextFloat() * 2.0F - 1.0F);
            if (dir.lengthSquared() < 1.0E-4F) continue;
            dir.normalize();
            bolt(vc, m, random, dir, REACH * (0.55F + random.nextFloat() * 0.6F), false);
        }
    }

    /** One bolt from the orb's skin outwards along {@code dir}; {@code toGround}: it ends exactly {@code length} away along the way down, spread by its wandering. */
    private static void bolt(VertexConsumer vc, Matrix4f m, RandomSource random, Vector3f dir, float length, boolean toGround) {
        Vector3f at = new Vector3f(dir).mul(0.45F);
        float wander = 0.5F;
        float step = length / SEGMENTS;
        for (int s = 0; s < SEGMENTS; s++) {
            Vector3f next = new Vector3f(at).add(dir.x * step + (random.nextFloat() - 0.5F) * wander, dir.y * step + (random.nextFloat() - 0.5F) * wander * (toGround ? 0.25F : 1.0F),
                    dir.z * step + (random.nextFloat() - 0.5F) * wander);
            if (toGround && s == SEGMENTS - 1) next.y = -length;               // the last point is on the ground
            ribbon(vc, m, at, next, 0.20F, 40, 90, 255, 55);              // the wide blue glow
            ribbon(vc, m, at, next, 0.11F, 70, 130, 255, 120);
            ribbon(vc, m, at, next, 0.045F, 200, 225, 255, 255);          // the white-blue core
            at = next;
        }
    }

    /** A segment as two crossed flat ribbons, so it shows from every side. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float half, int r, int g, int bl, int alpha) {
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f p1 = d.cross(Math.abs(d.y) > 0.9F * d.length() ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0), new Vector3f());
        if (p1.lengthSquared() < 1.0E-8F) return;
        p1.normalize().mul(half);
        Vector3f p2 = d.cross(p1, new Vector3f()).normalize().mul(half);
        for (Vector3f p : new Vector3f[] { p1, p2 }) {
            vc.addVertex(m, a.x - p.x, a.y - p.y, a.z - p.z).setColor(r, g, bl, alpha);
            vc.addVertex(m, a.x + p.x, a.y + p.y, a.z + p.z).setColor(r, g, bl, alpha);
            vc.addVertex(m, b.x + p.x, b.y + p.y, b.z + p.z).setColor(r, g, bl, alpha);
            vc.addVertex(m, b.x - p.x, b.y - p.y, b.z - p.z).setColor(r, g, bl, alpha);
        }
    }
}

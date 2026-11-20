package com.craftteamracing.client;

import com.craftteamracing.pickup.WarpOrbEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * The particles round the Warp Orb (shown as the End Crystal): electric sparks jumping off it, purple motes of the end drawn in towards its middle from a few blocks away (a portal particle
 * starts at its velocity's distance from the place it is given and ends on it) and purple ones thrown out. Larger than vanilla's, and plenty, so they read at the orb's size.
 */
public final class OrbFx {
    private OrbFx() {}

    public static void tick(WarpOrbEntity orb) {
        if (!WarpOrbRenderer.jarOrb(orb)) return;                  // a pack brings the original's orb, and its look is all there is
        RandomSource r = orb.level().random;
        double cx = orb.getX();
        double cy = orb.getY() + WarpOrbRenderer.centreHeight(orb, 0.0F);
        double cz = orb.getZ();
        for (int i = 0; i < 4; i++) {
            spawn(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * 1.6, cy + (r.nextDouble() - 0.5) * 1.6, cz + (r.nextDouble() - 0.5) * 1.6,
                    (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 2.2F);
        }
        for (int i = 0; i < 8; i++) {
            // on a sphere of 2 to 3.5 blocks, drawn in
            double ux = r.nextGaussian();
            double uy = r.nextGaussian();
            double uz = r.nextGaussian();
            double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (l < 1.0E-3) continue;
            double d = 2.0 + r.nextDouble() * 1.5;
            spawn(ParticleTypes.PORTAL, cx, cy, cz, ux / l * d, uy / l * d, uz / l * d, 2.6F);
        }
        for (int i = 0; i < 2; i++) {
            spawn(ParticleTypes.REVERSE_PORTAL, cx + (r.nextDouble() - 0.5), cy + (r.nextDouble() - 0.5), cz + (r.nextDouble() - 0.5), (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.3) * 0.3,
                    (r.nextDouble() - 0.5) * 0.3, 2.2F);
        }
    }

    private static void spawn(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz, float scale) {
        Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        if (p != null) p.scale(scale);
    }
}

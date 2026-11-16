package com.ctrcraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Confetti and streamers for the winners on the podium: small flat pieces and long ribbons in bright colours, thrown up, then drifting down turning over and swaying. Each piece picks its own
 * colour (a bright, saturated one). The picture is a white shape the colour is applied to; they are drawn at full brightness.
 */
public class ConfettiParticle extends TextureSheetParticle {
    private final boolean streamer;
    private final float spin;
    private final float swayPhase;

    protected ConfettiParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean streamer) {
        super(level, x, y, z);
        this.streamer = streamer;
        this.pickSprite(sprites);
        // a little up and out, whatever the velocity the server gave (it only says "a piece here")
        this.xd = vx * 0.6 + (random.nextDouble() - 0.5) * 0.12;
        this.zd = vz * 0.6 + (random.nextDouble() - 0.5) * 0.12;
        this.yd = 0.04 + random.nextDouble() * 0.16;
        this.gravity = streamer ? 0.018F : 0.03F;
        this.friction = streamer ? 0.93F : 0.95F;
        this.hasPhysics = false;
        this.lifetime = streamer ? 60 + random.nextInt(40) : 45 + random.nextInt(35);
        this.quadSize = streamer ? 0.2F + random.nextFloat() * 0.12F : 0.04F + random.nextFloat() * 0.04F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin = (random.nextFloat() - 0.5F) * (streamer ? 0.25F : 0.6F);
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        // a bright colour: a random hue at full saturation and value
        int rgb = Mth.hsvToRgb(random.nextFloat(), 0.8F + random.nextFloat() * 0.2F, 1.0F);
        this.rCol = ((rgb >> 16) & 255) / 255.0F;
        this.gCol = ((rgb >> 8) & 255) / 255.0F;
        this.bCol = (rgb & 255) / 255.0F;
    }

    @Override
    public void tick() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // drifting down it sways from side to side and turns over
        double sway = Math.sin(age * 0.35 + swayPhase) * (streamer ? 0.012 : 0.02);
        this.xd += sway;
        this.zd += Math.cos(age * 0.31 + swayPhase) * (streamer ? 0.012 : 0.02);
        this.yd -= 0.04 * gravity * 10.0;
        if (yd < (streamer ? -0.05 : -0.08)) yd = streamer ? -0.05 : -0.08;       // a piece of paper does not fall fast
        this.x += xd;
        this.y += yd;
        this.z += zd;
        this.xd *= friction;
        this.zd *= friction;
        this.roll += spin;
        if (age > lifetime - 12) this.alpha = Math.max(0.0F, (lifetime - age) / 12.0F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean streamer;

        public Provider(SpriteSet sprites, boolean streamer) {
            this.sprites = sprites;
            this.streamer = streamer;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ConfettiParticle(level, x, y, z, vx, vy, vz, sprites, streamer);
        }
    }
}

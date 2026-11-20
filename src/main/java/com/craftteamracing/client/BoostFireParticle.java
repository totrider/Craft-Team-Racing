package com.craftteamracing.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A spark of vanilla fire thrown out of an exhaust pipe while the boost flame burns: it lives a few ticks, drifts back along the pipe and shrinks, and like the exhaust smoke
 * ({@link ExhaustSmokeParticle}) it belongs to the kart: it moves in the kart's own frame, so it follows the kart and turns with it. The flame picture is vanilla's (the blue one, from the
 * soul fire, for the super turbo).
 */
public class BoostFireParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private com.craftteamracing.kart.KartEntity anchor;
    private double px;
    private double py;
    private double pz;
    private double vlx;
    private double vly;
    private double vlz;
    private final float startSize;

    /** Ties the spark to a kart: {@code (x, y, z)} is where it starts in the kart's frame and {@code (vx, vy, vz)} how it moves there each tick. */
    public void anchor(com.craftteamracing.kart.KartEntity kart, double x, double y, double z, double vx, double vy, double vz) {
        this.anchor = kart;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.vlx = vx;
        this.vly = vy;
        this.vlz = vz;
    }

    protected BoostFireParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        this.lifetime = 4 + random.nextInt(4);
        this.startSize = 0.07F + random.nextFloat() * 0.06F;
        this.quadSize = startSize;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 0.9F;
        pickSprite(sprites);
    }

    private void place(float partialTick) {
        if (anchor == null || anchor.isRemoved()) return;
        net.minecraft.world.phys.Vec3 k = anchor.getPosition(partialTick);
        org.joml.Vector3f at = KartRenderer.bodyMatrix(anchor, partialTick).transformPosition((float) (px + vlx * partialTick), (float) (py + vly * partialTick), (float) (pz + vlz * partialTick),
                new org.joml.Vector3f());
        this.x = k.x + at.x;
        this.y = k.y + at.y;
        this.z = k.z + at.z;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
        place(partialTicks);
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public void tick() {
        px += vlx;
        py += vly;
        pz += vlz;
        vlx *= friction;
        vly *= friction;
        vlz *= friction;
        if (age++ >= lifetime || anchor == null || anchor.isRemoved()) {
            remove();
            return;
        }
        float life = age / (float) lifetime;
        this.quadSize = startSize * (1.0F - 0.75F * life);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BoostFireParticle(level, x, y, z, sprites);
        }
    }
}

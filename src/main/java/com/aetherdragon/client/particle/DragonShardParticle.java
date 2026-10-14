package com.aetherdragon.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;

import net.minecraft.core.particles.SimpleParticleType;

public class DragonShardParticle extends GeometryParticle {
    private final float spin;
    private final float baseSize;

    protected DragonShardParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.9F;
        this.friction = 0.94F;
        this.hasPhysics = true;
        this.lifetime = 30 + this.random.nextInt(25);
        this.baseSize = 0.22F + this.random.nextFloat() * 0.2F;
        this.quadSize = this.baseSize;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.6F;
        this.roll = this.random.nextFloat() * 6.2832F;
        this.oRoll = this.roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spin;
        float left = (float) (this.lifetime - this.age) / 10.0F;
        if (left < 1.0F) {
            this.quadSize = this.baseSize * Math.max(0.0F, left);
        }
    }

    @Override
    protected int getLightCoords(float partialTicks) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getGroup() {
        return RebornParticleGroup.TYPE;
    }

    public static final class ShortProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ShortProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            DragonShardParticle particle = new DragonShardParticle(level, x, y, z, xd, yd, zd, this.sprites);
            particle.setLifetime(10 + level.getRandom().nextInt(8));
            return particle;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            return new DragonShardParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}

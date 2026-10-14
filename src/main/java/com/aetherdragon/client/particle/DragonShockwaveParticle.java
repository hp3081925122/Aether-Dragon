package com.aetherdragon.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class DragonShockwaveParticle extends GeometryParticle {
    private final float maxRadius;
    private final boolean warning;
    private boolean linear;

    protected DragonShockwaveParticle(ClientLevel level, double x, double y, double z, double radius, double life, boolean warning, SpriteSet sprites) {
        super(level, x, y, z);
        this.warning = warning;
        this.maxRadius = (float) Math.max(1.0D, radius);
        this.lifetime = Math.max(4, (int) life);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.position();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float progress = Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
        float inverse = 1.0F - progress;
        float radius = this.maxRadius * (1.0F - inverse * inverse * inverse);
        float alpha = 1.0F - progress * progress;
        if (this.linear) {
            radius = this.maxRadius * progress;
            alpha = Math.min(1.0F, (1.0F - progress) * 3.0F);
        }
        if (this.warning) {
            radius = 0.4F + (this.maxRadius - 0.4F) * inverse;
            alpha = 0.35F + 0.65F * progress;
            if (((this.age / 2) & 1) == 0) {
                alpha *= 0.75F;
            }
        }
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = 15728880;
        buffer.addVertex(px - radius, py, pz - radius).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px - radius, py, pz + radius).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px + radius, py, pz + radius).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px + radius, py, pz - radius).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px + radius, py, pz - radius).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px + radius, py, pz + radius).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px - radius, py, pz + radius).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(px - radius, py, pz - radius).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
    }


    @Override
    public ParticleRenderType getGroup() {
        return RebornParticleGroup.TYPE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            DragonShockwaveParticle particle = new DragonShockwaveParticle(level, x, y, z, xd, yd, false, this.sprites);
            if (zd > 0.5D) {
                particle.linear = true;
            }
            return particle;
        }
    }

    public static final class PlainWarningProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public PlainWarningProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            return new DragonShockwaveParticle(level, x, y, z, xd, yd, true, this.sprites);
        }
    }

    public static final class WarningProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public WarningProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            DragonShockwaveParticle particle = new DragonShockwaveParticle(level, x, y, z, xd, yd, true, this.sprites);
            if (zd > 0.5D) {
                particle.setColor(1.0F, 0.85F, 0.35F);
            } else {
                particle.setColor(1.0F, 0.45F, 0.3F);
            }
            return particle;
        }
    }
}
